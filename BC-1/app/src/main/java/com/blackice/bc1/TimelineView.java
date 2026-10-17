package com.blackice.bc1;

import android.graphics.*;
import android.view.*;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
import com.blackice.bc1.editor.*;
import java.util.*;

/** Layered timeline with a centred playhead: the content scrolls underneath it, so the horizontal
 * scroll offset is always playhead × scale. Gestures: tap a clip to select it, drag a selected body to
 * move, drag its edges to trim, drag a row's ≡ handle to reorder layers, drag empty space to scrub,
 * pinch to zoom. One continuous drag is one undo entry. */
final class TimelineView extends View {
    static final int RULER_DP=34,ROW_DP=52,BAR_DP=46;
    private final EditorActivity a;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF thumbBounds=new RectF();
    private final ScaleGestureDetector pinch;
    /** Row kinds: 0 track (index = track number), 1 text/shape/image layer (index = overlay), 2 music. */
    static final class Row{final int kind,index;float top;Row(int k,int i){kind=k;index=i;}}
    private List<Row> rows=new ArrayList<>();
    // Gesture state
    private float downX,downY,lastRawX,lastRawY,lastEventX;private int lastEventScroll;private long downTime,startPlayhead;
    private boolean pinching,moved,scrubbing,vertical,longPressed;
    private int operation,hitClip=-1,hitOverlay=-1,headerZone=-1,reorderFrom=-1,reorderTo=-1;
    private long pendingDelta,guide=-1;
    private EditProject.Placement original;private EditProject.Overlay originalOverlay;
    private final Runnable autoScroll=new Runnable(){@Override public void run(){autoScrollStep();}};
    private int autoDirection;
    static final int OP_TRIM_START=1,OP_TRIM_END=2,OP_MOVE=3,OP_OV_START=4,OP_OV_END=5,OP_OV_MOVE=6,OP_MULTI=7;

