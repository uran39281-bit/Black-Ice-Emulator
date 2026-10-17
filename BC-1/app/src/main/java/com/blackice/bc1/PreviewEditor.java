package com.blackice.bc1;

import android.app.AlertDialog;
import android.graphics.*;
import android.view.*;
import com.blackice.bc1.editor.*;
import java.util.*;

/** Direct manipulation over the preview: bounding box with corner / edge / rotation handles, centre and
 * edge guides, tap or long-press layer picking, position motion paths and preview navigation zoom.
 * Moves show a lightweight guide while dragging; the GLES preview is rebuilt once on release. */
final class PreviewEditor extends View {
    private final EditorActivity a;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private TransformMath.Transform base,live;
    private int gesture;private float downX,downY,span,angle;private long downTime;private boolean moved,multi;
    private float guideX=Float.NaN,guideY=Float.NaN;private int dragKey=-1;private long dragKeyTime;
    private float navZoomStart,navPanX,navPanY;
    static final int NONE=0,MOVE=1,CORNER=2,ROTATE=3,EDGE_X=4,EDGE_Y=5,PAN=6,KEY=7;

    PreviewEditor(EditorActivity activity){
        super(activity);a=activity;setFocusable(true);
        setContentDescription("Preview: tap a layer to select it, long-press for overlapping layers. Drag the selection to move, corner handles to resize, the top handle to rotate. Pinch with nothing selected to zoom the view.");
        setTooltipText("Drag to move • corners resize • top knob rotates • long-press: layers here");
    }
    private SurfaceView surface(){return a.surface;}
    private float sw(){return surface().getWidth();}
    private float sh(){return surface().getHeight();}
    /** Selected visual layer is visible at the playhead. */
    boolean available(){
        if(a.previewPending)return false;EditProject.Overlay o=a.overlay();if(o!=null)return a.project.playheadMs>=o.startMs&&a.project.playheadMs<o.endMs;
        EditProject.Clip c=a.clip();if(c==null||c.track>=4)return false;EditProject.Placement p=a.project.placements().get(a.selected);return a.project.playheadMs>=p.startMs&&a.project.playheadMs<p.endMs;
    }
    private float[] corners(TransformMath.Transform t){double[] half=a.layerHalf();float[] c=TransformMath.corners(t,sw(),sh(),half[0],half[1]);for(int i=0;i<4;i++){c[i*2]+=surface().getLeft();c[i*2+1]+=surface().getTop();}return c;}
    private float[] anchorPoint(TransformMath.Transform t){double A=sw()/sh();double ax=t.anchorX*.5*A,ay=t.anchorY*.5,tx=t.x*.5*A,ty=t.y*.5;return new float[]{(float)(surface().getLeft()+sw()/2+(tx+ax)*sh()),(float)(surface().getTop()+sh()/2-(ty+ay)*sh())};}

