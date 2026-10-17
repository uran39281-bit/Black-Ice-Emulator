package com.blackice.bc1;

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.*;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import androidx.media3.common.MimeTypes;
import androidx.media3.transformer.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.blackice.bc1.editor.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Renders real compositions and samples decoded pixels: layer order, visibility, solo (preview only)
 * and the shader's scale / anchor / position mapping, which the preview box shares via TransformMath. */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
@RunWith(AndroidJUnit4.class)
public class LayerRenderingTest {
    private final Instrumentation inst=InstrumentationRegistry.getInstrumentation();
    private final Context context=inst.getTargetContext();

    private EditProject.Clip picture(String name,int color,int track) throws Exception {
        File file=new File(context.getFilesDir(),name+".png");Bitmap b=Bitmap.createBitmap(320,180,Bitmap.Config.ARGB_8888);b.eraseColor(color);
        try(OutputStream out=new FileOutputStream(file)){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();
        EditProject.Clip c=new MediaImporter(context).importClip(Uri.fromFile(file),false);c.name=name;c.outMs=2000;c.track=track;return c;
    }
    private EditProject project() throws Exception {
        EditProject p=new EditProject();p.name="Layer rendering QA";
        p.clips.add(picture("blue",0xFF0000FF,0));
        // Green layer at half size, anchored at its right edge: it must occupy the right half of the frame.
        EditProject.Clip green=picture("green",0xFF00FF00,1);green.scale=.5f;green.anchorX=1;p.clips.add(green);
        // Red rectangle centred in the left half, soloed (solo must not affect export).
        EditProject.Overlay red=new EditProject.Overlay();red.shape=1;red.color=0xFFFF0000;red.x=-.5f;red.y=0;red.scale=1;red.startMs=0;red.endMs=2000;red.solo=true;p.overlays.add(red);
        p.validate();return p;
    }
    private Bitmap frame(EditProject project,boolean preview) throws Exception {
        File file=new File(context.getCacheDir(),"layers-"+System.nanoTime()+".mp4");CountDownLatch done=new CountDownLatch(1);AtomicReference<Throwable> error=new AtomicReference<>();
        inst.runOnMainSync(()->{try{
            Transformer t=new Transformer.Builder(context).setAssetLoaderFactory(CompositionFactory.assetLoader(context)).setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setVideoFrameProcessorFactory(CompositionFactory.frameProcessor()).addListener(new Transformer.Listener(){
                    @Override public void onCompleted(Composition c,ExportResult r){done.countDown();}
                    @Override public void onError(Composition c,ExportResult r,ExportException e){error.set(e);done.countDown();}
                }).build();
            t.start(CompositionFactory.buildAtHeight(context,project,360,preview),file.getPath());
        }catch(Exception e){error.set(e);done.countDown();}});
        assertTrue("Render timed out",done.await(180,TimeUnit.SECONDS));if(error.get()!=null)throw new AssertionError("Render failed",error.get());
        MediaMetadataRetriever r=new MediaMetadataRetriever();try{r.setDataSource(file.getPath());Bitmap b=r.getFrameAtTime(1_000_000,MediaMetadataRetriever.OPTION_CLOSEST);assertNotNull(b);return b;}finally{r.release();file.delete();}
    }
    private static int at(Bitmap b,float fx,float fy){return b.getPixel(Math.round(fx*(b.getWidth()-1)),Math.round(fy*(b.getHeight()-1)));}
    private static void assertColor(String message,int expected,int actual){
        int dr=Math.abs(Color.red(expected)-Color.red(actual)),dg=Math.abs(Color.green(expected)-Color.green(actual)),db=Math.abs(Color.blue(expected)-Color.blue(actual));
        assertTrue(message+String.format(" expected #%06X but was #%06X",expected&0xFFFFFF,actual&0xFFFFFF),dr<70&&dg<70&&db<70);
    }
    @Test public void exportHonoursOrderVisibilityAndTransforms() throws Exception {
        EditProject p=project();Bitmap b=frame(p,false);
        assertColor("Anchored, scaled layer fills the right half",0xFF00FF00,at(b,.75f,.5f));
        assertColor("Overlay renders above video; solo is ignored in export",0xFFFF0000,at(b,.25f,.5f));
        assertColor("Uncovered base layer shows through",0xFF0000FF,at(b,.03f,.05f));
        // TransformMath's corner prediction agrees with the shader: green spans x ∈ [0.5, 1.0] of the frame.
        TransformMath.Transform t=TransformMath.of(p.clips.get(1),0);float[] c=TransformMath.corners(t,640,360,640/360.0/2,.5);float[] bounds=TransformMath.bounds(c);
        assertEquals(320,bounds[0],1);assertEquals(640,bounds[2],1);
        assertColor("Just left of the predicted edge is not green",0xFF0000FF,at(b,.47f,.05f));
        b.recycle();
        p.tracks[1].hidden=true;p.overlays.get(0).hidden=true;b=frame(p,false);
        assertColor("Hidden video layer stays hidden in export",0xFF0000FF,at(b,.75f,.5f));
        assertColor("Hidden overlay stays hidden in export",0xFF0000FF,at(b,.25f,.5f));
        b.recycle();
    }
    @Test public void soloIsolatesOnlyInPreview() throws Exception {
        EditProject p=project();Bitmap b=frame(p,true);
        assertColor("Soloed overlay renders in preview",0xFFFF0000,at(b,.25f,.5f));
        int other=at(b,.75f,.5f);assertTrue("Non-soloed layers are isolated away in preview",Color.green(other)<70&&Color.blue(other)<70);
        b.recycle();
    }
}
