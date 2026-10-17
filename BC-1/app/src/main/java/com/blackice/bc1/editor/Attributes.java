package com.blackice.bc1.editor;

import org.json.*;

/** Selective copy/paste of layer attributes and the reusable presets built from them.
 * Categories: transform, effects (effect, intensity, opacity, chroma key), styling (text/shape look)
 * and animation (all keyframes). Incompatible categories are skipped, never forced. */
public final class Attributes {
    public static final int TRANSFORM=1,EFFECTS=2,STYLE=4,ANIMATION=8;
    public static final String[] NAMES={"Transform","Effects","Styling","Animation"};
    private final JSONObject data;
    public final int categories;
    private Attributes(JSONObject data,int categories){this.data=data;this.categories=categories;}

    public static Attributes of(EditProject.Clip c,int categories) {
        try {
            JSONObject j=new JSONObject();
            if((categories&TRANSFORM)!=0) j.put("transform",transform(c.x,c.y,c.scale,c.aspect,c.rotation,c.skewX,c.skewY,c.anchorX,c.anchorY,c.flipH,c.flipV));
            if((categories&EFFECTS)!=0&&c.track<4) j.put("effects",new JSONObject().put("effect",c.effect).put("intensity",c.intensity).put("opacity",c.opacity)
                    .put("chroma",c.chroma).put("keyColor",c.keyColor).put("keyTolerance",c.keyTolerance).put("keySoftness",c.keySoftness).put("keySpill",c.keySpill));
            if((categories&ANIMATION)!=0) j.put("animation",ProjectStoreAccess.motion(retime(c.motion,c.inMs,1/c.speed,true)));
            return new Attributes(j,mask(j));
        } catch(JSONException e) { throw new IllegalStateException(e); }
    }
    public static Attributes of(EditProject.Overlay o,int categories) {
        try {
            JSONObject j=new JSONObject();
            if((categories&TRANSFORM)!=0) j.put("transform",transform(o.x,o.y,o.scale,o.aspect,o.rotation,o.skewX,o.skewY,o.anchorX,o.anchorY,o.flipH,o.flipV));
            if((categories&EFFECTS)!=0) j.put("effects",new JSONObject().put("opacity",o.opacity));
            if((categories&STYLE)!=0) j.put("style",new JSONObject().put("font",o.font).put("color",o.color).put("alignment",o.alignment)
                    .put("borderWidth",o.borderWidth).put("borderColor",o.borderColor).put("shadow",o.shadow).put("blend",o.blend));
            if((categories&ANIMATION)!=0) j.put("animation",ProjectStoreAccess.motion(o.motion));
            return new Attributes(j,mask(j));
        } catch(JSONException e) { throw new IllegalStateException(e); }
    }
    private static int mask(JSONObject j){return (j.has("transform")?TRANSFORM:0)|(j.has("effects")?EFFECTS:0)|(j.has("style")?STYLE:0)|(j.has("animation")?ANIMATION:0);}
    private static JSONObject transform(float x,float y,float scale,float aspect,float rotation,float skewX,float skewY,float anchorX,float anchorY,boolean flipH,boolean flipV) throws JSONException {
        return new JSONObject().put("x",x).put("y",y).put("scale",scale).put("aspect",aspect).put("rotation",rotation).put("skewX",skewX).put("skewY",skewY)
                .put("anchorX",anchorX).put("anchorY",anchorY).put("flipH",flipH).put("flipV",flipV);
    }
    /** Applies the requested categories that this target supports; returns those applied. */
    public int applyTo(EditProject.Clip c,int requested) {
        int applied=0;
        try {
            JSONObject t=data.optJSONObject("transform");
            if(t!=null&&(requested&TRANSFORM)!=0&&c.track<4){c.x=f(t,"x");c.y=f(t,"y");c.scale=Math.max(.05f,Math.min(4,f(t,"scale")));c.aspect=f(t,"aspect");c.rotation=f(t,"rotation");c.skewX=f(t,"skewX");c.skewY=f(t,"skewY");c.anchorX=f(t,"anchorX");c.anchorY=f(t,"anchorY");c.flipH=t.getBoolean("flipH");c.flipV=t.getBoolean("flipV");applied|=TRANSFORM;}
            JSONObject e=data.optJSONObject("effects");
            if(e!=null&&(requested&EFFECTS)!=0&&c.track<4){if(e.has("effect")){c.effect=e.getInt("effect");c.intensity=f(e,"intensity");c.chroma=e.getBoolean("chroma");c.keyColor=e.getInt("keyColor");c.keyTolerance=f(e,"keyTolerance");c.keySoftness=f(e,"keySoftness");c.keySpill=f(e,"keySpill");}c.opacity=f(e,"opacity");applied|=EFFECTS;}
            JSONArray a=data.optJSONArray("animation");
            if(a!=null&&(requested&ANIMATION)!=0){c.motion=retime(ProjectStoreAccess.motion(a),c.inMs,c.speed,false);applied|=ANIMATION;}
        } catch(JSONException ex) { throw new IllegalArgumentException("The copied attributes are damaged."); }
        return applied;
    }
    public int applyTo(EditProject.Overlay o,int requested) {
        int applied=0;
        try {
            JSONObject t=data.optJSONObject("transform");
            if(t!=null&&(requested&TRANSFORM)!=0){o.x=Math.max(-1,Math.min(1,f(t,"x")));o.y=Math.max(-1,Math.min(1,f(t,"y")));o.scale=Math.max(.1f,Math.min(2,f(t,"scale")));o.aspect=f(t,"aspect");o.rotation=f(t,"rotation");o.skewX=f(t,"skewX");o.skewY=f(t,"skewY");o.anchorX=f(t,"anchorX");o.anchorY=f(t,"anchorY");o.flipH=t.getBoolean("flipH");o.flipV=t.getBoolean("flipV");applied|=TRANSFORM;}
            JSONObject e=data.optJSONObject("effects");
            if(e!=null&&(requested&EFFECTS)!=0){o.opacity=f(e,"opacity");applied|=EFFECTS;}
            JSONObject s=data.optJSONObject("style");
            if(s!=null&&(requested&STYLE)!=0){o.font=s.getString("font");o.color=s.getInt("color");o.alignment=s.getInt("alignment");o.borderWidth=f(s,"borderWidth");o.borderColor=s.getInt("borderColor");o.shadow=f(s,"shadow");o.blend=s.getInt("blend");applied|=STYLE;}
            JSONArray a=data.optJSONArray("animation");
            if(a!=null&&(requested&ANIMATION)!=0){o.motion=ProjectStoreAccess.motion(a);applied|=ANIMATION;}
        } catch(JSONException ex) { throw new IllegalArgumentException("The copied attributes are damaged."); }
        return applied;
    }
    /** Clip keys use source time; presets store time from the layer's start so they fit any trim or speed. */
    private static Motion retime(Motion source,long inMs,float factor,boolean toRelative) {
        Motion m=new Motion();
        for(int p=0;p<Motion.COUNT;p++) for(Motion.Key k:source.curves.get(p)) {
            Motion.Key c=k.copy();c.timeMs=toRelative?Math.round((k.timeMs-inMs)*factor):inMs+Math.round(k.timeMs*factor);
            if(c.timeMs>=0) m.put(p,c);
        }
        return m;
    }
    private static float f(JSONObject j,String key)throws JSONException{return (float)j.getDouble(key);}

