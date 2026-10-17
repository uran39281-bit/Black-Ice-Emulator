package com.blackice.bc1;

import android.graphics.*;
import android.view.*;
import android.widget.*;
import com.blackice.bc1.editor.*;
import java.util.Locale;

/** Move & Transform: Position, Rotation, Scale, Skew, Anchor and Align tabs, a movement touchpad,
 * a rotation dial and sliding value rulers with fine / coarse modes. Edits go through keyframes when
 * the property is animated, otherwise they change the static value. One drag is one undo entry. */
final class TransformPanel {
    private final EditorActivity a;
    int tab;
    boolean fine,linked=true,lockX,lockY;
    float angleSnap=15;
    TransformPanel(EditorActivity activity){a=activity;}
    static final String[] TABS={"Position","Rotation","Scale","Skew","Anchor","Align"};

    /** Current transform at the playhead and a writer that respects animation. */
    TransformMath.Transform current(){
        EditProject.Clip c=a.clip();EditProject.Overlay o=a.overlay();long t=a.panels.motionTime();
        return c!=null?TransformMath.of(c,t):TransformMath.of(o,t);
    }
    private Motion motion(){EditProject.Clip c=a.clip();return c!=null?c.motion:a.overlay().motion;}
    /** Writes one property: key at playhead when animated, otherwise the static field. */
    void write(int property,float value){
        if(!Motion.allowed(property,value))throw new IllegalArgumentException(Motion.PROPERTIES[property]+" is out of range.");
        EditProject.Overlay o=a.overlay();
        if(o!=null&&property==Motion.SCALE)value=Math.max(.1f,Math.min(2,value));
        if(o!=null&&(property==Motion.X||property==Motion.Y))value=Math.max(-1,Math.min(1,value));
        if(Keyframes.set(motion(),property,a.panels.motionTime(),value,a.panels.tolerance()))a.setStatic(property,value);
    }

