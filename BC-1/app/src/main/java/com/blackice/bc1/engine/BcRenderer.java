package com.blackice.bc1.engine;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** BC-1 OpenGL ES 3.0 backend: 2-layer input -> optional Gaussian passes -> transition composite. */
public final class BcRenderer implements GLSurfaceView.Renderer {
    public interface StatusListener { void onStatus(String gpu, int fps, int workWidth, int workHeight); }

    private final Context appContext;
    private final EngineSettings settings;
    private final StatusListener listener;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Bitmap[] sourceBitmaps = new Bitmap[2];
    private final int[] imageTextures = new int[2];
    private final int[] blurTextures = new int[2];
    private final int[] blurFbos = new int[2];
    private int surfaceWidth, surfaceHeight;
    private int workWidth, workHeight;
    private int quadVao, quadVbo;
    private int blurProgram, compositeProgram;
    private int configuredQuality = -1;
    private final Map<String,Integer> uniforms = new HashMap<>();
    private final boolean[] inputDirty = {false,false};
    private boolean blurDirty = true;
    private float blurredIntensity = -1f;
    private long fpsStartNs = 0;
    private int frames = 0;
    private String gpuName = "Unknown GPU";

    public BcRenderer(Context context, EngineSettings config, StatusListener callback) {
        appContext = context.getApplicationContext();
        settings = config;
        listener = callback;
        sourceBitmaps[0] = makeDemo("A  /  ICE", 0xFF0C4165, 0xFF11B3DB, 0xFF9BEAFF);
        sourceBitmaps[1] = makeDemo("B  /  EMBER", 0xFF572431, 0xFFE9744A, 0xFFFFDD9A);
    }

    /** MUST be called on the GL thread via GLSurfaceView.queueEvent(). */
    public void setInput(int slot, Bitmap bitmap) {
        if (slot < 0 || slot > 1) return;
        Bitmap previous=sourceBitmaps[slot];
        sourceBitmaps[slot]=bitmap;
        if(previous!=bitmap && previous!=null) previous.recycle();
        inputDirty[slot]=true;
        if(slot==0) blurDirty=true;
    }

