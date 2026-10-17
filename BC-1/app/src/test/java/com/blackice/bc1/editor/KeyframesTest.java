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
    @Test public void presetsAnimateTowardCurrentValues() {
        Motion m=new Motion();float[] current={.3f,0,1,0,.8f,.6f,0,0,0,0};
        Keyframes.preset(m,"Fade In",1000,4000,current);assertEquals(0,m.value(Motion.OPACITY,1000,1),1e-6);assertEquals(.8f,m.value(Motion.OPACITY,1600,1),1e-6);
        Keyframes.preset(m,"Slide In Left",1000,4000,current);assertEquals(-2,m.value(Motion.X,1000,0),1e-6);assertEquals(.3f,m.value(Motion.X,2000,0),1e-6);
        Keyframes.preset(m,"Spin",1000,4000,current);assertEquals(360,m.value(Motion.ROTATION,4000,0),1e-6);
        assertEquals("Opacity, X and rotation keys at 1.0 s",3,Keyframes.deleteAll(m,1000,5));assertEquals("Later slide key remains",.3f,m.value(Motion.X,1600,0),1e-6);
    }
    @Test public void keysAtPlayheadMoveAndEaseTogether() {
        Motion m=new Motion();m.put(Motion.X,new Motion.Key(500,0));m.put(Motion.Y,new Motion.Key(500,1));m.put(Motion.X,new Motion.Key(2000,1));
        assertEquals(2,Keyframes.moveAll(m,500,800,10));assertEquals(800,m.curves.get(Motion.Y).get(0).timeMs);
        assertEquals(2,Keyframes.easeAll(m,800,10,Keyframes.curve(1)));assertEquals(1,Keyframes.easingOf(m.curves.get(Motion.X).get(0)));
    }
    @Test public void beatsAreSpacedLocalPeaks() {
        float[] peaks=new float[64];for(int i=0;i<64;i++)peaks[i]=i%8==0?1f:.1f;
        java.util.List<Long> beats=Beats.detect(peaks,6400,250);assertEquals(7,beats.size());assertEquals(850,(long)beats.get(0));
        assertTrue(Beats.detect(peaks,6400,2000).size()<beats.size());assertTrue(Beats.detect(new float[3],1000,100).isEmpty());
    }
}
