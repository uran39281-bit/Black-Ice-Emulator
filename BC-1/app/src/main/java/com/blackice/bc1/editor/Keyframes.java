package com.blackice.bc1.editor;

import java.util.*;

/** Keyframe editing on top of {@link Motion}. Times are the motion's own time base (clip source time or
 * overlay local time), so moving, trimming, splitting and duplicating keep animation attached to content. */
public final class Keyframes {
    private Keyframes() {}
    public static final int STATIC=0,ANIMATED=1,ON_KEY=2;
    public static final String[] EASINGS={"Linear","Ease In","Ease Out","Ease In/Out"};
    private static final float[][] CURVES={{0,0,1,1},{.42f,0,1,1},{0,0,.58f,1},{.42f,0,.58f,1}};

    /** Property state for the diamond indicator: unanimated, animated between keys, or on a key. */
    public static int state(Motion m,int property,long time,long tolerance) {
        List<Motion.Key> keys=m.curves.get(property);
        if(keys.isEmpty()) return STATIC;
        for(Motion.Key k:keys) if(Math.abs(k.timeMs-time)<=tolerance) return ON_KEY;
        return ANIMATED;
    }
    public static Motion.Key keyAt(Motion m,int property,long time,long tolerance) {
        for(Motion.Key k:m.curves.get(property)) if(Math.abs(k.timeMs-time)<=tolerance) return k;
        return null;
    }
    /** Enables animation: the first key holds the current value at the playhead. */
    public static void enable(Motion m,int property,long time,float current) {
        if(m.curves.get(property).isEmpty()) m.put(property,new Motion.Key(time,current));
    }
    /** Disables animation; the returned value (at the playhead) becomes the static value. */
    public static float disable(Motion m,int property,long time,float fallback) {
        float value=m.value(property,time,fallback);m.curves.get(property).clear();return value;
    }
    /** Edits a property: changes the static value when unanimated (returns true so the caller stores it),
     * otherwise creates or updates the key at the playhead, keeping that key's easing. */
    public static boolean set(Motion m,int property,long time,float value,long tolerance) {
        if(!Motion.allowed(property,value)) throw new IllegalArgumentException(Motion.PROPERTIES[property]+" is out of range.");
        if(m.curves.get(property).isEmpty()) return true;
        Motion.Key existing=keyAt(m,property,time,tolerance);
        if(existing!=null){existing.value=value;return false;}
        Motion.Key key=new Motion.Key(time,value);Motion.Key before=previous(m.curves.get(property),time);
        if(before!=null){key.x1=before.x1;key.y1=before.y1;key.x2=before.x2;key.y2=before.y2;}
        m.put(property,key);return false;
    }
    private static Motion.Key previous(List<Motion.Key> keys,long time){Motion.Key found=null;for(Motion.Key k:keys)if(k.timeMs<=time)found=k;return found;}
    public static void toggle(Motion m,int property,long time,float current,long tolerance) {
        Motion.Key existing=keyAt(m,property,time,tolerance);
        if(existing!=null) m.curves.get(property).remove(existing);
        else m.put(property,new Motion.Key(time,current));
    }
    public static void move(Motion m,int property,long from,long to,long tolerance) {
        Motion.Key k=keyAt(m,property,from,tolerance);if(k==null) throw new IllegalArgumentException("No keyframe at the playhead.");
        if(to<0) throw new IllegalArgumentException("Keyframes cannot move before the start.");
        Motion.Key moved=k.copy();moved.timeMs=to;m.curves.get(property).remove(k);m.put(property,moved);
    }
    public static void delete(Motion m,int property,long time,long tolerance) {
        Motion.Key k=keyAt(m,property,time,tolerance);if(k==null) throw new IllegalArgumentException("No keyframe at the playhead.");
        m.curves.get(property).remove(k);
    }
    public static void ease(Motion.Key k,int preset){float[] c=CURVES[preset];k.x1=c[0];k.y1=c[1];k.x2=c[2];k.y2=c[3];}
    public static int easingOf(Motion.Key k){for(int i=0;i<CURVES.length;i++){float[] c=CURVES[i];if(k.x1==c[0]&&k.y1==c[1]&&k.x2==c[2]&&k.y2==c[3])return i;}return -1;}
    /** Nearest key time strictly before / after across the listed properties; -1 when none. */
    public static long adjacent(Motion m,long time,boolean next,int... properties) {
        long best=-1;
        for(int p:properties) for(Motion.Key k:m.curves.get(p)) {
            if(next&&k.timeMs>time&&(best<0||k.timeMs<best)) best=k.timeMs;
            if(!next&&k.timeMs<time&&(best<0||k.timeMs>best)) best=k.timeMs;
        }
        return best;
    }
    public static int[] all(){int[] a=new int[Motion.COUNT];for(int i=0;i<a.length;i++)a[i]=i;return a;}