    @Override public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        uniforms.clear();
        blurDirty=true;
        gpuName = GLES30.glGetString(GLES30.GL_RENDERER);
        if (gpuName == null) gpuName = "Unknown GPU";
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        GLES30.glDisable(GLES30.GL_BLEND);
        blurProgram = makeProgram(readShader("shaders/fullscreen.vert"), readShader("shaders/gaussian.frag"));
        compositeProgram = makeProgram(readShader("shaders/fullscreen.vert"), readShader("shaders/composite.frag"));
        float[] quad = {
                -1f,-1f, 0f,1f,
                 1f,-1f, 1f,1f,
                -1f, 1f, 0f,0f,
                 1f, 1f, 1f,0f
        };
        FloatBuffer data = ByteBuffer.allocateDirect(quad.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        data.put(quad).position(0);
        int[] ids = new int[1];
        GLES30.glGenVertexArrays(1, ids, 0);
        quadVao = ids[0];
        GLES30.glGenBuffers(1, ids, 0);
        quadVbo = ids[0];
        GLES30.glBindVertexArray(quadVao);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, quadVbo);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, quad.length*4, data, GLES30.GL_STATIC_DRAW);
        GLES30.glEnableVertexAttribArray(0);
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 16, 0);
        GLES30.glEnableVertexAttribArray(1);
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 16, 8);
        GLES30.glBindVertexArray(0);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);
        for (int i = 0; i < 2; i++) { imageTextures[i] = upload(sourceBitmaps[i]); inputDirty[i]=false; }
        cacheUniforms(blurProgram,new String[]{"uSource","uTexelStep","uRadius"});
        cacheUniforms(compositeProgram,new String[]{"uInputA","uInputB","uBlurredA","uProgress","uIntensity","uTransition","uEffect","uOpacity","uEasing","uInputTexel"});
        // Android may recreate the EGL context after switching apps.
        configuredQuality = -1;
        blurTextures[0] = blurTextures[1] = 0;
        blurFbos[0] = blurFbos[1] = 0;
        fpsStartNs = System.nanoTime();
        frames = 0;
        final String name=gpuName;
        ui.post(()->listener.onStatus(name,0,Math.round(1280*settings.renderScale()),Math.round(720*settings.renderScale())));
    }

    @Override public void onSurfaceChanged(GL10 gl, int width, int height) {
        surfaceWidth = Math.max(1,width);
        surfaceHeight = Math.max(1,height);
    }

    @Override public void onDrawFrame(GL10 gl) {
        if (surfaceWidth <= 0 || surfaceHeight <= 0) return;
        for(int i=0;i<2;i++) if(inputDirty[i]) {
            if(imageTextures[i]!=0) GLES30.glDeleteTextures(1,new int[]{imageTextures[i]},0);
            imageTextures[i]=upload(sourceBitmaps[i]); inputDirty[i]=false;
        }
        int effect = settings.effect;
        float intensity=settings.intensity;
        if (effect == 1 || effect == 2) {
            int quality=settings.quality;
            if(configuredQuality!=quality) resizeBuffers(quality);
            if(blurDirty || blurredIntensity!=intensity) {
            blurPass(imageTextures[0], blurFbos[0], 1f/workWidth, 0f,intensity);
            blurPass(blurTextures[0], blurFbos[1], 0f, 1f/workHeight,intensity);
            blurredIntensity=intensity; blurDirty=false;
            }
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
        GLES30.glViewport(0,0,surfaceWidth,surfaceHeight);
        GLES30.glClearColor(.025f,.035f,.055f,1f);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);

        // Always letterbox to the engine's 16:9 working canvas; never stretch imported images.
        float panelAspect = surfaceWidth/(float)surfaceHeight;
        float canvasAspect = 16f/9f;
        int vw = surfaceWidth, vh = surfaceHeight;
        if (panelAspect > canvasAspect) vw = Math.max(1, Math.round(surfaceHeight*canvasAspect));
        else vh = Math.max(1, Math.round(surfaceWidth/canvasAspect));
        GLES30.glViewport((surfaceWidth-vw)/2,(surfaceHeight-vh)/2,vw,vh);
        GLES30.glUseProgram(compositeProgram);
        bind(0,imageTextures[0],compositeProgram,"uInputA");
        bind(1,imageTextures[1],compositeProgram,"uInputB");
        bind(2,blurTextures[1] != 0 ? blurTextures[1] : imageTextures[0],compositeProgram,"uBlurredA");
        GLES30.glUniform1f(uniform(compositeProgram,"uProgress"), settings.progress);
        GLES30.glUniform1f(uniform(compositeProgram,"uIntensity"), intensity);
        GLES30.glUniform1i(uniform(compositeProgram,"uTransition"), settings.transition);
        GLES30.glUniform1i(uniform(compositeProgram,"uEffect"), effect);
        GLES30.glUniform1f(uniform(compositeProgram,"uOpacity"), settings.opacity);
        GLES30.glUniform1i(uniform(compositeProgram,"uEasing"), settings.easing);
        GLES30.glUniform2f(uniform(compositeProgram,"uInputTexel"),1f/sourceBitmaps[0].getWidth(),1f/sourceBitmaps[0].getHeight());
        drawQuad();

        frames++;
        long now = System.nanoTime();
        if (fpsStartNs == 0) fpsStartNs = now;
        long span = now-fpsStartNs;
        if (span >= 1_000_000_000L) {
            final int fps = (int)Math.round(frames*1_000_000_000.0/span);
            final int bw = Math.round(1280*settings.renderScale()), bh = Math.round(720*settings.renderScale());
            final String name = gpuName;
            ui.post(() -> listener.onStatus(name,fps,bw,bh));
            fpsStartNs = now;
            frames = 0;
        }
    }

    private void resizeBuffers(int quality) {
        for (int i=0; i<2; i++) {
            if (blurTextures[i] != 0) GLES30.glDeleteTextures(1,new int[]{blurTextures[i]},0);
            if (blurFbos[i] != 0) GLES30.glDeleteFramebuffers(1,new int[]{blurFbos[i]},0);
        }
        // Bounded and aspect-correct offscreen processing, safe for mobile memory budgets.
        workWidth = Math.max(256,Math.round(1280f*(quality==0?.5f:quality==2?1f:.75f)));
        workHeight = Math.max(144,Math.round(720f*(quality==0?.5f:quality==2?1f:.75f)));
        GLES30.glGenTextures(2,blurTextures,0);
        GLES30.glGenFramebuffers(2,blurFbos,0);
        for (int i=0; i<2; i++) {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,blurTextures[i]);
            texParams();
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D,0,GLES30.GL_RGBA8,workWidth,workHeight,0,GLES30.GL_RGBA,GLES30.GL_UNSIGNED_BYTE,null);
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,blurFbos[i]);
            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER,GLES30.GL_COLOR_ATTACHMENT0,GLES30.GL_TEXTURE_2D,blurTextures[i],0);
            int state = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER);
            if (state != GLES30.GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("BC-1 FBO incomplete: "+state);
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,0);
        configuredQuality = quality;
        blurDirty=true;
    }

    private void blurPass(int source, int framebuffer, float dx, float dy,float intensity) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,framebuffer);
        GLES30.glViewport(0,0,workWidth,workHeight);
        GLES30.glUseProgram(blurProgram);
        bind(0,source,blurProgram,"uSource");
        GLES30.glUniform2f(uniform(blurProgram,"uTexelStep"), dx,dy);
        GLES30.glUniform1f(uniform(blurProgram,"uRadius"), .25f + 5.5f*intensity);
        drawQuad();
    }

    private void bind(int unit, int tex, int program, String name) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0+unit);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,tex);
        GLES30.glUniform1i(uniform(program,name),unit);
    }
    private void cacheUniforms(int program,String[] names) {
        for(String name:names) uniforms.put(program+":"+name,GLES30.glGetUniformLocation(program,name));
    }
    private int uniform(int program,String name) { return uniforms.get(program+":"+name); }
    private void drawQuad() {
        GLES30.glBindVertexArray(quadVao);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP,0,4);
        GLES30.glBindVertexArray(0);
    }
    private static void texParams() {
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MIN_FILTER,GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MAG_FILTER,GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_S,GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_T,GLES30.GL_CLAMP_TO_EDGE);
    }
    private static int upload(Bitmap bitmap) {
        int[] id = new int[1];
        GLES30.glGenTextures(1,id,0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,id[0]);
        texParams();
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D,0,bitmap,0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,0);
        return id[0];
    }
    private String readShader(String name) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(appContext.getAssets().open(name)))) {
            StringBuilder s = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) s.append(line).append('\n');
            return s.toString();
        } catch(IOException ex) { throw new IllegalStateException("Cannot read shader "+name,ex); }
    }
    private static int compile(int type, String src) {
        int shader = GLES30.glCreateShader(type);
        GLES30.glShaderSource(shader,src);
        GLES30.glCompileShader(shader);
        int[] ok = new int[1];
        GLES30.glGetShaderiv(shader,GLES30.GL_COMPILE_STATUS,ok,0);
        if (ok[0] == 0) {
            String log = GLES30.glGetShaderInfoLog(shader);
            GLES30.glDeleteShader(shader);
            throw new IllegalStateException("Shader compile error: "+log);
        }
        return shader;
    }
    private static int makeProgram(String vs,String fs) {
        int v=compile(GLES30.GL_VERTEX_SHADER,vs), f=compile(GLES30.GL_FRAGMENT_SHADER,fs);
        int program=GLES30.glCreateProgram();
        GLES30.glAttachShader(program,v);
        GLES30.glAttachShader(program,f);
        GLES30.glLinkProgram(program);
        int[] ok=new int[1];
        GLES30.glGetProgramiv(program,GLES30.GL_LINK_STATUS,ok,0);
        GLES30.glDeleteShader(v);
        GLES30.glDeleteShader(f);
        if (ok[0]==0) throw new IllegalStateException("Program link error: "+GLES30.glGetProgramInfoLog(program));
        return program;
    }
    private static Bitmap makeDemo(String title, int start, int end, int accent) {
        final int w=1024,h=576;
        Bitmap bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0,0,w,h,start,end, Shader.TileMode.CLAMP));
        canvas.drawRect(0,0,w,h,p);
        p.setShader(null);
        p.setColor(accent);
        p.setAlpha(160);
        canvas.drawCircle(w*.77f,h*.38f,178,p);
        p.setAlpha(220);
        canvas.drawRoundRect(70,70,525,390,55,55,p);
        p.setColor(Color.rgb(10,17,32));
        p.setTextSize(56);
        p.setFakeBoldText(true);
        canvas.drawText(title,105,270,p);
        p.setTextSize(27);
        canvas.drawText("BC-1 / GPU ENGINE LAB",105,320,p);
        return bitmap;
    }
}
