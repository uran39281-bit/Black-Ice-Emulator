package com.blackice.bc1.editor;

import android.content.Context;
import android.graphics.*;
import android.media.ExifInterface;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.StatFs;
import android.provider.OpenableColumns;
import java.io.*;
import java.util.UUID;

/** Imports private media copies so project reopening does not depend on temporary URI grants. */
public final class MediaImporter {
    private final Context context;
    public MediaImporter(Context context) {this.context=context.getApplicationContext();}
    public EditProject.Clip importClip(Uri uri,boolean audioOnly) throws IOException {
        String name="Imported media";
        try(android.database.Cursor c=context.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)) {
            if(c!=null && c.moveToFirst()) name=c.getString(0);
        }
        String mime=context.getContentResolver().getType(uri);
        boolean image=mime!=null && mime.startsWith("image/");
        if(image && audioOnly) throw new IOException("Choose an audio file.");
        File directory=new File(context.getFilesDir(),"media");directory.mkdirs();
        File copy=new File(directory,UUID.randomUUID().toString()+".source");
        try {
            try(InputStream in=context.getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(copy)) {
                if(in==null) throw new IOException("Media cannot be opened.");
                byte[] bytes=new byte[256*1024]; int n; long sinceCheck=0;
                while((n=in.read(bytes))!=-1) {
                    if(Thread.currentThread().isInterrupted()) throw new IOException("Import cancelled.");
                    sinceCheck+=n;
                    if(sinceCheck>=1024*1024) { sinceCheck=0; if(new StatFs(directory.getPath()).getAvailableBytes()<64L*1024*1024) throw new IOException("Not enough storage to import this media."); }
                    out.write(bytes,0,n);
                }
            }
            EditProject.Clip clip=new EditProject.Clip();clip.name=name;clip.image=image;
            if(image) {
                Bitmap bitmap;
                if(Build.VERSION.SDK_INT>=28) bitmap=ImageDecoder.decodeBitmap(ImageDecoder.createSource(copy),(decoder,info,source)->{
                    int w=info.getSize().getWidth(),h=info.getSize().getHeight();
                    float scale=Math.min(1f,2048f/Math.max(w,h));decoder.setTargetSize(Math.max(1,Math.round(w*scale)),Math.max(1,Math.round(h*scale)));
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                });
                else {
                    BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeFile(copy.getPath(),options);
                    options.inSampleSize=1;while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>2048)options.inSampleSize*=2;
                    options.inJustDecodeBounds=false;bitmap=BitmapFactory.decodeFile(copy.getPath(),options);
                    if(bitmap!=null) {
                        ExifInterface exif=new ExifInterface(copy.getPath());int orientation=exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,1);
                        Matrix matrix=new Matrix();
                        if(orientation==2)matrix.setScale(-1,1);if(orientation==3)matrix.setRotate(180);if(orientation==4)matrix.setScale(1,-1);
                        if(orientation==5){matrix.setRotate(90);matrix.postScale(-1,1);}if(orientation==6)matrix.setRotate(90);
                        if(orientation==7){matrix.setRotate(270);matrix.postScale(-1,1);}if(orientation==8)matrix.setRotate(270);
                        if(!matrix.isIdentity()){Bitmap rotated=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),matrix,true);if(rotated!=bitmap)bitmap.recycle();bitmap=rotated;}
                    }
                }
                if(bitmap==null) throw new IOException("This image format could not be decoded.");
                File png=new File(directory,copy.getName()+".png");
                try(OutputStream out=new FileOutputStream(png)) {if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("Image conversion failed.");}
                finally {bitmap.recycle();}
                copy.delete();copy=png;clip.sourceDurationMs=600_000;clip.outMs=3000;
            } else {
                try(MediaMetadataRetriever metadata=new MediaMetadataRetriever()) {
                    metadata.setDataSource(copy.getPath());
                    String duration=metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                    clip.sourceDurationMs=duration==null?0:Long.parseLong(duration);clip.outMs=clip.sourceDurationMs;
                    boolean hasVideo="yes".equals(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO));
                    boolean hasAudio="yes".equals(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO));
                    if(clip.sourceDurationMs<EditProject.MIN_CLIP_MS || (audioOnly?!hasAudio:!hasVideo))
                        throw new IOException(audioOnly?"No playable audio was found.":"No playable video was found. Use Music for audio files.");
                } catch(RuntimeException e) {throw new IOException("This media could not be read. Try a local MP4, JPEG/PNG, or audio file.",e);}
            }
            clip.uri=Uri.fromFile(copy).toString();return clip;
        } catch(IOException|RuntimeException e) {copy.delete();throw e;}
    }
}