    @Override protected void onDraw(Canvas canvas){
        if(a.previewZoom!=1||a.previewPanX!=0||a.previewPanY!=0){paint.setColor(0xCC08131F);canvas.drawRoundRect(getWidth()-a.dp(84),a.dp(6),getWidth()-a.dp(6),a.dp(34),a.dp(6),a.dp(6),paint);paint.setColor(EditorActivity.CYAN);paint.setTextSize(a.dp(12));canvas.drawText(Math.round(a.previewZoom*100)+"% • Fit",getWidth()-a.dp(78),a.dp(25),paint);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(a.dp(1));paint.setColor(0x6640BFFF);canvas.drawRect(surface().getLeft(),surface().getTop(),surface().getRight(),surface().getBottom(),paint);paint.setStyle(Paint.Style.FILL);}
        if(a.compareSnapshot!=null&&a.comparing){paint.setColor(0xCC08131F);canvas.drawRoundRect(a.dp(6),a.dp(6),a.dp(96),a.dp(34),a.dp(6),a.dp(6),paint);paint.setColor(EditorActivity.TEXT);paint.setTextSize(a.dp(12));canvas.drawText("Before",a.dp(14),a.dp(25),paint);}
        if(!available())return;
        TransformMath.Transform t=gesture!=NONE&&live!=null?live:a.transformPanel.current();
        if(gesture==NONE&&!Float.isNaN(a.previewMoveX)){t=t.copy();t.x=a.previewMoveX;t.y=a.previewMoveY;}
        drawMotionPath(canvas);
        float[] c=corners(t);paint.setColor(EditorActivity.CYAN);paint.setStrokeWidth(a.dp(1.5f));paint.setStyle(Paint.Style.STROKE);
        Path box=new Path();box.moveTo(c[0],c[1]);for(int i=1;i<4;i++)box.lineTo(c[i*2],c[i*2+1]);box.close();canvas.drawPath(box,paint);
        float tx=(c[0]+c[2])/2,ty=(c[1]+c[3])/2,cxm=(c[0]+c[2]+c[4]+c[6])/4,cym=(c[1]+c[3]+c[5]+c[7])/4;float[] knob=rotationKnob(c);
        canvas.drawLine(tx,ty,knob[0],knob[1],paint);paint.setStyle(Paint.Style.FILL);
        for(int i=0;i<4;i++)canvas.drawCircle(c[i*2],c[i*2+1],a.dp(7),paint);
        if(!a.transformPanel.linked)for(int i=0;i<4;i++){int j=(i+1)%4;canvas.drawRect((c[i*2]+c[j*2])/2-a.dp(5),(c[i*2+1]+c[j*2+1])/2-a.dp(5),(c[i*2]+c[j*2])/2+a.dp(5),(c[i*2+1]+c[j*2+1])/2+a.dp(5),paint);}
        canvas.drawCircle(knob[0],knob[1],a.dp(8),paint);paint.setColor(0xFF04121C);canvas.drawCircle(knob[0],knob[1],a.dp(3),paint);
        float[] anchor=anchorPoint(t);paint.setColor(0xFFFFFFFF);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(a.dp(1.5f));canvas.drawCircle(anchor[0],anchor[1],a.dp(6),paint);canvas.drawLine(anchor[0]-a.dp(9),anchor[1],anchor[0]+a.dp(9),anchor[1],paint);canvas.drawLine(anchor[0],anchor[1]-a.dp(9),anchor[0],anchor[1]+a.dp(9),paint);paint.setStyle(Paint.Style.FILL);
        if(gesture!=NONE){paint.setColor(0xAA9FEAFF);paint.setStrokeWidth(a.dp(1));
            if(!Float.isNaN(guideX))canvas.drawLine(guideX,surface().getTop(),guideX,surface().getBottom(),paint);
            if(!Float.isNaN(guideY))canvas.drawLine(surface().getLeft(),guideY,surface().getRight(),guideY,paint);}
        if(TransformMath.offscreen(t,sw()/(double)sh(),a.layerHalf()[0],a.layerHalf()[1])){paint.setColor(0xFFFFC145);paint.setTextSize(a.dp(11));canvas.drawText("Selected layer is outside the frame • More → Locate",surface().getLeft()+a.dp(8),surface().getBottom()-a.dp(10),paint);}
    }
    private float[] rotationKnob(float[] c){float tx=(c[0]+c[2])/2,ty=(c[1]+c[3])/2,cx=(c[0]+c[4])/2,cy=(c[1]+c[5])/2;float dx=tx-cx,dy=ty-cy,len=(float)Math.max(1,Math.hypot(dx,dy));return new float[]{tx+dx/len*a.dp(28),ty+dy/len*a.dp(28)};}
    /** Position path through the layer's time span with a dot per X/Y keyframe. */
    private void drawMotionPath(Canvas canvas){
        Motion m=a.selectedMotion();if(m==null||(m.curves.get(Motion.X).isEmpty()&&m.curves.get(Motion.Y).isEmpty()))return;
        long[] range=keyRange();Path path=new Path();boolean first=true;
        for(int i=0;i<=60;i++){long t=range[0]+(range[1]-range[0])*i/60;float[] p=positionAt(t);if(first){path.moveTo(p[0],p[1]);first=false;}else path.lineTo(p[0],p[1]);}
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(a.dp(1.5f));paint.setColor(0xAAFFFFFF);paint.setPathEffect(new DashPathEffect(new float[]{a.dp(4),a.dp(4)},0));canvas.drawPath(path,paint);paint.setPathEffect(null);paint.setStyle(Paint.Style.FILL);
        for(long t:keyTimes()){float[] p=positionAt(t);paint.setColor(t==dragKeyTime&&gesture==KEY?EditorActivity.CYAN:0xFFFFFFFF);Path d=new Path();float s=a.dp(6);d.moveTo(p[0],p[1]-s);d.lineTo(p[0]+s,p[1]);d.lineTo(p[0],p[1]+s);d.lineTo(p[0]-s,p[1]);d.close();canvas.drawPath(d,paint);}
    }
    private long[] keyRange(){EditProject.Clip c=a.clip();EditProject.Overlay o=a.overlay();return c!=null?new long[]{c.inMs,c.outMs}:new long[]{0,o.endMs-o.startMs};}
    private TreeSet<Long> keyTimes(){Motion m=a.selectedMotion();TreeSet<Long> times=new TreeSet<>();for(Motion.Key k:m.curves.get(Motion.X))times.add(k.timeMs);for(Motion.Key k:m.curves.get(Motion.Y))times.add(k.timeMs);return times;}
    private float[] positionAt(long time){EditProject.Clip c=a.clip();TransformMath.Transform t=c!=null?TransformMath.of(c,time):TransformMath.of(a.overlay(),time);return anchorPoint(t);}

