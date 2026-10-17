package com.blackice.bc1.editor;

import org.junit.Test;
import static org.junit.Assert.*;

public class KeyframesTest {
    @Test public void staticEditsUntilAnimationIsEnabled() {
        Motion m=new Motion();assertEquals(Keyframes.STATIC,Keyframes.state(m,Motion.OPACITY,0,16));
        assertTrue("Unanimated edits change the static value",Keyframes.set(m,Motion.OPACITY,500,.5f,16));assertTrue(m.curves.get(Motion.OPACITY).isEmpty());
        Keyframes.enable(m,Motion.OPACITY,0,1);assertEquals(Keyframes.ON_KEY,Keyframes.state(m,Motion.OPACITY,0,16));
        assertFalse(Keyframes.set(m,Motion.OPACITY,1000,.25f,16));assertEquals(2,m.curves.get(Motion.OPACITY).size());
        assertEquals(Keyframes.ANIMATED,Keyframes.state(m,Motion.OPACITY,500,16));
        assertFalse(Keyframes.set(m,Motion.OPACITY,1005,.1f,16));assertEquals("Edits on a key update it",2,m.curves.get(Motion.OPACITY).size());assertEquals(.1f,m.value(Motion.OPACITY,1000,1),1e-6);
    }
    @Test public void moveCopyPasteDeleteAndNavigate() {
        Motion m=new Motion();m.put(Motion.X,new Motion.Key(0,0));m.put(Motion.X,new Motion.Key(1000,1));m.put(Motion.ROTATION,new Motion.Key(600,90));
        assertEquals(600,Keyframes.adjacent(m,0,true,Keyframes.all()));assertEquals(1000,Keyframes.adjacent(m,600,true,Keyframes.all()));assertEquals(600,Keyframes.adjacent(m,1000,false,Keyframes.all()));assertEquals(-1,Keyframes.adjacent(m,1000,true,Keyframes.all()));
        Keyframes.move(m,Motion.X,1000,1500,10);assertEquals(1500,m.curves.get(Motion.X).get(1).timeMs);
        Keyframes.Clip copied=Keyframes.copy(m,1500,10,Motion.X);Keyframes.paste(m,copied,3000);assertEquals(3,m.curves.get(Motion.X).size());assertEquals(1,m.value(Motion.X,3000,0),1e-6);
        Keyframes.delete(m,Motion.X,3000,10);assertEquals(2,m.curves.get(Motion.X).size());
        try{Keyframes.delete(m,Motion.X,2222,10);fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void easingPresetsAreRecognised() {
        Motion.Key k=new Motion.Key(0,0);for(int i=0;i<Keyframes.EASINGS.length;i++){Keyframes.ease(k,i);assertEquals(i,Keyframes.easingOf(k));}
        k.x1=.3f;assertEquals(-1,Keyframes.easingOf(k));
        Motion m=new Motion();Motion.Key a=new Motion.Key(0,0);Keyframes.ease(a,0);m.put(Motion.X,a);m.put(Motion.X,new Motion.Key(1000,1));
        assertEquals("Linear is linear",.25f,m.value(Motion.X,250,0),1e-3);
    }
    @Test public void newKeysInheritTheSurroundingEasing() {
        Motion m=new Motion();Motion.Key a=new Motion.Key(0,0);Keyframes.ease(a,2);m.put(Motion.X,a);m.put(Motion.X,new Motion.Key(1000,1));
        Keyframes.set(m,Motion.X,500,.4f,10);assertEquals(2,Keyframes.easingOf(m.curves.get(Motion.X).get(1)));
    }
    @Test public void outOfRangeValuesAreRejected() {
        Motion m=new Motion();try{Keyframes.set(m,Motion.SKEW_X,0,120,10);fail();}catch(IllegalArgumentException expected){}
        assertTrue(Motion.allowed(Motion.ANCHOR_X,-1.5f));assertFalse(Motion.allowed(Motion.ANCHOR_X,3));
    }
}