    TimelineView(EditorActivity activity){
        super(activity);a=activity;setFocusable(true);
        setContentDescription("Timeline: tap a layer to select it, drag a selected layer to move it, drag its edges to trim, drag empty space to scrub, pinch to zoom. The playhead stays centred.");
        pinch=new ScaleGestureDetector(activity,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            public boolean onScaleBegin(ScaleGestureDetector d){pinching=true;operation=0;pendingDelta=0;guide=-1;stopAutoScroll();return true;}
            public boolean onScale(ScaleGestureDetector d){a.zoomTimeline(d.getScaleFactor(),d.getFocusX()-a.timelineScroll.getScrollX());return true;}
        });
    }
    // ---- Geometry ---------------------------------------------------------------------------------
    int viewport(){int w=a.timelineScroll==null?0:a.timelineScroll.getWidth();return w>0?w:getResources().getDisplayMetrics().widthPixels;}
    float pad(){return viewport()/2f;}
    float timelineScale(){return Math.min(a.dp(a.pixelsPerSecond),16_000_000f/Math.max(1f,a.project.durationMs()/1000f));}
    float x(long ms){return pad()+ms/1000f*timelineScale();}
    long time(float x){return Math.round((x-pad())/timelineScale()*1000);}
    int headerWidth(){return a.dp(a.wideLayout?150:120);}
    List<Row> rows(){
        List<Row> list=new ArrayList<>();float y=a.dp(RULER_DP);
        for(int[] r:a.layerRows()){Row row=new Row(r[0],r[1]);row.top=y;y+=a.dp(ROW_DP);list.add(row);}
        Row music=new Row(2,0);music.top=y;list.add(music);rows=list;return list;
    }
    float contentHeight(){return a.dp(RULER_DP)+(a.layerRows().size()+1)*a.dp(ROW_DP)+a.dp(8);}
    Row rowAt(float y){for(Row r:rows)if(y>=r.top&&y<r.top+a.dp(ROW_DP))return r;return null;}
    float rowTopOfTrack(int track){for(Row r:rows())if(r.kind==0&&r.index==track)return r.top;return -1;}
    float rowTopOfOverlay(int overlay){for(Row r:rows())if(r.kind==1&&r.index==overlay)return r.top;return -1;}
    @Override protected void onMeasure(int w,int h){setMeasuredDimension(Math.max(viewport()+1,Math.round(viewport()+a.project.durationMs()/1000f*timelineScale())+a.dp(2)),Math.round(contentHeight()));}
    /** Keeps the playhead centred; called whenever the playhead or scale changes. */
    void syncScroll(){if(a.timelineScroll==null)return;int target=Math.round(a.project.playheadMs/1000f*timelineScale());if(a.timelineScroll.getScrollX()!=target)a.timelineScroll.scrollTo(target,0);invalidate();}

    // ---- Drawing ----------------------------------------------------------------------------------
    @Override protected void onDraw(Canvas canvas){
        List<Row> rows=rows();int height=getHeight();float left=a.timelineScroll.getScrollX(),right=left+viewport();
        float top=0,bottom=height;if(a.timelineScroll.getParent() instanceof ScrollView){ScrollView sv=(ScrollView)a.timelineScroll.getParent();top=sv.getScrollY();bottom=top+sv.getHeight();}
        canvas.drawColor(EditorActivity.PANEL);
        for(int i=0;i<rows.size();i++){Row r=rows.get(i);paint.setColor(i%2==0?0xFF0B1620:EditorActivity.PANEL);canvas.drawRect(left,r.top,right,r.top+a.dp(ROW_DP),paint);}
        drawRuler(canvas,left,right,height);
        EditProject p=a.project;List<EditProject.Placement> placements=p.placements();float bar=a.dp(BAR_DP);
        for(EditProject.Placement pl:placements){float y=rowTopOfTrackCached(rows,pl.clip.track);if(y<0)continue;float l=x(pl.startMs),r=x(pl.endMs);if(r<left||l>right||y+bar<top||y>bottom)continue;drawClip(canvas,pl,l,r,y+a.dp(3),bar,left,right);}
        for(Row row:rows)if(row.kind==1){String g=a.project.overlays.get(row.index).group;
            if(g!=null&&a.collapsedGroups.contains(g)){for(int i=0;i<a.project.overlays.size();i++)if(g.equals(a.project.overlays.get(i).group))drawOverlay(canvas,i,row.top+a.dp(3),bar,left,right);}
            else drawOverlay(canvas,row.index,row.top+a.dp(3),bar,left,right);}
        Row musicRow=rows.get(rows.size()-1);
        if(p.music!=null){long end=p.music.loop?p.durationMs():Math.min(p.durationMs(),p.music.startMs+p.music.outMs-p.music.inMs);paint.setColor(0xFF277A79);canvas.drawRoundRect(x(p.music.startMs),musicRow.top+a.dp(8),x(end),musicRow.top+a.dp(40),a.dp(4),a.dp(4),paint);paint.setColor(EditorActivity.TEXT);paint.setTextSize(a.dp(11));canvas.drawText("♫  "+p.music.name,Math.max(x(p.music.startMs),left+headerWidth())+a.dp(6),musicRow.top+a.dp(29),paint);}
        if(operation!=0&&moved)drawDragPreview(canvas,rows,bar);
        if(guide>=0){paint.setColor(0xAA9FEAFF);paint.setStrokeWidth(a.dp(1));for(float y=a.dp(RULER_DP);y<height;y+=a.dp(8))canvas.drawLine(x(guide),y,x(guide),y+a.dp(4),paint);}
        // Centred playhead with the current time directly above it.
        float px=left+viewport()/2f;paint.setColor(EditorActivity.CYAN);paint.setStrokeWidth(a.dp(2));canvas.drawLine(px,a.dp(RULER_DP)-a.dp(6),px,height,paint);
        String label=EditorActivity.clock(p.playheadMs)+"  f"+frame(p.playheadMs);paint.setTextSize(a.dp(11));paint.setTypeface(Typeface.DEFAULT_BOLD);float tw=paint.measureText(label);
        paint.setColor(EditorActivity.CYAN);canvas.drawRoundRect(px-tw/2-a.dp(6),a.dp(2),px+tw/2+a.dp(6),a.dp(19),a.dp(9),a.dp(9),paint);paint.setColor(0xFF04121C);canvas.drawText(label,px-tw/2,a.dp(15),paint);paint.setTypeface(Typeface.DEFAULT);
        drawHeaders(canvas,rows,left);
    }
    private float rowTopOfTrackCached(List<Row> rows,int track){for(Row r:rows)if(r.kind==0&&r.index==track)return r.top;return -1;}
    long frame(long ms){return Math.round(ms*a.project.frameRate/1000.0);}
    private void drawRuler(Canvas canvas,float left,float right,int height){
        EditProject p=a.project;paint.setColor(0xFF09131C);canvas.drawRect(left,0,right,a.dp(RULER_DP),paint);
        if(p.loopInMs>=0){paint.setColor(0x5540BFFF);canvas.drawRect(x(p.loopInMs),a.dp(20),x(p.loopOutMs),a.dp(RULER_DP),paint);paint.setColor(0x1A40BFFF);canvas.drawRect(x(p.loopInMs),a.dp(RULER_DP),x(p.loopOutMs),height,paint);}
        double interval=TimelineEdits.rulerStep(timelineScale(),a.dp(56),p.frameRate)*1000;
        long first=(long)Math.max(0,Math.floor(time(left)/interval)),last=(long)Math.ceil(time(right)/interval);
        paint.setTextSize(a.dp(10));paint.setStrokeWidth(a.dp(1));
        for(long tick=first;tick<=last&&tick-first<400;tick++){long ms=Math.round(tick*interval);float xx=x(ms);if(ms>p.durationMs()+interval)break;
            paint.setColor(EditorActivity.MUTED);canvas.drawLine(xx,a.dp(22),xx,a.dp(RULER_DP),paint);canvas.drawText(TimelineEdits.rulerLabel(ms,interval/1000,p.frameRate),xx+a.dp(3),a.dp(31),paint);
            paint.setColor(0x22FFFFFF);canvas.drawLine(xx,a.dp(RULER_DP),xx,height,paint);}
        float perFrame=timelineScale()/p.frameRate;
        if(perFrame>=a.dp(5)){paint.setColor(0xFF345465);long f0=(long)Math.max(0,Math.floor(time(left)*p.frameRate/1000.0)),f1=(long)Math.ceil(time(right)*p.frameRate/1000.0);for(long f=f0;f<=f1&&f-f0<2000;f++){float xx=x(Math.round(f*1000.0/p.frameRate));canvas.drawLine(xx,a.dp(RULER_DP)-a.dp(5),xx,a.dp(RULER_DP),paint);}}
        paint.setColor(0xFFFFC145);for(long m:p.markers){float xx=x(m);if(xx<left-a.dp(8)||xx>right+a.dp(8))continue;Path tri=new Path();tri.moveTo(xx-a.dp(5),a.dp(20));tri.lineTo(xx+a.dp(5),a.dp(20));tri.lineTo(xx,a.dp(28));tri.close();canvas.drawPath(tri,paint);paint.setStrokeWidth(a.dp(1));canvas.drawLine(xx,a.dp(28),xx,height,paint);}
    }
    private boolean multiSelected(int clip){return a.multiClips.contains(clip);}
    private void drawClip(Canvas canvas,EditProject.Placement pl,float l,float r,float y,float bar,float left,float right){
        EditProject p=a.project;EditProject.Clip c=pl.clip;boolean selected=pl.index==a.selected||multiSelected(pl.index),hidden=c.track<4?p.tracks[c.track].hidden:p.tracks[c.track].muted;
        paint.setAlpha(255);paint.setColor(selected?0xFF226D99:c.track>=4?0xFF1D4B4A:0xFF204057);canvas.drawRoundRect(l,y,r,y+bar,a.dp(5),a.dp(5),paint);
        if(c.track<4){Bitmap thumb=a.thumbnails.get(c,a.timelineThumbnailsReady);if(thumb!=null){canvas.save();canvas.clipRect(l+a.dp(1),y+a.dp(1),r-a.dp(1),y+bar-a.dp(1));float tw=bar*thumb.getWidth()/thumb.getHeight();float firstX=l+Math.max(0,(float)Math.floor((left-l)/tw))*tw;for(float tx=firstX;tx<Math.min(r,right);tx+=tw){thumbBounds.set(tx,y,tx+tw,y+bar);canvas.drawBitmap(thumb,null,thumbBounds,paint);}paint.setColor(0xAA08131F);canvas.drawRect(l,y+bar-a.dp(20),r,y+bar,paint);canvas.restore();}}
        float[] peaks=a.waveforms.get(c.uri);if(peaks!=null){paint.setColor(0xFF74D3C1);for(float xx=Math.max(l,left);xx<Math.min(r,right);xx+=a.dp(3)){long source=c.inMs+(long)((xx-l)/(r-l)*(c.outMs-c.inMs));int bin=(int)Math.min(peaks.length-1,source*peaks.length/Math.max(1,c.sourceDurationMs));float amp=peaks[Math.max(0,bin)]*a.dp(12);canvas.drawLine(xx,y+bar/2-amp,xx,y+bar/2+amp,paint);}}
        if(pl.outgoingMs>0){paint.setColor(0x9940BFFF);canvas.drawRect(x(pl.endMs-pl.outgoingMs),y,r,y+bar,paint);}
        canvas.save();canvas.clipRect(l+a.dp(4),y,r-a.dp(3),y+bar);paint.setColor(EditorActivity.TEXT);paint.setTextSize(a.dp(11));canvas.drawText(c.name,Math.max(l,left+headerWidth())+a.dp(7),y+bar-a.dp(6),paint);canvas.restore();
        if(selected)drawKeys(canvas,c.motion,l,y,bar,k->x(pl.startMs+Math.round((k-c.inMs)/(double)c.speed)),left,right);
        if(hidden){paint.setColor(0x99060D13);canvas.drawRoundRect(l,y,r,y+bar,a.dp(5),a.dp(5),paint);}
        if(p.tracks[c.track].locked){paint.setColor(0x33FFFFFF);paint.setStrokeWidth(a.dp(1));canvas.save();canvas.clipRect(l,y,r,y+bar);for(float xx=Math.max(l,left)-bar;xx<Math.min(r,right);xx+=a.dp(10))canvas.drawLine(xx,y+bar,xx+bar,y,paint);canvas.restore();}
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(a.dp(selected?2:1));paint.setColor(selected?EditorActivity.CYAN:0xFF3B677C);canvas.drawRoundRect(l,y,r,y+bar,a.dp(5),a.dp(5),paint);paint.setStyle(Paint.Style.FILL);
        if(pl.index==a.selected&&a.multiCount()<=1){paint.setColor(EditorActivity.CYAN);float handle=Math.min(a.dp(8),Math.max(a.dp(3),(r-l)/4));canvas.drawRoundRect(l,y,l+handle,y+bar,a.dp(3),a.dp(3),paint);canvas.drawRoundRect(r-handle,y,r,y+bar,a.dp(3),a.dp(3),paint);
            paint.setColor(0xFF04121C);canvas.drawRect(l+handle/2-a.dp(.75f),y+bar/2-a.dp(7),l+handle/2+a.dp(.75f),y+bar/2+a.dp(7),paint);canvas.drawRect(r-handle/2-a.dp(.75f),y+bar/2-a.dp(7),r-handle/2+a.dp(.75f),y+bar/2+a.dp(7),paint);}
    }
    interface KeyX{float x(long keyTime);}
    /** Diamonds at keyframe times so the clip's timing stays visible while editing animation. */
    private void drawKeys(Canvas canvas,Motion m,float l,float y,float bar,KeyX at,float left,float right){
        paint.setColor(0xFFFFFFFF);TreeSet<Long> times=new TreeSet<>();for(List<Motion.Key> keys:m.curves)for(Motion.Key k:keys)times.add(k.timeMs);
        for(long t:times){float xx=at.x(t);if(xx<left||xx>right)continue;Path d=new Path();float s=a.dp(5),cy=y+a.dp(9);d.moveTo(xx,cy-s);d.lineTo(xx+s,cy);d.lineTo(xx,cy+s);d.lineTo(xx-s,cy);d.close();canvas.drawPath(d,paint);}
    }
    private void drawOverlay(Canvas canvas,int index,float y,float bar,float left,float right){
        EditProject.Overlay o=a.project.overlays.get(index);boolean chosen=(index==a.selectedOverlay&&a.selected<0)||a.multiOverlays.contains(index);
        long delta=chosen&&operation>=OP_OV_START&&operation<=OP_OV_MOVE&&moved?pendingDelta:0;
        float l=x(o.startMs+(operation==OP_OV_START||operation==OP_OV_MOVE?delta:0)),r=x(o.endMs+(operation==OP_OV_END||operation==OP_OV_MOVE?delta:0));
        if(r<left||l>right)return;
        int base=o.imageUri!=null?0xFF3E5C7A:o.shape>0?0xFF2F6D5A:0xFF5A4683;paint.setColor(chosen?0xFF325C79:base);canvas.drawRoundRect(l,y,r,y+bar,a.dp(5),a.dp(5),paint);
        paint.setColor(o.imageUri==null?(o.color|0xFF000000):0xFF9FEAFF);canvas.drawRect(l,y,l+a.dp(4),y+bar,paint);
        canvas.save();canvas.clipRect(l+a.dp(6),y,r-a.dp(6),y+bar);paint.setColor(EditorActivity.TEXT);paint.setTextSize(a.dp(11));canvas.drawText((o.shape>0?"◆ ":o.imageUri!=null?"▣ ":"T  ")+o.label(),Math.max(l,left+headerWidth())+a.dp(9),y+bar-a.dp(8),paint);canvas.restore();
        if(chosen)drawKeys(canvas,o.motion,l,y,bar,k->x(o.startMs+k),left,right);
        if(o.hidden){paint.setColor(0x99060D13);canvas.drawRoundRect(l,y,r,y+bar,a.dp(5),a.dp(5),paint);}
        if(o.locked){paint.setColor(0x33FFFFFF);paint.setStrokeWidth(a.dp(1));canvas.save();canvas.clipRect(l,y,r,y+bar);for(float xx=Math.max(l,left)-bar;xx<Math.min(r,right);xx+=a.dp(10))canvas.drawLine(xx,y+bar,xx+bar,y,paint);canvas.restore();}
        if(chosen){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(a.dp(2));paint.setColor(EditorActivity.CYAN);canvas.drawRoundRect(l,y,r,y+bar,a.dp(5),a.dp(5),paint);paint.setStyle(Paint.Style.FILL);
            if(a.multiCount()<=1){float handle=Math.min(a.dp(8),Math.max(a.dp(3),(r-l)/4));canvas.drawRoundRect(l,y,l+handle,y+bar,a.dp(3),a.dp(3),paint);canvas.drawRoundRect(r-handle,y,r,y+bar,a.dp(3),a.dp(3),paint);}}
    }
    private void drawDragPreview(Canvas canvas,List<Row> rows,float bar){
        if(original!=null&&operation<=OP_MOVE){float y=rowTopOfTrackCached(rows,original.clip.track)+a.dp(3),l=x(original.startMs+(operation==OP_TRIM_START||operation==OP_MOVE?pendingDelta:0)),r=x(original.endMs+(operation==OP_TRIM_END||operation==OP_MOVE?pendingDelta:0));
            paint.setColor(0x5540BFFF);canvas.drawRect(l,y,r,y+bar,paint);paint.setColor(EditorActivity.CYAN);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(a.dp(2));canvas.drawRect(l,y,r,y+bar,paint);paint.setStyle(Paint.Style.FILL);
            long length=original.clip.durationMs()+(operation==OP_TRIM_START?-pendingDelta:operation==OP_TRIM_END?pendingDelta:0);paint.setTextSize(a.dp(11));canvas.drawText(EditorActivity.seconds(length)+" s",Math.max(a.timelineScroll.getScrollX()+headerWidth(),l)+a.dp(4),y+a.dp(16),paint);}
        if(operation==OP_MULTI){paint.setColor(0x5540BFFF);for(int i:a.selectedClips()){EditProject.Placement pl=a.project.placements().get(i);float y=rowTopOfTrackCached(rows,pl.clip.track)+a.dp(3);canvas.drawRect(x(pl.startMs+pendingDelta),y,x(pl.endMs+pendingDelta),y+bar,paint);}
            for(int i:a.selectedOverlays()){EditProject.Overlay o=a.project.overlays.get(i);float y=rowTopOfOverlay(i)+a.dp(3);canvas.drawRect(x(o.startMs+pendingDelta),y,x(o.endMs+pendingDelta),y+bar,paint);}}
    }
    /** Pinned left column: ≡ reorder handle, color, name, visibility and lock per row. */
    private void drawHeaders(Canvas canvas,List<Row> rows,float left){
        int w=headerWidth();paint.setColor(0xF0080F15);canvas.drawRect(left,a.dp(RULER_DP),left+w,getHeight(),paint);
        paint.setColor(0xFF263A48);paint.setStrokeWidth(a.dp(1));canvas.drawLine(left+w,a.dp(RULER_DP),left+w,getHeight(),paint);
        for(Row r:rows){
            float y=r.top,mid=y+a.dp(ROW_DP)/2f;boolean drop=reorderFrom>=0&&reorderTo==rows.indexOf(r);
            if(drop){paint.setColor(0x3340BFFF);canvas.drawRect(left,y,left+w,y+a.dp(ROW_DP),paint);}
            if(r.kind==2){paint.setColor(EditorActivity.MUTED);paint.setTextSize(a.dp(11));canvas.drawText("Music",left+a.dp(28),mid+a.dp(4),paint);continue;}
            boolean overlay=r.kind==1;EditProject.Overlay o=overlay?a.project.overlays.get(r.index):null;EditProject.Track t=overlay?null:a.project.tracks[r.index];
            boolean audio=!overlay&&r.index>=4,hidden=overlay?o.hidden:audio?t.muted:t.hidden,locked=overlay?o.locked:t.locked,solo=overlay?o.solo:t.solo;
            int color=overlay?(o.imageUri==null?(o.color|0xFF000000):0xFF9FEAFF):t.color;
            if(r.kind==0&&r.index!=0||overlay){paint.setColor(EditorActivity.MUTED);for(int i=-1;i<=1;i++)canvas.drawRect(left+a.dp(7),mid+i*a.dp(5)-a.dp(.75f),left+a.dp(19),mid+i*a.dp(5)+a.dp(.75f),paint);}
            paint.setColor(color);canvas.drawRect(left+a.dp(24),y+a.dp(8),left+a.dp(27),y+a.dp(ROW_DP)-a.dp(8),paint);
            Bitmap thumb=null;if(!overlay&&r.index<4)for(EditProject.Clip c:a.project.clips)if(c.track==r.index){thumb=a.thumbnails.get(c,a.timelineThumbnailsReady);break;}
            float textLeft=a.dp(32);if(thumb!=null&&w>=a.dp(140)){thumbBounds.set(left+a.dp(31),y+a.dp(10),left+a.dp(31)+a.dp(32)*thumb.getWidth()/(float)thumb.getHeight()*.75f,y+a.dp(34));canvas.drawBitmap(thumb,null,thumbBounds,paint);textLeft=thumbBounds.right-left+a.dp(5);}
            paint.setColor(hidden?EditorActivity.MUTED:EditorActivity.TEXT);paint.setTextSize(a.dp(11));paint.setTypeface(Typeface.DEFAULT_BOLD);
            String name=a.rowName(new int[]{r.kind,r.index});canvas.save();canvas.clipRect(left+textLeft,y,left+w-a.dp(48),y+a.dp(ROW_DP));canvas.drawText(name,left+textLeft,mid-a.dp(2),paint);canvas.restore();paint.setTypeface(Typeface.DEFAULT);
            paint.setTextSize(a.dp(9));paint.setColor(EditorActivity.MUTED);canvas.drawText(overlay?(o.group!=null&&a.collapsedGroups.contains(o.group)?"group":o.imageUri!=null?"image":o.shape>0?"shape":"text"):r.index==0?"main":audio?"audio":"video",left+textLeft,mid+a.dp(12),paint);
            if(solo){paint.setColor(0xFFFFC145);canvas.drawText("S",left+a.dp(80),mid+a.dp(12),paint);}
            drawIcon(canvas,audio?(hidden?"Mute":"Volume"):hidden?"Hidden":"Eye",left+w-a.dp(46),mid-a.dp(10),hidden?EditorActivity.MUTED:EditorActivity.TEXT);
            drawIcon(canvas,locked?"Lock":"Unlock",left+w-a.dp(23),mid-a.dp(10),locked?EditorActivity.CYAN:EditorActivity.MUTED);
        }
    }
    private void drawIcon(Canvas canvas,String name,float x,float y,int color){V1Icon icon=new V1Icon(name,color);icon.setBounds(Math.round(x),Math.round(y),Math.round(x+a.dp(20)),Math.round(y+a.dp(20)));icon.draw(canvas);}

    // ---- Hit testing -------------------------------------------------------------------------------
    int hitClip(float xx,float yy){Row r=rowAt(yy);if(r==null||r.kind!=0)return -1;List<EditProject.Placement> ps=a.project.placements();for(int i=ps.size()-1;i>=0;i--){EditProject.Placement p=ps.get(i);if(p.clip.track==r.index&&xx>=x(p.startMs)-a.dp(6)&&xx<=x(p.endMs)+a.dp(6))return p.index;}return -1;}
    int hitOverlay(float xx,float yy){Row r=rowAt(yy);if(r==null||r.kind!=1)return -1;EditProject.Overlay o=a.project.overlays.get(r.index);return xx>=x(o.startMs)-a.dp(6)&&xx<=x(o.endMs)+a.dp(6)?r.index:-1;}
    private boolean inHeader(float xx){return xx<a.timelineScroll.getScrollX()+headerWidth();}

    // ---- Touch -------------------------------------------------------------------------------------
    @Override public boolean onTouchEvent(MotionEvent e){
        int action=e.getActionMasked();pinch.onTouchEvent(e);
        if(action==MotionEvent.ACTION_DOWN)return down(e);
        if(e.getPointerCount()>1){pinching=true;operation=0;pendingDelta=0;guide=-1;scrubbing=false;stopAutoScroll();invalidate();return true;}
        if(action==MotionEvent.ACTION_MOVE&&!pinching)return move(e);
        if(action==MotionEvent.ACTION_UP)return up(e);
        if(action==MotionEvent.ACTION_CANCEL)reset();
        return true;
    }
    private boolean down(MotionEvent e){
        rows();downX=e.getX();downY=e.getY();lastRawX=e.getRawX();lastRawY=e.getRawY();downTime=e.getEventTime();startPlayhead=a.project.playheadMs;
        moved=false;pinching=false;scrubbing=false;vertical=false;longPressed=false;operation=0;pendingDelta=0;guide=-1;original=null;originalOverlay=null;headerZone=-1;reorderFrom=-1;reorderTo=-1;
        getParent().requestDisallowInterceptTouchEvent(true);a.pausePlayback();
        if(downY>=a.dp(RULER_DP)&&inHeader(downX)){Row r=rowAt(downY);if(r!=null&&r.kind!=2){float local=downX-a.timelineScroll.getScrollX();int w=headerWidth();
            headerZone=local<a.dp(24)?0:local>w-a.dp(24)?3:local>w-a.dp(48)?2:1;if(headerZone==0&&(r.kind==1||r.index!=0))reorderFrom=rows.indexOf(r);}return true;}
        hitClip=downY<a.dp(RULER_DP)?-1:hitClip(downX,downY);hitOverlay=hitClip>=0||downY<a.dp(RULER_DP)?-1:hitOverlay(downX,downY);
        // Edge handles of the selected clip win even slightly outside its bar.
        if(a.multiCount()<=1&&a.clip()!=null&&!LayerOps.locked(a.project,a.selected)){EditProject.Placement chosen=a.project.placements().get(a.selected);float y=rowTopOfTrack(chosen.clip.track);
            if(y>=0&&downY>=y&&downY<=y+a.dp(ROW_DP)&&Math.min(Math.abs(downX-x(chosen.startMs)),Math.abs(downX-x(chosen.endMs)))<=a.dp(16))hitClip=a.selected;}
        if(a.uiLocked||a.loading||a.importing||a.exporting())return true;
        if(a.multiCount()>1&&(hitClip>=0&&a.multiClips.contains(hitClip)||hitOverlay>=0&&a.multiOverlays.contains(hitOverlay))){boolean anyLocked=false;for(int i:a.selectedClips())anyLocked|=LayerOps.locked(a.project,i);for(int i:a.selectedOverlays())anyLocked|=a.project.overlays.get(i).locked;if(!anyLocked)operation=OP_MULTI;return true;}
        if(hitClip>=0&&hitClip==a.selected&&!LayerOps.locked(a.project,hitClip)){original=a.project.placements().get(hitClip);float dl=Math.abs(downX-x(original.startMs)),dr=Math.abs(downX-x(original.endMs));operation=Math.min(dl,dr)<=a.dp(16)?(dl<dr?OP_TRIM_START:OP_TRIM_END):OP_MOVE;}
        else if(hitOverlay>=0&&hitOverlay==a.selectedOverlay&&a.selected<0&&!a.project.overlays.get(hitOverlay).locked){originalOverlay=a.project.overlays.get(hitOverlay);float dl=Math.abs(downX-x(originalOverlay.startMs)),dr=Math.abs(downX-x(originalOverlay.endMs));operation=Math.min(dl,dr)<=a.dp(16)?(dl<dr?OP_OV_START:OP_OV_END):OP_OV_MOVE;}
        if(e.isButtonPressed(MotionEvent.BUTTON_SECONDARY)){operation=0;if(hitClip>=0)a.selectClip(hitClip,false);else if(hitOverlay>=0)a.selectOverlay(hitOverlay);if(hitClip>=0||hitOverlay>=0)a.moreMenu();}
        return true;
    }
    private boolean move(MotionEvent e){
        float dx=e.getRawX()-lastRawX,dy=e.getRawY()-lastRawY;
        if(!moved&&Math.hypot(e.getX()-downX,e.getY()-downY)>a.dp(6)){moved=true;vertical=Math.abs(e.getY()-downY)>Math.abs(e.getX()-downX)*1.3f;}
        if(!moved)return true;
        if(reorderFrom>=0){Row r=rowAt(e.getY());reorderTo=r==null||r.kind==2?-1:rows.indexOf(r);invalidate();return true;}
        if(headerZone>=0){((ScrollView)a.timelineScroll.getParent()).scrollBy(0,Math.round(-dy));lastRawX=e.getRawX();lastRawY=e.getRawY();return true;}
        if(operation!=0){lastEventX=e.getX();lastEventScroll=a.timelineScroll.getScrollX();updateDrag();updateAutoScroll();return true;}
        // Empty space (or an unselected layer): horizontal drags scrub under the centred playhead, vertical drags scroll layers.
        if(vertical)((ScrollView)a.timelineScroll.getParent()).scrollBy(0,Math.round(-dy));
        else{scrubbing=true;a.seek(Math.max(0,a.project.playheadMs-Math.round(dx/timelineScale()*1000)));}
        lastRawX=e.getRawX();lastRawY=e.getRawY();return true;
    }
    /** The finger's content x: the last event position plus any auto-scroll since that event. */
    private float contentX(){return lastEventX+(a.timelineScroll.getScrollX()-lastEventScroll);}
    private void updateDrag(){
        float xx=contentX();EditProject p=a.project;long tol=Math.round(a.dp(8)*1000/timelineScale());
        // Content coordinates already include any auto-scroll since the drag began.
        pendingDelta=time(xx)-time(downX);guide=-1;
        if(operation==OP_MULTI){long earliest=Long.MAX_VALUE;for(int i:a.selectedClips())earliest=Math.min(earliest,p.placements().get(i).startMs);for(int i:a.selectedOverlays())earliest=Math.min(earliest,p.overlays.get(i).startMs);
            if(a.snapping){long snapped=TimelineEdits.snap(p,-1,earliest+pendingDelta,tol);if(snapped!=earliest+pendingDelta)guide=snapped;pendingDelta=snapped-earliest;}pendingDelta=Math.max(-earliest,pendingDelta);invalidate();return;}
        if(operation>=OP_OV_START){long anchor=operation==OP_OV_END?originalOverlay.endMs:originalOverlay.startMs;if(a.snapping){long snapped=TimelineEdits.snap(p,-1,anchor+pendingDelta,tol);if(snapped!=anchor+pendingDelta)guide=snapped;pendingDelta=snapped-anchor;}
            long before=pendingDelta;pendingDelta=Math.max(operation==OP_OV_END?originalOverlay.startMs+EditProject.MIN_CLIP_MS-originalOverlay.endMs:-originalOverlay.startMs,pendingDelta);if(operation==OP_OV_START)pendingDelta=Math.min(originalOverlay.endMs-originalOverlay.startMs-EditProject.MIN_CLIP_MS,pendingDelta);if(pendingDelta!=before)guide=-1;invalidate();return;}
        long anchor=operation==OP_TRIM_END?original.endMs:original.startMs,desired=anchor+pendingDelta;
        if(a.snapping){long snapped=TimelineEdits.snap(p,a.selected,desired,tol);if(snapped==desired&&operation==OP_MOVE){long end=original.endMs+pendingDelta,endSnap=TimelineEdits.snap(p,a.selected,end,tol);if(endSnap!=end){snapped=desired+endSnap-end;guide=endSnap;}}else if(snapped!=desired)guide=snapped;pendingDelta=snapped-anchor;}
        long before=pendingDelta;EditProject.Clip c=original.clip;
        // Pictures extend freely; video and audio stop at their source unless speed is changed explicitly.
        if(operation==OP_TRIM_START){long min=c.image?-7_200_000_000L:Math.round(-c.inMs/c.speed);if(c.track>0)min=Math.max(min,-original.startMs);pendingDelta=Math.max(min,Math.min(c.durationMs()-EditProject.MIN_CLIP_MS,pendingDelta));}
        if(operation==OP_TRIM_END)pendingDelta=Math.max(EditProject.MIN_CLIP_MS-c.durationMs(),Math.min(c.image?7_200_000_000L-c.outMs:Math.round((c.sourceDurationMs-c.outMs)/c.speed),pendingDelta));
        if(operation==OP_MOVE)pendingDelta=Math.max(-original.startMs,pendingDelta);
        if(pendingDelta!=before)guide=-1;invalidate();
    }
    /** Dragging near a viewport edge keeps scrolling (and scrubbing) so long moves and trims are possible. */
    private void updateAutoScroll(){
        float local=lastEventX-lastEventScroll,edge=a.dp(36);
        int direction=local<edge+headerWidth()?-1:local>viewport()-edge?1:0;
        if(direction!=autoDirection){autoDirection=direction;removeCallbacks(autoScroll);if(direction!=0)postOnAnimation(autoScroll);}
    }
    private void autoScrollStep(){
        if(autoDirection==0||operation==0)return;long step=Math.max(1,Math.round(a.dp(6)*1000/timelineScale()));
        long next=Math.max(0,Math.min(a.project.durationMs()+5000,a.project.playheadMs+autoDirection*step));if(next==a.project.playheadMs){autoDirection=0;return;}
        a.project.playheadMs=next;a.syncPosition();updateDrag();postOnAnimation(autoScroll);
    }
    private void stopAutoScroll(){autoDirection=0;removeCallbacks(autoScroll);}
    private boolean up(MotionEvent e){
        stopAutoScroll();boolean longPress=e.getEventTime()-downTime>=ViewConfiguration.getLongPressTimeout();
        if(reorderFrom>=0){int from=reorderFrom,to=reorderTo;reset();if(moved&&to>=0&&to!=from)a.reorderRows(from,to);else if(!moved)a.tell("Drag the ≡ handle up or down to reorder layers.");return true;}
        if(headerZone>=0&&!moved){Row r=rowAt(downY);int zone=headerZone;reset();if(r!=null)headerTap(r,zone,longPress);return true;}
        if(!pinching&&operation!=0&&moved&&pendingDelta!=0)commit();
        else if(!pinching&&!moved){
            boolean multi=longPress||(e.getMetaState()&(KeyEvent.META_CTRL_ON|KeyEvent.META_SHIFT_ON))!=0;
            if(downY<a.dp(RULER_DP)){if(longPress)a.toggleMarkerAt(time(downX));else a.seek(Math.max(0,time(downX)));}
            else if(hitClip>=0){if(multi)a.toggleMulti(hitClip,-1);else a.selectClip(hitClip,false);}
            else if(hitOverlay>=0){if(multi)a.toggleMulti(-1,hitOverlay);else a.selectOverlay(hitOverlay);}
            else a.deselect();
            performClick();
        }
        reset();return true;
    }
    private void headerTap(Row r,int zone,boolean longPress){
        int[] row={r.kind,r.index};
        if(zone==3){a.rowFlag(row,"lock");return;}
        if(zone==2){a.rowFlag(row,"hide");return;}
        if(longPress){a.renameRow(row);return;}
        if(r.kind==1)a.selectOverlay(r.index);else for(int i=0;i<a.project.clips.size();i++)if(a.project.clips.get(i).track==r.index){a.selectClip(i,false);return;}
    }
    private void commit(){
        int op=operation;long delta=pendingDelta;EditProject.Placement origin=original;EditProject.Overlay ov=originalOverlay;
        if(op==OP_MULTI){java.util.List<Integer> clips=a.selectedClips(),overlays=a.selectedOverlays();java.util.List<Integer> movable=new ArrayList<>();for(int i:clips)if(a.project.clips.get(i).track>0)movable.add(i);
            if(movable.size()<clips.size())a.tell("Main-sequence clips keep their order; other selected layers moved.");a.edit("Layers moved",()->LayerOps.moveMany(a.project,movable,overlays,delta));return;}
        a.edit(op==OP_MOVE||op==OP_OV_MOVE?"Clip moved":"Trim applied",()->{
            if(op>=OP_OV_START){if(op==OP_OV_START||op==OP_OV_MOVE)ov.startMs+=delta;if(op==OP_OV_END||op==OP_OV_MOVE)ov.endMs+=delta;}
            else if(op<OP_MOVE)TimelineEdits.edge(a.project,a.selected,op==OP_TRIM_START,delta);
            else if(origin.clip.track>0)a.project.setPlacement(a.selected,origin.clip.track,origin.startMs+delta);
            else{int destination=a.selected;long center=origin.startMs+delta+origin.clip.durationMs()/2;for(EditProject.Placement p:a.project.placements())if(p.clip.track==0&&p.index!=a.selected&&center>=p.startMs&&center<=p.endMs)destination=p.index;a.project.move(a.selected,destination-a.selected);a.selected=destination;}
        });
    }
    private void reset(){operation=0;guide=-1;pendingDelta=0;pinching=false;scrubbing=false;headerZone=-1;reorderFrom=-1;reorderTo=-1;getParent().requestDisallowInterceptTouchEvent(false);invalidate();}
    @Override public boolean performClick(){super.performClick();return true;}
    @Override public boolean onGenericMotionEvent(MotionEvent event){
        if(event.getAction()==MotionEvent.ACTION_SCROLL){float wheel=event.getAxisValue(MotionEvent.AXIS_VSCROLL),horizontal=event.getAxisValue(MotionEvent.AXIS_HSCROLL);
            if((event.getMetaState()&KeyEvent.META_CTRL_ON)!=0){a.zoomTimeline(wheel>0?1.2f:1/1.2f,Float.NaN);return true;}
            boolean scrub=(event.getMetaState()&KeyEvent.META_SHIFT_ON)!=0||horizontal!=0||contentHeight()<=((View)a.timelineScroll.getParent()).getHeight();
            if(scrub){float amount=horizontal!=0?horizontal:wheel;a.seek(Math.max(0,a.project.playheadMs-Math.round(amount*a.dp(70)/timelineScale()*1000)));}
            else((ScrollView)a.timelineScroll.getParent()).smoothScrollBy(0,Math.round(-wheel*a.dp(52)));
            return true;}
        return super.onGenericMotionEvent(event);
    }
}
