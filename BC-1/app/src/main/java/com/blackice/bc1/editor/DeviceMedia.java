package com.blackice.bc1.editor;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.LruCache;
import android.util.Size;
import java.util.*;

/** Read-only view of the device gallery for the Add drawer. Nothing is imported until chosen, and
 * chosen items are copied by {@link MediaImporter} so projects never depend on gallery files. */
public final class DeviceMedia {
    public static final class Item {
        public final Uri uri; public final String name,album; public final boolean video,audio; public final long durationMs;
        Item(Uri uri,String name,String album,boolean video,boolean audio,long durationMs){this.uri=uri;this.name=name;this.album=album;this.video=video;this.audio=audio;this.durationMs=durationMs;}
    }
    private final Context context;
    private final LruCache<String,Bitmap> thumbnails=new LruCache<String,Bitmap>(24*1024*1024){@Override protected int sizeOf(String k,Bitmap b){return b.getByteCount();}};
    public DeviceMedia(Context context){this.context=context.getApplicationContext();}

    /** Permissions for the current API level: full visual access, or user-selected access on Android 14+. */
    public static String[] visualPermissions() {
        if(Build.VERSION.SDK_INT>=34) return new String[]{Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VIDEO,Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED};
        if(Build.VERSION.SDK_INT>=33) return new String[]{Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VIDEO};
        return new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
    }
    public static String[] audioPermissions() {
        return Build.VERSION.SDK_INT>=33?new String[]{Manifest.permission.READ_MEDIA_AUDIO}:new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
    }
    /** "full", "partial" (Android 14 selected photos) or "none". */
    public String access(boolean audio) {
        if(audio) return granted(audioPermissions()[0])?"full":"none";
        if(Build.VERSION.SDK_INT>=33&&granted(Manifest.permission.READ_MEDIA_IMAGES)&&granted(Manifest.permission.READ_MEDIA_VIDEO)) return "full";
        if(Build.VERSION.SDK_INT>=34&&granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)) return "partial";
        if(Build.VERSION.SDK_INT<33&&granted(Manifest.permission.READ_EXTERNAL_STORAGE)) return "full";
        return "none";
    }
    private boolean granted(String permission){return context.checkSelfPermission(permission)==PackageManager.PERMISSION_GRANTED;}

    /** Newest first, at most {@code limit} items; call off the main thread. */
    public List<Item> query(boolean audio,int limit) {
        List<Item> result=new ArrayList<>();
        if(audio) { query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,false,true,limit,result); return result; }
        query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,false,false,limit,result);
        query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,true,false,limit,result);
        return result;
    }
    private void query(Uri collection,boolean video,boolean audio,int limit,List<Item> into) {
        String bucket=audio?MediaStore.Audio.Media.ALBUM:MediaStore.MediaColumns.BUCKET_DISPLAY_NAME;
        String[] columns=video||audio?new String[]{MediaStore.MediaColumns._ID,MediaStore.MediaColumns.DISPLAY_NAME,bucket,MediaStore.MediaColumns.DURATION}:new String[]{MediaStore.MediaColumns._ID,MediaStore.MediaColumns.DISPLAY_NAME,bucket};
        try(Cursor c=context.getContentResolver().query(collection,columns,null,null,MediaStore.MediaColumns.DATE_MODIFIED+" DESC")) {
            if(c==null) return;
            int n=0; while(c.moveToNext()&&n++<limit) {
                long id=c.getLong(0);String name=c.getString(1),album=c.getString(2);long duration=columns.length>3?c.getLong(3):0;
                into.add(new Item(ContentUris.withAppendedId(collection,id),name==null?"Media":name,album==null?"Other":album,video,audio,duration));
            }
        } catch(SecurityException denied) { /* Access was revoked while browsing; the UI offers access management. */ }
    }
    public static List<String> albums(List<Item> items) {
        TreeSet<String> names=new TreeSet<>(String.CASE_INSENSITIVE_ORDER);for(Item i:items)names.add(i.album);return new ArrayList<>(names);
    }
    public Bitmap cached(Uri uri){return thumbnails.get(uri.toString());}
    /** Decodes a small thumbnail; call off the main thread. */
    public Bitmap thumbnail(Item item,int size) {
        Bitmap cached=thumbnails.get(item.uri.toString());if(cached!=null)return cached;
        try {
            Bitmap b;
            if(Build.VERSION.SDK_INT>=29) b=context.getContentResolver().loadThumbnail(item.uri,new Size(size,size),null);
            else if(item.video){android.media.MediaMetadataRetriever r=new android.media.MediaMetadataRetriever();try{r.setDataSource(context,item.uri);b=r.getFrameAtTime(0);}finally{r.release();}}
            else {android.graphics.BitmapFactory.Options o=new android.graphics.BitmapFactory.Options();o.inSampleSize=8;try(java.io.InputStream in=context.getContentResolver().openInputStream(item.uri)){b=android.graphics.BitmapFactory.decodeStream(in,null,o);}}
            if(b!=null) thumbnails.put(item.uri.toString(),b);
            return b;
        } catch(Exception e) { return null; }
    }
}
