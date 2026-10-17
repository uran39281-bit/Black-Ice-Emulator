package com.blackice.bc1.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Portable edit decision list. Times are integer milliseconds; media is never modified. */
public final class EditProject {
    public static final int SCHEMA = 4;
    public static final long MIN_CLIP_MS = 100;
    public String id = UUID.randomUUID().toString(), name = "Untitled project";
    public boolean portrait;
    public int previewHeight = 540;
    public long playheadMs;
    public final List<Clip> clips = new ArrayList<>();
    public final List<Overlay> overlays = new ArrayList<>();
    public Music music;
    /** Editor frame rate: timeline frame grid, nudging and export cadence. */
    public int frameRate = 30;
    /** Row metadata for tracks 0–7; clips keep their track numbers, so older projects map directly. */
    public final Track[] tracks = new Track[8];
    /** Beat / cue markers in timeline milliseconds, kept sorted. */
    public final List<Long> markers = new ArrayList<>();
    /** Preview-only loop range; negative when unset. Never affects export. */
    public long loopInMs = -1, loopOutMs = -1;
    public EditProject(){for(int t=0;t<tracks.length;t++)tracks[t]=new Track(Track.defaultName(t));}

    public static final class Track {
        public String name; public int color;
        public boolean hidden, solo, locked, muted, collapsed;
        public Track(String name){this.name=name;color=0xFF40BFFF;}
        public static String defaultName(int t){return t==0?"Main":t<4?"Layer "+t:"Audio "+(t-3);}
        public Track copy(){Track c=new Track(name);c.color=color;c.hidden=hidden;c.solo=solo;c.locked=locked;c.muted=muted;c.collapsed=collapsed;return c;}
    }

