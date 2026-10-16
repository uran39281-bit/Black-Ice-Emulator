package com.blackice.bc1.editor;

import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import androidx.media3.common.*;
import androidx.media3.common.audio.GainProcessor;
import androidx.media3.common.util.Size;
import androidx.media3.effect.*;
import androidx.media3.transformer.*;
import com.google.common.collect.ImmutableList;
import java.util.*;

/** The Android backend for the portable EditProject. Preview and export use the same graph. */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class CompositionFactory {
    private CompositionFactory() {}
    public static DefaultVideoFrameProcessor.Factory frameProcessor() {
        return new DefaultVideoFrameProcessor.Factory.Builder()
                .setGlObjectsProvider(new Gles3Provider())
                .setSdrWorkingColorSpace(DefaultVideoFrameProcessor.WORKING_COLOR_SPACE_ORIGINAL).build();
    }
    public static Composition build(Context context,EditProject project,boolean export) {
        return buildAtHeight(context,project,export?1080:project.previewHeight);
    }
    public static Composition buildAtHeight(Context context,EditProject project,int shortEdge) {
        project.validate(); if(project.clips.isEmpty()) throw new IllegalArgumentException("Import a video or image first.");
        int longEdge=(shortEdge*16/9+1)/2*2;
        int w=project.portrait?shortEdge:longEdge, h=project.portrait?longEdge:shortEdge;
        List<EditProject.Placement> placements=project.placements(); long duration=project.durationMs();
        List<EditedMediaItemSequence> sequences=new ArrayList<>();
        int tracks=project.clips.size()>1?2:1;
        for(int track=0;track<tracks;track++) {
            EditedMediaItemSequence.Builder sequence=new EditedMediaItemSequence.Builder(Set.of(C.TRACK_TYPE_VIDEO,C.TRACK_TYPE_AUDIO));
            long cursor=0;
            for(EditProject.Placement p:placements) if(p.index%2==track) {
                if(p.startMs>cursor) sequence.addGap((p.startMs-cursor)*1000);
                EditProject.Clip c=p.clip;
                MediaItem.Builder source=new MediaItem.Builder().setUri(c.uri);
                if(c.image) source.setImageDurationMs(c.durationMs());
                else source.setClippingConfiguration(new MediaItem.ClippingConfiguration.Builder().setStartPositionMs(c.inMs).setEndPositionMs(c.outMs).build());
                List<Effect> effects=List.of(
                        Presentation.createForWidthAndHeight(w,h,Presentation.LAYOUT_SCALE_TO_FIT),
                        new ClipEffect(p,p.index>0?project.clips.get(p.index-1):null),
                        FrameDropEffect.createDefaultFrameDropEffect(30));
                EditedMediaItem item=new EditedMediaItem.Builder(source.build()).setFrameRate(30)
                        .setDurationUs((c.image?c.durationMs():c.sourceDurationMs)*1000)
                        .setEffects(new Effects(List.of(gain(c.volume)),effects)).build();
                sequence.addItem(item); cursor=p.endMs;
            }
            if(cursor<duration) sequence.addGap((duration-cursor)*1000);
            sequences.add(sequence.build());
        }
        if(project.music!=null && project.music.startMs<duration) {
            EditProject.Music m=project.music;
            EditedMediaItemSequence.Builder audio=new EditedMediaItemSequence.Builder(Set.of(C.TRACK_TYPE_AUDIO));
            if(m.startMs>0) audio.addGap(m.startMs*1000);
            long cursor=m.startMs; int count=0;
            do {
                long length=Math.min(m.outMs-m.inMs,duration-cursor);
                MediaItem item=new MediaItem.Builder().setUri(m.uri).setClippingConfiguration(
                        new MediaItem.ClippingConfiguration.Builder().setStartPositionMs(m.inMs).setEndPositionMs(m.inMs+length).build()).build();
                audio.addItem(new EditedMediaItem.Builder(item).setRemoveVideo(true).setDurationUs(m.sourceDurationMs*1000)
                        .setEffects(new Effects(List.of(gain(m.volume)),List.of())).build());
                cursor+=length;
                if(++count>2000) throw new IllegalArgumentException("Music segment is too short to loop across this project. Use a longer trim.");
            } while(m.loop && cursor<duration);
            if(cursor<duration) audio.addGap((duration-cursor)*1000);
            sequences.add(audio.build());
        }
        List<Effect> finalEffects=new ArrayList<>();
        if(!project.overlays.isEmpty()) finalEffects.add(new OverlayEffect(ImmutableList.of(new ProjectOverlay(context,project.overlays))));
        finalEffects.add(Presentation.createForWidthAndHeight(w,h,Presentation.LAYOUT_SCALE_TO_FIT));
        finalEffects.add(FrameDropEffect.createDefaultFrameDropEffect(30));
        return new Composition.Builder(sequences).setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
                .setVideoCompositorSettings(new VideoCompositorSettings() {
                    @Override public Size getOutputSize(List<Size> sizes) { return new Size(w,h); }
                    @Override public OverlaySettings getOverlaySettings(int inputId,long timeUs) {
                        long ms=timeUs/1000; boolean active=false;
                        for(EditProject.Placement p:placements) if(p.index%2==inputId && ms>=p.startMs && ms<p.endMs) {active=true;break;}
                        return new StaticOverlaySettings.Builder().setAlphaScale(active?1:0).build();
                    }
                }).setEffects(new Effects(List.of(),finalEffects)).build();
    }
    private static GainProcessor gain(float volume) {
        return new GainProcessor(new GainProcessor.GainProvider() {
            @Override public float getGainFactorAtSamplePosition(long sample,int rate) { return volume; }
            @Override public long isUnityUntil(long sample,int rate) { return volume==1?C.TIME_END_OF_SOURCE:C.TIME_UNSET; }
        });
    }
    /** Upload only when timed overlay visibility changes, not once per decoded frame. */
    private static final class ProjectOverlay extends BitmapOverlay {
        private final Context context;
        private final List<EditProject.Overlay> overlays;
        private final Map<String,Bitmap> images=new HashMap<>();
        private Bitmap bitmap;
        private int width,height,activeMask=-1;
        ProjectOverlay(Context context,List<EditProject.Overlay> overlays) { this.context=context.getApplicationContext();this.overlays=overlays; }
        @Override public void configure(Size size) {super.configure(size);width=size.getWidth();height=size.getHeight();}
        @Override public Bitmap getBitmap(long timeUs) throws VideoFrameProcessingException {
            int mask=0; for(int i=0;i<overlays.size();i++) {EditProject.Overlay o=overlays.get(i); if(timeUs/1000>=o.startMs && timeUs/1000<o.endMs) mask|=1<<i;}
            if(bitmap==null || bitmap.getWidth()!=width || bitmap.getHeight()!=height) {
                if(bitmap!=null) bitmap.recycle(); bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);activeMask=-1;
            }
            if(activeMask==mask) return bitmap;
            bitmap.eraseColor(Color.TRANSPARENT); Canvas canvas=new Canvas(bitmap); Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
            for(int i=0;i<overlays.size();i++) if((mask&(1<<i))!=0) {
                EditProject.Overlay o=overlays.get(i); float x=(o.x+1)*width/2f,y=(1-o.y)*height/2f;
                paint.setAlpha(Math.round(o.opacity*255));
                if(o.imageUri!=null) {
                    Bitmap image=images.get(o.imageUri);
                    if(image==null) try(java.io.InputStream in=context.getContentResolver().openInputStream(Uri.parse(o.imageUri))) {
                        image=BitmapFactory.decodeStream(in); if(image==null) throw new java.io.IOException("Cannot decode overlay image"); images.put(o.imageUri,image);
                    } catch(java.io.IOException e) {throw new VideoFrameProcessingException(e);}
                    float iw=width*.45f*o.scale,ih=iw*image.getHeight()/image.getWidth();
                    canvas.drawBitmap(image,null,new RectF(x-iw/2,y-ih/2,x+iw/2,y+ih/2),paint);
                } else {
                    paint.setColor(Color.WHITE); paint.setAlpha(Math.round(o.opacity*255)); paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
                    paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(Math.min(width,height)*.065f*o.scale);
                    paint.setShadowLayer(3,0,2,Color.BLACK);
                    String[] lines=o.text.split("\n",-1); float lineHeight=paint.getTextSize()*1.25f;
                    for(int line=0;line<lines.length;line++) {
                        String text=lines[line]; float measure=paint.measureText(text);
                        if(measure>width*.9f) paint.setTextSize(paint.getTextSize()*width*.9f/measure);
                        canvas.drawText(text,x,y+(line-(lines.length-1)/2f)*lineHeight,paint);
                    }
                    paint.clearShadowLayer();
                }
            }
            activeMask=mask; return bitmap;
        }
        @Override public void release() throws VideoFrameProcessingException {super.release();if(bitmap!=null)bitmap.recycle();for(Bitmap b:images.values())b.recycle();images.clear();}
    }
}
