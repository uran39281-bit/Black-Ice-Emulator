package com.blackice.bc1.engine;
import org.junit.Test;
import static org.junit.Assert.*;
public class EngineSettingsTest {
    @Test public void restoresCorruptParametersSafely() {
        EngineSettings s=EngineSettings.defaults();
        s.effect=999;s.transition=-5;s.quality=20;s.easing=-2;
        s.intensity=Float.NaN;s.opacity=Float.POSITIVE_INFINITY;s.progress=2;
        s.sanitize();
        assertEquals(11,s.effect);assertEquals(0,s.transition);assertEquals(2,s.quality);
        assertEquals(0,s.easing);assertEquals(.6f,s.intensity,0);assertEquals(1,s.opacity,0);assertEquals(1,s.progress,0);
    }
    @Test public void v02IdsRemainCompatible() {
        assertEquals("Opacity",EngineSettings.EFFECTS[5]);
        assertEquals("Radial Reveal",EngineSettings.TRANSITIONS[3]);
        EngineSettings s=EngineSettings.defaults();
        s.quality=0;assertEquals(.5f,s.renderScale(),0);
        s.quality=1;assertEquals(.75f,s.renderScale(),0);
        s.quality=2;assertEquals(1,s.renderScale(),0);
    }
}
