package com.blackice.bc1;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public final class LayoutPolicyTest {
    @Test public void portraitPhoneUsesPhoneWorkspace() {
        assertEquals(LayoutPolicy.Workspace.PHONE,
            LayoutPolicy.resolve(LayoutPolicy.Preference.AUTO, 412, 915, false));
    }
    @Test public void landscapeTabletUsesWideWorkspace() {
        assertEquals(LayoutPolicy.Workspace.WIDE,
            LayoutPolicy.resolve(LayoutPolicy.Preference.AUTO, 1280, 800, false));
    }
    @Test public void dexSizedExternalDisplayUsesWideWorkspace() {
        assertEquals(LayoutPolicy.Workspace.WIDE,
            LayoutPolicy.resolve(LayoutPolicy.Preference.AUTO, 840, 720, true));
    }
    @Test public void explicitPhoneOverridesDesktop() {
        assertEquals(LayoutPolicy.Workspace.PHONE,
            LayoutPolicy.resolve(LayoutPolicy.Preference.PHONE, 1280, 800, true));
    }
    @Test public void explicitWideOnNarrowPhoneFallsBackSafely() {
        assertEquals(LayoutPolicy.Workspace.PHONE,
            LayoutPolicy.resolve(LayoutPolicy.Preference.WIDE, 412, 915, false));
    }
    @Test public void explicitWideInLandscapeWorks() {
        assertEquals(LayoutPolicy.Workspace.WIDE,
            LayoutPolicy.resolve(LayoutPolicy.Preference.WIDE, 780, 420, false));
    }
}