    void build(LinearLayout into){
        if(a.clip()==null&&a.overlay()==null){into.addView(a.note("Select a visual layer to transform it."));return;}
        if(a.clip()!=null&&a.clip().track>=4){into.addView(a.note("Audio has no picture to transform."));return;}
        HorizontalScrollView scroll=new HorizontalScrollView(a);scroll.setHorizontalScrollBarEnabled(false);LinearLayout tabs=a.row();
        for(int i=0;i<TABS.length;i++){final int index=i;TextView b=a.button(TABS[i],v->{tab=index;a.renderInspector();});b.setTextSize(11);b.setSelected(i==tab);b.setTextColor(i==tab?EditorActivity.CYAN:EditorActivity.TEXT);b.setBackground(a.navBackground(i==tab));tabs.addView(b,new LinearLayout.LayoutParams(-2,a.dp(48)));}
        scroll.addView(tabs);into.addView(scroll);
        LinearLayout mode=a.row();
        TextView precision=a.button(fine?"Fine adjustment":"Coarse adjustment",v->{fine=!fine;a.renderInspector();});precision.setContentDescription("Ruler precision: "+(fine?"fine":"coarse")+". Tap to switch.");
        mode.addView(precision,new LinearLayout.LayoutParams(0,a.dp(48),1));
        mode.addView(a.button("Reset All Transforms",v->resetAll()),new LinearLayout.LayoutParams(0,a.dp(48),1));
        into.addView(mode);
        a.panels.keyNavigation();
        TransformMath.Transform t=current();
        switch(tab){
            case 0:position(into,t);break;
            case 1:rotation(into,t);break;
            case 2:scale(into,t);break;
            case 3:ruler(into,"Skew X",Motion.SKEW_X,t.skewX,0,"°",1,true);ruler(into,"Skew Y",Motion.SKEW_Y,t.skewY,0,"°",1,true);break;
            case 4:anchor(into,t);break;
            default:align(into);break;
        }
    }
    private void position(LinearLayout into,TransformMath.Transform t){
        int[] size=a.exportSize();
        ruler(into,"X",Motion.X,t.x,0,"px",size[0]/2f,true);ruler(into,"Y",Motion.Y,t.y,0,"px",size[1]/2f,true);
        LinearLayout axes=a.row();
        axes.addView(toggle("Horizontal only",lockY,v->{lockY=!lockY;if(lockY)lockX=false;a.renderInspector();}),new LinearLayout.LayoutParams(0,a.dp(48),1));
        axes.addView(toggle("Vertical only",lockX,v->{lockX=!lockX;if(lockX)lockY=false;a.renderInspector();}),new LinearLayout.LayoutParams(0,a.dp(48),1));
        into.addView(axes);
        Touchpad pad=new Touchpad();into.addView(pad,new LinearLayout.LayoutParams(-1,a.dp(150)));
        LinearLayout nudge=a.row();String[] arrows={"←","→","↑","↓"};int[][] d={{-1,0},{1,0},{0,1},{0,-1}};
        for(int i=0;i<4;i++){final int[] dir=d[i];TextView b=a.compactButton(arrows[i],"Nudge one project pixel "+new String[]{"left","right","up","down"}[i]+(fine?"":"; coarse mode moves ten"),v->a.nudge(dir[0]*(fine?1:10),dir[1]*(fine?1:10)));nudge.addView(b,new LinearLayout.LayoutParams(0,a.dp(48),1));}
        into.addView(nudge);
        into.addView(a.note("Drag the touchpad to move the layer without covering the preview. Keyboard: arrows nudge 1 px, Shift + arrows 10 px."));
    }
    private void rotation(LinearLayout into,TransformMath.Transform t){
        Dial dial=new Dial(t.rotation);into.addView(dial,new LinearLayout.LayoutParams(-1,a.dp(170)));
        ruler(into,"Rotation",Motion.ROTATION,t.rotation,0,"°",1,true);
        LinearLayout snaps=a.row();
        for(float s:new float[]{0,15,45,90}){TextView b=toggle(s==0?"Free":"Snap "+(int)s+"°",angleSnap==s,v->{angleSnap=s;a.renderInspector();});b.setTextSize(11);snaps.addView(b,new LinearLayout.LayoutParams(0,a.dp(48),1));}
        into.addView(snaps);
        LinearLayout quick=a.row();
        quick.addView(a.button("−90°",v->commit("Rotation",()->write(Motion.ROTATION,current().rotation-90))),new LinearLayout.LayoutParams(0,a.dp(48),1));
        quick.addView(a.button("+90°",v->commit("Rotation",()->write(Motion.ROTATION,current().rotation+90))),new LinearLayout.LayoutParams(0,a.dp(48),1));
        into.addView(quick);
    }
    private void scale(LinearLayout into,TransformMath.Transform t){
        TextView link=toggle(linked?"Width and height linked":"Width and height independent",linked,v->{linked=!linked;a.renderInspector();});
        link.setContentDescription(linked?"Proportions linked: tap to adjust width and height independently":"Proportions unlinked: tap to link width and height");into.addView(link);
        ruler(into,"Width",Motion.SCALE,t.scale,1,"%",100,true);
        // Height = width × aspect. Aspect is a static proportion (not animated).
        float height=t.scale*t.aspect;
        valueEntry(into,"Height",height*100,"%",value->commit("Height",()->{float h=value/100f;if(linked){write(Motion.SCALE,h/a.aspectOf());}else a.setAspect(Math.max(.05f,Math.min(20,h/Math.max(.05f,current().scale))));}));
        if(!linked)into.addView(a.note("Unlinked: Width changes keep the current height proportion; Height changes stretch only vertically."));
        LinearLayout fits=a.row();
        fits.addView(a.button("Fit",v->a.fitLayer(0)),new LinearLayout.LayoutParams(0,a.dp(48),1));
        fits.addView(a.button("Fill",v->a.fitLayer(1)),new LinearLayout.LayoutParams(0,a.dp(48),1));
        fits.addView(a.button("Original Size",v->a.fitLayer(2)),new LinearLayout.LayoutParams(0,a.dp(48),1));
        into.addView(fits);
        LinearLayout flips=a.row();
        flips.addView(toggle("Flip Horizontal",t.flipH,v->commit("Flip",()->a.setFlip(true))),new LinearLayout.LayoutParams(0,a.dp(48),1));
        flips.addView(toggle("Flip Vertical",t.flipV,v->commit("Flip",()->a.setFlip(false))),new LinearLayout.LayoutParams(0,a.dp(48),1));
        into.addView(flips);
        into.addView(a.note("Fit and Fill keep the aspect ratio; Fill covers the frame and may crop."));
    }
    private void anchor(LinearLayout into,TransformMath.Transform t){
        into.addView(a.note("Rotation and scaling happen around the anchor. Moving it keeps the layer where it is."));
        float[][] points={{-1,1},{0,1},{1,1},{-1,0},{0,0},{1,0},{-1,-1},{0,-1},{1,-1}};String[] names={"Top left","Top","Top right","Left","Center","Right","Bottom left","Bottom","Bottom right"};
        double[] half=a.layerHalf();float sx=(float)(half[0]/(a.frameAspect()/2)),sy=(float)(half[1]/.5);
        for(int row=0;row<3;row++){LinearLayout r=a.row();for(int col=0;col<3;col++){int i=row*3+col;float[] p=points[i];
            // Anchor presets are the layer's own corners/edges, expressed in its unscaled, unrotated frame.
            float ax=p[0]*sx,ay=p[1]*sy;boolean on=Math.abs(t.anchorX-ax)<.01f&&Math.abs(t.anchorY-ay)<.01f;
            TextView b=toggle(names[i],on,v->commit("Anchor",()->a.moveAnchor(ax,ay)));b.setTextSize(10);r.addView(b,new LinearLayout.LayoutParams(0,a.dp(48),1));}into.addView(r);}
        ruler(into,"Anchor X",Motion.ANCHOR_X,t.anchorX,0,"",1,false);ruler(into,"Anchor Y",Motion.ANCHOR_Y,t.anchorY,0,"",1,false);
    }
    private void align(LinearLayout into){
        String[][] rows={{"Left edge","Center horizontally","Right edge"},{"Top edge","Center vertically","Bottom edge"}};int[][] h={{-1,0,1},{0,0,0}},v={{0,0,0},{1,0,-1}};
        for(int r=0;r<2;r++){LinearLayout line=a.row();for(int c=0;c<3;c++){final int hh=h[r][c],vv=v[r][c];final boolean x=r==0,y=r==1;TextView b=a.button(rows[r][c],view->commit("Alignment",()->a.alignLayer(hh,vv,x,y)));b.setTextSize(10);line.addView(b,new LinearLayout.LayoutParams(0,a.dp(52),1));}into.addView(line);}
        into.addView(a.button("Center on Canvas",view->commit("Centered on canvas",()->a.alignLayer(0,0,true,true))));
        into.addView(a.button("Locate Selected Layer",view->a.locateSelected()));
        into.addView(a.note("Locate pans the preview to the layer without moving it. Center on Canvas repositions it."));
    }
    void resetAll(){
        commit("Transforms reset",()->{EditProject.Clip c=a.clip();EditProject.Overlay o=a.overlay();Motion m=motion();
            for(int p:new int[]{Motion.X,Motion.Y,Motion.SCALE,Motion.ROTATION,Motion.SKEW_X,Motion.SKEW_Y,Motion.ANCHOR_X,Motion.ANCHOR_Y})m.curves.get(p).clear();
            if(c!=null){c.x=0;c.y=0;c.scale=1;c.rotation=0;c.aspect=1;c.skewX=0;c.skewY=0;c.anchorX=0;c.anchorY=0;c.flipH=false;c.flipV=false;}
            else{EditProject.Overlay d=new EditProject.Overlay();o.x=d.x;o.y=d.y;o.scale=d.scale;o.rotation=0;o.aspect=1;o.skewX=0;o.skewY=0;o.anchorX=0;o.anchorY=0;o.flipH=false;o.flipV=false;}});
    }
    void commit(String what,Runnable change){if(!a.layerEditable()){a.renderInspector();return;}a.edit(what+" updated",change);}
    private TextView toggle(String title,boolean on,View.OnClickListener l){TextView b=a.button(title,l);b.setSelected(on);b.setTextColor(on?EditorActivity.CYAN:EditorActivity.TEXT);b.setBackground(a.buttonBackground(on));b.setTextSize(11);return b;}
    interface FloatAction{void apply(float v);}
    private void valueEntry(LinearLayout into,String title,float shown,String unit,FloatAction action){
        LinearLayout row=a.row();row.setGravity(Gravity.CENTER_VERTICAL);row.addView(new View(a),new LinearLayout.LayoutParams(a.dp(48),a.dp(48)));
        row.addView(a.label(title,12,EditorActivity.TEXT,true),new LinearLayout.LayoutParams(0,-2,1));
        EditText field=numeric(title,shown,unit);row.addView(field,new LinearLayout.LayoutParams(a.dp(96),a.dp(48)));row.addView(a.label(unit,11,EditorActivity.MUTED,false));
        row.addView(a.button("Set",v->{try{action.apply(Float.parseFloat(field.getText().toString().trim()));}catch(NumberFormatException e){a.tell("Enter a number.");}}),new LinearLayout.LayoutParams(-2,a.dp(48)));
        into.addView(row);
    }
    private EditText numeric(String title,float shown,String unit){
        EditText field=new EditText(a);field.setText(String.format(Locale.US,"%.2f",shown));field.setTextColor(EditorActivity.TEXT);field.setSingleLine(true);field.setSelectAllOnFocus(true);field.setGravity(Gravity.END);
        field.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL|android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);field.setContentDescription(title+" exact value"+(unit.isEmpty()?"":" in "+unit));return field;
    }
    /** Diamond, label, exact entry, Reset and a sliding ruler for one animatable property. */
    private void ruler(LinearLayout into,String title,int property,float value,float reset,String unit,float display,boolean keyframes){
        LinearLayout row=a.row();row.setGravity(Gravity.CENTER_VERTICAL);
        if(keyframes)row.addView(a.panels.diamond(title,motion(),property,value),new LinearLayout.LayoutParams(a.dp(48),a.dp(48)));else row.addView(new View(a),new LinearLayout.LayoutParams(a.dp(48),a.dp(48)));
        row.addView(a.label(title,12,EditorActivity.TEXT,true),new LinearLayout.LayoutParams(0,-2,1));
        EditText field=numeric(title,value*display,unit);row.addView(field,new LinearLayout.LayoutParams(a.dp(96),a.dp(48)));
        if(!unit.isEmpty())row.addView(a.label(unit,11,EditorActivity.MUTED,false));
        row.addView(a.button("Set",v->{try{float typed=Float.parseFloat(field.getText().toString().trim())/display;commit(title,()->apply(property,typed));}catch(NumberFormatException e){a.tell("Enter a number.");}}),new LinearLayout.LayoutParams(-2,a.dp(48)));
        row.addView(a.compactButton("↺","Reset "+title,v->commit(title,()->apply(property,reset))),new LinearLayout.LayoutParams(a.dp(44),a.dp(48)));
        into.addView(row);
        // Coarse: one ruler tick (12dp) ≈ 1% of the range unit; fine is ten times smaller.
        float step=(property==Motion.ROTATION||property>=Motion.SKEW_X&&property<=Motion.SKEW_Y?1f:property==Motion.SCALE?.01f:.01f)*(fine?.1f:1);
        ValueRuler r=new ValueRuler(title,value,step,v->field.setText(String.format(Locale.US,"%.2f",v*display)),v->commit(title,()->apply(property,v)));
        into.addView(r,new LinearLayout.LayoutParams(-1,a.dp(44)));
    }
    private void apply(int property,float value){
        if(property==Motion.ROTATION&&angleSnap>0)value=TransformMath.snapAngle(value,angleSnap);
        if(property==Motion.ANCHOR_X||property==Motion.ANCHOR_Y){TransformMath.Transform t=current();a.moveAnchor(property==Motion.ANCHOR_X?value:t.anchorX,property==Motion.ANCHOR_Y?value:t.anchorY);return;}
        if(property==Motion.SCALE&&!linked){float height=current().scale*current().aspect;write(Motion.SCALE,value);a.setAspect(Math.max(.05f,Math.min(20,height/Math.max(.05f,value))));return;}
        write(property,value);
    }

    /** Drag horizontally to change a value; the tick marks slide under a fixed center line. */
    final class ValueRuler extends View {
        interface Listener{void value(float v);}
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final float step;final Listener live,done;float value,start,downX;boolean dragging;
        ValueRuler(String title,float value,float step,Listener live,Listener done){super(a);this.value=value;this.step=step;this.live=live;this.done=done;setContentDescription(title+" ruler: drag left or right");setFocusable(true);}
        @Override protected void onDraw(Canvas c){float w=getWidth(),h=getHeight(),spacing=a.dp(12);c.drawColor(EditorActivity.PANEL);paint.setColor(EditorActivity.MUTED);paint.setStrokeWidth(a.dp(1));
            float offset=(value/step)%10*spacing;for(float x=w/2-offset-spacing*40;x<w;x+=spacing){int index=Math.round((x-(w/2-offset))/spacing);float len=index%10==0?h*.55f:index%5==0?h*.4f:h*.25f;c.drawLine(x,h-len,x,h,paint);}
            paint.setColor(EditorActivity.CYAN);paint.setStrokeWidth(a.dp(2));c.drawLine(w/2,0,w/2,h,paint);}
        @Override public boolean onTouchEvent(MotionEvent e){
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN:if(!a.layerEditable())return false;dragging=true;start=value;downX=e.getX();getParent().requestDisallowInterceptTouchEvent(true);a.pausePlayback();return true;
                case MotionEvent.ACTION_MOVE:if(!dragging)return false;value=start-(e.getX()-downX)/a.dp(12)*step;live.value(value);invalidate();return true;
                case MotionEvent.ACTION_UP:if(dragging&&Math.abs(e.getX()-downX)>a.dp(2))done.value(value);dragging=false;getParent().requestDisallowInterceptTouchEvent(false);return true;
                case MotionEvent.ACTION_CANCEL:value=start;dragging=false;invalidate();return true;
            }
            return false;
        }
    }
    /** Relative movement pad: the layer follows the finger without the finger covering the preview. */
    final class Touchpad extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);float downX,downY,baseX,baseY,nowX,nowY;boolean dragging;
        Touchpad(){super(a);setContentDescription("Movement touchpad: drag to reposition the selected layer");setFocusable(true);}
        @Override protected void onDraw(Canvas c){paint.setColor(0xFF0A1824);c.drawRoundRect(0,0,getWidth(),getHeight(),a.dp(10),a.dp(10),paint);paint.setColor(0xFF294357);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(a.dp(1));c.drawRoundRect(1,1,getWidth()-1,getHeight()-1,a.dp(10),a.dp(10),paint);
            c.drawLine(getWidth()/2f,a.dp(10),getWidth()/2f,getHeight()-a.dp(10),paint);c.drawLine(a.dp(10),getHeight()/2f,getWidth()-a.dp(10),getHeight()/2f,paint);paint.setStyle(Paint.Style.FILL);
            paint.setColor(EditorActivity.MUTED);paint.setTextSize(a.dp(11));paint.setTextAlign(Paint.Align.CENTER);c.drawText(lockY?"Horizontal only":lockX?"Vertical only":fine?"Fine movement":"Drag to move",getWidth()/2f,getHeight()-a.dp(12),paint);
            if(dragging){paint.setColor(EditorActivity.CYAN);c.drawCircle(getWidth()/2f+(nowX-downX),getHeight()/2f+(nowY-downY),a.dp(8),paint);}}
        @Override public boolean onTouchEvent(MotionEvent e){
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN:if(!a.layerEditable())return false;TransformMath.Transform t=current();baseX=t.x;baseY=t.y;downX=nowX=e.getX();downY=nowY=e.getY();dragging=true;getParent().requestDisallowInterceptTouchEvent(true);a.pausePlayback();invalidate();return true;
                case MotionEvent.ACTION_MOVE:nowX=e.getX();nowY=e.getY();a.previewMove(target()[0],target()[1]);invalidate();return true;
                case MotionEvent.ACTION_UP:dragging=false;float[] p=target();a.previewMove(Float.NaN,Float.NaN);if(Math.hypot(nowX-downX,nowY-downY)>a.dp(2))commit("Position",()->{write(Motion.X,p[0]);write(Motion.Y,p[1]);});getParent().requestDisallowInterceptTouchEvent(false);invalidate();return true;
                case MotionEvent.ACTION_CANCEL:dragging=false;a.previewMove(Float.NaN,Float.NaN);invalidate();return true;
            }
            return false;
        }
        /** Pad width spans the frame width in coarse mode and a tenth of it in fine mode. */
        float[] target(){float gain=(fine?.2f:2f)/Math.max(1,getWidth());float x=lockX?baseX:baseX+(nowX-downX)*gain,y=lockY?baseY:baseY-(nowY-downY)*gain;boolean overlay=a.overlay()!=null;float lim=overlay?1:2;return new float[]{TransformMath.clamp(x,-lim,lim),TransformMath.clamp(y,-lim,lim)};}
    }
    /** Circular rotation control; snapping applies while dragging. */
    final class Dial extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);float angle,base,downAngle;boolean dragging;
        Dial(float angle){super(a);this.angle=angle;setContentDescription("Rotation dial: drag around the circle");setFocusable(true);}
        private float pointer(MotionEvent e){return (float)Math.toDegrees(Math.atan2(getHeight()/2f-e.getY(),e.getX()-getWidth()/2f));}
        @Override protected void onDraw(Canvas c){float r=Math.min(getWidth(),getHeight())/2f-a.dp(12),cx=getWidth()/2f,cy=getHeight()/2f;paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(a.dp(2));paint.setColor(0xFF294357);c.drawCircle(cx,cy,r,paint);
            for(int d=0;d<360;d+=15){double rad=Math.toRadians(d);float len=d%90==0?a.dp(10):d%45==0?a.dp(7):a.dp(4);c.drawLine(cx+(float)Math.cos(rad)*r,cy-(float)Math.sin(rad)*r,cx+(float)Math.cos(rad)*(r-len),cy-(float)Math.sin(rad)*(r-len),paint);}
            double rad=Math.toRadians(angle+90);paint.setColor(EditorActivity.CYAN);c.drawLine(cx,cy,cx+(float)Math.cos(rad)*r,cy-(float)Math.sin(rad)*r,paint);paint.setStyle(Paint.Style.FILL);c.drawCircle(cx+(float)Math.cos(rad)*r,cy-(float)Math.sin(rad)*r,a.dp(8),paint);
            paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(a.dp(16));paint.setColor(EditorActivity.TEXT);c.drawText(String.format(Locale.US,"%.1f°",angle),cx,cy+a.dp(6),paint);}
        @Override public boolean onTouchEvent(MotionEvent e){
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN:if(!a.layerEditable())return false;dragging=true;base=angle;downAngle=pointer(e);getParent().requestDisallowInterceptTouchEvent(true);a.pausePlayback();return true;
                case MotionEvent.ACTION_MOVE:if(!dragging)return false;float delta=(pointer(e)-downAngle+540)%360-180;downAngle=pointer(e);base+=delta;angle=angleSnap>0?TransformMath.snapAngle(base,angleSnap):base;invalidate();return true;
                case MotionEvent.ACTION_UP:if(dragging){float value=angle;commit("Rotation",()->write(Motion.ROTATION,value));}dragging=false;getParent().requestDisallowInterceptTouchEvent(false);return true;
                case MotionEvent.ACTION_CANCEL:dragging=false;return true;
            }
            return false;
        }
    }
}
