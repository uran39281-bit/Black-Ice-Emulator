package com.blackice.bc1.editor;

import java.util.*;

/** Layer visibility rules and multi-clip timeline arrangement. Every edit validates or throws without
 * partial changes, so the UI can wrap one call in one undo entry. */
public final class LayerOps {
    private LayerOps() {}

    public static boolean anySolo(EditProject p) {
        for(EditProject.Track t:p.tracks) if(t.solo) return true;
        for(EditProject.Overlay o:p.overlays) if(o.solo) return true;
        return false;
    }
    /** Export ignores solo; preview isolates soloed rows while keeping hidden rows hidden. */
    public static boolean renders(EditProject p,EditProject.Clip c,boolean preview) {
        EditProject.Track t=p.tracks[c.track];
        if(c.track>=4) return !t.muted&&(!preview||!anySolo(p)||t.solo);
        return !t.hidden&&(!preview||!anySolo(p)||t.solo);
    }
    public static boolean renders(EditProject p,EditProject.Overlay o,boolean preview) {
        return !o.hidden&&(!preview||!anySolo(p)||o.solo);
    }
    public static boolean locked(EditProject p,int clip) { return clip>=0&&clip<p.clips.size()&&p.tracks[p.clips.get(clip).track].locked; }
    public static boolean locked(EditProject.Overlay o) { return o!=null&&o.locked; }

    /** Moves independent-layer clips and overlays together, preserving their spacing.
     * Main-sequence clips keep their ripple order and are reported as unmovable. */
    public static void moveMany(EditProject p,Collection<Integer> clips,Collection<Integer> overlays,long delta) {
        Snapshot undo=new Snapshot(p);
        try {
            for(int i:clips){EditProject.Clip c=p.clips.get(i);if(c.track==0)throw new IllegalArgumentException("Main-sequence clips move by reordering; select layer clips to move them in time.");c.startMs=Math.addExact(c.startMs,delta);if(c.startMs<0)throw new IllegalArgumentException("Clips cannot start before zero.");}
            for(int i:overlays){EditProject.Overlay o=p.overlays.get(i);o.startMs=Math.addExact(o.startMs,delta);o.endMs=Math.addExact(o.endMs,delta);if(o.startMs<0)throw new IllegalArgumentException("Layers cannot start before zero.");}
            p.validate();
        } catch(RuntimeException e) { undo.restore(p); throw e; }
    }
    public static long start(EditProject p,int clip){return p.placements().get(clip).startMs;}
    public static long end(EditProject p,int clip){return p.placements().get(clip).endMs;}

