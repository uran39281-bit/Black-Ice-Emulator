package com.blackice.bc1.editor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.*;

/** Bounded thumbnail cache. Source decoding never runs on the UI/render thread. */
public final class ThumbnailCache implements AutoCloseable {
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(8*1024*1024){
        @Override protected int sizeOf(String key,Bitmap bitmap){return bitmap.getByteCount();}
    };
    private final Map<String,List<Runnable>> pending=new HashMap<>();
    private final Set<String> failed=new HashSet<>();
    private volatile boolean closed;
    public ThumbnailCache(Context context){this.context=context.getApplicationContext();}
    private String key(EditProject.Clip c){return c.uri+"@"+c.inMs;}
    public Bitmap peek(EditProject.Clip c){return cache.get(key(c));}
    public Bitmap get(EditProject.Clip c,Runnable ready){
        String key=key(c);Bitmap current=cache.get(key);if(current!=null||closed||failed.contains(key))return current;
        if(pending.containsKey(key)){if(pending.get(key).size()<8&&!pending.get(key).contains(ready))pending.get(key).add(ready);return null;}
        List<Runnable> listeners=new ArrayList<>();listeners.add(ready);pending.put(key,listeners);
        String uri=c.uri;long time=c.inMs;boolean image=c.image;
        worker.execute(()->{Bitmap result=null;try{result=decode(uri,time,image);}catch(Exception ignored){}final Bitmap decoded=result;
            main.post(()->{List<Runnable> callbacks=pending.remove(key);if(closed){if(decoded!=null)decoded.recycle();return;}if(decoded==null)failed.add(key);else cache.put(key,decoded);if(callbacks!=null)for(Runnable callback:callbacks)callback.run();});});
        return null;
    }
    private Bitmap decode(String source,long time,boolean image)throws Exception{
        Uri uri=Uri.parse(source);Bitmap bitmap;
        if(image){BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
            try(InputStream in=context.getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,options);}
            options.inSampleSize=1;while(options.outWidth/options.inSampleSize>320||options.outHeight/options.inSampleSize>180)options.inSampleSize*=2;
            options.inJustDecodeBounds=false;try(InputStream in=context.getContentResolver().openInputStream(uri)){bitmap=BitmapFactory.decodeStream(in,null,options);}
        }else{try(MediaMetadataRetriever retriever=new MediaMetadataRetriever()){retriever.setDataSource(context,uri);bitmap=Build.VERSION.SDK_INT>=27?retriever.getScaledFrameAtTime(time*1000,MediaMetadataRetriever.OPTION_CLOSEST_SYNC,160,90):retriever.getFrameAtTime(time*1000,MediaMetadataRetriever.OPTION_CLOSEST_SYNC);}}
        if(bitmap==null)return null;float factor=Math.min(1f,Math.min(160f/bitmap.getWidth(),90f/bitmap.getHeight()));
        if(factor<1){Bitmap small=Bitmap.createScaledBitmap(bitmap,Math.max(1,Math.round(bitmap.getWidth()*factor)),Math.max(1,Math.round(bitmap.getHeight()*factor)),true);if(small!=bitmap)bitmap.recycle();return small;}return bitmap;
    }
    @Override public void close(){closed=true;worker.shutdownNow();pending.clear();cache.evictAll();}
}
