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
            EditProject.Clip c=new MediaImporter(context).importClip(Uri.parse(fixture(name)),false);c.name=name;assertTrue(c.sourceDurationMs>=2990);c.inMs=300;c.outMs=2300;c.transition=transition;c.easing=0;p.clips.add(c);
        }
        return p;
    }
    private File export(EditProject project,int height) throws Exception {
        File file=new File(context.getCacheDir(),"qa-"+System.nanoTime()+".mp4");CountDownLatch done=new CountDownLatch(1);AtomicReference<Throwable> error=new AtomicReference<>();AtomicReference<Transformer> running=new AtomicReference<>();
        inst.runOnMainSync(()->{try{
            Transformer transformer=new Transformer.Builder(context).setAssetLoaderFactory(CompositionFactory.assetLoader(context)).setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setPortraitEncodingEnabled(true).setVideoFrameProcessorFactory(CompositionFactory.frameProcessor())
                    .addListener(new Transformer.Listener(){
                        @Override public void onCompleted(Composition c,ExportResult r){done.countDown();}
                        @Override public void onError(Composition c,ExportResult r,ExportException e){error.set(e);done.countDown();}
                    }).build();running.set(transformer);transformer.start(CompositionFactory.buildAtHeight(context,project,height),file.getPath());
        }catch(Exception e){error.set(e);done.countDown();}});
        if(!done.await(180,TimeUnit.SECONDS)){inst.runOnMainSync(()->{if(running.get()!=null)running.get().cancel();});fail("Export timed out");}
        if(error.get()!=null)throw new AssertionError("Export failed for transition "+project.clips.get(0).transition,error.get());assertTrue(file.length()>1000);return file;
    }
    private File serviceExport(EditProject project,boolean cancel) throws Exception {
        return serviceExport(project,cancel,false);
    }
    private File serviceExport(EditProject project,boolean cancel,boolean expectFailure) throws Exception {
        File snapshot=new File(context.getCacheDir(),"export-test-project.json");
        try(OutputStream out=new FileOutputStream(snapshot)){out.write(ProjectStore.encode(project).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
        File destination=new File(context.getCacheDir(),"published-"+System.nanoTime()+".mp4");
        android.content.SharedPreferences prefs=context.getSharedPreferences(ExportService.PREFS,0);
        prefs.edit().remove("running").remove("status").remove("success").remove("finishedAt").commit();
        context.getSharedPreferences(EditorActivity.PREFS,0).edit().remove("last-project").commit();
        try(ActivityScenario<EditorActivity> scenario=ActivityScenario.launch(EditorActivity.class)) {
            scenario.onActivity(a->a.startForegroundService(new android.content.Intent(a,ExportService.class).putExtra("project",snapshot.getPath()).putExtra("destination",Uri.fromFile(destination).toString())));
            long deadline=System.currentTimeMillis()+180000;
            while(!prefs.contains("status")&&System.currentTimeMillis()<deadline)Thread.sleep(50);
            if(cancel)scenario.onActivity(a->a.startService(new android.content.Intent(a,ExportService.class).setAction(ExportService.ACTION_CANCEL)));
            while(prefs.getBoolean("running",false)&&System.currentTimeMillis()<deadline)Thread.sleep(100);
            assertFalse("Export service timed out",prefs.getBoolean("running",false));
            while(ExportService.isRunning()&&System.currentTimeMillis()<deadline)Thread.sleep(50);
            assertFalse("Finished export service releases its lifecycle",ExportService.isRunning());
            if(cancel){assertEquals("Export cancelled",prefs.getString("status",""));assertFalse(prefs.getBoolean("success",false));}
            else if(expectFailure){assertFalse("Broken source must fail",prefs.getBoolean("success",true));assertTrue(prefs.getString("status","").startsWith("Export failed:"));assertFalse(destination.exists());}
            else {assertTrue(prefs.getString("status","Export did not start"),prefs.getBoolean("success",false));assertTrue(destination.length()>1000);}
        }
        return destination;
    }
    @Test public void speedAndTextStyleSurviveReopenAndExport() throws Exception {
        EditProject p=project(-1);TimelineEdits.speed(p,0,2);TimelineEdits.speed(p,1,.5f);assertEquals(5000,p.durationMs());
        Motion.Key first=new Motion.Key(300,0);first.x1=0;first.y1=0;first.x2=1;first.y2=1;p.clips.get(0).motion.put(4,first);p.clips.get(0).motion.put(4,new Motion.Key(2300,1));
        EditProject.Overlay title=new EditProject.Overlay();title.text="ICE";title.font="monospace";title.color=Color.GREEN;title.alignment=0;title.x=-.8f;title.y=.5f;title.scale=1.5f;title.endMs=5000;p.overlays.add(title);
        ProjectStore store=new ProjectStore(context);store.save(p);p=store.load(p.id);assertEquals(2,p.clips.get(0).speed,0);assertEquals("monospace",p.overlays.get(0).font);assertEquals(Color.GREEN,p.overlays.get(0).color);assertEquals(0,p.overlays.get(0).alignment);
        File file=export(p,180);try(MediaMetadataRetriever r=new MediaMetadataRetriever()){
            r.setDataSource(file.getPath());long duration=Long.parseLong(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));assertTrue("Speed changes video duration",Math.abs(duration-5000)<150);
            int middle=pixel(r,500),blue=pixel(r,2000);assertTrue("Speed preserves source-time opacity keys: "+Integer.toHexString(middle),Color.red(middle)>70&&Color.red(middle)<190);assertTrue(Color.blue(blue)>200&&Color.red(blue)<30);
            Bitmap frame=r.getFrameAtTime(2000000,MediaMetadataRetriever.OPTION_CLOSEST);int green=0;for(int y=0;y<frame.getHeight()/2;y++)for(int x=0;x<frame.getWidth()/2;x++){int color=frame.getPixel(x,y);if(Color.green(color)>140&&Color.red(color)<60&&Color.blue(color)<80)green++;}frame.recycle();assertTrue("Styled green left-aligned text is exported",green>15);
        }finally{File qa=new File(context.getFilesDir(),"qa");qa.mkdirs();java.nio.file.Files.copy(file.toPath(),new File(qa,"speed-180p.mp4").toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);file.delete();}
        org.json.JSONObject old=new org.json.JSONObject(ProjectStore.encode(p));old.put("schema",2);for(int i=0;i<old.getJSONArray("clips").length();i++)old.getJSONArray("clips").getJSONObject(i).remove("speed");org.json.JSONObject overlay=old.getJSONArray("overlays").getJSONObject(0);overlay.remove("font");overlay.remove("color");overlay.remove("alignment");EditProject legacy=ProjectStore.decode(old.toString());assertEquals(1,legacy.clips.get(0).speed,0);assertEquals(Color.WHITE,legacy.overlays.get(0).color);
    }
    @Test public void foregroundExportCanBeCancelledWithoutPublishing() throws Exception {
        File file=serviceExport(project(0),true);assertFalse(file.exists());
    }
    @Test public void missingMediaRecordsRedactedCauseAndNextExportRecovers() throws Exception {
        EditProject broken=project(0);
        broken.clips.get(0).uri=Uri.fromFile(new File(context.getCacheDir(),"private-missing-video.mp4")).toString();
        serviceExport(broken,false,true);
        android.content.SharedPreferences prefs=context.getSharedPreferences(ExportService.PREFS,0);
        String report=prefs.getString(ExportService.LAST_FAILURE,"");
        assertTrue(report.contains("ExportException"));assertTrue(report.contains("Stage: encoding"));
        assertFalse(report.contains("private-missing-video"));assertFalse(report.contains("file://"));
        File[] leftovers=context.getCacheDir().listFiles((d,n)->n.startsWith("bc1-export-")&&n.endsWith(".mp4"));
        assertNotNull(leftovers);assertEquals("Failed temporary export removed",0,leftovers.length);
        File recovered=serviceExport(project(0),false);
        assertEquals("Success preserves last failure for support",report,prefs.getString(ExportService.LAST_FAILURE,""));
        File qa=new File(context.getFilesDir(),"qa");qa.mkdirs();
        java.nio.file.Files.copy(recovered.toPath(),new File(qa,"recovery-1080p.mp4").toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);recovered.delete();
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
        // Repeated fresh foreground exports expose intermittent codec/muxer failures.
        // This is not a retry: every iteration must pass, and every output is decoded by CI.
        for(int iteration=0;iteration<3;iteration++){
        android.util.Log.i("BC1ExportTest","1080p service export iteration "+iteration);
        File file=serviceExport(restored,false);
        try(MediaMetadataRetriever r=new MediaMetadataRetriever()) {
            r.setDataSource(file.getPath());assertEquals("1920",r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));assertEquals("1080",r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            assertEquals("yes",r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO));assertTrue(Color.blue(pixel(r,3000))>200);
        }
        MediaExtractor extractor=new MediaExtractor();extractor.setDataSource(file.getPath());boolean audio=false,video=false;
        for(int i=0;i<extractor.getTrackCount();i++){String mime=extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME);audio|="audio/mp4a-latm".equals(mime);video|="video/avc".equals(mime);}
        extractor.release();assertTrue(audio&&video);
        // Keep the acceptance export in test output for manual decoding / ffprobe checks.
        File report=new File(context.getFilesDir(),"qa");report.mkdirs();java.nio.file.Files.copy(file.toPath(),new File(report,"acceptance-1080p-"+iteration+".mp4").toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);file.delete();
        }
    }
    @Test public void previewSeeksAfterTrimAcrossBothTracks() throws Exception {
        EditProject p=project(0);CountDownLatch ready=new CountDownLatch(1);AtomicReference<PlaybackException> error=new AtomicReference<>();AtomicReference<CompositionPlayer> ref=new AtomicReference<>();
        context.getSharedPreferences(EditorActivity.PREFS,0).edit().remove("last-project").commit();
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
        try{ProjectStore.decode(json.replace("\"schema\": "+EditProject.SCHEMA,"\"schema\": 999"));fail("Unsupported schema must fail");}catch(org.json.JSONException expected){}
        assertEquals(json,ProjectStore.encode(store.load(p.id)));
    }
    @Test public void independentLayerKeyframesCompositeAndChromaKey() throws Exception {
        EditProject p=project(-1);EditProject.Clip upper=p.clips.get(1);upper.track=1;upper.startMs=500;upper.outMs=1300;upper.scale=.5f;
        Motion.Key first=new Motion.Key(300,0);first.x1=0;first.y1=0;first.x2=1;first.y2=1;upper.motion.put(4,first);upper.motion.put(4,new Motion.Key(1300,1));
        File file=export(p,180);try(MediaMetadataRetriever r=new MediaMetadataRetriever()){r.setDataSource(file.getPath());int before=pixel(r,200),early=pixel(r,750),late=pixel(r,1250),after=pixel(r,1750);assertTrue(Color.red(before)>200);assertTrue("Animated blue increases",Color.blue(late)>Color.blue(early)+60);assertTrue("Base returns after overlay ends",Color.red(after)>200&&Color.blue(after)<30);Bitmap frame=r.getFrameAtTime(1000000,MediaMetadataRetriever.OPTION_CLOSEST);int corner=frame.getPixel(280,140);assertTrue("Scaled layer leaves base visible",Color.red(corner)>200&&Color.blue(corner)<30);frame.recycle();}finally{file.delete();}
        File image=new File(context.getFilesDir(),"key-green.png");Bitmap b=Bitmap.createBitmap(320,180,Bitmap.Config.ARGB_8888);b.eraseColor(Color.GREEN);try(OutputStream out=new FileOutputStream(image)){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();upper.uri=Uri.fromFile(image).toString();upper.image=true;upper.scale=1;upper.motion=new Motion();upper.chroma=true;
        file=export(p,180);try(MediaMetadataRetriever r=new MediaMetadataRetriever()){r.setDataSource(file.getPath());int center=pixel(r,1000);assertTrue("Green removed to reveal red base",Color.red(center)>200&&Color.green(center)<40);}finally{file.delete();}
    }
    @Test public void projectBundlesRoundTripMediaAndRejectTraversal() throws Exception {
        EditProject p=project(-1);p.clips.get(1).track=1;p.clips.get(1).startMs=500;p.clips.get(1).motion.put(0,new Motion.Key(0,.4f));ByteArrayOutputStream out=new ByteArrayOutputStream();ProjectBundle.write(context,p,out);EditProject restored=ProjectBundle.read(context,new ByteArrayInputStream(out.toByteArray()));assertNotEquals(p.id,restored.id);assertEquals(1,restored.clips.get(1).track);assertEquals(.4f,restored.clips.get(1).motion.value(0,0,0),0);assertTrue(new File(Uri.parse(restored.clips.get(0).uri).getPath()).isFile());
        out.reset();try(java.util.zip.ZipOutputStream zip=new java.util.zip.ZipOutputStream(out)){zip.putNextEntry(new java.util.zip.ZipEntry("../escape"));zip.write(1);zip.closeEntry();}try{ProjectBundle.read(context,new ByteArrayInputStream(out.toByteArray()));fail("Traversal must be rejected");}catch(IOException expected){}
        String legacy=ProjectStore.encode(project(-1)).replace("\"schema\": 3","\"schema\": 1");assertEquals(0,ProjectStore.decode(legacy).clips.get(0).track);
    }
    @Test public void audioWaveformDecodesRealPeaks() throws Exception {
        String uri=fixture("music.wav");float[] peaks=AudioWaveform.read(context,uri,1000);float max=0;for(float peak:peaks)max=Math.max(max,peak);assertTrue(max>.03f&&max<=1);assertTrue(AudioWaveform.hasAudio(context,uri));
    }
    @Test public void extractedAudioLayerOffsetsAndFadesInExport() throws Exception {
        EditProject p=project(-1);p.clips.remove(1);p.clips.get(0).volume=0;EditProject.Clip audio=p.clips.get(0).copy();audio.track=4;audio.startMs=500;audio.outMs=1300;audio.volume=1;audio.fadeInMs=300;audio.fadeOutMs=300;p.clips.add(audio);
        File file=export(p,180);float[] peaks=AudioWaveform.read(context,Uri.fromFile(file).toString(),2000);float middle=peaks[256],early=peaks[145],before=peaks[50],after=peaks[460];assertTrue("Independent audio begins after offset",before<.005f&&middle>.03f);assertTrue("Fade in attenuates the start",early<middle*.65f);assertTrue("Independent audio ends before base video",after<.005f);file.delete();
    }
}
