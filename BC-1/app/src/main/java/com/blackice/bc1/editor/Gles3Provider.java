package com.blackice.bc1.editor;

import android.opengl.*;
import androidx.media3.common.*;
import androidx.media3.common.util.GlUtil;
import androidx.media3.effect.DefaultGlObjectsProvider;

/** Require the GLES 3.0 backend used by BC-1; MediaCodec surfaces stay on the GPU. */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class Gles3Provider implements GlObjectsProvider {
    private final DefaultGlObjectsProvider delegate=new DefaultGlObjectsProvider();
    @Override public EGLContext createEglContext(EGLDisplay d,int version,int[] config) throws GlUtil.GlException {
        return delegate.createEglContext(d,Math.max(3,version),config);
    }
    @Override public EGLSurface createEglSurface(EGLDisplay d,Object surface,int transfer,boolean encoder) throws GlUtil.GlException {
        return delegate.createEglSurface(d,surface,transfer,encoder);
    }
    @Override public EGLSurface createFocusedPlaceholderEglSurface(EGLContext c,EGLDisplay d) throws GlUtil.GlException {
        return delegate.createFocusedPlaceholderEglSurface(c,d);
    }
    @Override public GlTextureInfo createBuffersForTexture(int id,int w,int h) throws GlUtil.GlException {
        return delegate.createBuffersForTexture(id,w,h);
    }
    @Override public void release(EGLDisplay d) throws GlUtil.GlException { delegate.release(d); }
}
