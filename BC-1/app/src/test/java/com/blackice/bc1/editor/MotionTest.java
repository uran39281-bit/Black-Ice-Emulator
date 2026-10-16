package com.blackice.bc1.editor;
import org.junit.Test;import static org.junit.Assert.*;
public class MotionTest {
 private EditProject.Clip clip(int track,long start){EditProject.Clip c=new EditProject.Clip();c.uri="file:///test.mp4";c.name="test";c.sourceDurationMs=4000;c.outMs=2000;c.track=track;c.startMs=start;return c;}
 @Test public void independentLayerExtendsDurationWithoutShiftingBase(){EditProject p=new EditProject();p.clips.add(clip(0,0));p.clips.add(clip(2,1500));p.clips.add(clip(0,0));assertEquals(2000,p.placements().get(2).startMs);assertEquals(4000,p.durationMs());p.validate();}
 @Test public void overlapIsRejectedAndPlacementRollsBack(){EditProject p=new EditProject();p.clips.add(clip(1,0));p.clips.add(clip(2,500));try{p.setPlacement(1,1,500);fail();}catch(IllegalArgumentException expected){}assertEquals(2,p.clips.get(1).track);p.validate();}
 @Test public void sourceTimeAnimationSurvivesSplit(){EditProject p=new EditProject();EditProject.Clip c=clip(1,500);Motion.Key a=new Motion.Key(0,0);a.x1=0;a.y1=0;a.x2=1;a.y2=1;c.motion.put(0,a);c.motion.put(0,new Motion.Key(2000,1));p.clips.add(c);p.split(0,1500);assertEquals(1500,p.placements().get(1).startMs);assertEquals(.75f,p.clips.get(1).motion.value(0,1500,0),.001);assertEquals(2500,p.durationMs());p.validate();}
 @Test public void curvesClampEndpointsAndCopyDeeply(){Motion m=new Motion();m.put(4,new Motion.Key(100,0));m.put(4,new Motion.Key(1000,1));assertEquals(0,m.value(4,0,1),0);assertEquals(1,m.value(4,2000,0),0);Motion copy=m.copy();copy.curves.get(4).get(0).value=.5f;assertEquals(0,m.value(4,100,1),0);}
 @Test public void duplicateKeysReplaceAndInvalidCurvesFail(){Motion m=new Motion();m.put(2,new Motion.Key(0,1));m.put(2,new Motion.Key(0,2));assertEquals(1,m.curves.get(2).size());m.curves.get(2).get(0).x1=Float.NaN;try{m.validate();fail();}catch(IllegalArgumentException expected){}}
 @Test public void audioTrackAllocationUsesActualTimeline(){EditProject p=new EditProject();p.clips.add(clip(4,1000));assertEquals(4,p.freeAudioTrack(0,500));assertEquals(5,p.freeAudioTrack(1500,500));}
}
