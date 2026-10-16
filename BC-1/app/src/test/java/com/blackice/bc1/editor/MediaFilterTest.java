package com.blackice.bc1.editor;

import java.util.Locale;
import org.junit.Test;
import static org.junit.Assert.*;

public class MediaFilterTest {
    @Test public void kindsDistinguishAudioImageAndVideo() {
        EditProject.Clip c=new EditProject.Clip();c.name="Ice";
        assertTrue(MediaFilter.matches(c,MediaFilter.Kind.VIDEO,""));
        assertFalse(MediaFilter.matches(c,MediaFilter.Kind.IMAGE,""));
        c.image=true;
        assertTrue(MediaFilter.matches(c,MediaFilter.Kind.IMAGE,""));
        assertFalse(MediaFilter.matches(c,MediaFilter.Kind.VIDEO,""));
        c.track=4;
        assertTrue(MediaFilter.matches(c,MediaFilter.Kind.AUDIO,""));
        assertFalse(MediaFilter.matches(c,MediaFilter.Kind.IMAGE,""));
        assertFalse(MediaFilter.matches(c,MediaFilter.Kind.VIDEO,""));
        assertTrue(MediaFilter.matches(c,MediaFilter.Kind.ALL,""));
    }
    @Test public void searchIsTrimmedAndLocaleIndependent() {
        Locale before=Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr","TR"));
            EditProject.Clip c=new EditProject.Clip();c.name="ICE / DETAIL";
            assertTrue(MediaFilter.matches(c,MediaFilter.Kind.ALL," ice "));
            assertFalse(MediaFilter.matches(c,MediaFilter.Kind.ALL,"opening"));
            assertEquals("ICE / DETAIL",c.name);
        } finally {Locale.setDefault(before);}
    }
    @Test public void emptySearchAcceptsUnnamedMediaWithoutMutatingIt() {
        EditProject.Clip c=new EditProject.Clip();c.name=null;
        assertTrue(MediaFilter.matches(c,MediaFilter.Kind.ALL,null));
        assertTrue(MediaFilter.matches(c,MediaFilter.Kind.ALL,"  "));
        assertFalse(MediaFilter.matches(c,MediaFilter.Kind.ALL,"clip"));
        assertNull(c.name);
    }
}
