package com.blackice.bc1;

import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.blackice.bc1.editor.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class PreviewProxyTest {
    private final Instrumentation inst=InstrumentationRegistry.getInstrumentation();
    private File prepare(PreviewProxy proxy,EditProject project)throws Exception{
        CountDownLatch done=new CountDownLatch(1);AtomicReference<File> result=new AtomicReference<>();AtomicReference<String> error=new AtomicReference<>();
        inst.runOnMainSync(()->proxy.prepare(project,new PreviewProxy.Listener(){
            public void onProgress(int percent){}
            public void onReady(File file){result.set(file);done.countDown();}
            public void onError(String message){error.set(message);done.countDown();}
        }));
        assertTrue("Preview preparation completes",done.await(90,TimeUnit.SECONDS));assertNull(error.get());assertNotNull(result.get());return result.get();
    }
    @Test public void cancellationCacheReuseAndEditInvalidation()throws Exception{
        EditProject project=new EditProject();project.previewHeight=360;
        File source=new File(inst.getTargetContext().getFilesDir(),"proxy-"+project.id+".png");
        Bitmap image=Bitmap.createBitmap(160,90,Bitmap.Config.ARGB_8888);image.eraseColor(0xFF2080E0);
        try(OutputStream out=new FileOutputStream(source)){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();
        EditProject.Clip clip=new EditProject.Clip();clip.name="Preview cache fixture";clip.image=true;clip.uri=Uri.fromFile(source).toString();clip.sourceDurationMs=1000;clip.outMs=1000;project.clips.add(clip);
        PreviewProxy proxy=new PreviewProxy(inst.getTargetContext());AtomicInteger staleCallbacks=new AtomicInteger();
        try{
            inst.runOnMainSync(()->{proxy.prepare(project,new PreviewProxy.Listener(){
                public void onProgress(int percent){staleCallbacks.incrementAndGet();}
                public void onReady(File file){staleCallbacks.incrementAndGet();}
                public void onError(String message){staleCallbacks.incrementAndGet();}
            });proxy.cancel();});
            File first=prepare(proxy,project);assertEquals("Cancelled work must not publish stale callbacks",0,staleCallbacks.get());
            try(MediaMetadataRetriever retriever=new MediaMetadataRetriever()){
                retriever.setDataSource(first.getAbsolutePath());
                assertTrue(Long.parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION))>=900);
                Bitmap frame=retriever.getFrameAtTime(0);assertNotNull(frame);int pixel=frame.getPixel(frame.getWidth()/2,frame.getHeight()/2);frame.recycle();
                assertTrue("Prepared preview contains the source pixels",android.graphics.Color.blue(pixel)>180);
            }
            project.playheadMs=500;assertEquals("Seeking reuses the prepared file",first,prepare(proxy,project));
            clip.rotation=90;File edited=prepare(proxy,project);assertNotEquals("Editing invalidates the cached composition",first,edited);
        }finally{inst.runOnMainSync(proxy::close);source.delete();}
    }
}
