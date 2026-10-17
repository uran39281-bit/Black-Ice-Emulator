package com.blackice.bc1.editor;

/** Portable, validated timeline operations. UI gestures commit one history entry. */
public final class TimelineEdits {
    private TimelineEdits() {}
    public static void duration(EditProject p,int index,long duration) {
        EditProject.Clip c=p.clips.get(index);
        if(duration<EditProject.MIN_CLIP_MS || duration>7_200_000_000L)
            throw new IllegalArgumentException("Duration must be at least 0.10 seconds.");
        p.trim(index,c.inMs,Math.addExact(c.inMs,Math.round(duration*c.speed)));
    }
    public static void slip(EditProject p,int index,long sourceIn) {
        EditProject.Clip c=p.clips.get(index);
        if(c.image)throw new IllegalArgumentException("Still images have no source segment to slip.");
        p.trim(index,sourceIn,Math.addExact(sourceIn,c.outMs-c.inMs));
    }
    public static void edge(EditProject p,int index,boolean left,long deltaMs) {
        EditProject.Clip c=p.clips.get(index);
        long oldStart=c.startMs;
        // Main sequence trims ripple; independent tracks retain the opposite edge.
        if(left && c.track>0)c.startMs=Math.addExact(c.startMs,deltaMs);
        try {
            if(c.startMs<0)throw new IllegalArgumentException("A clip cannot start before zero.");
            if(c.image){long length=c.durationMs()+(left?-deltaMs:deltaMs);duration(p,index,length);}
            else p.trim(index,c.inMs+(left?Math.round(deltaMs*c.speed):0),c.outMs+(left?0:Math.round(deltaMs*c.speed)));
        }catch(RuntimeException e){c.startMs=oldStart;throw e;}
    }
    public static void speed(EditProject p,int index,float speed){
        EditProject.Clip c=p.clips.get(index);if(c.image||!Float.isFinite(speed)||speed<.25f||speed>4)throw new IllegalArgumentException("Use a video/audio speed from 0.25× to 4×.");
        float before=c.speed;long fadeIn=c.fadeInMs,fadeOut=c.fadeOutMs;c.speed=speed;c.fadeInMs=Math.min(fadeIn,c.durationMs());c.fadeOutMs=Math.min(fadeOut,c.durationMs());
        try{p.validate();}catch(RuntimeException e){c.speed=before;c.fadeInMs=fadeIn;c.fadeOutMs=fadeOut;throw e;}
    }
    /** Snaps to zero, the playhead, markers and clip edges; returns the input unchanged when none lies within the screen-space tolerance. */
    public static long snap(EditProject p,int excluded,long time,long tolerance) {
        long target=time,distance=tolerance+1;
        long[] fixed={0,p.playheadMs};
        for(long edge:fixed){long d=Math.abs(edge-time);if(d<=tolerance&&d<distance){target=edge;distance=d;}}
        for(long edge:p.markers){long d=Math.abs(edge-time);if(d<=tolerance&&d<distance){target=edge;distance=d;}}
        for(EditProject.Placement placement:p.placements())if(placement.index!=excluded)
            for(long edge:new long[]{placement.startMs,placement.endMs}){
                long d=Math.abs(edge-time);if(d<=tolerance&&d<distance){target=edge;distance=d;}
            }
        return target;
    }
    public static double rulerStep(double pixelsPerSecond,double minimumPixels) {return rulerStep(pixelsPerSecond,minimumPixels,30);}
    /** Coarse zoom labels whole seconds; closer views step to half seconds, then frames of the project rate. */
    public static double rulerStep(double pixelsPerSecond,double minimumPixels,int fps) {
        double[] steps={1d/fps,2d/fps,5d/fps,10d/fps,.5,1,2,5,10,15,30,60,120,300,600,1800,3600};
        for(double step:steps)if(step*pixelsPerSecond>=minimumPixels)return step;
        return 3600*Math.ceil(minimumPixels/pixelsPerSecond/3600);
    }
    /** "4.0s" / "1.5s" at second and half-second steps; "1s 12f" at frame steps; m:ss beyond a minute. */
    public static String rulerLabel(long ms,double stepSeconds,int fps) {
        if(stepSeconds>=60) return String.format(java.util.Locale.US,"%d:%02d",ms/60000,(ms/1000)%60);
        if(stepSeconds>=.5) return String.format(java.util.Locale.US,"%.1fs",ms/1000d);
        long frames=Math.round(ms*fps/1000.0);
        return frames%fps==0?(frames/fps)+"s":(frames/fps)+"s "+(frames%fps)+"f";
    }
}