    // ---- Touch -------------------------------------------------------------------------------------
    @Override public boolean onTouchEvent(MotionEvent e){
        int action=e.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){
            downX=e.getX();downY=e.getY();downTime=e.getEventTime();moved=false;multi=false;gesture=NONE;dragKey=-1;guideX=guideY=Float.NaN;
            if(a.previewZoom!=1||a.previewPanX!=0||a.previewPanY!=0){if(downX>getWidth()-a.dp(90)&&downY<a.dp(40)){a.resetPreviewView();return true;}}
            getParent().requestDisallowInterceptTouchEvent(true);
            boolean editable=available()&&!a.uiLocked&&!a.loading&&!a.importing&&!a.exporting()&&!a.selectionLocked();
            if(editable){a.pausePlayback();base=a.transformPanel.current();live=base.copy();gesture=hitHandle(e.getX(),e.getY());
                if(gesture==NONE&&TransformMath.contains(corners(base),e.getX(),e.getY()))gesture=MOVE;}
            if(gesture==NONE){navZoomStart=a.previewZoom;navPanX=a.previewPanX;navPanY=a.previewPanY;gesture=PAN;}
            span=0;return true;
        }
        if(gesture==NONE)return false;
        if(action==MotionEvent.ACTION_POINTER_DOWN&&e.getPointerCount()==2){multi=true;span=dist(e);angle=ang(e);if(gesture!=PAN&&gesture!=KEY){base=live.copy();gesture=MOVE;}else navZoomStart=a.previewZoom;return true;}
        if(action==MotionEvent.ACTION_MOVE){moved|=Math.hypot(e.getX()-downX,e.getY()-downY)>a.dp(4)||e.getPointerCount()>1;if(!moved)return true;update(e);invalidate();return true;}
        if(action==MotionEvent.ACTION_UP){finish(e);return true;}
        if(action==MotionEvent.ACTION_CANCEL){gesture=NONE;live=null;guideX=guideY=Float.NaN;getParent().requestDisallowInterceptTouchEvent(false);invalidate();}
        return true;
    }
    private float dist(MotionEvent e){return (float)Math.hypot(e.getX(1)-e.getX(0),e.getY(1)-e.getY(0));}
    private float ang(MotionEvent e){return (float)Math.toDegrees(Math.atan2(e.getY(1)-e.getY(0),e.getX(1)-e.getX(0)));}
    private int hitHandle(float x,float y){
        if(a.selectedMotion()!=null)for(long t:keyTimes()){float[] p=positionAt(t);if(Math.hypot(x-p[0],y-p[1])<a.dp(16)){dragKeyTime=t;return KEY;}}
        float[] c=corners(base);float[] knob=rotationKnob(c);if(Math.hypot(x-knob[0],y-knob[1])<a.dp(22))return ROTATE;
        for(int i=0;i<4;i++)if(Math.hypot(x-c[i*2],y-c[i*2+1])<a.dp(22))return CORNER;
        if(!a.transformPanel.linked)for(int i=0;i<4;i++){int j=(i+1)%4;if(Math.hypot(x-(c[i*2]+c[j*2])/2,y-(c[i*2+1]+c[j*2+1])/2)<a.dp(18))return i%2==0?EDGE_Y:EDGE_X;}
        return NONE;
    }
    private void update(MotionEvent e){
        float[] anchor=anchorPoint(base);boolean overlay=a.overlay()!=null;float limit=overlay?1:2,minScale=overlay?.1f:.05f,maxScale=overlay?2:4;
        if(gesture==PAN){
            if(e.getPointerCount()==2&&span>0)a.previewZoom=TransformMath.clamp(navZoomStart*dist(e)/span,.2f,4);
            else if(a.previewZoom!=1||a.previewPanX!=0||a.previewPanY!=0){a.previewPanX=navPanX+(e.getX()-downX)/Math.max(1,sw());a.previewPanY=navPanY+(e.getY()-downY)/Math.max(1,sh());}
            if(a.monitor!=null)a.monitor.requestLayout();return;
        }
        if(gesture==KEY){live.x=TransformMath.clamp(base.x+2*(e.getX()-downX)/Math.max(1,sw()),-limit,limit);live.y=TransformMath.clamp(base.y-2*(e.getY()-downY)/Math.max(1,sh()),-limit,limit);return;}
        if(e.getPointerCount()==2&&span>0){live.scale=TransformMath.clamp(base.scale*dist(e)/span,minScale,maxScale);float delta=(ang(e)-angle+540)%360-180;live.rotation=TransformMath.clamp(snapRotation(base.rotation-delta),-3600,3600);return;}
        if(multi)return;
        switch(gesture){
            case MOVE:{live.x=TransformMath.clamp(base.x+2*(e.getX()-downX)/Math.max(1,sw()),-limit,limit);live.y=TransformMath.clamp(base.y-2*(e.getY()-downY)/Math.max(1,sh()),-limit,limit);snapToGuides();break;}
            case CORNER:{float d0=(float)Math.hypot(downX-anchor[0],downY-anchor[1]),d1=(float)Math.hypot(e.getX()-anchor[0],e.getY()-anchor[1]);float factor=d1/Math.max(1,d0);
                // Proportional unless width/height are unlinked in Move & Transform.
                if(a.transformPanel.linked)live.scale=TransformMath.clamp(base.scale*factor,minScale,maxScale);
                else{float fx=Math.abs(e.getX()-anchor[0])/Math.max(1,Math.abs(downX-anchor[0])),fy=Math.abs(e.getY()-anchor[1])/Math.max(1,Math.abs(downY-anchor[1]));live.scale=TransformMath.clamp(base.scale*fx,minScale,maxScale);live.aspect=TransformMath.clamp(base.aspect*fy/Math.max(.05f,fx),.05f,20);}
                break;}
            case EDGE_X:{float fx=Math.abs(e.getX()-anchor[0])/Math.max(1,Math.abs(downX-anchor[0]));live.scale=TransformMath.clamp(base.scale*fx,minScale,maxScale);live.aspect=TransformMath.clamp(base.aspect/Math.max(.05f,fx),.05f,20);break;}
            case EDGE_Y:{float fy=Math.abs(e.getY()-anchor[1])/Math.max(1,Math.abs(downY-anchor[1]));live.aspect=TransformMath.clamp(base.aspect*fy,.05f,20);break;}
            case ROTATE:{float a0=(float)Math.toDegrees(Math.atan2(downY-anchor[1],downX-anchor[0])),a1=(float)Math.toDegrees(Math.atan2(e.getY()-anchor[1],e.getX()-anchor[0]));live.rotation=TransformMath.clamp(snapRotation(base.rotation-((a1-a0+540)%360-180)),-3600,3600);break;}
        }
    }
    private float snapRotation(float r){return a.transformPanel.angleSnap>0?TransformMath.snapAngle(r,a.transformPanel.angleSnap):r;}
    /** Snap the layer's centre / edges to the frame's centre / edges within 8dp, with visible guides. */
    private void snapToGuides(){
        guideX=guideY=Float.NaN;float tol=a.dp(8);float[] b=TransformMath.bounds(corners(live));float left=surface().getLeft(),top=surface().getTop(),right=surface().getRight(),bottom=surface().getBottom(),cx=(left+right)/2,cy=(top+bottom)/2;
        float[] xs={(b[0]+b[2])/2,b[0],b[2]},targetsX={cx,left,right};float[] ys={(b[1]+b[3])/2,b[1],b[3]},targetsY={cy,top,bottom};
        outer:for(float x:xs)for(float tx:targetsX)if(Math.abs(x-tx)<tol){live.x+=2*(tx-x)/Math.max(1,sw());guideX=tx;break outer;}
        outer2:for(float y:ys)for(float ty:targetsY)if(Math.abs(y-ty)<tol){live.y-=2*(ty-y)/Math.max(1,sh());guideY=ty;break outer2;}
    }
    private void finish(MotionEvent e){
        int g=gesture;gesture=NONE;getParent().requestDisallowInterceptTouchEvent(false);guideX=guideY=Float.NaN;
        boolean longPress=e.getEventTime()-downTime>=ViewConfiguration.getLongPressTimeout();
        if(!moved){if(longPress)pickList(e.getX(),e.getY());else tapSelect(e.getX(),e.getY());live=null;invalidate();performClick();return;}
        if(g==PAN||live==null){live=null;invalidate();return;}
        TransformMath.Transform result=live;live=null;
        if(g==KEY){long t=dragKeyTime;a.edit("Motion path keyframe moved",()->{Motion m=a.selectedMotion();m.put(Motion.X,keyWithValue(m,Motion.X,t,result.x));m.put(Motion.Y,keyWithValue(m,Motion.Y,t,result.y));});invalidate();return;}
        a.edit("Transform updated",()->{
            if(result.x!=base.x||result.y!=base.y){a.transformPanel.write(Motion.X,result.x);a.transformPanel.write(Motion.Y,result.y);}
            if(result.scale!=base.scale)a.transformPanel.write(Motion.SCALE,result.scale);
            if(result.rotation!=base.rotation)a.transformPanel.write(Motion.ROTATION,result.rotation);
            if(result.aspect!=base.aspect)a.setAspect(result.aspect);
            // Group members follow the primary layer's move.
            moveGroupMembers(result.x-base.x,result.y-base.y);
        });
        invalidate();performClick();
    }
    private Motion.Key keyWithValue(Motion m,int property,long t,float value){Motion.Key existing=Keyframes.keyAt(m,property,t,0);Motion.Key k=existing!=null?existing.copy():new Motion.Key(t,value);k.value=value;return k;}
    private void moveGroupMembers(float dx,float dy){
        if(dx==0&&dy==0)return;Set<Integer> clips=new HashSet<>(a.selectedClips()),overlays=new HashSet<>(a.selectedOverlays());LayerOps.expandGroups(a.project,clips,overlays);
        for(int i:clips)if(i!=a.selected){EditProject.Clip c=a.project.clips.get(i);if(c.track<4&&c.motion.curves.get(Motion.X).isEmpty()&&c.motion.curves.get(Motion.Y).isEmpty()){c.x=TransformMath.clamp(c.x+dx,-2,2);c.y=TransformMath.clamp(c.y+dy,-2,2);}}
        for(int i:overlays)if(!(a.selected<0&&i==a.selectedOverlay)){EditProject.Overlay o=a.project.overlays.get(i);if(o.motion.curves.get(Motion.X).isEmpty()&&o.motion.curves.get(Motion.Y).isEmpty()){o.x=TransformMath.clamp(o.x+dx,-1,1);o.y=TransformMath.clamp(o.y+dy,-1,1);}}
    }
    /** Visual layers active at the playhead whose transformed bounds contain the point, topmost first. */
    List<int[]> layersAt(float x,float y){
        List<int[]> hits=new ArrayList<>();long now=a.project.playheadMs;double A=sw()/(double)sh();
        for(int i=a.project.overlays.size()-1;i>=0;i--){EditProject.Overlay o=a.project.overlays.get(i);if(o.hidden||now<o.startMs||now>=o.endMs)continue;if(contains(TransformMath.of(o,now-o.startMs),halfOf(null,o,A),x,y))hits.add(new int[]{1,i});}
        List<EditProject.Placement> ps=a.project.placements();
        for(int track=3;track>=0;track--)for(EditProject.Placement p:ps)if(p.clip.track==track&&!a.project.tracks[track].hidden&&now>=p.startMs&&now<p.endMs){EditProject.Clip c=p.clip;long source=c.inMs+Math.round((now-p.startMs)*c.speed);if(contains(TransformMath.of(c,source),halfOf(c,null,A),x,y))hits.add(new int[]{0,p.index});}
        return hits;
    }
    private double[] halfOf(EditProject.Clip c,EditProject.Overlay o,double A){return a.layerHalf(c,o);}
    private boolean contains(TransformMath.Transform t,double[] half,float x,float y){float[] c=TransformMath.corners(t,sw(),sh(),half[0],half[1]);for(int i=0;i<4;i++){c[i*2]+=surface().getLeft();c[i*2+1]+=surface().getTop();}return TransformMath.contains(c,x,y);}
    /** Tap selects the topmost layer under the finger (the current one when it is still under it). */
    private void tapSelect(float x,float y){List<int[]> hits=layersAt(x,y);if(hits.isEmpty()){if(a.clip()!=null||a.overlay()!=null)a.deselect();return;}
        for(int[] h:hits)if(h[0]==0&&h[1]==a.selected||h[0]==1&&a.selected<0&&h[1]==a.selectedOverlay)return;select(hits.get(0));}
    private void select(int[] hit){if(hit[0]==1)a.selectOverlay(hit[1]);else a.selectClip(hit[1],false);}
    /** Long-press: choose among overlapping layers at that point. */
    private void pickList(float x,float y){
        List<int[]> hits=layersAt(x,y);if(hits.isEmpty()){a.tell("No visible layer here at the playhead.");return;}if(hits.size()==1){select(hits.get(0));return;}
        String[] names=new String[hits.size()];for(int i=0;i<hits.size();i++){int[] h=hits.get(i);names[i]=(i==0?"Top • ":"")+(h[0]==1?a.project.overlays.get(h[1]).label():a.project.clips.get(h[1]).name+"  ("+a.project.tracks[a.project.clips.get(h[1]).track].name+")");}
        new AlertDialog.Builder(a).setTitle("Layers at this point").setItems(names,(d,i)->select(hits.get(i))).setNegativeButton("Cancel",null).show();
    }
    @Override public boolean performClick(){super.performClick();return true;}
    @Override public boolean onGenericMotionEvent(MotionEvent event){
        if(event.getAction()==MotionEvent.ACTION_SCROLL&&(event.getMetaState()&KeyEvent.META_CTRL_ON)!=0){a.previewZoom=TransformMath.clamp(a.previewZoom*(event.getAxisValue(MotionEvent.AXIS_VSCROLL)>0?1.15f:1/1.15f),.2f,4);if(a.monitor!=null)a.monitor.requestLayout();invalidate();return true;}
        return super.onGenericMotionEvent(event);
    }
}
