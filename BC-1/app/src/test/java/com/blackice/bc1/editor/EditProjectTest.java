package com.blackice.bc1.editor;
import org.junit.Test;
import static org.junit.Assert.*;

public class EditProjectTest {
    private EditProject.Clip clip(long duration){EditProject.Clip c=new EditProject.Clip();c.uri="file:///fixture.mp4";c.name="fixture";c.sourceDurationMs=duration;c.outMs=duration;return c;}
    private EditProject project(){EditProject p=new EditProject();p.clips.add(clip(4000));p.clips.add(clip(3000));return p;}
    @Test public void cutsPreserveSourceDurations(){EditProject p=project();assertEquals(7000,p.durationMs());assertEquals(4000,p.placements().get(1).startMs);}
    @Test public void overlapLimitsNeverCreateThreeActiveClips(){EditProject p=project();p.clips.add(clip(1000));for(EditProject.Clip c:p.clips){c.transition=0;c.transitionMs=5000;}assertEquals(1500,p.overlapAfter(0));assertEquals(500,p.overlapAfter(1));assertEquals(6000,p.durationMs());assertTrue(p.placements().get(2).startMs>=p.placements().get(0).endMs);}
    @Test public void splitPreservesTotalDurationAndMovesOutgoingTransition(){EditProject p=project();p.clips.get(0).transition=4;long before=p.durationMs();p.split(0,1700);assertEquals(before,p.durationMs());assertEquals(1700,p.clips.get(1).inMs);assertEquals(-1,p.clips.get(0).transition);assertEquals(4,p.clips.get(1).transition);assertNotEquals(p.clips.get(0).id,p.clips.get(1).id);}
    @Test public void splitUsesTimelineCoordinatesAfterOverlapAndTrim(){EditProject p=project();p.trim(0,1000,4000);p.clips.get(0).transition=0;p.trim(1,500,3000);assertEquals(2500,p.placements().get(1).startMs);p.split(1,3500);assertEquals(1500,p.clips.get(2).inMs);}
    @Test(expected=IllegalArgumentException.class) public void splitRejectsZeroLength(){EditProject p=project();p.split(0,0);}
    @Test(expected=IllegalArgumentException.class) public void trimRejectsOutsideSource(){EditProject p=project();p.trim(0,0,4001);}
    @Test public void trimRetainsSourceAndRecalculatesOverlap(){EditProject p=project();p.clips.get(0).transition=0;p.trim(0,3800,4000);assertEquals(4000,p.clips.get(0).sourceDurationMs);assertEquals(100,p.overlapAfter(0));assertEquals(3100,p.durationMs());}
    @Test public void moveAndDuplicateKeepIndependentSettings(){EditProject p=project();EditProject.Clip first=p.clips.get(0);p.move(0,1);assertSame(first,p.clips.get(1));EditProject.Clip copy=first.copy();copy.intensity=.2f;assertEquals(.6f,first.intensity,0);assertNotEquals(copy.id,first.id);}
    @Test public void snapUsesClosestTimelineEdgeAndClamps(){EditProject p=project();p.clips.get(0).transition=0;assertEquals(3500,p.snap(3515,30));assertEquals(3515,p.snap(3515,10));assertEquals(0,p.snap(-400,10));assertEquals(6500,p.snap(9000,10));}
    @Test(expected=IllegalArgumentException.class) public void nonfiniteEffectRejected(){EditProject p=project();p.clips.get(0).intensity=Float.NaN;p.validate();}
    @Test(expected=IllegalArgumentException.class) public void unsafeProjectPathRejected(){EditProject p=project();p.id="../../project";p.validate();}
    @Test public void audioOnlyPlacementCannotBecomeVisual(){EditProject p=project();EditProject.Clip audio=clip(1000);audio.audioOnly=true;audio.track=4;p.clips.add(audio);p.validate();try{p.setPlacement(2,1,0);fail("Audio-only file must not become a video layer");}catch(IllegalArgumentException expected){}assertEquals(4,audio.track);assertTrue(audio.copy().audioOnly);}
}
