package com.blackice.bc1.editor;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class LayerOpsTest {
    private static EditProject.Clip clip(String name,long duration,int track,long start,boolean image){EditProject.Clip c=new EditProject.Clip();c.uri="file:///"+name;c.name=name;c.sourceDurationMs=duration;c.outMs=duration;c.track=track;c.startMs=start;c.image=image;return c;}
    private static EditProject project(){EditProject p=new EditProject();p.clips.add(clip("main",4000,0,0,false));p.clips.add(clip("a",1000,1,500,true));p.clips.add(clip("b",1000,1,3000,true));p.clips.add(clip("c",2000,2,1000,false));return p;}

    @Test public void hiddenLayersNeverRenderAndSoloIsPreviewOnly() {
        EditProject p=project();p.tracks[1].hidden=true;p.tracks[2].solo=true;
        assertFalse(LayerOps.renders(p,p.clips.get(1),false));assertFalse("Hidden stays hidden while soloing",LayerOps.renders(p,p.clips.get(1),true));
        assertTrue(LayerOps.renders(p,p.clips.get(3),true));assertFalse("Solo isolates in preview",LayerOps.renders(p,p.clips.get(0),true));
        assertTrue("Export ignores solo",LayerOps.renders(p,p.clips.get(0),false));
        p.tracks[2].solo=false;assertTrue("Previous visibility returns",LayerOps.renders(p,p.clips.get(0),true));
    }
    @Test public void moveManyPreservesSpacingAndIsAtomic() {
        EditProject p=project();LayerOps.moveMany(p,List.of(1,2),List.of(),700);
        assertEquals(1200,p.clips.get(1).startMs);assertEquals(3700,p.clips.get(2).startMs);
        try{LayerOps.moveMany(p,List.of(1,2),List.of(),-1500);fail();}catch(IllegalArgumentException expected){}
        assertEquals("Failed move leaves every clip in place",1200,p.clips.get(1).startMs);assertEquals(3700,p.clips.get(2).startMs);
        try{LayerOps.moveMany(p,List.of(0,1),List.of(),100);fail("Main sequence cannot move in time");}catch(IllegalArgumentException expected){}
    }
    @Test public void alignAndMatchDurationRespectSourceLimits() {
        EditProject p=project();
        // Clip b would land on clip a's layer at 500: the whole alignment rolls back.
        try{LayerOps.align(p,List.of(2,3),List.of(),false,500);fail();}catch(IllegalArgumentException expected){}
        assertEquals(3000,p.clips.get(2).startMs);assertEquals(1000,p.clips.get(3).startMs);
        LayerOps.align(p,List.of(3),List.of(),false,500);assertEquals(500,p.clips.get(3).startMs);
        p=project();LayerOps.align(p,List.of(3),List.of(),true,5000);assertEquals(3000,p.clips.get(3).startMs);
        p=project();List<String> skipped=LayerOps.matchDuration(p,List.of(1,3),List.of(),2500);
        assertEquals("Pictures extend freely",2500,p.clips.get(1).durationMs());assertEquals(List.of("c"),skipped);assertEquals(2000,p.clips.get(3).durationMs());
    }
    @Test public void duplicateAboveOverlapsAndAfterIsSequential() {
        EditProject p=project();int above=LayerOps.duplicateAbove(p,1);EditProject.Clip copy=p.clips.get(above);
        assertEquals("Layer 2 is busy at 1.0 s, so the copy goes to layer 3",3,copy.track);assertEquals(500,copy.startMs);assertNotEquals(copy.id,p.clips.get(1).id);
        p=project();int after=LayerOps.duplicateAfter(p,1);assertEquals(1,p.clips.get(after).track);assertEquals(1500,p.clips.get(after).startMs);
        p=project();int mainCopy=LayerOps.duplicateAfter(p,0);assertEquals(4000,p.placements().get(mainCopy).startMs);assertEquals(8000,p.durationMs());
        p=project();p.clips.get(2).startMs=1500;try{LayerOps.duplicateAfter(p,1);fail("Occupied space must be refused");}catch(IllegalArgumentException expected){}
    }
    @Test public void closeGapsOnlyTouchesTheChosenTracks() {
        EditProject p=project();long removed=LayerOps.closeGaps(p,List.of(1));
        assertEquals(1500,removed);assertEquals(500,p.clips.get(1).startMs);assertEquals(1500,p.clips.get(2).startMs);assertEquals("Other tracks unchanged",1000,p.clips.get(3).startMs);
    }
    @Test public void trimToPlayheadKeepsOppositeEdgeAndRejectsZeroLength() {
        EditProject p=project();p.playheadMs=1100;LayerOps.trimToPlayhead(p,3,false);assertEquals(1000,p.clips.get(3).startMs);assertEquals(100,p.clips.get(3).durationMs());
        p=project();p.playheadMs=1500;LayerOps.trimToPlayhead(p,3,true);assertEquals(1500,p.clips.get(3).startMs);assertEquals(1500,p.clips.get(3).durationMs());assertEquals(500,p.clips.get(3).inMs);
        p=project();p.playheadMs=1020;assertFalse(LayerOps.canSplitOrTrim(p,3));try{LayerOps.trimToPlayhead(p,3,true);fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void swapTracksMovesRowsWithTheirSettings() {
        EditProject p=project();p.tracks[1].name="Logos";p.tracks[1].locked=true;LayerOps.swapTracks(p,1,3);
        assertEquals(3,p.clips.get(1).track);assertEquals("Logos",p.tracks[3].name);assertTrue(p.tracks[3].locked);assertEquals(500,p.clips.get(1).startMs);
        try{LayerOps.swapTracks(p,0,2);fail("Main sequence stays at the bottom");}catch(IllegalArgumentException expected){}
        try{LayerOps.swapTracks(p,2,5);fail("Visual and audio rows do not mix");}catch(IllegalArgumentException expected){}
    }
    @Test public void groupsExpandSelection() {
        EditProject p=project();String id=LayerOps.group(p,List.of(1,3),List.of());Set<Integer> clips=new HashSet<>(List.of(1)),overlays=new HashSet<>();
        LayerOps.expandGroups(p,clips,overlays);assertEquals(Set.of(1,3),clips);assertEquals(id,p.clips.get(3).group);
        LayerOps.ungroup(p,List.of(1,3),List.of());assertNull(p.clips.get(1).group);
    }
    @Test public void markersToggleAndStaySorted() {
        EditProject p=project();LayerOps.toggleMarker(p,2000,10);LayerOps.toggleMarker(p,500,10);assertEquals(List.of(500L,2000L),p.markers);
        LayerOps.toggleMarker(p,2005,10);assertEquals(List.of(500L),p.markers);p.validate();
    }
    @Test public void markersParticipateInSnapping() {
        EditProject p=project();p.markers.add(2250L);assertEquals(2250,TimelineEdits.snap(p,-1,2270,30));
    }
}
