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
        String json=encode(p);File old=new File(directory,p.id+".json"),backup=new File(directory,p.id+".v1-backup");
        if(old.isFile()){byte[] bytes=new AtomicFile(old).readFully();try{int schema=new JSONObject(new String(bytes,StandardCharsets.UTF_8)).optInt("schema");backup=new File(directory,p.id+".v"+schema+"-backup");if(schema<EditProject.SCHEMA&&!backup.exists())java.nio.file.Files.write(backup.toPath(),bytes);}catch(JSONException ignored){}}
        AtomicFile file=new AtomicFile(new File(directory,p.id+".json"));
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
                .put("audioOnly",c.audioOnly).put("image",c.image).put("sourceDurationMs",c.sourceDurationMs).put("inMs",c.inMs).put("outMs",c.outMs)
                .put("effect",c.effect).put("intensity",c.intensity).put("opacity",c.opacity).put("volume",c.volume).put("speed",c.speed)
                .put("track",c.track).put("startMs",c.startMs).put("fadeInMs",c.fadeInMs).put("fadeOutMs",c.fadeOutMs)
                .put("x",c.x).put("y",c.y).put("scale",c.scale).put("rotation",c.rotation).put("motion",motion(c.motion))
                .put("chroma",c.chroma).put("keyColor",c.keyColor).put("keyTolerance",c.keyTolerance).put("keySoftness",c.keySoftness).put("keySpill",c.keySpill)
                .put("transition",c.transition).put("transitionMs",c.transitionMs).put("easing",c.easing));
        j.put("clips",clips); JSONArray overlays=new JSONArray();
        for(EditProject.Overlay o:p.overlays) overlays.put(new JSONObject().put("id",o.id).put("text",o.text)
                .put("motion",motion(o.motion)).put("imageUri",o.imageUri).put("startMs",o.startMs).put("endMs",o.endMs)
                .put("x",o.x).put("y",o.y).put("scale",o.scale).put("opacity",o.opacity).put("font",o.font).put("color",o.color).put("alignment",o.alignment));
        j.put("overlays",overlays);
        if(p.music!=null) { EditProject.Music m=p.music;
            j.put("music",new JSONObject().put("uri",m.uri).put("name",m.name).put("sourceDurationMs",m.sourceDurationMs)
                    .put("inMs",m.inMs).put("outMs",m.outMs).put("startMs",m.startMs).put("volume",m.volume).put("loop",m.loop)); }
        return j.toString(2);
    }
    public static EditProject decode(String json) throws JSONException {
        JSONObject j=new JSONObject(json);
        if(j.getInt("schema")<1||j.getInt("schema")>EditProject.SCHEMA) throw new JSONException("Project version not supported; original file is preserved.");
        EditProject p=new EditProject(); p.id=j.getString("id"); p.name=j.getString("name"); p.portrait=j.getBoolean("portrait");
        p.previewHeight=j.getInt("previewHeight"); p.playheadMs=j.getLong("playheadMs");
        JSONArray clips=j.getJSONArray("clips");
        for(int i=0;i<clips.length();i++) { JSONObject v=clips.getJSONObject(i); EditProject.Clip c=new EditProject.Clip();
            c.track=v.optInt("track",0);c.startMs=v.optLong("startMs",0);c.fadeInMs=v.optLong("fadeInMs",0);c.fadeOutMs=v.optLong("fadeOutMs",0);
            c.x=(float)v.optDouble("x",0);c.y=(float)v.optDouble("y",0);c.scale=(float)v.optDouble("scale",1);c.rotation=(float)v.optDouble("rotation",0);c.motion=motion(v.optJSONArray("motion"));
            c.chroma=v.optBoolean("chroma",false);c.keyColor=v.optInt("keyColor",0xFF00FF00);c.keyTolerance=(float)v.optDouble("keyTolerance",.25);c.keySoftness=(float)v.optDouble("keySoftness",.08);c.keySpill=(float)v.optDouble("keySpill",.5);
            c.id=v.getString("id"); c.uri=v.getString("uri"); c.name=v.getString("name"); c.image=v.getBoolean("image");c.audioOnly=v.optBoolean("audioOnly",false);
            c.sourceDurationMs=v.getLong("sourceDurationMs"); c.inMs=v.getLong("inMs"); c.outMs=v.getLong("outMs");
            c.effect=v.getInt("effect"); c.intensity=(float)v.getDouble("intensity"); c.opacity=(float)v.getDouble("opacity");
            c.volume=(float)v.getDouble("volume");c.speed=(float)v.optDouble("speed",1); c.transition=v.getInt("transition"); c.transitionMs=v.getLong("transitionMs"); c.easing=v.getInt("easing"); p.clips.add(c); }
        JSONArray overlays=j.getJSONArray("overlays");
        for(int i=0;i<overlays.length();i++) { JSONObject v=overlays.getJSONObject(i); EditProject.Overlay o=new EditProject.Overlay();
            o.motion=motion(v.optJSONArray("motion"));o.id=v.getString("id"); o.text=v.getString("text"); o.imageUri=v.optString("imageUri",null);
            o.startMs=v.getLong("startMs"); o.endMs=v.getLong("endMs"); o.x=(float)v.getDouble("x"); o.y=(float)v.getDouble("y");
            o.scale=(float)v.getDouble("scale"); o.opacity=(float)v.getDouble("opacity");o.font=v.optString("font","sans-serif-medium");o.color=v.optInt("color",0xFFFFFFFF);o.alignment=v.optInt("alignment",1); p.overlays.add(o); }
        if(j.has("music")) { JSONObject v=j.getJSONObject("music"); EditProject.Music m=new EditProject.Music();
            m.uri=v.getString("uri"); m.name=v.getString("name"); m.sourceDurationMs=v.getLong("sourceDurationMs");
            m.inMs=v.getLong("inMs"); m.outMs=v.getLong("outMs"); m.startMs=v.getLong("startMs");
            m.volume=(float)v.getDouble("volume"); m.loop=v.getBoolean("loop"); p.music=m; }
        p.validate(); return p;
    }
    private static JSONArray motion(Motion m)throws JSONException{JSONArray all=new JSONArray();for(java.util.List<Motion.Key> curve:m.curves){JSONArray keys=new JSONArray();for(Motion.Key k:curve)keys.put(new JSONArray().put(k.timeMs).put(k.value).put(k.x1).put(k.y1).put(k.x2).put(k.y2));all.put(keys);}return all;}
    private static Motion motion(JSONArray all)throws JSONException{Motion m=new Motion();if(all==null)return m;if(all.length()!=6)throw new JSONException("Invalid motion channels");for(int p=0;p<6;p++){JSONArray keys=all.getJSONArray(p);for(int i=0;i<keys.length();i++){JSONArray a=keys.getJSONArray(i);Motion.Key k=new Motion.Key(a.getLong(0),(float)a.getDouble(1));k.x1=(float)a.getDouble(2);k.y1=(float)a.getDouble(3);k.x2=(float)a.getDouble(4);k.y2=(float)a.getDouble(5);m.curves.get(p).add(k);}}return m;}
    public static EditProject snapshot(EditProject p) {
        try { return decode(encode(p)); } catch(JSONException e) { throw new IllegalStateException(e); }
    }
}
