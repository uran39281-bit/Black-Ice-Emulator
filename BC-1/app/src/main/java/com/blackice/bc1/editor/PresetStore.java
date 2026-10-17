package com.blackice.bc1.editor;

import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** App-wide saved effect stacks, animation presets and grouped elements, independent of projects. */
public final class PresetStore {
    public static final String EFFECTS="effects",ANIMATION="animation",ELEMENT="element";
    public static final class Preset {
        public final String id,name,kind,payload;
        Preset(String id,String name,String kind,String payload){this.id=id;this.name=name;this.kind=kind;this.payload=payload;}
    }
    private final AtomicFile file;
    public PresetStore(Context context){file=new AtomicFile(new File(context.getFilesDir(),"bc1-presets.json"));}
    public synchronized List<Preset> list(String kind) {
        List<Preset> result=new ArrayList<>();
        try { JSONArray all=new JSONArray(new String(file.readFully(),StandardCharsets.UTF_8));
            for(int i=0;i<all.length();i++){JSONObject j=all.getJSONObject(i);if(kind==null||kind.equals(j.getString("kind")))result.add(new Preset(j.getString("id"),j.getString("name"),j.getString("kind"),j.getString("payload")));}
        } catch(FileNotFoundException missing) { /* No presets saved yet. */ }
        catch(IOException|JSONException e) { /* A damaged file is left for recovery and not overwritten here. */ }
        return result;
    }
    /** @throws IllegalArgumentException for invalid names, a full store or a failed write (with a readable message). */
    public synchronized void save(String name,String kind,String payload) {
        if(name==null||name.isBlank()||name.length()>60) throw new IllegalArgumentException("Use a preset name of 1 to 60 characters.");
        List<Preset> all=list(null);if(all.size()>=200) throw new IllegalArgumentException("Up to 200 presets can be saved. Delete one first.");
        all.add(new Preset(UUID.randomUUID().toString(),name.trim(),kind,payload));write(all);
    }
    public synchronized void delete(String id) { List<Preset> all=list(null);all.removeIf(p->p.id.equals(id));write(all); }
    private void write(List<Preset> all) {
        JSONArray array=new JSONArray();
        try { for(Preset p:all) array.put(new JSONObject().put("id",p.id).put("name",p.name).put("kind",p.kind).put("payload",p.payload)); }
        catch(JSONException e) { throw new IllegalArgumentException("Presets could not be encoded."); }
        FileOutputStream out=null;
        try { out=file.startWrite(); out.write(array.toString().getBytes(StandardCharsets.UTF_8)); file.finishWrite(out); }
        catch(IOException e) { if(out!=null) file.failWrite(out); throw new IllegalArgumentException("Presets could not be saved: "+e.getMessage()); }
    }

    /** Saved elements store layers with times relative to the earliest selected start. */
    public static String element(EditProject p,Collection<Integer> clips,Collection<Integer> overlays) {
        try {
            long origin=Long.MAX_VALUE;for(int i:clips)origin=Math.min(origin,p.placements().get(i).startMs);for(int i:overlays)origin=Math.min(origin,p.overlays.get(i).startMs);
            EditProject holder=new EditProject();
            for(int i:clips){EditProject.Clip c=p.clips.get(i).copy();long start=p.placements().get(i).startMs-origin;c.track=c.track==0?1:c.track;c.startMs=start;c.transition=-1;c.group=null;holder.clips.add(c);}
            for(int i:overlays){EditProject.Overlay o=p.overlays.get(i).copy();long length=o.endMs-o.startMs;o.startMs-=origin;o.endMs=o.startMs+length;o.group=null;holder.overlays.add(o);}
            return ProjectStore.encode(holder);
        } catch(JSONException e) { throw new IllegalArgumentException("These layers cannot be saved."); }
    }
    /** Inserts a saved element at the given time as a new group; returns the number of layers added. */
    public static int insertElement(EditProject p,String payload,long at) {
        EditProject element;
        try { element=ProjectStore.decode(payload); } catch(JSONException e) { throw new IllegalArgumentException("This saved element cannot be read."); }
        String group="g"+Long.toString(System.nanoTime()&0xFFFFFFFFL,36);int added=0;
        if(p.clips.size()+element.clips.size()>100||p.overlays.size()+element.overlays.size()>20) throw new IllegalArgumentException("Not enough room: projects hold 100 clips and 20 text/shape/image layers.");
        for(EditProject.Clip c:element.clips){
            c.startMs+=at;c.group=group;boolean placed=false;int first=c.track>=4?4:1,last=c.track>=4?7:3;
            for(int t=first;t<=last&&!placed;t++){if(LayerOps.free(p,t,c.startMs,c.startMs+c.durationMs(),-1)){c.track=t;placed=true;}}
            if(!placed) throw new IllegalArgumentException("No free layer for “"+c.name+"” at this time.");
            p.clips.add(c);added++;
        }
        for(EditProject.Overlay o:element.overlays){o.startMs+=at;o.endMs+=at;o.group=group;p.overlays.add(o);added++;}
        p.validate();return added;
    }
}
