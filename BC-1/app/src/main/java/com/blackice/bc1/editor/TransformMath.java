package com.blackice.bc1.editor;

/** Layer transform geometry shared by the preview box, hit testing and the GLES shader.
 *
 * Coordinates are "frame units": the frame spans [-A/2, A/2] × [-1/2, 1/2] with y up, where A is the
 * frame aspect (width / height). Position and anchor values use the project's ±1 = frame-edge units.
 * A local point l maps to the frame as  s = T + a + M (l − a),  M = R(rotation) · S(scale) · K(skew),
 * which is exactly the inverse the fragment shader evaluates. */
public final class TransformMath {
    private TransformMath() {}

    public static final class Transform {
        public float x,y,scale=1,aspect=1,rotation,skewX,skewY,anchorX,anchorY; public boolean flipH,flipV;
        public Transform copy(){Transform t=new Transform();t.x=x;t.y=y;t.scale=scale;t.aspect=aspect;t.rotation=rotation;t.skewX=skewX;t.skewY=skewY;t.anchorX=anchorX;t.anchorY=anchorY;t.flipH=flipH;t.flipV=flipV;return t;}
    }
    /** Static values, animated where keyframes exist, at clip source time / overlay local time. */
    public static Transform of(EditProject.Clip c,long sourceTime) {
        Transform t=new Transform();Motion m=c.motion;
        t.x=m.value(Motion.X,sourceTime,c.x);t.y=m.value(Motion.Y,sourceTime,c.y);t.scale=m.value(Motion.SCALE,sourceTime,c.scale);t.rotation=m.value(Motion.ROTATION,sourceTime,c.rotation);
        t.skewX=m.value(Motion.SKEW_X,sourceTime,c.skewX);t.skewY=m.value(Motion.SKEW_Y,sourceTime,c.skewY);t.anchorX=m.value(Motion.ANCHOR_X,sourceTime,c.anchorX);t.anchorY=m.value(Motion.ANCHOR_Y,sourceTime,c.anchorY);
        t.aspect=c.aspect;t.flipH=c.flipH;t.flipV=c.flipV;return t;
    }
    public static Transform of(EditProject.Overlay o,long localTime) {
        Transform t=new Transform();Motion m=o.motion;
        t.x=m.value(Motion.X,localTime,o.x);t.y=m.value(Motion.Y,localTime,o.y);t.scale=m.value(Motion.SCALE,localTime,o.scale);t.rotation=m.value(Motion.ROTATION,localTime,o.rotation);
        t.skewX=m.value(Motion.SKEW_X,localTime,o.skewX);t.skewY=m.value(Motion.SKEW_Y,localTime,o.skewY);t.anchorX=m.value(Motion.ANCHOR_X,localTime,o.anchorX);t.anchorY=m.value(Motion.ANCHOR_Y,localTime,o.anchorY);
        t.aspect=o.aspect;t.flipH=o.flipH;t.flipV=o.flipV;return t;
    }
    /** 2×2 matrix M as {m00, m01, m10, m11}. */
    public static double[] matrix(Transform t) {
        double r=Math.toRadians(t.rotation),cs=Math.cos(r),sn=Math.sin(r);
        double sx=t.scale*(t.flipH?-1:1),sy=t.scale*t.aspect*(t.flipV?-1:1);
        double kx=Math.tan(Math.toRadians(t.skewX)),ky=Math.tan(Math.toRadians(t.skewY));
        // S·K
        double a=sx,b=sx*kx,c=sy*ky,d=sy;
        return new double[]{cs*a-sn*c,cs*b-sn*d,sn*a+cs*c,sn*b+cs*d};
    }
    /** Maps a local frame-unit point to frame units. */
    public static double[] forward(Transform t,double frameAspect,double lx,double ly) {
        double[] m=matrix(t);double ax=t.anchorX*.5*frameAspect,ay=t.anchorY*.5,tx=t.x*.5*frameAspect,ty=t.y*.5;
        double dx=lx-ax,dy=ly-ay;
        return new double[]{tx+ax+m[0]*dx+m[1]*dy,ty+ay+m[2]*dx+m[3]*dy};
    }
    /** Inverse of {@link #forward}; NaN when the transform is degenerate. */
    public static double[] inverse(Transform t,double frameAspect,double sx,double sy) {
        double[] m=matrix(t);double det=m[0]*m[3]-m[1]*m[2];if(Math.abs(det)<1e-9)return new double[]{Double.NaN,Double.NaN};
        double ax=t.anchorX*.5*frameAspect,ay=t.anchorY*.5,tx=t.x*.5*frameAspect,ty=t.y*.5;
        double dx=sx-tx-ax,dy=sy-ty-ay;
        return new double[]{ax+( m[3]*dx-m[1]*dy)/det,ay+(-m[2]*dx+m[0]*dy)/det};
    }
    /** Local content half-size in frame units for content of the given aspect, fitted inside the frame. */
    public static double[] fittedHalf(double frameAspect,double contentAspect) {
        if(!(contentAspect>0)) return new double[]{frameAspect/2,.5};
        return contentAspect>frameAspect?new double[]{frameAspect/2,frameAspect/contentAspect/2}:new double[]{contentAspect/2,.5};
    }
    /** Corner points (screen pixels, y down) for a view of the given size: TL, TR, BR, BL. */
    public static float[] corners(Transform t,float viewW,float viewH,double halfW,double halfH) {
        double A=viewW/(double)viewH;double[][] local={{-halfW,halfH},{halfW,halfH},{halfW,-halfH},{-halfW,-halfH}};float[] out=new float[8];
        for(int i=0;i<4;i++){double[] s=forward(t,A,local[i][0],local[i][1]);out[i*2]=(float)(viewW/2+s[0]*viewH);out[i*2+1]=(float)(viewH/2-s[1]*viewH);}
        return out;
    }
    public static boolean contains(float[] corners,float x,float y) {
        boolean inside=false;
        for(int i=0,j=3;i<4;j=i++){float xi=corners[i*2],yi=corners[i*2+1],xj=corners[j*2],yj=corners[j*2+1];
            if((yi>y)!=(yj>y)&&x<(xj-xi)*(y-yi)/(yj-yi)+xi) inside=!inside;}
        return inside;
    }
    /** Screen-space bounds {left, top, right, bottom} of the corners. */
    public static float[] bounds(float[] c) {
        float l=Float.MAX_VALUE,t=Float.MAX_VALUE,r=-Float.MAX_VALUE,b=-Float.MAX_VALUE;
        for(int i=0;i<4;i++){l=Math.min(l,c[i*2]);r=Math.max(r,c[i*2]);t=Math.min(t,c[i*2+1]);b=Math.max(b,c[i*2+1]);}
        return new float[]{l,t,r,b};
    }
    /** Position change that keeps the layer's visible placement when the anchor moves. */
    public static float[] repositionForAnchor(Transform t,double frameAspect,float anchorX,float anchorY) {
        double[] m=matrix(t);
        double ax=t.anchorX*.5*frameAspect,ay=t.anchorY*.5,bx=anchorX*.5*frameAspect,by=anchorY*.5;
        double dx=bx-ax,dy=by-ay;
        // T' = T + (M − I)(a' − a)
        double tx=t.x*.5*frameAspect+(m[0]-1)*dx+m[1]*dy,ty=t.y*.5+m[2]*dx+(m[3]-1)*dy;
        return new float[]{(float)(tx/(.5*frameAspect)),(float)(ty/.5)};
    }
    /** Fill scale for fitted content: covers the frame while preserving aspect (may crop). */
    public static float fillScale(double frameAspect,double contentAspect) {
        if(!(contentAspect>0)) return 1;
        return (float)Math.max(frameAspect/contentAspect,contentAspect/frameAspect);
    }
    /** Scale that shows source pixels 1:1 in the export frame. */
    public static float originalScale(int exportW,int exportH,int sourceW,int sourceH) {
        if(sourceW<=0||sourceH<=0) return 1;
        double content=sourceW/(double)sourceH,fittedW=Math.min(exportW,exportH*content);
        return (float)Math.max(.05,Math.min(4,sourceW/fittedW));
    }
    public static float snapAngle(float degrees,float step) {
        if(step<=0) return degrees;
        return Math.round(degrees/step)*step;
    }
    /** One project pixel in position units along x / y. */
    public static float pixelX(int exportW){return 2f/exportW;}
    public static float pixelY(int exportH){return 2f/exportH;}
    /** Position that moves the layer's bounds to an alignment target: -1 left/bottom, 0 center, 1 right/top. */
    public static float[] align(Transform t,double frameAspect,double halfW,double halfH,int horizontal,int vertical,boolean alignX,boolean alignY) {
        double minX=Double.MAX_VALUE,maxX=-Double.MAX_VALUE,minY=Double.MAX_VALUE,maxY=-Double.MAX_VALUE;
        double[][] local={{-halfW,halfH},{halfW,halfH},{halfW,-halfH},{-halfW,-halfH}};
        for(double[] l:local){double[] s=forward(t,frameAspect,l[0],l[1]);minX=Math.min(minX,s[0]);maxX=Math.max(maxX,s[0]);minY=Math.min(minY,s[1]);maxY=Math.max(maxY,s[1]);}
        double shiftX=0,shiftY=0;
        if(alignX) shiftX=horizontal<0?-frameAspect/2-minX:horizontal>0?frameAspect/2-maxX:-(minX+maxX)/2;
        if(alignY) shiftY=vertical<0?-.5-minY:vertical>0?.5-maxY:-(minY+maxY)/2;
        return new float[]{(float)(t.x+shiftX/(.5*frameAspect)),(float)(t.y+shiftY/.5)};
    }
    /** True when no part of the layer's bounds intersects the frame. */
    public static boolean offscreen(Transform t,double frameAspect,double halfW,double halfH) {
        double minX=Double.MAX_VALUE,maxX=-Double.MAX_VALUE,minY=Double.MAX_VALUE,maxY=-Double.MAX_VALUE;
        double[][] local={{-halfW,halfH},{halfW,halfH},{halfW,-halfH},{-halfW,-halfH}};
        for(double[] l:local){double[] s=forward(t,frameAspect,l[0],l[1]);minX=Math.min(minX,s[0]);maxX=Math.max(maxX,s[0]);minY=Math.min(minY,s[1]);maxY=Math.max(maxY,s[1]);}
        return maxX<-frameAspect/2||minX>frameAspect/2||maxY<-.5||minY>.5;
    }
    public static float clamp(float v,float low,float high){return Math.max(low,Math.min(high,v));}
}
