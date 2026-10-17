package com.blackice.bc1.editor;
import org.junit.Test;
import static org.junit.Assert.*;
public class TimelineEditsTest {
    private EditProject project(boolean image){EditProject p=new EditProject();EditProject.Clip c=new EditProject.Clip();c.uri="file:///media";c.name="test";c.image=image;c.sourceDurationMs=5000;c.inMs=500;c.outMs=3500;p.clips.add(c);return p;}
    @Test public void imagesExtendPastSyntheticSourceLimit(){EditProject p=project(true);TimelineEdits.duration(p,0,900_000);assertEquals(900_000,p.durationMs());assertEquals(900_500,p.clips.get(0).sourceDurationMs);p.validate();}
    @Test public void videoExtensionAndNegativeDurationFailWithoutMutation(){EditProject p=project(false);for(long duration:new long[]{0,-500,5000}){try{TimelineEdits.duration(p,0,duration);fail();}catch(IllegalArgumentException expected){}assertEquals(500,p.clips.get(0).inMs);assertEquals(3500,p.clips.get(0).outMs);}}
    @Test public void slipPreservesPlacementDurationAndFades(){EditProject p=project(false);EditProject.Clip c=p.clips.get(0);c.track=1;c.startMs=1000;c.fadeInMs=400;TimelineEdits.slip(p,0,1200);assertEquals(3000,c.durationMs());assertEquals(1000,p.placements().get(0).startMs);assertEquals(4200,c.outMs);assertEquals(400,c.fadeInMs);}
    @Test public void stillImageSlipIsRejected(){try{TimelineEdits.slip(project(true),0,0);fail();}catch(IllegalArgumentException expected){}}
    @Test public void independentLeftTrimKeepsRightEdgeAndSource(){EditProject p=project(false);EditProject.Clip c=p.clips.get(0);c.track=1;c.startMs=1000;TimelineEdits.edge(p,0,true,500);assertEquals(1500,c.startMs);assertEquals(1000,c.inMs);assertEquals(4000,p.placements().get(0).endMs);}
    @Test public void invalidOverlapAndNegativeStartRollBack(){EditProject p=project(false);EditProject.Clip c=p.clips.get(0);c.track=1;c.startMs=1000;EditProject.Clip next=c.copy();next.startMs=4000;p.clips.add(next);try{TimelineEdits.edge(p,0,false,100);fail();}catch(IllegalArgumentException expected){}assertEquals(3500,c.outMs);try{TimelineEdits.edge(p,0,true,-1100);fail();}catch(IllegalArgumentException expected){}assertEquals(1000,c.startMs);assertEquals(500,c.inMs);p.validate();}
    @Test public void snapExcludesSelfAndIncludesPlayhead(){EditProject p=project(false);p.playheadMs=1500;assertEquals(1500,TimelineEdits.snap(p,0,1510,20));assertEquals(2990,TimelineEdits.snap(p,0,2990,20));EditProject.Clip next=p.clips.get(0).copy();p.clips.add(next);assertEquals(3000,TimelineEdits.snap(p,0,2990,20));assertEquals(3010,TimelineEdits.snap(p,0,3010,5));}
    @Test public void rulerSupportsHalfSecondsAndFrameDetails(){assertEquals(.5,TimelineEdits.rulerStep(120,52),0);assertEquals(1d/30,TimelineEdits.rulerStep(2400,52),.000001);assertEquals(2,TimelineEdits.rulerStep(48,52),0);}
    @Test public void speedSplitTrimAndSlipUseSourceTime(){EditProject p=project(false);TimelineEdits.speed(p,0,2);assertEquals(1500,p.durationMs());p.split(0,500);assertEquals(1500,p.clips.get(1).inMs);assertEquals(1500,p.durationMs());TimelineEdits.slip(p,1,2000);assertEquals(4000,p.clips.get(1).outMs);assertEquals(1000,p.clips.get(1).durationMs());TimelineEdits.duration(p,1,500);assertEquals(3000,p.clips.get(1).outMs);p.validate();}
    @Test public void invalidSpeedPreservesSourceAndFades(){EditProject p=project(false);for(float speed:new float[]{0,Float.NaN,5}){try{TimelineEdits.speed(p,0,speed);fail();}catch(IllegalArgumentException expected){}assertEquals(1,p.clips.get(0).speed,0);}p.clips.get(0).image=true;try{TimelineEdits.speed(p,0,2);fail();}catch(IllegalArgumentException expected){}}
    @Test public void rulerMovesFromSecondsToHalfSecondsToFrames() {
        assertEquals(1,TimelineEdits.rulerStep(60,56,30),1e-9);assertEquals("4.0s",TimelineEdits.rulerLabel(4000,1,30));
        double half=TimelineEdits.rulerStep(120,56,30);assertEquals(.5,half,1e-9);assertEquals("1.5s",TimelineEdits.rulerLabel(1500,half,30));assertEquals("2.0s",TimelineEdits.rulerLabel(2000,half,30));
        double frames=TimelineEdits.rulerStep(1200,56,24);assertEquals(2d/24,frames,1e-9);assertEquals("1s 12f",TimelineEdits.rulerLabel(1500,frames,24));assertEquals("2s",TimelineEdits.rulerLabel(2000,frames,24));
        assertEquals("1:30",TimelineEdits.rulerLabel(90000,60,30));
    }
}
