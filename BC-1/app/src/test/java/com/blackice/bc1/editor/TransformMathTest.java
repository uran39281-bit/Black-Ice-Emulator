package com.blackice.bc1.editor;

import org.junit.Test;
import static org.junit.Assert.*;

public class TransformMathTest {
    private static TransformMath.Transform t(){TransformMath.Transform t=new TransformMath.Transform();t.x=.3f;t.y=-.2f;t.scale=.8f;t.aspect=1.3f;t.rotation=37;t.skewX=12;t.skewY=-7;t.anchorX=.4f;t.anchorY=.1f;t.flipH=true;return t;}

    @Test public void inverseUndoesForward() {
        TransformMath.Transform t=t();double[] s=TransformMath.forward(t,16/9.0,.21,-.13),l=TransformMath.inverse(t,16/9.0,s[0],s[1]);
        assertEquals(.21,l[0],1e-9);assertEquals(-.13,l[1],1e-9);
    }
    @Test public void identityKeepsTheFrame() {
        float[] c=TransformMath.corners(new TransformMath.Transform(),1600,900,800/900.0,.5);
        assertArrayEquals(new float[]{0,0,1600,0,1600,900,0,900},c,1e-3f);
        assertTrue(TransformMath.contains(c,800,450));assertFalse(TransformMath.contains(c,1700,450));
    }
    @Test public void anchorChangesPreserveVisiblePlacement() {
        TransformMath.Transform t=t();double A=16/9.0;double[] before=TransformMath.forward(t,A,.3,.2);
        float[] position=TransformMath.repositionForAnchor(t,A,-.5f,.7f);t.x=position[0];t.y=position[1];t.anchorX=-.5f;t.anchorY=.7f;
        double[] after=TransformMath.forward(t,A,.3,.2);assertEquals(before[0],after[0],1e-6);assertEquals(before[1],after[1],1e-6);
    }
    @Test public void rotationHappensAroundTheAnchor() {
        TransformMath.Transform t=new TransformMath.Transform();t.anchorX=1;t.anchorY=1;t.rotation=90;double A=2;
        double[] corner=TransformMath.forward(t,A,1,.5);assertEquals(1,corner[0],1e-9);assertEquals(.5,corner[1],1e-9);
    }
    @Test public void fitFillAndOriginalSizes() {
        assertEquals(1,TransformMath.fillScale(16/9.0,16/9.0),1e-6);assertEquals(16/9.0/(9/16.0),TransformMath.fillScale(16/9.0,9/16.0),1e-4);
        assertEquals(.5f,TransformMath.originalScale(1920,1080,960,540),1e-6);assertEquals(2f,TransformMath.originalScale(1920,1080,3840,2160),1e-6);
        double[] half=TransformMath.fittedHalf(16/9.0,1);assertEquals(.5,half[0],1e-9);assertEquals(.5,half[1],1e-9);
    }
    @Test public void alignmentMovesBoundsToFrameEdges() {
        TransformMath.Transform t=new TransformMath.Transform();t.scale=.5f;double A=16/9.0;
        float[] p=TransformMath.align(t,A,A/2,.5,-1,1,true,true);t.x=p[0];t.y=p[1];
        float[] c=TransformMath.corners(t,1600,900,A/2,.5);float[] b=TransformMath.bounds(c);assertEquals(0,b[0],1e-2);assertEquals(0,b[1],1e-2);
        assertFalse(TransformMath.offscreen(t,A,A/2,.5));t.x=5;assertTrue(TransformMath.offscreen(t,A,A/2,.5));
    }
    @Test public void angleSnapping() { assertEquals(45,TransformMath.snapAngle(52,15)+0*TransformMath.snapAngle(0,0),1e-6);assertEquals(90,TransformMath.snapAngle(70,45),1e-6);assertEquals(33,TransformMath.snapAngle(33,0),1e-6); }
}
