package com.blackice.bc1.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Portable edit decision list. Times are integer milliseconds; media is never modified. */
public final class EditProject {
    public static final int SCHEMA = 3;
    public static final long MIN_CLIP_MS = 100;
    public String id = UUID.randomUUID().toString(), name = "Untitled project";
    public boolean portrait;
    public int previewHeight = 540;
    public long playheadMs;
    public final List<Clip> clips = new ArrayList<>();
    public final List<Overlay> overlays = new ArrayList<>();
    public Music music;

    public static final class Clip {
        public String id = UUID.randomUUID().toString(), uri, name;
        public boolean image, audioOnly;
        public int track; public long startMs,fadeInMs,fadeOutMs;
        public float x,y,scale=1,rotation,keyTolerance=.25f,keySoftness=.08f,keySpill=.5f;
        public int keyColor=0xFF00FF00; public boolean chroma;
        public Motion motion=new Motion();
        public long sourceDurationMs, inMs, outMs;
        public int effect, transition = -1, easing = 1;
        public float intensity = .6f, opacity = 1f, volume = .7f,speed=1f;
        public long transitionMs = 500;
        public long durationMs() { return Math.round((outMs - inMs)/(double)speed); }
        public Clip copy() {
            Clip c = new Clip(); c.uri=uri; c.name=name; c.image=image;c.audioOnly=audioOnly;
            c.track=track;c.startMs=startMs;c.fadeInMs=fadeInMs;c.fadeOutMs=fadeOutMs;c.x=x;c.y=y;c.scale=scale;c.rotation=rotation;c.chroma=chroma;c.keyColor=keyColor;c.keyTolerance=keyTolerance;c.keySoftness=keySoftness;c.keySpill=keySpill;c.motion=motion.copy();
            c.sourceDurationMs=sourceDurationMs; c.inMs=inMs; c.outMs=outMs;
            c.effect=effect; c.transition=transition; c.easing=easing;
            c.intensity=intensity; c.opacity=opacity; c.volume=volume; c.transitionMs=transitionMs;c.speed=speed;
            return c;
        }
    }
    public static final class Overlay {
        public String id = UUID.randomUUID().toString(), text = "BLACK ICE", imageUri;
        public long startMs, endMs = 3000;
        public float x=0, y=-.65f, scale=.7f, opacity=1f; public Motion motion=new Motion();
        public String font="sans-serif-medium";public int color=0xFFFFFFFF,alignment=1;
    }
    public static final class Music {
        public String uri, name;
        public long sourceDurationMs, inMs, outMs, startMs;
        public float volume=.3f;
        public boolean loop=true;
    }
    public static final class Placement {
        public final Clip clip;
        public final int index;
        public final long startMs, endMs, incomingMs, outgoingMs;
        Placement(Clip clip, int index, long start, long incoming, long outgoing) {
            this.clip=clip; this.index=index; startMs=start; endMs=start+clip.durationMs();
            incomingMs=incoming; outgoingMs=outgoing;
        }
    }
    private int nextBase(int i){for(int n=i+1;n<clips.size();n++)if(clips.get(n).track==0)return n;return -1;}
    public long overlapAfter(int i) {
        if(i<0||i>=clips.size()||clips.get(i).track!=0||clips.get(i).transition<0)return 0;
        int n=nextBase(i);if(n<0)return 0;
        return Math.max(0,Math.min(clips.get(i).transitionMs,Math.min(clips.get(i).durationMs()/2,clips.get(n).durationMs()/2)));
    }
    public List<Placement> placements() {
        List<Placement> result=new ArrayList<>();long start=0,incoming=0;
        for(int i=0;i<clips.size();i++){Clip c=clips.get(i);long out=overlapAfter(i);
            Placement p=new Placement(c,i,c.track==0?start:c.startMs,c.track==0?incoming:0,out);result.add(p);
            if(c.track==0){start=p.endMs-out;incoming=out;}}
        return result;
    }
    public long durationMs(){long total=0;for(Placement p:placements())total=Math.max(total,p.endMs);for(Overlay o:overlays)total=Math.max(total,o.endMs);return total;}
    public int freeAudioTrack(long start,long duration){for(int t=4;t<=7;t++){boolean free=true;for(Placement p:placements())if(p.clip.track==t&&p.startMs<start+duration&&p.endMs>start)free=false;if(free)return t;}throw new IllegalArgumentException("All four audio tracks are occupied at this time.");}
    public void setPlacement(int index,int track,long start){Clip c=clips.get(index);int old=c.track;long oldStart=c.startMs;c.track=track;c.startMs=start;try{validate();}catch(RuntimeException e){c.track=old;c.startMs=oldStart;throw e;}}
    public void trim(int index,long in,long out) {
        Clip c=clips.get(index);
        if(in<0 || (!c.image&&out>c.sourceDurationMs) || out-in<MIN_CLIP_MS*c.speed || out>7_200_000_000L) throw new IllegalArgumentException("Keep at least 0.10 seconds inside the source.");
        long oldIn=c.inMs,oldOut=c.outMs,oldSource=c.sourceDurationMs,fadeIn=c.fadeInMs,fadeOut=c.fadeOutMs;
        c.inMs=in;c.outMs=out;if(c.image)c.sourceDurationMs=Math.max(c.sourceDurationMs,out);
        c.fadeInMs=Math.min(c.fadeInMs,c.durationMs());c.fadeOutMs=Math.min(c.fadeOutMs,c.durationMs());
        try{validate();}catch(RuntimeException e){c.inMs=oldIn;c.outMs=oldOut;c.sourceDurationMs=oldSource;c.fadeInMs=fadeIn;c.fadeOutMs=fadeOut;throw e;}
    }
    public void split(int index,long timelineMs) {
        if(clips.size()>=100)throw new IllegalArgumentException("This preview supports up to 100 clips.");
        Placement p=placements().get(index); long offset=timelineMs-p.startMs;
        if(offset<MIN_CLIP_MS || p.clip.durationMs()-offset<MIN_CLIP_MS)
            throw new IllegalArgumentException("Place the playhead inside the selected clip, at least 0.10 seconds from either edge.");
        Clip right=p.clip.copy(); right.inMs+=Math.round(offset*p.clip.speed);if(right.track>0)right.startMs=p.startMs+offset;
        p.clip.outMs=right.inMs; p.clip.transition=-1;p.clip.fadeInMs=Math.min(p.clip.fadeInMs,p.clip.durationMs());p.clip.fadeOutMs=0;right.fadeInMs=0;right.fadeOutMs=Math.min(right.fadeOutMs,right.durationMs());
        clips.add(index+1,right);
    }
    public void move(int index,int delta) {
        int next=index+delta; if(next<0 || next>=clips.size()) return;
        Clip c=clips.remove(index); clips.add(next,c);
    }
    public long snap(long timeMs,long thresholdMs) {
        long target=Math.max(0,Math.min(durationMs(),timeMs)), distance=thresholdMs+1;
        for(Placement p:placements()) for(long edge:new long[]{p.startMs,p.endMs}) {
            long d=Math.abs(edge-timeMs); if(d<=thresholdMs && d<distance) {target=edge; distance=d;}
        }
        return target;
    }
    public void validate() {
        if(id==null || !id.matches("[a-zA-Z0-9-]{1,80}") || name==null || name.length()>120)
            throw new IllegalArgumentException("Invalid project identity.");
        if(clips.size()>100 || overlays.size()>20) throw new IllegalArgumentException("Preview supports up to 100 clips and 20 overlays.");
        if(previewHeight!=360 && previewHeight!=540 && previewHeight!=1080) throw new IllegalArgumentException("Invalid preview resolution.");
        for(Clip c:clips) {
            c.motion.validate();
            if(c.audioOnly&&c.track<4)throw new IllegalArgumentException("Audio-only media belongs on an audio lane.");
            if(c.track<0||c.track>7||c.startMs<0||c.fadeInMs<0||c.fadeOutMs<0||c.fadeInMs>c.durationMs()||c.fadeOutMs>c.durationMs()||!Motion.allowed(0,c.x)||!Motion.allowed(1,c.y)||!Motion.allowed(2,c.scale)||!Motion.allowed(3,c.rotation)||!unit(c.keyTolerance)||!unit(c.keySoftness)||c.keySoftness<.001f||!unit(c.keySpill)||(c.track>=4&&c.image))throw new IllegalArgumentException("Invalid track, transform or fade.");
            if(c.uri==null || c.name==null || c.inMs<0 || c.outMs>c.sourceDurationMs || c.durationMs()<MIN_CLIP_MS || !Float.isFinite(c.speed)||c.speed<.25f||c.speed>4||(c.image&&c.speed!=1)
                    || c.effect<0 || c.effect>11 || c.transition< -1 || c.transition>6 || c.easing<0 || c.easing>1
                    || c.transitionMs<0 || c.transitionMs>5000 || !unit(c.intensity) || !unit(c.opacity) || !unit(c.volume))
                throw new IllegalArgumentException("Invalid clip: "+c.name);
        }
        List<Placement> ps=placements();
        for(int i=0;i<ps.size();i++)for(int n=i+1;n<ps.size();n++){Placement a=ps.get(i),b=ps.get(n);if(a.clip.track>0&&a.clip.track==b.clip.track&&a.startMs<b.endMs&&b.startMs<a.endMs)throw new IllegalArgumentException("Clips on the same layer cannot overlap. Use another layer or move the start time.");}
        for(Overlay o:overlays)o.motion.validate();
        for(Overlay o:overlays) if(o.startMs<0 || o.endMs<=o.startMs || !unit(o.opacity)
                || !Float.isFinite(o.scale) || o.scale<.1f || o.scale>2 || !Float.isFinite(o.x) || Math.abs(o.x)>1
                || !Float.isFinite(o.y) || Math.abs(o.y)>1 || o.text==null || o.text.length()>200 || o.alignment<0||o.alignment>2 || !("sans-serif-medium".equals(o.font)||"serif".equals(o.font)||"monospace".equals(o.font)))
            throw new IllegalArgumentException("Invalid overlay.");
        if(music!=null && (music.uri==null || music.inMs<0 || music.outMs>music.sourceDurationMs
                || music.outMs-music.inMs<MIN_CLIP_MS || music.startMs<0 || !unit(music.volume)))
            throw new IllegalArgumentException("Invalid music trim.");
        playheadMs=Math.max(0,Math.min(playheadMs,durationMs()));
    }
    private static boolean unit(float f) { return Float.isFinite(f) && f>=0 && f<=1; }
}
