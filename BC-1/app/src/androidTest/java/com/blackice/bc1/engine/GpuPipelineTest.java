package com.blackice.bc1.engine;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.opengl.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import java.nio.ByteBuffer;
import static org.junit.Assert.*;

/** Executes production shaders and renderer in a real GLES3 pbuffer, including context loss. */
@RunWith(AndroidJUnit4.class)
public class GpuPipelineTest {
    private EGLDisplay display;
    private EGLContext context;
    private EGLSurface surface;
    private EngineSettings settings;
    private BcRenderer renderer;
    @Before public void setup() {
        createContext();
        settings=EngineSettings.defaults();
        renderer=new BcRenderer(InstrumentationRegistry.getInstrumentation().getTargetContext(),settings,(g,f,w,h)->{});
        renderer.onSurfaceCreated(null,null);
        renderer.onSurfaceChanged(null,160,90);
        renderer.setInput(0,solid(Color.RED));renderer.setInput(1,solid(Color.BLUE));
    }
    private Bitmap solid(int color) {Bitmap b=Bitmap.createBitmap(160,90,Bitmap.Config.ARGB_8888);b.eraseColor(color);return b;}
    private void createContext() {
        display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        assertTrue(EGL14.eglInitialize(display,new int[2],0,new int[2],0));
        EGLConfig[] configs=new EGLConfig[1];int[] count=new int[1];
        assertTrue(EGL14.eglChooseConfig(display,new int[]{EGL14.EGL_SURFACE_TYPE,EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RENDERABLE_TYPE,0x40,EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_NONE},0,configs,0,1,count,0));
        assertTrue(count[0]>0);
        context=EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,3,EGL14.EGL_NONE},0);
        surface=EGL14.eglCreatePbufferSurface(display,configs[0],new int[]{EGL14.EGL_WIDTH,160,EGL14.EGL_HEIGHT,90,EGL14.EGL_NONE},0);
        assertTrue(EGL14.eglMakeCurrent(display,surface,surface,context));
    }
    private ByteBuffer frame() {
        renderer.onDrawFrame(null);
        ByteBuffer pixels=ByteBuffer.allocateDirect(160*90*4);
        GLES30.glReadPixels(0,0,160,90,GLES30.GL_RGBA,GLES30.GL_UNSIGNED_BYTE,pixels);
        assertEquals("GLES error",GLES30.GL_NO_ERROR,GLES30.glGetError());
        return pixels;
    }
    private void assertColor(ByteBuffer pixels,int r,int g,int b) {
        for(int i=0;i<160*90;i++) {
            assertEquals(r,pixels.get(i*4)&255,2);assertEquals(g,pixels.get(i*4+1)&255,2);
            assertEquals(b,pixels.get(i*4+2)&255,2);
        }
    }
    @Test public void everyTransitionHasExactEndpoints() {
        for(int easing=0;easing<2;easing++) for(int t=0;t<EngineSettings.TRANSITIONS.length;t++) {
            settings.easing=easing;settings.transition=t;
            settings.progress=0;assertColor(frame(),255,0,0);
            settings.progress=1;assertColor(frame(),0,0,255);
            settings.progress=.5f;frame();
        }
    }
    @Test public void everyEffectRendersAtEveryQualityAndStrength() {
        for(int q=0;q<3;q++) for(int e=0;e<EngineSettings.EFFECTS.length;e++) for(float k:new float[]{0,.5f,1}) {
            settings.quality=q;settings.effect=e;settings.intensity=k;frame();
        }
    }
    @Test public void opacityAndDipMidpointAreExact() {
        settings.opacity=0;assertColor(frame(),0,0,255);
        settings.opacity=1;settings.transition=6;settings.progress=.5f;assertColor(frame(),0,0,0);
    }
    @Test public void blurPreservesImageOrientationAndSurvivesContextLoss() {
        Bitmap b=solid(Color.BLUE);
        for(int y=0;y<45;y++) for(int x=0;x<160;x++) b.setPixel(x,y,Color.RED);
        renderer.setInput(0,b);settings.effect=1;settings.intensity=1;
        ByteBuffer pixels=frame();
        assertEquals(255,pixels.get((75*160+80)*4)&255,3);
        assertEquals(255,pixels.get((15*160+80)*4+2)&255,3);
        destroyContext();createContext();
        renderer.onSurfaceCreated(null,null);renderer.onSurfaceChanged(null,160,90);
        pixels=frame();assertEquals(255,pixels.get((75*160+80)*4)&255,3);
        renderer.setInput(0,solid(Color.GREEN));assertColor(frame(),0,255,0);
    }
    private void destroyContext() {
        EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
        EGL14.eglDestroySurface(display,surface);EGL14.eglDestroyContext(display,context);EGL14.eglTerminate(display);
    }
    @After public void cleanup(){destroyContext();}
}
