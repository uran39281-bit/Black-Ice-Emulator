package com.blackice.bc1;

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.*;
import android.media.*;
import android.net.Uri;
import android.view.SurfaceView;
import androidx.media3.common.*;
import androidx.media3.effect.MultipleInputVideoGraph;
import androidx.media3.transformer.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.blackice.bc1.editor.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@androidx.annotation.OptIn(markerClass = {androidx.media3.common.util.UnstableApi.class, androidx.media3.common.util.ExperimentalApi.class})
@RunWith(AndroidJUnit4.class)
public class VideoEditorTest {
    private final Instrumentation inst=InstrumentationRegistry.getInstrumentation();
    private final Context context=inst.getTargetContext();
    private String fixture(String name) throws Exception {
        File file=new File(context.getFilesDir(),name);
        try(InputStream in=inst.getContext().getAssets().open(name);OutputStream out=new FileOutputStream(file)){byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);}
        return Uri.fromFile(file).toString();
    }
    private EditProject project(int transition) throws Exception {
        EditProject p=new EditProject();p.name="BC-1 acceptance";
        for(String name:new String[]{"red.mp4","blue.mp4"}){
            EditProject.Clip c=new EditProject.Clip();c.name=name;c.uri=fixture(name);c.sourceDurationMs=3000;c.inMs=300;c.outMs=2300;c.transition=transition;c.easing=0;p.clips.add(c);
        }
        return p;
    }
    private File export(EditProject project,int height) throws Exception {
        File file=new File(context.getCacheDir(),"qa-"+System.nanoTime()+".mp4");CountDownLatch done=new CountDownLatch(1);AtomicReference<Throwable> error=new AtomicReference<>();AtomicReference<Transformer> running=new AtomicReference<>();
        inst.runOnMainSync(()->{try{
            Transformer transformer=new Transformer.Builder(context).setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setPortraitEncodingEnabled(true).setVideoFrameProcessorFactory(CompositionFactory.frameProcessor())
                    .addListener(new Transformer.Listener(){
                        @Override public void onCompleted(Composition c,ExportResult r){done.countDown();}
                        @Override public void onError(Composition c,ExportResult r,ExportException e){error.set(e);done.countDown();}
                    }).build();running.set(transformer);transformer.start(CompositionFactory.buildAtHeight(context,project,height),file.getPath());
        }catch(Exception e){error.set(e);done.countDown();}});
        if(!done.await(180,TimeUnit.SECONDS)){inst.runOnMainSync(()->{if(running.get()!=null)running.get().cancel();});fail("Export timed out");}
        if(error.get()!=null)throw new AssertionError("Export failed",error.get());assertTrue(file.length()>1000);return file;
    }
    private File serviceExport(EditProject project,boolean cancel) throws Exception {
        File snapshot=new File(context.getCacheDir(),"export-test-project.json");
        try(OutputStream out=new FileOutputStream(snapshot)){out.write(ProjectStore.encode(project).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
        File destination=new File(context.getCacheDir(),"published-"+System.nanoTime()+".mp4");
        android.content.SharedPreferences prefs=context.getSharedPreferences(ExportService.PREFS,0);prefs.edit().clear().commit();
        context.getSharedPreferences("EditorActivity",0).edit().remove("last-project").commit();
        try(ActivityScenario<EditorActivity> scenario=ActivityScenario.launch(EditorActivity.class)) {
            scenario.onActivity(a->a.startForegroundService(new android.content.Intent(a,ExportService.class).putExtra("project",snapshot.getPath()).putExtra("destination",Uri.fromFile(destination).toString())));
            long deadline=System.currentTimeMillis()+180000;
            while(!prefs.contains("status")&&System.currentTimeMillis()<deadline)Thread.sleep(50);
            if(cancel)scenario.onActivity(a->a.startService(new android.content.Intent(a,ExportService.class).setAction(ExportService.ACTION_CANCEL)));
            while(prefs.getBoolean("running",false)&&System.currentTimeMillis()<deadline)Thread.sleep(100);
            assertFalse("Export service timed out",prefs.getBoolean("running",false));
            if(cancel){assertEquals("Export cancelled",prefs.getString("status",""));assertFalse(prefs.getBoolean("success",false));}
            else {assertTrue(prefs.getString("status","Export did not start"),prefs.getBoolean("success",false));assertTrue(destination.length()>1000);}
        }
        return destination;
    }
    @Test public void foregroundExportCanBeCancelledWithoutPublishing() throws Exception {
        File file=serviceExport(project(0),true);assertFalse(file.exists());
    }
    @Test public void videoEffectsAndTimedImageOverlayReachTheExport() throws Exception {
        EditProject p=project(-1);p.clips.get(0).effect=4;p.clips.get(0).intensity=1;p.clips.get(1).opacity=0;
        File image=new File(context.getFilesDir(),"overlay-green.png");Bitmap bitmap=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.GREEN);
        try(OutputStream out=new FileOutputStream(image)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
        EditProject.Overlay overlay=new EditProject.Overlay();overlay.imageUri=Uri.fromFile(image).toString();overlay.startMs=900;overlay.endMs=1500;overlay.x=0;overlay.y=0;p.overlays.add(overlay);
        File file=export(p,180);
        try(MediaMetadataRetriever r=new MediaMetadataRetriever()) {
            r.setDataSource(file.getPath());int gray=pixel(r,500),green=pixel(r,1200),black=pixel(r,3000);
            assertTrue(Math.abs(Color.red(gray)-Color.blue(gray))<8&&Color.red(gray)>20);
            assertTrue("Timed image overlay",Color.green(green)>200&&Color.red(green)<40);
            assertTrue("Independent clip opacity",Color.red(black)<10&&Color.blue(black)<10);
        }finally{file.delete();}
    }
    private int pixel(MediaMetadataRetriever retriever,long ms) {
        Bitmap frame=retriever.getFrameAtTime(ms*1000,MediaMetadataRetriever.OPTION_CLOSEST);assertNotNull(frame);
        int color=frame.getPixel(frame.getWidth()/2,frame.getHeight()/2);frame.recycle();return color;
    }
    @Test public void everyVideoTransitionExportsCorrectEndpointsAndMidpoint() throws Exception {
        for(int type=0;type<7;type++) {
            File file=export(project(type),180);
            try(MediaMetadataRetriever r=new MediaMetadataRetriever()){
                r.setDataSource(file.getPath());int first=pixel(r,500),last=pixel(r,3000),middle=pixel(r,1750);
                assertTrue("First source red, transition "+type,Color.red(first)>200&&Color.blue(first)<40);
                assertTrue("Last source blue, transition "+type,Color.blue(last)>200&&Color.red(last)<40);
                if(type==0||type==5)assertTrue("Overlap blends both sources: "+Integer.toHexString(middle),Color.red(middle)>60&&Color.blue(middle)>60);
                if(type==6)assertTrue("Dip reaches black: "+Integer.toHexString(middle),Color.red(middle)<35&&Color.blue(middle)<35);
                long duration=Long.parseLong(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));assertTrue("Duration "+duration,Math.abs(duration-3500)<150);
            }finally{file.delete();}
        }
    }
    @Test public void projectRoundTripEditsMusicTextAnd1080pExport() throws Exception {
        EditProject p=project(0);p.split(0,1000);p.clips.get(0).effect=11;
        EditProject.Overlay title=new EditProject.Overlay();title.text="BLACK ICE";title.startMs=250;title.endMs=2000;p.overlays.add(title);
        EditProject.Music music=new EditProject.Music();music.uri=fixture("music.wav");music.name="test music";music.sourceDurationMs=1000;music.outMs=1000;music.startMs=500;p.music=music;
        ProjectStore store=new ProjectStore(context);store.save(p);EditProject restored=store.load(p.id);
        assertEquals(ProjectStore.encode(p),ProjectStore.encode(restored));assertEquals(3,restored.clips.size());
        File file=serviceExport(restored,false);
        try(MediaMetadataRetriever r=new MediaMetadataRetriever()) {
            r.setDataSource(file.getPath());assertEquals("1920",r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));assertEquals("1080",r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            assertEquals("yes",r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO));assertTrue(Color.blue(pixel(r,3000))>200);
        }
        MediaExtractor extractor=new MediaExtractor();extractor.setDataSource(file.getPath());boolean audio=false,video=false;
        for(int i=0;i<extractor.getTrackCount();i++){String mime=extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME);audio|="audio/mp4a-latm".equals(mime);video|="video/avc".equals(mime);}
        extractor.release();assertTrue(audio&&video);
        // Keep the acceptance export in test output for manual decoding / ffprobe checks.
        File report=new File(context.getExternalFilesDir(null),"qa");report.mkdirs();java.nio.file.Files.copy(file.toPath(),new File(report,"acceptance-1080p.mp4").toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);file.delete();
    }
    @Test public void previewSeeksAfterTrimAcrossBothTracks() throws Exception {
        EditProject p=project(0);CountDownLatch ready=new CountDownLatch(1);AtomicReference<PlaybackException> error=new AtomicReference<>();AtomicReference<CompositionPlayer> ref=new AtomicReference<>();
        context.getSharedPreferences("EditorActivity",0).edit().remove("last-project").commit();
        try(ActivityScenario<EditorActivity> scenario=ActivityScenario.launch(EditorActivity.class)) {
            scenario.onActivity(activity->{
                SurfaceView view=new SurfaceView(activity);activity.setContentView(view);
                CompositionPlayer player=new CompositionPlayer.Builder(activity).setVideoGraphFactory(new MultipleInputVideoGraph.Factory(CompositionFactory.frameProcessor())).build();
                ref.set(player);player.setVideoSurfaceView(view);player.addListener(new Player.Listener(){
                    @Override public void onPlaybackStateChanged(int state){if(state==Player.STATE_READY)ready.countDown();}
                    @Override public void onPlayerError(PlaybackException e){error.set(e);ready.countDown();}
                });player.setComposition(CompositionFactory.buildAtHeight(activity,p,180));player.prepare();
            });
            assertTrue(ready.await(45,TimeUnit.SECONDS));assertNull(error.get());
            inst.runOnMainSync(()->ref.get().seekTo(2600));Thread.sleep(1500);
            inst.runOnMainSync(()->{assertEquals(2600,ref.get().getCurrentPosition());ref.get().play();});Thread.sleep(1000);assertNull(error.get());
        }finally{inst.runOnMainSync(()->{if(ref.get()!=null)ref.get().release();});}
    }
    @Test public void corruptOrFutureProjectDoesNotReplaceSavedProject() throws Exception {
        EditProject p=project(0);String json=ProjectStore.encode(p);ProjectStore store=new ProjectStore(context);store.save(p);
        try{ProjectStore.decode(json.replace("\"schema\": 1","\"schema\": 999"));fail("Unsupported schema must fail");}catch(org.json.JSONException expected){}
        assertEquals(json,ProjectStore.encode(store.load(p.id)));
    }
}
