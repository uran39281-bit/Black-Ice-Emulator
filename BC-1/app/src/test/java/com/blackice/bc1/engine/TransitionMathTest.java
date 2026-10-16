package com.blackice.bc1.engine;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public final class TransitionMathTest {
    @Test public void dissolveEndpoints() {
        assertEquals(0f, TransitionMath.dissolve(0f), 0.0001f);
        assertEquals(1f, TransitionMath.dissolve(1f), 0.0001f);
        assertEquals(.5f, TransitionMath.dissolve(.5f), 0.0001f);
    }
    @Test public void wipeEndpoints() {
        for (float u : new float[]{0f,.2f,.5f,.8f,1f}) {
            assertEquals(0f, TransitionMath.wipe(0f,u), 0.0001f);
            assertEquals(1f, TransitionMath.wipe(1f,u), 0.0001f);
        }
    }
    @Test public void radialEndpoints() {
        for (float u : new float[]{0f,.5f,1f}) for (float v : new float[]{0f,.5f,1f}) {
            assertEquals(0f, TransitionMath.radial(0f,u,v), 0.0001f);
            assertEquals(1f, TransitionMath.radial(1f,u,v), 0.0001f);
        }
    }
}