    /** Copied keys relative to the copy time, so they paste at any playhead. */
    public static final class Clip {
        public final Map<Integer,List<Motion.Key>> keys=new TreeMap<>();
        public boolean isEmpty(){return keys.isEmpty();}
    }
    /** Copies keys at the playhead for the given properties (or, when none sit there, every key of them). */
    public static Clip copy(Motion m,long time,long tolerance,int... properties) {
        Clip clip=new Clip();
        for(int p:properties){Motion.Key at=keyAt(m,p,time,tolerance);List<Motion.Key> list=new ArrayList<>();
            if(at!=null){Motion.Key k=at.copy();k.timeMs=0;list.add(k);}
            if(!list.isEmpty())clip.keys.put(p,list);}
        if(clip.isEmpty()) for(int p:properties){List<Motion.Key> list=new ArrayList<>();
            for(Motion.Key k:m.curves.get(p)){Motion.Key c=k.copy();c.timeMs=k.timeMs-time;list.add(c);}
            if(!list.isEmpty())clip.keys.put(p,list);}
        return clip;
    }
    public static void paste(Motion m,Clip clip,long time) {
        Motion check=m.copy();
        for(Map.Entry<Integer,List<Motion.Key>> e:clip.keys.entrySet()) for(Motion.Key k:e.getValue()){
            Motion.Key c=k.copy();c.timeMs=time+k.timeMs;if(c.timeMs<0)continue;check.put(e.getKey(),c);}
        check.validate();
        for(int p=0;p<Motion.COUNT;p++){m.curves.get(p).clear();m.curves.get(p).addAll(check.curves.get(p));}
    }
    /** For the right half of a split overlay: keys move to the new local time base; the value at the
     * cut is kept as a key at zero so the visible animation does not jump. */
    public static void splitAt(Motion m,long offset) {
        for(int p=0;p<Motion.COUNT;p++){List<Motion.Key> keys=m.curves.get(p);if(keys.isEmpty())continue;
            float atCut=m.value(p,offset,0);boolean dropped=false;
            for(Motion.Key k:keys)k.timeMs-=offset;
            for(Iterator<Motion.Key> it=keys.iterator();it.hasNext();)if(it.next().timeMs<0){it.remove();dropped=true;}
            if(dropped&&(keys.isEmpty()||keys.get(0).timeMs>0))m.put(p,new Motion.Key(0,atCut));}
    }
    public static final String[] PRESETS={"Fade In","Fade Out","Pop In","Slide In Left","Slide In Right","Spin"};
    /** Built-in animation in the motion's time base between start and end; current values are the targets. */
    public static void preset(Motion m,String name,long start,long end,float[] current) {
        long length=Math.max(EditProject.MIN_CLIP_MS,end-start),intro=Math.min(600,length/2);
        Motion check=m.copy();
        switch(name){
            case "Fade In":key(check,Motion.OPACITY,start,0,2);key(check,Motion.OPACITY,start+intro,current[Motion.OPACITY],2);break;
            case "Fade Out":key(check,Motion.OPACITY,end-intro,current[Motion.OPACITY],1);key(check,Motion.OPACITY,end,0,1);break;
            case "Pop In":key(check,Motion.SCALE,start,Math.max(.05f,current[Motion.SCALE]*.2f),2);key(check,Motion.SCALE,start+intro*2/3,Math.min(4,current[Motion.SCALE]*1.1f),3);key(check,Motion.SCALE,start+intro,current[Motion.SCALE],3);break;
            case "Slide In Left":case "Slide In Right":key(check,Motion.X,start,name.endsWith("Left")?-2:2,2);key(check,Motion.X,start+intro,current[Motion.X],2);break;
            case "Spin":key(check,Motion.ROTATION,start,current[Motion.ROTATION],3);key(check,Motion.ROTATION,end,Math.max(-3600,Math.min(3600,current[Motion.ROTATION]+360)),3);break;
            default:throw new IllegalArgumentException("Unknown animation preset.");
        }
        check.validate();for(int p=0;p<Motion.COUNT;p++){m.curves.get(p).clear();m.curves.get(p).addAll(check.curves.get(p));}
    }
    private static void key(Motion m,int property,long time,float value,int easing){Motion.Key k=new Motion.Key(Math.max(0,time),value);ease(k,easing);m.put(property,k);}
    /** Keys at the playhead (any property) moved to a new time together. */
    public static int moveAll(Motion m,long from,long to,long tolerance){int moved=0;for(int p=0;p<Motion.COUNT;p++)if(keyAt(m,p,from,tolerance)!=null){move(m,p,from,to,tolerance);moved++;}if(moved==0)throw new IllegalArgumentException("No keyframe at the playhead.");return moved;}
    public static int deleteAll(Motion m,long time,long tolerance){int removed=0;for(int p=0;p<Motion.COUNT;p++){Motion.Key k=keyAt(m,p,time,tolerance);if(k!=null){m.curves.get(p).remove(k);removed++;}}return removed;}
    public static int easeAll(Motion m,long time,long tolerance,float[] curve){int changed=0;for(int p=0;p<Motion.COUNT;p++){Motion.Key k=keyAt(m,p,time,tolerance);if(k!=null){k.x1=curve[0];k.y1=curve[1];k.x2=curve[2];k.y2=curve[3];changed++;}}return changed;}
    public static float[] curve(int preset){return CURVES[preset].clone();}
    /** Shifts every key (used when an animation preset is applied at a new start). */
    public static void shift(Motion m,long delta){for(List<Motion.Key> keys:m.curves)for(Motion.Key k:keys)k.timeMs+=delta;}
}
