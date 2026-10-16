package com.blackice.bc1.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Portable edit decision list. Times are integer milliseconds; media is never modified. */
public final class EditProject {
    public static final int SCHEMA = 1;
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
        public boolean image;
        public long sourceDurationMs, inMs, outMs;
        public int effect, transition = -1, easing = 1;
        public float intensity = .6f, opacity = 1f, volume = .7f;
        public long transitionMs = 500;
        public long durationMs() { return outMs - inMs; }
        public Clip copy() {
            Clip c = new Clip(); c.uri=uri; c.name=name; c.image=image;
            c.sourceDurationMs=sourceDurationMs; c.inMs=inMs; c.outMs=outMs;
            c.effect=effect; c.transition=transition; c.easing=easing;
            c.intensity=intensity; c.opacity=opacity; c.volume=volume; c.transitionMs=transitionMs;
            return c;
        }
    }
    public static final class Overlay {
        public String id = UUID.randomUUID().toString(), text = "BLACK ICE", imageUri;
        public long startMs, endMs = 3000;
        public float x=0, y=-.65f, scale=.7f, opacity=1f;
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
    public long overlapAfter(int i) {
        if(i<0 || i>=clips.size()-1 || clips.get(i).transition<0) return 0;
        return Math.max(0,Math.min(clips.get(i).transitionMs,
                Math.min(clips.get(i).durationMs()/2,clips.get(i+1).durationMs()/2)));
    }
    public List<Placement> placements() {
        List<Placement> result=new ArrayList<>(); long start=0;
        for(int i=0;i<clips.size();i++) {
            long incoming=overlapAfter(i-1), outgoing=overlapAfter(i);
            Placement p=new Placement(clips.get(i),i,start,incoming,outgoing);
            result.add(p); start=p.endMs-outgoing;
        }
        return result;
    }
    public long durationMs() {
        long total=0; for(int i=0;i<clips.size();i++) total+=clips.get(i).durationMs()-overlapAfter(i);
        return total;
    }
    public void trim(int index,long in,long out) {
        Clip c=clips.get(index);
        if(in<0 || out>c.sourceDurationMs || out-in<MIN_CLIP_MS) throw new IllegalArgumentException("Keep at least 0.10 seconds inside the source.");
        c.inMs=in; c.outMs=out; playheadMs=Math.min(playheadMs,durationMs());
    }
    public void split(int index,long timelineMs) {
        if(clips.size()>=100)throw new IllegalArgumentException("This preview supports up to 100 clips.");
        Placement p=placements().get(index); long offset=timelineMs-p.startMs;
        if(offset<MIN_CLIP_MS || p.clip.durationMs()-offset<MIN_CLIP_MS)
            throw new IllegalArgumentException("Place the playhead inside the selected clip, at least 0.10 seconds from either edge.");
        Clip right=p.clip.copy(); right.inMs+=offset;
        p.clip.outMs=right.inMs; p.clip.transition=-1;
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
            if(c.uri==null || c.name==null || c.inMs<0 || c.outMs>c.sourceDurationMs || c.durationMs()<MIN_CLIP_MS
                    || c.effect<0 || c.effect>11 || c.transition< -1 || c.transition>6 || c.easing<0 || c.easing>1
                    || c.transitionMs<0 || c.transitionMs>5000 || !unit(c.intensity) || !unit(c.opacity) || !unit(c.volume))
                throw new IllegalArgumentException("Invalid clip: "+c.name);
        }
        for(Overlay o:overlays) if(o.startMs<0 || o.endMs<=o.startMs || !unit(o.opacity)
                || !Float.isFinite(o.scale) || o.scale<.1f || o.scale>2 || !Float.isFinite(o.x) || Math.abs(o.x)>1
                || !Float.isFinite(o.y) || Math.abs(o.y)>1 || o.text==null || o.text.length()>200)
            throw new IllegalArgumentException("Invalid overlay.");
        if(music!=null && (music.uri==null || music.inMs<0 || music.outMs>music.sourceDurationMs
                || music.outMs-music.inMs<MIN_CLIP_MS || music.startMs<0 || !unit(music.volume)))
            throw new IllegalArgumentException("Invalid music trim.");
        playheadMs=Math.max(0,Math.min(playheadMs,durationMs()));
    }
    private static boolean unit(float f) { return Float.isFinite(f) && f>=0 && f<=1; }
}
