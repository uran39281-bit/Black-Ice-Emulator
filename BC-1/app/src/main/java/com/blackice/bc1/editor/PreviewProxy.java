package com.blackice.bc1.editor;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.media3.common.MimeTypes;
import androidx.media3.transformer.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;

/** Seekable preview of complex compositions, rendered by the same GLES graph as export.
 * Media3 1.11's live multi-input player cannot reliably render paused/seeking frames.
 * All calls and callbacks run on the application thread; codecs/GLES run asynchronously.
 */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class PreviewProxy implements AutoCloseable {
    public interface Listener {
        void onProgress(int percent);
        void onReady(File file);
        void onError(String message);
    }
    private static final long BUDGET=512L*1024*1024;
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final File directory;
    private Transformer transformer;
    private File partial;
    private int generation;
    private Runnable poll;

    public PreviewProxy(Context context) {
        this.context=context.getApplicationContext();
        directory=new File(context.getCacheDir(),"bc1-preview-v11");
    }
    public static boolean required(EditProject project) {
        return project.clips.size()>1 || (!project.clips.isEmpty()&&project.clips.get(0).image);
    }
    public void prepare(EditProject project,Listener listener) {
        cancel();final int request=generation;
        try {
            EditProject snapshot=ProjectStore.snapshot(project);snapshot.playheadMs=0;
            directory.mkdirs();
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(ProjectStore.encode(snapshot).getBytes(StandardCharsets.UTF_8));
            StringBuilder key=new StringBuilder();for(byte b:digest)key.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
            File completed=new File(directory,key+".mp4");
            if(completed.isFile()&&completed.length()>0){completed.setLastModified(System.currentTimeMillis());listener.onReady(completed);return;}
            trimCache(null);
            if(directory.getUsableSpace()<64L*1024*1024)throw new IOException("Free some storage to prepare the preview.");
            partial=new File(directory,key+".part.mp4");partial.delete();
            transformer=new Transformer.Builder(context)
                    .setAssetLoaderFactory(CompositionFactory.assetLoader(context))
                    .setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setPortraitEncodingEnabled(true)
                    .setVideoFrameProcessorFactory(CompositionFactory.frameProcessor())
                    .setEncoderFactory(new DefaultEncoderFactory.Builder(context).setEnableFallback(true)
                            .setRequestedVideoEncoderSettings(new VideoEncoderSettings.Builder().setBitrate(2_000_000).build()).build())
                    .addListener(new Transformer.Listener(){
                        @Override public void onCompleted(Composition composition,ExportResult result){
                            if(request!=generation)return;
                            main.removeCallbacks(poll);transformer=null;
                            if(partial==null||!partial.renameTo(completed)){fail(request,listener,"Could not save the prepared preview.");return;}
                            partial=null;trimCache(completed);listener.onReady(completed);
                        }
                        @Override public void onError(Composition composition,ExportResult result,ExportException error){
                            fail(request,listener,error.getMessage());
                        }
                    }).build();
            ProgressHolder progress=new ProgressHolder();
            poll=new Runnable(){@Override public void run(){
                if(request!=generation||transformer==null)return;
                if(partial.length()>BUDGET||directory.getUsableSpace()<16L*1024*1024){fail(request,listener,"Preview cache is full. Shorten the project or free storage; the project is preserved.");return;}
                int state=transformer.getProgress(progress);
                listener.onProgress(state==Transformer.PROGRESS_STATE_AVAILABLE?progress.progress:-1);
                main.postDelayed(this,500);
            }};
            transformer.start(CompositionFactory.build(context,snapshot,false),partial.getAbsolutePath());
            main.post(poll);
        }catch(Exception error){fail(request,listener,error.getMessage());}
    }
    private void fail(int request,Listener listener,String message){
        if(request!=generation)return;cancel();listener.onError(message==null?"Preview preparation failed.":message);
    }
    private void trimCache(File keep){
        File[] files=directory.listFiles((d,n)->n.endsWith(".mp4"));if(files==null)return;
        Arrays.sort(files,Comparator.comparingLong(File::lastModified));long bytes=0;
        for(File file:files)bytes+=file.length();
        for(File file:files)if(!file.equals(keep)&&(file.getName().endsWith(".part.mp4")||bytes>BUDGET/2)){
            long length=file.length();if(file.delete())bytes-=length;
        }
    }
    public void cancel(){
        generation++;if(poll!=null)main.removeCallbacks(poll);poll=null;
        if(transformer!=null){transformer.cancel();transformer=null;}
        if(partial!=null){partial.delete();partial=null;}
    }
    @Override public void close(){cancel();}
}