    public static final class Clip {
        public String id = UUID.randomUUID().toString(), uri, name;
        public boolean image, audioOnly;
        public int track; public long startMs,fadeInMs,fadeOutMs;
        public float x,y,scale=1,rotation,keyTolerance=.25f,keySoftness=.08f,keySpill=.5f;
        /** Height relative to width scale (1 keeps proportions), skew in degrees, anchor in position units. */
        public float aspect=1,skewX,skewY,anchorX,anchorY; public boolean flipH,flipV;
        /** Source pixel size when known (0 = unknown, measured lazily for Fit/Fill). */
        public int width,height;
        /** Optional group identifier shared by layers that move together. */
        public String group;
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
            c.aspect=aspect;c.skewX=skewX;c.skewY=skewY;c.anchorX=anchorX;c.anchorY=anchorY;c.flipH=flipH;c.flipV=flipV;c.width=width;c.height=height;c.group=group;
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
        /** 0 text/image, 1 rectangle, 2 ellipse, 3 triangle, 4 line. */
        public int shape;
        public String name, group;
        public boolean hidden, solo, locked, flipH, flipV;
        public float rotation, aspect=1, skewX, skewY, anchorX, anchorY, borderWidth, shadow;
        public int borderColor=0xFF000000, blend;
        public Overlay copy(){Overlay o=new Overlay();o.text=text;o.imageUri=imageUri;o.startMs=startMs;o.endMs=endMs;o.x=x;o.y=y;o.scale=scale;o.opacity=opacity;o.motion=motion.copy();o.font=font;o.color=color;o.alignment=alignment;o.shape=shape;o.name=name;o.group=group;o.hidden=hidden;o.solo=solo;o.locked=locked;o.flipH=flipH;o.flipV=flipV;o.rotation=rotation;o.aspect=aspect;o.skewX=skewX;o.skewY=skewY;o.anchorX=anchorX;o.anchorY=anchorY;o.borderWidth=borderWidth;o.shadow=shadow;o.borderColor=borderColor;o.blend=blend;return o;}
        public String label(){if(name!=null&&!name.isEmpty())return name;if(imageUri!=null)return "Image";if(shape>0)return SHAPES[shape];return text;}
    }
    public static final String[] SHAPES={"Text","Rectangle","Ellipse","Triangle","Line"};
    public static final String[] BLENDS={"Normal","Multiply","Screen","Overlay","Add","Darken","Lighten"};
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
        if(frameRate!=24&&frameRate!=25&&frameRate!=30) throw new IllegalArgumentException("Use 24, 25 or 30 fps.");
        for(Track t:tracks) if(t==null||t.name==null||t.name.isEmpty()||t.name.length()>60) throw new IllegalArgumentException("Every layer needs a name of up to 60 characters.");
        if(markers.size()>500) throw new IllegalArgumentException("Use at most 500 markers.");
        for(int i=0;i<markers.size();i++) if(markers.get(i)<0||(i>0&&markers.get(i)<=markers.get(i-1))) throw new IllegalArgumentException("Markers must be distinct times from zero.");
        if((loopInMs<0)!=(loopOutMs<0)||(loopInMs>=0&&loopOutMs-loopInMs<MIN_CLIP_MS)) throw new IllegalArgumentException("Loop range needs at least 0.10 seconds.");
        for(Clip c:clips) {
            c.motion.validate();
            if(!(c.aspect>=.05f&&c.aspect<=20)||!(Math.abs(c.skewX)<=80)||!(Math.abs(c.skewY)<=80)||!Motion.allowed(0,c.anchorX)||!Motion.allowed(1,c.anchorY)||c.width<0||c.height<0||(c.group!=null&&c.group.length()>40)) throw new IllegalArgumentException("Invalid transform: "+c.name);
            if(c.audioOnly&&c.track<4)throw new IllegalArgumentException("Audio-only media belongs on an audio lane.");
            if(c.track<0||c.track>7||c.startMs<0||c.fadeInMs<0||c.fadeOutMs<0||c.fadeInMs>c.durationMs()||c.fadeOutMs>c.durationMs()||!Motion.allowed(0,c.x)||!Motion.allowed(1,c.y)||!Motion.allowed(2,c.scale)||!Motion.allowed(3,c.rotation)||!unit(c.keyTolerance)||!unit(c.keySoftness)||c.keySoftness<.001f||!unit(c.keySpill)||(c.track>=4&&c.image))throw new IllegalArgumentException("Invalid track, transform or fade.");
            if(c.uri==null || c.name==null || c.inMs<0 || c.outMs>c.sourceDurationMs || c.durationMs()<MIN_CLIP_MS || !Float.isFinite(c.speed)||c.speed<.25f||c.speed>4||(c.image&&c.speed!=1)
                    || c.effect<0 || c.effect>11 || c.transition< -1 || c.transition>6 || c.easing<0 || c.easing>1
                    || c.transitionMs<0 || c.transitionMs>5000 || !unit(c.intensity) || !unit(c.opacity) || !unit(c.volume))
                throw new IllegalArgumentException("Invalid clip: "+c.name);
        }
        List<Placement> ps=placements();
        for(int i=0;i<ps.size();i++)for(int n=i+1;n<ps.size();n++){Placement a=ps.get(i),b=ps.get(n);if(a.clip.track>0&&a.clip.track==b.clip.track&&a.startMs<b.endMs&&b.startMs<a.endMs)throw new IllegalArgumentException("Clips on the same layer cannot overlap. Use another layer or move the start time.");}
        for(Overlay o:overlays){o.motion.validate();if(o.shape<0||o.shape>=SHAPES.length||o.blend<0||o.blend>=BLENDS.length||!(o.aspect>=.05f&&o.aspect<=20)||!(Math.abs(o.skewX)<=80)||!(Math.abs(o.skewY)<=80)||!(Math.abs(o.rotation)<=3600)||!Motion.allowed(0,o.anchorX)||!Motion.allowed(1,o.anchorY)||!(o.borderWidth>=0&&o.borderWidth<=1)||!(o.shadow>=0&&o.shadow<=1)||(o.name!=null&&o.name.length()>60)||(o.group!=null&&o.group.length()>40))throw new IllegalArgumentException("Invalid overlay style.");}
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
