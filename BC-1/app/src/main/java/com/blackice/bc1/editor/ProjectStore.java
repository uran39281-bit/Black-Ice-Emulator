package com.blackice.bc1.editor;

import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Versioned JSON, atomic writes and one file per project; v0.3 sessions stay untouched. */
public final class ProjectStore {
    private final File directory;
    public ProjectStore(Context context) { directory=new File(context.getFilesDir(),"projects-v1"); directory.mkdirs(); }
    public synchronized void save(EditProject p) throws IOException, JSONException {
        String json=encode(p); AtomicFile file=new AtomicFile(new File(directory,p.id+".json"));
        FileOutputStream stream=null;
        try { stream=file.startWrite(); stream.write(json.getBytes(StandardCharsets.UTF_8)); file.finishWrite(stream); }
        catch(IOException e) { if(stream!=null) file.failWrite(stream); throw e; }
    }
    public synchronized EditProject load(String id) throws IOException,JSONException {
        if(id==null || !id.matches("[a-zA-Z0-9-]{1,80}")) throw new IOException("Invalid project ID");
        return decode(new String(new AtomicFile(new File(directory,id+".json")).readFully(),StandardCharsets.UTF_8));
    }
    public static final class Entry {
        public final String id,name;
        Entry(String id,String name) { this.id=id; this.name=name; }
    }
    public synchronized List<Entry> recent() {
        List<Entry> result=new ArrayList<>(); File[] files=directory.listFiles((d,n)->n.endsWith(".json"));
        if(files==null) return result;
        Arrays.sort(files,(a,b)->Long.compare(b.lastModified(),a.lastModified()));
        for(File f:files) try { EditProject p=load(f.getName().replace(".json","")); result.add(new Entry(p.id,p.name)); }
        catch(Exception ignored) { /* A damaged file is retained for recovery, never overwritten. */ }
        return result;
    }
    public static String encode(EditProject p) throws JSONException {
        p.validate(); JSONObject j=new JSONObject(); j.put("schema",EditProject.SCHEMA).put("id",p.id).put("name",p.name)
                .put("portrait",p.portrait).put("previewHeight",p.previewHeight).put("playheadMs",p.playheadMs);
        JSONArray clips=new JSONArray();
        for(EditProject.Clip c:p.clips) clips.put(new JSONObject().put("id",c.id).put("uri",c.uri).put("name",c.name)
                .put("image",c.image).put("sourceDurationMs",c.sourceDurationMs).put("inMs",c.inMs).put("outMs",c.outMs)
                .put("effect",c.effect).put("intensity",c.intensity).put("opacity",c.opacity).put("volume",c.volume)
                .put("transition",c.transition).put("transitionMs",c.transitionMs).put("easing",c.easing));
        j.put("clips",clips); JSONArray overlays=new JSONArray();
        for(EditProject.Overlay o:p.overlays) overlays.put(new JSONObject().put("id",o.id).put("text",o.text)
                .put("imageUri",o.imageUri).put("startMs",o.startMs).put("endMs",o.endMs)
                .put("x",o.x).put("y",o.y).put("scale",o.scale).put("opacity",o.opacity));
        j.put("overlays",overlays);
        if(p.music!=null) { EditProject.Music m=p.music;
            j.put("music",new JSONObject().put("uri",m.uri).put("name",m.name).put("sourceDurationMs",m.sourceDurationMs)
                    .put("inMs",m.inMs).put("outMs",m.outMs).put("startMs",m.startMs).put("volume",m.volume).put("loop",m.loop)); }
        return j.toString(2);
    }
    public static EditProject decode(String json) throws JSONException {
        JSONObject j=new JSONObject(json);
        if(j.getInt("schema")!=EditProject.SCHEMA) throw new JSONException("Project version not supported; original file is preserved.");
        EditProject p=new EditProject(); p.id=j.getString("id"); p.name=j.getString("name"); p.portrait=j.getBoolean("portrait");
        p.previewHeight=j.getInt("previewHeight"); p.playheadMs=j.getLong("playheadMs");
        JSONArray clips=j.getJSONArray("clips");
        for(int i=0;i<clips.length();i++) { JSONObject v=clips.getJSONObject(i); EditProject.Clip c=new EditProject.Clip();
            c.id=v.getString("id"); c.uri=v.getString("uri"); c.name=v.getString("name"); c.image=v.getBoolean("image");
            c.sourceDurationMs=v.getLong("sourceDurationMs"); c.inMs=v.getLong("inMs"); c.outMs=v.getLong("outMs");
            c.effect=v.getInt("effect"); c.intensity=(float)v.getDouble("intensity"); c.opacity=(float)v.getDouble("opacity");
            c.volume=(float)v.getDouble("volume"); c.transition=v.getInt("transition"); c.transitionMs=v.getLong("transitionMs"); c.easing=v.getInt("easing"); p.clips.add(c); }
        JSONArray overlays=j.getJSONArray("overlays");
        for(int i=0;i<overlays.length();i++) { JSONObject v=overlays.getJSONObject(i); EditProject.Overlay o=new EditProject.Overlay();
            o.id=v.getString("id"); o.text=v.getString("text"); o.imageUri=v.optString("imageUri",null);
            o.startMs=v.getLong("startMs"); o.endMs=v.getLong("endMs"); o.x=(float)v.getDouble("x"); o.y=(float)v.getDouble("y");
            o.scale=(float)v.getDouble("scale"); o.opacity=(float)v.getDouble("opacity"); p.overlays.add(o); }
        if(j.has("music")) { JSONObject v=j.getJSONObject("music"); EditProject.Music m=new EditProject.Music();
            m.uri=v.getString("uri"); m.name=v.getString("name"); m.sourceDurationMs=v.getLong("sourceDurationMs");
            m.inMs=v.getLong("inMs"); m.outMs=v.getLong("outMs"); m.startMs=v.getLong("startMs");
            m.volume=(float)v.getDouble("volume"); m.loop=v.getBoolean("loop"); p.music=m; }
        p.validate(); return p;
    }
    public static EditProject snapshot(EditProject p) {
        try { return decode(encode(p)); } catch(JSONException e) { throw new IllegalStateException(e); }
    }
}