    /** Aligns selected layer starts (or ends) to the reference item without changing durations. */
    public static void align(EditProject p,List<Integer> clips,List<Integer> overlays,boolean ends,long reference) {
        Snapshot undo=new Snapshot(p);
        try {
            for(int i:clips){EditProject.Clip c=p.clips.get(i);if(c.track==0)continue;c.startMs=ends?reference-c.durationMs():reference;if(c.startMs<0)throw new IllegalArgumentException("Alignment would move a clip before zero.");}
            for(int i:overlays){EditProject.Overlay o=p.overlays.get(i);long length=o.endMs-o.startMs;o.startMs=ends?reference-length:reference;o.endMs=o.startMs+length;if(o.startMs<0)throw new IllegalArgumentException("Alignment would move a layer before zero.");}
            p.validate();
        } catch(RuntimeException e) { undo.restore(p); throw e; }
    }
    /** Applies the duration wherever source limits allow; returns the names left unchanged. */
    public static List<String> matchDuration(EditProject p,List<Integer> clips,List<Integer> overlays,long duration) {
        List<String> skipped=new ArrayList<>();
        for(int i:clips){EditProject.Clip c=p.clips.get(i);String before=state(p);
            try{TimelineEdits.duration(p,i,duration);}catch(RuntimeException e){restore(p,before);skipped.add(c.name);}}
        for(int i:overlays){EditProject.Overlay o=p.overlays.get(i);o.endMs=o.startMs+Math.max(EditProject.MIN_CLIP_MS,duration);}
        p.validate();
        return skipped;
    }
    /** An overlapping copy on the next free layer above; returns the new clip index. */
    public static int duplicateAbove(EditProject p,int index) {
        EditProject.Clip c=p.clips.get(index);EditProject.Placement placement=p.placements().get(index);
        int first=c.track>=4?4:1,last=c.track>=4?7:3;
        for(int track=Math.max(first,c.track+1);track<=last;track++) if(free(p,track,placement.startMs,placement.endMs,-1)) return insertCopy(p,index,track,placement.startMs);
        for(int track=first;track<=last;track++) if(track!=c.track&&free(p,track,placement.startMs,placement.endMs,-1)) return insertCopy(p,index,track,placement.startMs);
        throw new IllegalArgumentException("No free "+(c.track>=4?"audio":"visual")+" layer is available at this time.");
    }
    /** A sequential copy starting where the original ends; returns the new clip index. */
    public static int duplicateAfter(EditProject p,int index) {
        EditProject.Clip c=p.clips.get(index);EditProject.Placement placement=p.placements().get(index);
        if(c.track==0) return insertCopy(p,index,0,0);
        long start=placement.endMs,end=start+c.durationMs();
        if(!free(p,c.track,start,end,-1))throw new IllegalArgumentException("The space after this clip is occupied on its layer.");
        return insertCopy(p,index,c.track,start);
    }
    public static int duplicateOverlay(EditProject p,int index,boolean after) {
        if(p.overlays.size()>=20) throw new IllegalArgumentException("This project supports up to 20 text, shape and image layers.");
        EditProject.Overlay o=p.overlays.get(index).copy();
        if(after){long length=o.endMs-o.startMs;o.startMs=o.endMs;o.endMs=o.startMs+length;}
        p.overlays.add(index+1,o);p.validate();return index+1;
    }
    private static int insertCopy(EditProject p,int index,int track,long start) {
        if(p.clips.size()>=100) throw new IllegalArgumentException("The 100-clip limit was reached.");
        EditProject.Clip copy=p.clips.get(index).copy();copy.track=track;copy.startMs=track==0?0:start;if(track>0)copy.transition=-1;
        p.clips.add(index+1,copy);
        try{p.validate();}catch(RuntimeException e){p.clips.remove(index+1);throw e;}
        return index+1;
    }
    public static boolean free(EditProject p,int track,long start,long end,int excluded) {
        for(EditProject.Placement placement:p.placements()) if(placement.index!=excluded&&placement.clip.track==track&&placement.startMs<end&&start<placement.endMs) return false;
        return true;
    }
    /** Removes gaps between clips on independent tracks; the main sequence is already gapless.
     * The first clip of each track keeps its start time. Returns the total time removed. */
    public static long closeGaps(EditProject p,Collection<Integer> tracks) {
        long removed=0;Snapshot undo=new Snapshot(p);
        try {
            for(int track:tracks){if(track<=0||track>7)continue;
                List<EditProject.Clip> row=new ArrayList<>();for(EditProject.Clip c:p.clips)if(c.track==track)row.add(c);
                row.sort(Comparator.comparingLong(c->c.startMs));
                for(int i=1;i<row.size();i++){long end=row.get(i-1).startMs+row.get(i-1).durationMs();long gap=row.get(i).startMs-end;if(gap>0){row.get(i).startMs=end;removed+=gap;}}
            }
            p.validate();
        } catch(RuntimeException e) { undo.restore(p); throw e; }
        return removed;
    }
    /** Trim the selected clip edge to the playhead without moving the opposite edge. */
    public static void trimToPlayhead(EditProject p,int index,boolean start) {
        EditProject.Placement placement=p.placements().get(index);long playhead=p.playheadMs;
        if(playhead-placement.startMs<EditProject.MIN_CLIP_MS||placement.endMs-playhead<EditProject.MIN_CLIP_MS)
            throw new IllegalArgumentException("Place the playhead inside the clip, at least 0.10 seconds from either edge.");
        TimelineEdits.edge(p,index,start,start?playhead-placement.startMs:playhead-placement.endMs);
    }
    public static boolean canSplitOrTrim(EditProject p,int index) {
        if(index<0||index>=p.clips.size()) return false;
        EditProject.Placement placement=p.placements().get(index);
        return p.playheadMs-placement.startMs>=EditProject.MIN_CLIP_MS&&placement.endMs-p.playheadMs>=EditProject.MIN_CLIP_MS;
    }
    /** Swap two independent visual or audio layers, including their row settings; timing is untouched. */
    public static void swapTracks(EditProject p,int a,int b) {
        if(a==b) return;
        if(a<1||b<1||a>7||b>7||(a<4)!=(b<4)) throw new IllegalArgumentException("Reorder within visual layers 1–3 or audio layers 1–4. The main sequence stays at the bottom.");
        for(EditProject.Clip c:p.clips) if(c.track==a)c.track=b; else if(c.track==b)c.track=a;
        EditProject.Track t=p.tracks[a];p.tracks[a]=p.tracks[b];p.tracks[b]=t;
        p.validate();
    }
    /** Overlay list order is stacking order: later entries render above earlier ones. */
    public static void moveOverlay(EditProject p,int from,int to) {
        if(to<0||to>=p.overlays.size()||from==to) return;
        p.overlays.add(to,p.overlays.remove(from));
    }
    public static void toggleMarker(EditProject p,long time,long tolerance) {
        for(int i=0;i<p.markers.size();i++) if(Math.abs(p.markers.get(i)-time)<=tolerance){p.markers.remove(i);return;}
        if(p.markers.size()>=500) throw new IllegalArgumentException("Use at most 500 markers.");
        p.markers.add(Math.max(0,time));Collections.sort(p.markers);
    }
    /** All clip / overlay indices that share a group with the given item. */
    public static void expandGroups(EditProject p,Set<Integer> clips,Set<Integer> overlays) {
        Set<String> groups=new HashSet<>();
        for(int i:clips) if(p.clips.get(i).group!=null) groups.add(p.clips.get(i).group);
        for(int i:overlays) if(p.overlays.get(i).group!=null) groups.add(p.overlays.get(i).group);
        if(groups.isEmpty()) return;
        for(int i=0;i<p.clips.size();i++) if(groups.contains(p.clips.get(i).group)) clips.add(i);
        for(int i=0;i<p.overlays.size();i++) if(groups.contains(p.overlays.get(i).group)) overlays.add(i);
    }
    public static String group(EditProject p,Collection<Integer> clips,Collection<Integer> overlays) {
        if(clips.size()+overlays.size()<2) throw new IllegalArgumentException("Select at least two layers to group.");
        String id="g"+Long.toString(System.nanoTime()&0xFFFFFFFFL,36);
        for(int i:clips) p.clips.get(i).group=id;
        for(int i:overlays) p.overlays.get(i).group=id;
        return id;
    }
    public static void ungroup(EditProject p,Collection<Integer> clips,Collection<Integer> overlays) {
        for(int i:clips) p.clips.get(i).group=null;
        for(int i:overlays) p.overlays.get(i).group=null;
    }

    private static String state(EditProject p){try{return ProjectStore.encode(p);}catch(Exception e){throw new IllegalStateException(e);}}
    private static void restore(EditProject p,String json){Snapshot.copyInto(p,decode(json));}
    private static EditProject decode(String json){try{return ProjectStore.decode(json);}catch(Exception e){throw new IllegalStateException(e);}}
    /** Restores the full project if a multi-item operation fails part-way. */
    static final class Snapshot {
        private final String json;
        Snapshot(EditProject p){json=state(p);}
        void restore(EditProject p){copyInto(p,decode(json));}
        static void copyInto(EditProject target,EditProject source){
            target.clips.clear();target.clips.addAll(source.clips);target.overlays.clear();target.overlays.addAll(source.overlays);
            target.markers.clear();target.markers.addAll(source.markers);System.arraycopy(source.tracks,0,target.tracks,0,target.tracks.length);
            target.music=source.music;target.loopInMs=source.loopInMs;target.loopOutMs=source.loopOutMs;target.playheadMs=source.playheadMs;
        }
    }
}