    /** Presets persist as JSON: a name, a kind and the attribute payload (or, for elements, layers). */
    public String encode() { return data.toString(); }
    public static Attributes decode(String json) {
        try { JSONObject j=new JSONObject(json); return new Attributes(j,mask(j)); }
        catch(JSONException e) { throw new IllegalArgumentException("This preset cannot be read."); }
    }
    public static String names(int categories){StringBuilder b=new StringBuilder();for(int i=0;i<NAMES.length;i++)if((categories&(1<<i))!=0){if(b.length()>0)b.append(", ");b.append(NAMES[i]);}return b.length()==0?"Nothing":b.toString();}

    /** Bridges the package-private motion codec used by projects, keeping one JSON layout. */
    static final class ProjectStoreAccess {
        static JSONArray motion(Motion m)throws JSONException{JSONArray all=new JSONArray();for(java.util.List<Motion.Key> curve:m.curves){JSONArray keys=new JSONArray();for(Motion.Key k:curve)keys.put(new JSONArray().put(k.timeMs).put(k.value).put(k.x1).put(k.y1).put(k.x2).put(k.y2));all.put(keys);}return all;}
        static Motion motion(JSONArray all)throws JSONException{Motion m=new Motion();for(int p=0;p<Math.min(all.length(),Motion.COUNT);p++){JSONArray keys=all.getJSONArray(p);for(int i=0;i<keys.length();i++){JSONArray a=keys.getJSONArray(i);Motion.Key k=new Motion.Key(a.getLong(0),(float)a.getDouble(1));k.x1=(float)a.getDouble(2);k.y1=(float)a.getDouble(3);k.x2=(float)a.getDouble(4);k.y2=(float)a.getDouble(5);m.curves.get(p).add(k);}}m.validate();return m;}
    }
}
