package com.blackice.bc1.editor;

import android.content.Context;
import android.opengl.GLES30;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.*;
import androidx.media3.effect.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** GLES 3 port of the v0.3 effects, with deterministic timeline-time transition masks. */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class ClipEffect implements GlEffect {
    private final EditProject.Placement placement;
    private final EditProject.Clip previous;
    private final boolean top,visible;
    public ClipEffect(EditProject.Placement placement,EditProject.Clip previous){this(placement,previous,placement.index%2==0);}
    public ClipEffect(EditProject.Placement placement,EditProject.Clip previous,boolean top){this(placement,previous,top,true);}
    /** Hidden layers keep their decoder slot and timing but contribute no pixels. */
    public ClipEffect(EditProject.Placement placement,EditProject.Clip previous,boolean top,boolean visible){this.placement=placement;this.previous=previous;this.top=top;this.visible=visible;}
    @Override public GlShaderProgram toGlShaderProgram(Context context,boolean useHdr) throws VideoFrameProcessingException {
        return new Program(context,placement,previous,top,visible,useHdr);
    }
    private static final class Program extends BaseGlShaderProgram {
        private final GlProgram program;
        private final EditProject.Placement p;
        private final EditProject.Clip previous;
        private final boolean top,visible;
        Program(Context context,EditProject.Placement p,EditProject.Clip previous,boolean top,boolean visible,boolean hdr) throws VideoFrameProcessingException {
            super(hdr,1); this.p=p; this.previous=previous;this.top=top;this.visible=visible;
            try {
                String vertex,fragment;
                try(java.io.InputStream in=context.getAssets().open("shaders/video.vert")) { vertex=readShader(in); }
                try(java.io.InputStream in=context.getAssets().open("shaders/video.frag")) { fragment=readShader(in); }
                program=new GlProgram(vertex,fragment);
                program.setBufferAttribute("aPosition",GlUtil.getNormalizedCoordinateBounds(),4);
            } catch(IOException|GlUtil.GlException e) { throw new VideoFrameProcessingException(e); }
        }
        private static String readShader(java.io.InputStream in) throws IOException {
            java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;
            while((n=in.read(buffer))!=-1)out.write(buffer,0,n);return out.toString("UTF-8");
        }
        @Override public Size configure(int w,int h) {
            program.setFloatsUniform("uTexel",new float[]{1f/w,1f/h});
            program.setFloatUniform("uAspect",(float)w/h);
            return new Size(w,h);
        }
        @Override public void drawFrame(int tex,long timeUs) throws VideoFrameProcessingException {
            long timeMs=timeUs/1000; EditProject.Clip c=p.clip;
            int type=-1,role=0,easing=c.easing; float progress=0;
            if(p.incomingMs>0 && timeMs<p.startMs+p.incomingMs && previous!=null) {
                type=previous.transition; role=1; easing=previous.easing;
                progress=(float)(timeMs-p.startMs)/p.incomingMs;
            } else if(p.outgoingMs>0 && timeMs>=p.endMs-p.outgoingMs) {
                type=c.transition; role=2; progress=(float)(timeMs-(p.endMs-p.outgoingMs))/p.outgoingMs;
            }
            progress=Math.max(0,Math.min(1,progress));
            if(easing==1) progress=progress*progress*(3-2*progress);
            try {
                program.use(); program.setSamplerTexIdUniform("uInput",tex,0);
                long sourceTime=Math.round(Math.max(0,timeMs-p.startMs)*c.speed)+c.inMs;
                TransformMath.Transform t=TransformMath.of(c,sourceTime);
                program.setFloatsUniform("uTransform",new float[]{t.x,t.y,t.scale,(float)Math.toRadians(t.rotation)});
                program.setFloatsUniform("uShape",new float[]{t.flipH?-1:1,t.aspect*(t.flipV?-1:1),(float)Math.tan(Math.toRadians(t.skewX)),(float)Math.tan(Math.toRadians(t.skewY))});
                program.setFloatsUniform("uAnchor",new float[]{t.anchorX,t.anchorY});
                program.setFloatsUniform("uKeyColor",new float[]{((c.keyColor>>16)&255)/255f,((c.keyColor>>8)&255)/255f,(c.keyColor&255)/255f});
                program.setFloatsUniform("uChroma",new float[]{c.chroma?1:0,c.keyTolerance,c.keySoftness,c.keySpill});
                program.setIntUniform("uEffect",c.effect); program.setFloatUniform("uIntensity",c.motion.value(5,sourceTime,c.intensity));
                program.setFloatUniform("uOpacity",visible?c.motion.value(4,sourceTime,c.opacity):0); program.setIntUniform("uTransition",type);
                program.setIntUniform("uRole",role); program.setIntUniform("uTop",top?1:0);
                program.setFloatUniform("uProgress",progress); program.bindAttributesAndUniforms();
                GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP,0,4); GlUtil.checkGlError();
            } catch(GlUtil.GlException e) { throw new VideoFrameProcessingException(e,timeUs); }
        }
        @Override public void release() throws VideoFrameProcessingException {
            super.release(); try {program.delete();} catch(GlUtil.GlException e) {throw new VideoFrameProcessingException(e);}
        }
    }
}
