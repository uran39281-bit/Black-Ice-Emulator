package com.blackice.bc1.editor;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProjectMigrationTest {
    private static EditProject.Clip clip(){EditProject.Clip c=new EditProject.Clip();c.uri="file:///fixture.mp4";c.name="fixture";c.sourceDurationMs=4000;c.outMs=4000;return c;}

    @Test public void schemaThreeProjectsOpenWithNeutralDefaults() throws Exception {
        EditProject p=new EditProject();p.clips.add(clip());EditProject.Overlay o=new EditProject.Overlay();p.overlays.add(o);
        JSONObject json=new JSONObject(ProjectStore.encode(p));json.put("schema",3);json.remove("tracks");json.remove("markers");json.remove("frameRate");json.remove("loopInMs");json.remove("loopOutMs");
        JSONObject c=json.getJSONArray("clips").getJSONObject(0);for(String k:new String[]{"aspect","skewX","skewY","anchorX","anchorY","flipH","flipV","width","height","group"})c.remove(k);
        // Schema 3 stored six motion channels.
        org.json.JSONArray six=new org.json.JSONArray();for(int i=0;i<6;i++)six.put(new org.json.JSONArray());c.put("motion",six);
        JSONObject v=json.getJSONArray("overlays").getJSONObject(0);for(String k:new String[]{"shape","name","hidden","solo","locked","rotation","aspect","blend","borderWidth"})v.remove(k);
        EditProject old=ProjectStore.decode(json.toString());
        assertEquals(30,old.frameRate);assertEquals("Main",old.tracks[0].name);assertEquals("Layer 2",old.tracks[2].name);assertFalse(old.tracks[1].hidden);
        assertEquals(1,old.clips.get(0).aspect,0);assertEquals(0,old.clips.get(0).anchorX,0);assertNull(old.clips.get(0).group);
        assertEquals(0,old.overlays.get(0).shape);assertFalse(old.overlays.get(0).hidden);assertEquals(Motion.COUNT,old.clips.get(0).motion.curves.size());
    }
    @Test public void schemaFourRoundTripsLayerData() throws Exception {
        EditProject p=new EditProject();p.clips.add(clip());p.tracks[0].name="Hero";p.tracks[0].locked=true;p.tracks[5].muted=true;p.markers.add(1200L);p.loopInMs=100;p.loopOutMs=900;p.frameRate=25;
        EditProject.Clip c=p.clips.get(0);c.skewX=10;c.anchorY=-.5f;c.flipV=true;c.width=1920;c.height=1080;c.group="g1";c.motion.put(Motion.ANCHOR_X,new Motion.Key(200,.5f));
        EditProject.Overlay o=new EditProject.Overlay();o.shape=2;o.name="Badge";o.blend=1;o.borderWidth=.2f;o.hidden=true;p.overlays.add(o);
        EditProject back=ProjectStore.decode(ProjectStore.encode(p));
        assertEquals("Hero",back.tracks[0].name);assertTrue(back.tracks[0].locked);assertTrue(back.tracks[5].muted);assertEquals(1200L,(long)back.markers.get(0));assertEquals(900,back.loopOutMs);assertEquals(25,back.frameRate);
        EditProject.Clip bc=back.clips.get(0);assertEquals(10,bc.skewX,0);assertEquals(-.5f,bc.anchorY,0);assertTrue(bc.flipV);assertEquals(1920,bc.width);assertEquals("g1",bc.group);assertEquals(.5f,bc.motion.value(Motion.ANCHOR_X,200,0),0);
        EditProject.Overlay bo=back.overlays.get(0);assertEquals(2,bo.shape);assertEquals("Badge",bo.label());assertEquals(1,bo.blend);assertTrue(bo.hidden);
        assertEquals(ProjectStore.encode(p),ProjectStore.encode(back));
    }
    @Test(expected=IllegalArgumentException.class) public void invalidFrameRateRejected(){EditProject p=new EditProject();p.frameRate=60;p.validate();}
    @Test public void attributesCopySelectivelyAndRetimeAnimation() {
        EditProject.Clip a=clip();a.x=.5f;a.effect=4;a.inMs=1000;a.motion.put(Motion.X,new Motion.Key(1500,.2f));
        EditProject.Clip b=clip();b.inMs=200;b.speed=2;
        Attributes copied=Attributes.of(a,Attributes.TRANSFORM|Attributes.ANIMATION);
        assertEquals(Attributes.TRANSFORM|Attributes.ANIMATION,copied.applyTo(b,Attributes.TRANSFORM|Attributes.EFFECTS|Attributes.ANIMATION));
        assertEquals(.5f,b.x,0);assertEquals("Effects were not copied",0,b.effect);
        assertEquals("Key stays 0.5 s after layer start, in the target's source time",1200,b.motion.curves.get(Motion.X).get(0).timeMs);
        EditProject.Overlay o=new EditProject.Overlay();o.color=0xFF112233;EditProject.Overlay target=new EditProject.Overlay();
        Attributes style=Attributes.decode(Attributes.of(o,Attributes.STYLE).encode());assertEquals(Attributes.STYLE,style.applyTo(target,Attributes.STYLE));assertEquals(0xFF112233,target.color);
        assertEquals("Styling does not apply to video",0,style.applyTo(clip(),Attributes.STYLE));
    }
}
