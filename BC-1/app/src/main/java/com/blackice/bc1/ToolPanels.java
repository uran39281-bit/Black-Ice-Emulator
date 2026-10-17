package com.blackice.bc1;

import android.app.AlertDialog;
import android.graphics.Color;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import com.blackice.bc1.editor.*;
import com.blackice.bc1.engine.EngineSettings;
import java.util.*;

/** Selection-based tool grids and focused adjustment panels. One panel is open at a time; Back returns
 * to the previous panel without touching selection, playhead or timeline scroll. */
final class ToolPanels {
    interface Setter{void set(float value);}
    private final EditorActivity a;
    private final ArrayDeque<Panel> stack=new ArrayDeque<>();
    private Panel current;
    private String owner="";
    private static final class Panel{final String title;final Runnable build;Panel(String t,Runnable b){title=t;build=b;}}
    ToolPanels(EditorActivity activity){a=activity;}

    private String selectionKey(){return a.selected+":"+a.selectedOverlay+":"+a.tab;}
    /** Opens a focused panel; the previous one is kept for Back. */
    void open(String title,Runnable build){if(current!=null)stack.push(current);current=new Panel(title,build);owner=selectionKey();a.renderInspector();}
    void back(){current=stack.isEmpty()?null:stack.pop();a.renderInspector();}
    void reset(){stack.clear();current=null;}
    boolean showing(){return current!=null&&owner.equals(selectionKey());}
    String title(){return current==null?null:current.title;}

    /** Renders the open panel, or the contextual tool grid for the current selection. */
    void render(LinearLayout into) {
        if(current!=null&&!owner.equals(selectionKey()))reset();
        if(current!=null){header(into,current.title);current.build.run();return;}
        if(a.tab==2){effectsHome();return;}
        EditProject.Clip c=a.clip();EditProject.Overlay o=a.overlay();
        if(a.multiCount()>1){multiTools();return;}
        if(c==null&&o==null){browseHome();return;}
        String kind=kind(c,o);
        into.addView(a.note(kind+"  •  "+(c!=null?c.name:o.label())+(locked()?"  •  Layer locked":"")));
        renderCategories(c,o);
        playheadRow(c,o);
        for(String tool:tools(c,o))a.gridAction(tool,()->runTool(tool));
        a.flushGrid();
        into.addView(a.note(c!=null&&c.track==0?"Drag handles to ripple trim; drag the selected body to reorder the main sequence. Drag empty space to scrub.":"Drag handles to trim; drag the selected body to move. Drag empty space to scrub; pinch to zoom."));
    }
    private boolean locked(){EditProject.Clip c=a.clip();EditProject.Overlay o=a.overlay();return c!=null?LayerOps.locked(a.project,a.selected):LayerOps.locked(o);}
    static String kind(EditProject.Clip c,EditProject.Overlay o){
        if(c!=null)return c.track>=4?"Audio":c.image?"Picture":"Video";
        return o.imageUri!=null?"Image layer":o.shape>0?"Shape":"Text";
    }
    private void header(LinearLayout into,String title){
        LinearLayout bar=a.row();bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView back=a.button("‹  Back",v->back());back.setContentDescription("Back to previous panel");bar.addView(back,new LinearLayout.LayoutParams(-2,a.dp(48)));
        TextView heading=a.label(title,13,EditorActivity.CYAN,true);heading.setPadding(a.dp(10),0,0,0);bar.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        into.addView(bar);
    }
    /** Pictures, videos, audio, text, shapes and image layers each get only their compatible tools. */
    static List<String> tools(EditProject.Clip c,EditProject.Overlay o){
        List<String> t=new ArrayList<>();
        if(c!=null&&c.track>=4){Collections.addAll(t,"Split","Trim","Slip","Volume","Fade In","Fade Out","Duplicate");return t;}
        if(c!=null&&c.image){Collections.addAll(t,"Duration: "+EditorActivity.seconds(c.durationMs())+" s","Transform","Opacity","Effects","Chroma Key","Animation","Duplicate");return t;}
        if(c!=null){Collections.addAll(t,"Split","Trim","Slip","Speed: "+trimZero(c.speed)+"×","Extract Audio","Transform","Opacity","Effects","Chroma Key","Volume","Animation","Duplicate");return t;}
        if(o.imageUri==null&&o.shape==0){Collections.addAll(t,"Edit Text","Font","Size","Color","Alignment","Transform","Opacity","Duration: "+EditorActivity.seconds(o.endMs-o.startMs)+" s","Animation");return t;}
        if(o.shape>0){Collections.addAll(t,"Color","Transform","Opacity","Duration: "+EditorActivity.seconds(o.endMs-o.startMs)+" s","Animation","Duplicate");return t;}
        Collections.addAll(t,"Transform","Opacity","Duration: "+EditorActivity.seconds(o.endMs-o.startMs)+" s","Animation","Duplicate");return t;
    }
    static String trimZero(float v){String s=String.format(Locale.US,"%.2f",v);return s.replaceAll("0+$","").replaceAll("\\.$","");}
    static List<String> categories(EditProject.Clip c,EditProject.Overlay o){
        List<String> list=new ArrayList<>();if(c!=null&&c.track>=4)return list;
        list.add("Move & Transform");list.add("Color & Fill");if(o!=null)list.add("Border & Shadow");list.add("Blending & Opacity");if(c!=null)list.add("Effects");list.add("Presets");
        return list;
    }
    private void renderCategories(EditProject.Clip c,EditProject.Overlay o){
        List<String> list=categories(c,o);if(list.isEmpty())return;
        HorizontalScrollView scroll=new HorizontalScrollView(a);scroll.setHorizontalScrollBarEnabled(false);scroll.setContentDescription("Tool categories");
        LinearLayout chips=a.row();for(String name:list){TextView chip=a.iconButton(name,v->category(name));chip.setTextSize(9);chips.addView(chip,new LinearLayout.LayoutParams(a.dp(name.length()>14?96:80),a.dp(60)));}
        scroll.addView(chips);a.inspector.addView(scroll);
    }
    private void playheadRow(EditProject.Clip c,EditProject.Overlay o){
        boolean valid=c!=null?LayerOps.canSplitOrTrim(a.project,a.selected):o!=null&&a.project.playheadMs-o.startMs>=EditProject.MIN_CLIP_MS&&o.endMs-a.project.playheadMs>=EditProject.MIN_CLIP_MS;
        LinearLayout row=a.row();String[] names={"Trim Start to Playhead","Split at Playhead","Trim End to Playhead"};
        for(int i=0;i<3;i++){final int index=i;TextView b=a.button(names[i],v->playheadEdit(index));b.setTextSize(10);b.setEnabled(valid);b.setAlpha(valid?1:.4f);
            b.setContentDescription(names[i]+(valid?"":" (move the playhead inside the selected layer)"));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,a.dp(52),1);lp.rightMargin=a.dp(3);row.addView(b,lp);}
        a.inspector.addView(row);
    }
    private void playheadEdit(int which){
        EditProject.Clip c=a.clip();EditProject.Overlay o=a.overlay();if(!a.layerEditable())return;
        if(c!=null){if(which==1)a.splitSelected();else a.edit(which==0?"Trimmed start to playhead":"Trimmed end to playhead",()->LayerOps.trimToPlayhead(a.project,a.selected,which==0));return;}
        long t=a.project.playheadMs;
        if(which==0)a.edit("Trimmed start to playhead",()->o.startMs=t);
        else if(which==2)a.edit("Trimmed end to playhead",()->o.endMs=t);
        else a.edit("Layer split",()->{if(a.project.overlays.size()>=20)throw new IllegalArgumentException("Up to 20 text, shape and image layers.");EditProject.Overlay right=o.copy();long offset=t-o.startMs;right.startMs=t;Keyframes.splitAt(right.motion,offset);o.endMs=t;a.project.overlays.add(a.project.overlays.indexOf(o)+1,right);});
    }
    void runTool(String tool){
        if(tool.startsWith("Duration:"))tool="Duration";else if(tool.startsWith("Speed:"))tool="Speed";
        EditProject.Clip c=a.clip();EditProject.Overlay o=a.overlay();
        switch(tool){
            case "Split":if(a.layerEditable())a.splitSelected();return;
            case "Duplicate":a.duplicateSelected();return;
            case "Transform":category("Move & Transform");return;
            case "Effects":category("Effects");return;
            case "Opacity":open("Opacity",()->opacityPanel());return;
            case "Volume":open("Volume",()->percent("Volume",c.volume,.7f,v->c.volume=v,null,0));return;
            case "Duration":open("Duration",this::durationPanel);return;
            case "Trim":open("Trim",this::trimPanel);return;
            case "Slip":open("Slip",this::slipPanel);return;
            case "Speed":open("Speed",this::speedPanel);return;
            case "Fade In":open("Fade In",()->fadePanel(true));return;
            case "Fade Out":open("Fade Out",()->fadePanel(false));return;
            case "Extract Audio":open("Extract Audio",this::extractPanel);return;
            case "Chroma Key":open("Chroma Key",this::chromaPanel);return;
            case "Edit Text":open("Edit Text",this::textPanel);return;
            case "Font":open("Font",this::fontPanel);return;
            case "Size":open("Size",()->percent("Size",o.scale/2f,.35f,v->o.scale=Math.max(.1f,v*2),o.motion,Motion.SCALE,2,.05f));return;
            case "Color":category("Color & Fill");return;
            case "Alignment":open("Alignment",this::alignmentPanel);return;
            case "Animation":open("Animation",this::animationPanel);return;
        }
    }
    void category(String name){
        switch(name){
            case "Move & Transform":open(name,()->a.transformPanel.build(a.inspector));return;
            case "Color & Fill":open(name,this::colorPanel);return;
            case "Border & Shadow":open(name,this::borderPanel);return;
            case "Blending & Opacity":open(name,this::blendPanel);return;
            case "Effects":open(name,this::effectsPanel);return;
            case "Presets":open(name,this::presetsPanel);return;
        }
    }

    // ---- Shared value controls -------------------------------------------------------------------
    /** Motion time at the playhead in the selected layer's key time base. */
    long motionTime(){
        EditProject.Clip c=a.clip();EditProject.Overlay o=a.overlay();
        if(c!=null){EditProject.Placement p=a.project.placements().get(a.selected);return Math.max(c.inMs,Math.min(c.outMs,c.inMs+Math.round((a.project.playheadMs-p.startMs)*c.speed)));}
        if(o!=null)return Math.max(0,Math.min(o.endMs-o.startMs,a.project.playheadMs-o.startMs));
        return 0;
    }
    /** Half a frame, so a key "at the playhead" survives frame-quantised seeking. */
    long tolerance(){EditProject.Clip c=a.clip();float speed=c==null?1:c.speed;return Math.max(1,Math.round(500f/a.project.frameRate*speed));}
    /** A labelled 0–1 value with slider, exact entry, Reset and an optional keyframe diamond. */
    void percent(String title,float initial,float defaultValue,Setter setter,Motion motion,int property){percent(title,initial,defaultValue,setter,motion,property,1,0);}
    void percent(String title,float initial,float defaultValue,Setter setter,Motion motion,int property,float motionFactor,float minimum){
        float shown=motion==null?initial:motion.value(property,motionTime(),initial*motionFactor)/motionFactor;
        valueRow(title,shown,0,1,defaultValue,100,"%",v->{float stored=Math.max(minimum,v)*motionFactor;
            if(motion==null||Keyframes.set(motion,property,motionTime(),stored,tolerance()))setter.set(Math.max(minimum,v));},motion,property,motionFactor);
    }
    /** Generic row: value in model units; display = value × displayFactor with a unit suffix. */
    void valueRow(String title,float value,float min,float max,float defaultValue,float displayFactor,String unit,Setter apply,Motion motion,int property,float motionFactor){
        LinearLayout row=a.row();row.setGravity(Gravity.CENTER_VERTICAL);
        if(motion!=null)row.addView(diamond(title,motion,property,value*motionFactor),new LinearLayout.LayoutParams(a.dp(48),a.dp(48)));
        TextView label=a.label(title,12,EditorActivity.TEXT,true);row.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        EditText exact=new EditText(a);exact.setText(format(value*displayFactor));exact.setTextColor(EditorActivity.TEXT);exact.setSingleLine(true);exact.setSelectAllOnFocus(true);
        exact.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);exact.setContentDescription(title+" exact value"+(unit.isEmpty()?"":" in "+unit));exact.setGravity(Gravity.END);
        row.addView(exact,new LinearLayout.LayoutParams(a.dp(88),a.dp(48)));
        if(!unit.isEmpty())row.addView(a.label(unit,11,EditorActivity.MUTED,false));
        a.inspector.addView(row);
        SeekBar slider=new SeekBar(a);slider.setMax(1000);slider.setProgress(Math.round((value-min)/(max-min)*1000));slider.setContentDescription(title);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar s,int p,boolean user){if(user)exact.setText(format((min+(max-min)*p/1000f)*displayFactor));}
            public void onStartTrackingTouch(SeekBar s){a.pausePlayback();}
            // One slider drag is one undo entry.
            public void onStopTrackingTouch(SeekBar s){commit(title,min+(max-min)*s.getProgress()/1000f,apply);}
        });
        a.inspector.addView(slider,new LinearLayout.LayoutParams(-1,a.dp(44)));
        LinearLayout actions=a.row();
        actions.addView(a.button("Apply",v->{try{float typed=Float.parseFloat(exact.getText().toString().trim())/displayFactor;if(!Float.isFinite(typed)||typed<min-1e-6f||typed>max+1e-6f)throw new NumberFormatException();commit(title,typed,apply);}catch(NumberFormatException e){a.tell("Enter "+format(min*displayFactor)+" to "+format(max*displayFactor)+(unit.isEmpty()?"":" "+unit)+".");}}),new LinearLayout.LayoutParams(0,a.dp(48),1));
        actions.addView(a.button("Reset",v->commit(title,defaultValue,apply)),new LinearLayout.LayoutParams(0,a.dp(48),1));
        a.inspector.addView(actions);
    }
    private void commit(String title,float value,Setter apply){if(!a.layerEditable()){a.renderInspector();return;}a.edit(title+" updated",()->apply.set(value));}
    static String format(float v){return String.format(Locale.US,Math.abs(v)>=100?"%.0f":"%.2f",v);}
    /** ◇ unanimated · cyan ◇ animated between keys · filled ◆ on a key at the playhead. */
    TextView diamond(String title,Motion motion,int property,float current){
        int state=Keyframes.state(motion,property,motionTime(),tolerance());
        TextView d=a.label(state==Keyframes.ON_KEY?"◆":"◇",20,state==Keyframes.STATIC?EditorActivity.MUTED:EditorActivity.CYAN,true);d.setGravity(Gravity.CENTER);
        String[] words={"not animated • tap to animate","animated • tap to add a keyframe here","keyframe at playhead • tap to remove"};
        d.setContentDescription(title+" keyframe: "+words[state]);d.setTooltipText(d.getContentDescription());d.setFocusable(true);
        d.setOnClickListener(v->{if(!a.layerEditable())return;long t=motionTime(),tol=tolerance();
            a.edit(state==Keyframes.ON_KEY?title+" keyframe removed":title+" keyframe added",()->{
                if(state==Keyframes.ON_KEY&&motion.curves.get(property).size()==1){a.setStatic(property,Keyframes.disable(motion,property,t,current));}
                else Keyframes.toggle(motion,property,t,motion.value(property,t,current),tol);
            });});
        return d;
    }

    // ---- Panels ----------------------------------------------------------------------------------
    private void opacityPanel(){
        EditProject.Clip c=a.clip();EditProject.Overlay o=a.overlay();
        if(c!=null)percent("Opacity",c.opacity,1,v->c.opacity=v,c.motion,Motion.OPACITY);else percent("Opacity",o.opacity,1,v->o.opacity=v,o.motion,Motion.OPACITY);
        a.inspector.addView(a.note("0% is fully transparent. Lower layers show through transparent regions."));
    }
    private void blendPanel(){
        opacityPanel();EditProject.Overlay o=a.overlay();
        if(o==null){a.inspector.addView(a.note("Video and picture layers use normal blending."));return;}
        a.inspector.addView(a.note("Blend mode (text, shape and image layers)"));
        for(int i=0;i<EditProject.BLENDS.length;i++){final int mode=i;a.gridAction((o.blend==i?"● ":"")+EditProject.BLENDS[i],()->{if(a.layerEditable())a.edit("Blend mode updated",()->o.blend=mode);});}
        a.flushGrid();
        a.inspector.addView(a.note("Blend modes combine with the text, shape and image layers beneath this one."));
    }
    private void durationPanel(){
        EditProject.Clip c=a.clip();EditProject.Overlay o=a.overlay();
        long start=c!=null?a.project.placements().get(a.selected).startMs:o.startMs,end=c!=null?a.project.placements().get(a.selected).endMs:o.endMs;
        a.inspector.addView(a.note(c!=null&&!c.image&&c.track<4||c!=null&&c.track>=4?"Video and audio are limited to the available source ("+EditorActivity.seconds(Math.round(c.sourceDurationMs/(double)c.speed))+" s at "+trimZero(c.speed)+"×).":"Pictures, text and shapes can extend to any project length."));
        timeField("Duration (seconds)",end-start,value->a.edit("Duration updated",()->{if(c!=null)TimelineEdits.duration(a.project,a.selected,value);else{if(value<EditProject.MIN_CLIP_MS)throw new IllegalArgumentException("Keep at least 0.10 seconds.");o.endMs=Math.addExact(o.startMs,value);}}));
        if(c==null||c.track>0)timeField("Start (seconds)",start,value->a.edit("Start updated",()->{if(c!=null)a.project.setPlacement(a.selected,c.track,value);else{long length=o.endMs-o.startMs;o.startMs=value;o.endMs=value+length;}}));
        else a.inspector.addView(a.note("Main-sequence clips start where the previous clip ends. Move this clip to a layer to set an exact start."));
        timeField("End (seconds)",end,value->a.edit("End updated",()->{if(c!=null)TimelineEdits.duration(a.project,a.selected,value-start);else{if(value-o.startMs<EditProject.MIN_CLIP_MS)throw new IllegalArgumentException("End must be at least 0.10 seconds after the start.");o.endMs=value;}}));
    }
    interface TimeAction{void apply(long ms);}
    void timeField(String title,long ms,TimeAction action){
        LinearLayout row=a.row();row.setGravity(Gravity.CENTER_VERTICAL);row.addView(a.label(title,12,EditorActivity.TEXT,true),new LinearLayout.LayoutParams(0,-2,1));
        EditText field=new EditText(a);field.setText(EditorActivity.seconds(ms));field.setTextColor(EditorActivity.TEXT);field.setSingleLine(true);field.setSelectAllOnFocus(true);
        field.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);field.setContentDescription(title);field.setGravity(Gravity.END);
        row.addView(field,new LinearLayout.LayoutParams(a.dp(96),a.dp(48)));
        row.addView(a.button("Set",v->{if(!a.layerEditable())return;try{action.apply(EditorActivity.parseMs(field.getText().toString().trim()));}catch(NumberFormatException e){a.tell("Enter a time in seconds, for example 3.50.");}catch(IllegalArgumentException e){a.tell(e.getMessage());}}),new LinearLayout.LayoutParams(-2,a.dp(48)));
        a.inspector.addView(row);
    }
    private void trimPanel(){
        EditProject.Clip c=a.clip();if(c==null)return;
        a.inspector.addView(a.note("Source length "+EditorActivity.seconds(c.sourceDurationMs)+" s • using "+EditorActivity.seconds(c.inMs)+"–"+EditorActivity.seconds(c.outMs)+" s. The imported file is never changed."));
        timeField("Source in (seconds)",c.inMs,value->a.edit("Trim applied",()->a.project.trim(a.selected,value,c.outMs)));
        timeField("Source out (seconds)",c.outMs,value->a.edit("Trim applied",()->a.project.trim(a.selected,c.inMs,value)));
        durationPanel();
    }
    private void slipPanel(){
        EditProject.Clip c=a.clip();if(c==null)return;
        if(c.image){a.inspector.addView(a.note("Still pictures have no source segment to slip."));return;}
        a.inspector.addView(a.note("Slip shows a different part of the source while the clip keeps its timeline position and duration.\nAvailable: 0.00–"+EditorActivity.seconds(c.sourceDurationMs-(c.outMs-c.inMs))+" s"));
        timeField("Source in (seconds)",c.inMs,value->a.edit("Source segment slipped",()->TimelineEdits.slip(a.project,a.selected,value)));
        long frame=Math.max(1,Math.round(1000.0/a.project.frameRate));
        LinearLayout row=a.row();String[] names={"−1 s","−1 frame","+1 frame","+1 s"};long[] deltas={-1000,-frame,frame,1000};
        for(int i=0;i<4;i++){final long delta=deltas[i];TextView b=a.button(names[i],v->{if(a.layerEditable())a.edit("Source segment slipped",()->TimelineEdits.slip(a.project,a.selected,c.inMs+Math.round(delta*c.speed)));});b.setTextSize(11);row.addView(b,new LinearLayout.LayoutParams(0,a.dp(48),1));}
        a.inspector.addView(row);
    }
    private void speedPanel(){
        EditProject.Clip c=a.clip();if(c==null)return;
        valueRow("Speed",c.speed,.25f,4,1,1,"×",v->TimelineEdits.speed(a.project,a.selected,Math.round(v*100)/100f),null,0,1);
        LinearLayout row=a.row();for(float s:new float[]{.5f,1,1.5f,2}){TextView b=a.button(trimZero(s)+"×",v->commit("Speed",s,x->TimelineEdits.speed(a.project,a.selected,x)));row.addView(b,new LinearLayout.LayoutParams(0,a.dp(48),1));}
        a.inspector.addView(row);
        a.inspector.addView(a.note("Speed changes timing explicitly, so the clip may extend past its earlier length. Audio and video stay synchronized."));
    }
    private void fadePanel(boolean in){
        EditProject.Clip c=a.clip();if(c==null)return;long max=c.durationMs();
        valueRow(in?"Fade in":"Fade out",(in?c.fadeInMs:c.fadeOutMs)/1000f,0,max/1000f,0,1,"s",v->{long ms=Math.round(v*1000);if(in)c.fadeInMs=ms;else c.fadeOutMs=ms;},null,0,1);
    }
    private void extractPanel(){
        EditProject.Clip c=a.clip();if(c==null)return;
        a.inspector.addView(a.note("Creates an independent audio clip on a free audio layer, aligned with this video. It can be trimmed, moved and faded separately."));
        CheckBox mute=new CheckBox(a);mute.setText("Mute the original video's audio");mute.setTextColor(EditorActivity.TEXT);mute.setChecked(true);mute.setMinHeight(a.dp(48));a.inspector.addView(mute);
        a.inspector.addView(a.button("Extract audio",v->a.extractAudio(mute.isChecked())));
    }
    private void chromaPanel(){
        EditProject.Clip c=a.clip();if(c==null)return;
        CheckBox on=new CheckBox(a);on.setText("Remove key color");on.setTextColor(EditorActivity.TEXT);on.setChecked(c.chroma);on.setMinHeight(a.dp(48));
        on.setOnCheckedChangeListener((v,checked)->{if(a.layerEditable())a.edit("Chroma key updated",()->c.chroma=checked);});a.inspector.addView(on);
        swatches("Key color",c.keyColor,new int[]{0xFF00FF00,0xFF00B140,0xFF0047BB,0xFF0000FF},value->c.keyColor=value);
        valueRow("Tolerance",c.keyTolerance,0,1,.25f,100,"%",v->c.keyTolerance=v,null,0,1);
        valueRow("Edge softness",c.keySoftness,.001f,1,.08f,100,"%",v->c.keySoftness=Math.max(.001f,v),null,0,1);
        valueRow("Spill removal",c.keySpill,0,1,.5f,100,"%",v->c.keySpill=v,null,0,1);
    }
    interface ColorSetter{void set(int color);}
    void swatches(String title,int current,int[] colors,ColorSetter setter){
        a.inspector.addView(a.note(title+"  •  "+String.format(Locale.US,"#%06X",current&0xFFFFFF)));
        LinearLayout row=a.row();
        for(int color:colors){View chip=new View(a);android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(color);bg.setCornerRadius(a.dp(6));bg.setStroke(a.dp(color==current?3:1),color==current?EditorActivity.CYAN:0xFF294357);chip.setBackground(bg);
            chip.setContentDescription(title+" "+String.format(Locale.US,"#%06X",color&0xFFFFFF));chip.setFocusable(true);chip.setOnClickListener(v->{if(a.layerEditable())a.edit(title+" updated",()->setter.set(color));});
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,a.dp(44),1);lp.rightMargin=a.dp(4);row.addView(chip,lp);}
        a.inspector.addView(row);
        LinearLayout hex=a.row();EditText field=new EditText(a);field.setText(String.format(Locale.US,"#%06X",current&0xFFFFFF));field.setTextColor(EditorActivity.TEXT);field.setSingleLine(true);field.setContentDescription(title+" hex value");
        hex.addView(field,new LinearLayout.LayoutParams(0,a.dp(48),1));
        hex.addView(a.button("Set",v->{if(!a.layerEditable())return;try{int color=Color.parseColor(field.getText().toString().trim());a.edit(title+" updated",()->setter.set(color|0xFF000000));}catch(IllegalArgumentException e){a.tell("Use a color such as #40BFFF.");}}),new LinearLayout.LayoutParams(-2,a.dp(48)));
        a.inspector.addView(hex);
    }
    private static final int[] PALETTE={0xFFFFFFFF,0xFF000000,0xFF40BFFF,0xFF9FEAFF,0xFFFF4D6D,0xFFFFC145,0xFF6BE675,0xFFB388FF};
    private void colorPanel(){
        EditProject.Clip c=a.clip();EditProject.Overlay o=a.overlay();
        if(o!=null&&o.imageUri==null){swatches(o.shape>0?"Fill color":"Text color",o.color,PALETTE,value->o.color=value);return;}
        if(o!=null){a.inspector.addView(a.note("Image layers keep their own colors. Use Border & Shadow or Blending & Opacity."));return;}
        a.inspector.addView(a.note("Color adjustments for this "+(c.image?"picture":"video")+". One color effect is active at a time."));
        int[] ids={0,4,9,10,11};
        for(int id:ids)a.gridAction((c.effect==id?"● ":"")+EngineSettings.EFFECTS[id],()->{if(a.layerEditable())a.edit("Color updated",()->c.effect=id);});
        a.flushGrid();
        if(c.effect!=0)percent("Strength",c.intensity,.6f,v->c.intensity=v,c.motion,Motion.INTENSITY);
    }
    private void borderPanel(){
        EditProject.Overlay o=a.overlay();if(o==null){a.inspector.addView(a.note("Borders and shadows apply to text, shape and image layers."));return;}
        valueRow("Border width",o.borderWidth,0,1,0,100,"%",v->o.borderWidth=v,null,0,1);
        swatches("Border color",o.borderColor,PALETTE,value->o.borderColor=value);
        valueRow("Shadow",o.shadow,0,1,0,100,"%",v->o.shadow=v,null,0,1);
    }
    private void effectsPanel(){
        EditProject.Clip c=a.clip();if(c==null||c.track>=4){a.inspector.addView(a.note("Select a video or picture to apply visual effects."));return;}
        for(int i=0;i<EngineSettings.EFFECTS.length;i++){final int id=i;a.gridAction((c.effect==i?"● ":"")+EngineSettings.EFFECTS[i],()->{if(a.layerEditable())a.edit("Effect applied",()->c.effect=id);});}
        a.flushGrid();
        percent("Effect intensity",c.intensity,.6f,v->c.intensity=v,c.motion,Motion.INTENSITY);
        a.inspector.addView(a.button("Chroma Key…",v->open("Chroma Key",this::chromaPanel)));
        if(c.track==0){
            String[] transitions=new String[EngineSettings.TRANSITIONS.length+1];transitions[0]="Cut (no overlap)";System.arraycopy(EngineSettings.TRANSITIONS,0,transitions,1,EngineSettings.TRANSITIONS.length);
            a.inspector.addView(a.note("Transition to next clip"));
            for(int i=0;i<transitions.length;i++){final int id=i-1;a.gridAction((c.transition==id?"● ":"")+transitions[i],()->{if(a.layerEditable())a.edit("Transition updated",()->c.transition=id);});}
            a.flushGrid();
            if(c.transition>=0)valueRow("Transition length",c.transitionMs/1000f,0,5,.5f,1,"s",v->c.transitionMs=Math.round(v*1000),null,0,1);
        }
    }
    private void effectsHome(){
        EditProject.Clip c=a.clip();
        if(c==null||c.track>=4){a.inspector.addView(a.note("Select a video or picture to apply visual effects."));return;}
        a.inspector.addView(a.note("Selected: "+c.name));effectsPanel();
    }
    private void textPanel(){
        EditProject.Overlay o=a.overlay();if(o==null)return;
        EditText text=new EditText(a);text.setText(o.text);text.setTextColor(EditorActivity.TEXT);text.setMinLines(2);text.setContentDescription("Layer text");a.inspector.addView(text);
        a.inspector.addView(a.button("Apply text",v->{if(!a.layerEditable())return;String value=text.getText().toString();a.edit("Text updated",()->{if(value.isEmpty()||value.length()>200)throw new IllegalArgumentException("Use 1 to 200 characters.");o.text=value;});}));
    }
    private void fontPanel(){
        EditProject.Overlay o=a.overlay();if(o==null)return;String[] fonts={"sans-serif-medium","serif","monospace"},names={"Sans serif","Serif","Monospace"};
        for(int i=0;i<fonts.length;i++){final String f=fonts[i];a.gridAction((f.equals(o.font)?"● ":"")+names[i],()->{if(a.layerEditable())a.edit("Font updated",()->o.font=f);});}
        a.flushGrid();
    }
    private void alignmentPanel(){
        EditProject.Overlay o=a.overlay();if(o==null)return;String[] names={"Left","Center","Right"};
        for(int i=0;i<3;i++){final int al=i;a.gridAction((o.alignment==i?"● ":"")+names[i],()->{if(a.layerEditable())a.edit("Alignment updated",()->o.alignment=al);});}
        a.flushGrid();
    }
    private void presetsPanel(){
        EditProject.Clip c=a.clip();EditProject.Overlay o=a.overlay();
        a.inspector.addView(a.note("Saved presets are available in every project."));
        a.inspector.addView(a.button("Save current effects as preset",v->a.savePreset(PresetStore.EFFECTS,Attributes.EFFECTS|Attributes.STYLE)));
        a.inspector.addView(a.button("Save animation as preset",v->a.savePreset(PresetStore.ANIMATION,Attributes.ANIMATION)));
        for(String kind:new String[]{PresetStore.EFFECTS,PresetStore.ANIMATION}) for(PresetStore.Preset preset:a.presets.list(kind)){
            LinearLayout row=a.row();
            row.addView(a.button((kind.equals(PresetStore.EFFECTS)?"Effects: ":"Animation: ")+preset.name,v->{if(!a.layerEditable())return;Attributes attributes=Attributes.decode(preset.payload);
                a.edit("Preset applied",()->{int applied=c!=null?attributes.applyTo(c,attributes.categories):attributes.applyTo(o,attributes.categories);if(applied==0)throw new IllegalArgumentException("This preset does not apply to the selected layer.");});}),new LinearLayout.LayoutParams(0,a.dp(52),1));
            row.addView(a.compactButton("✕","Delete preset "+preset.name,v->a.deletePreset(preset)),new LinearLayout.LayoutParams(a.dp(52),a.dp(52)));
            a.inspector.addView(row);
        }
    }
    private void browseHome(){
        a.inspector.addView(a.note("Nothing selected. Tap a layer in the timeline or preview to edit it, or add content."));
        a.inspector.addView(a.button("+ Add media, audio, text or shapes",v->{a.tab=0;a.renderInspector();}));
        a.inspector.addView(a.button("Layers & visibility",v->{a.tab=3;a.renderInspector();}));
        a.inspector.addView(a.button((a.project.loopInMs>=0?"Clear loop range ":"Loop range…"),v->a.loopDialog()));
        a.inspector.addView(a.button("Markers & beats",v->open("Markers & beats",this::markersPanel)));
    }
    // ---- Animation ---------------------------------------------------------------------------------
    /** Timeline ms → this layer's key time, and back. */
    long toMotion(long timeline){EditProject.Clip c=a.clip();EditProject.Overlay o=a.overlay();if(c!=null){EditProject.Placement p=a.project.placements().get(a.selected);return Math.max(0,c.inMs+Math.round((timeline-p.startMs)*c.speed));}return Math.max(0,timeline-o.startMs);}
    long toTimeline(long motion){EditProject.Clip c=a.clip();EditProject.Overlay o=a.overlay();if(c!=null){EditProject.Placement p=a.project.placements().get(a.selected);return p.startMs+Math.round((motion-c.inMs)/(double)c.speed);}return o.startMs+motion;}
    void keyNavigation(){
        LinearLayout row=a.row();
        row.addView(a.button("◆‹  Previous keyframe",v->a.jumpKeyframe(false)),new LinearLayout.LayoutParams(0,a.dp(48),1));
        row.addView(a.button("Next keyframe  ›◆",v->a.jumpKeyframe(true)),new LinearLayout.LayoutParams(0,a.dp(48),1));
        a.inspector.addView(row);
    }
    private void animationPanel(){
        Motion m=a.selectedMotion();if(m==null)return;EditProject.Clip c=a.clip();EditProject.Overlay o=a.overlay();long t=motionTime(),tol=tolerance();
        a.inspector.addView(a.note("◇ grey: not animated (edits change the static value) • ◇ blue: animated between keyframes • ◆ keyframe at the playhead. Keyframes follow the layer's content when it is moved, trimmed, split or duplicated."));
        keyNavigation();
        List<String> here=new ArrayList<>();for(int p=0;p<Motion.COUNT;p++)if(Keyframes.keyAt(m,p,t,tol)!=null)here.add(Motion.PROPERTIES[p]);
        a.inspector.addView(a.label(here.isEmpty()?"No keyframe at the playhead":"Keyframes here: "+String.join(", ",here),12,EditorActivity.TEXT,true));
        if(!here.isEmpty()){
            a.inspector.addView(a.note("Easing from this keyframe to the next"));
            for(int i=0;i<Keyframes.EASINGS.length;i++){final int preset=i;a.gridAction(Keyframes.EASINGS[i],()->{if(a.layerEditable())a.edit("Easing updated",()->Keyframes.easeAll(m,t,tol,Keyframes.curve(preset)));});}
            a.gridAction("Custom curve…",()->open("Custom curve",this::curvePanel));a.flushGrid();
            timeField("Move keyframes to (timeline seconds)",a.project.playheadMs,value->a.edit("Keyframes moved",()->Keyframes.moveAll(m,t,toMotion(value),tol)));
            LinearLayout row=a.row();
            row.addView(a.button("Snap to nearest marker",v->{if(!a.layerEditable())return;if(a.project.markers.isEmpty()){a.tell("Add markers first (Markers & beats).");return;}long best=a.project.markers.get(0);for(long mk:a.project.markers)if(Math.abs(mk-a.project.playheadMs)<Math.abs(best-a.project.playheadMs))best=mk;long target=best;a.edit("Keyframes snapped to marker",()->Keyframes.moveAll(m,t,toMotion(target),tol));a.seek(target);}),new LinearLayout.LayoutParams(0,a.dp(48),1));
            row.addView(a.button("Delete keyframes here",v->{if(a.layerEditable())a.edit("Keyframes deleted",()->Keyframes.deleteAll(m,t,tol));}),new LinearLayout.LayoutParams(0,a.dp(48),1));
            a.inspector.addView(row);
        }
        LinearLayout clip=a.row();clip.addView(a.button("Copy keyframes",v->a.copyKeys()),new LinearLayout.LayoutParams(0,a.dp(48),1));clip.addView(a.button("Paste at playhead",v->a.pasteKeys()),new LinearLayout.LayoutParams(0,a.dp(48),1));a.inspector.addView(clip);
        a.inspector.addView(a.note("Properties"));
        float[] current=currentValues();
        int[] props=c!=null?new int[]{Motion.X,Motion.Y,Motion.SCALE,Motion.ROTATION,Motion.OPACITY,Motion.SKEW_X,Motion.SKEW_Y,Motion.ANCHOR_X,Motion.ANCHOR_Y,Motion.INTENSITY}:new int[]{Motion.X,Motion.Y,Motion.SCALE,Motion.ROTATION,Motion.OPACITY,Motion.SKEW_X,Motion.SKEW_Y,Motion.ANCHOR_X,Motion.ANCHOR_Y};
        for(int p:props){LinearLayout row=a.row();row.setGravity(Gravity.CENTER_VERTICAL);row.addView(diamond(Motion.PROPERTIES[p],m,p,current[p]),new LinearLayout.LayoutParams(a.dp(48),a.dp(48)));
            int state=Keyframes.state(m,p,t,tol);row.addView(a.label(Motion.PROPERTIES[p]+"  ·  "+(state==Keyframes.STATIC?"not animated":m.curves.get(p).size()+" keyframes"),12,state==Keyframes.STATIC?EditorActivity.MUTED:EditorActivity.TEXT,false),new LinearLayout.LayoutParams(0,-2,1));
            row.addView(a.label(format(m.value(p,t,current[p])),12,EditorActivity.TEXT,true));a.inspector.addView(row);}
        a.inspector.addView(a.note("Animation presets (use the layer's current values as the end state)"));
        long start=toMotion(c!=null?a.project.placements().get(a.selected).startMs:o.startMs),end=toMotion(c!=null?a.project.placements().get(a.selected).endMs:o.endMs);
        for(String preset:Keyframes.PRESETS)a.gridAction(preset,()->{if(a.layerEditable())a.edit(preset+" applied",()->Keyframes.preset(m,preset,start,end,currentValues()));});
        a.flushGrid();
        for(PresetStore.Preset saved:a.presets.list(PresetStore.ANIMATION))a.addAction("Saved: "+saved.name,()->{if(!a.layerEditable())return;Attributes attr=Attributes.decode(saved.payload);a.edit("Animation preset applied",()->{if(c!=null)attr.applyTo(c,Attributes.ANIMATION);else attr.applyTo(o,Attributes.ANIMATION);});});
        a.inspector.addView(a.button("Save this animation as a preset",v->a.savePreset(PresetStore.ANIMATION,Attributes.ANIMATION)));
    }
    float[] currentValues(){EditProject.Clip c=a.clip();EditProject.Overlay o=a.overlay();
        if(c!=null)return new float[]{c.x,c.y,c.scale,c.rotation,c.opacity,c.intensity,c.skewX,c.skewY,c.anchorX,c.anchorY};
        return new float[]{o.x,o.y,o.scale,o.rotation,o.opacity,0,o.skewX,o.skewY,o.anchorX,o.anchorY};}
    private void curvePanel(){
        Motion m=a.selectedMotion();if(m==null)return;long t=motionTime(),tol=tolerance();Motion.Key first=null;for(int p=0;p<Motion.COUNT&&first==null;p++)first=Keyframes.keyAt(m,p,t,tol);
        if(first==null){a.inspector.addView(a.note("Move the playhead onto a keyframe to edit its curve."));return;}
        Motion.Key working=first.copy();
        a.inspector.addView(a.note("Drag the two handles. The curve shapes the interval from this keyframe to the next, for every property keyed here."));
        a.inspector.addView(a.new CurveView(working,()->{}),new LinearLayout.LayoutParams(-1,a.dp(200)));
        a.inspector.addView(a.button("Apply curve",v->{if(a.layerEditable())a.edit("Custom easing applied",()->Keyframes.easeAll(m,t,tol,new float[]{working.x1,working.y1,working.x2,working.y2}));}));
    }
    // ---- Markers ------------------------------------------------------------------------------------
    private void markersPanel(){
        a.inspector.addView(a.note("Markers are snap targets for trims, moves, splits (move the playhead to a marker first) and keyframes. Shortcuts: M toggles a marker; , and . jump between markers."));
        LinearLayout nav=a.row();nav.addView(a.button("‹ Previous marker",v->a.jumpMarker(false)),new LinearLayout.LayoutParams(0,a.dp(48),1));nav.addView(a.button("Next marker ›",v->a.jumpMarker(true)),new LinearLayout.LayoutParams(0,a.dp(48),1));a.inspector.addView(nav);
        a.inspector.addView(a.button("Add / remove marker at playhead",v->a.toggleMarker()));
        a.inspector.addView(a.button("Detect beats from audio…",v->a.detectBeats()));
        for(int i=0;i<a.project.markers.size();i++){final long time=a.project.markers.get(i);final int index=i;
            timeField("Marker "+(i+1),time,value->a.edit("Marker moved",()->{a.project.markers.remove(index);if(a.project.markers.contains(value))return;a.project.markers.add(Math.max(0,value));Collections.sort(a.project.markers);}));}
        if(!a.project.markers.isEmpty())a.inspector.addView(a.button("Clear all markers",v->{if(a.editable())a.edit("Markers cleared",()->a.project.markers.clear());}));
    }
    private void multiTools(){
        a.inspector.addView(a.note(a.multiCount()+" layers selected. They move and transform together."));
        String[] names={"Align starts","Align ends","Match duration","Close gaps","Group","Ungroup","Duplicate","Delete"};
        for(String n:names)a.gridAction(n,()->a.multiAction(n));
        a.flushGrid();
    }
}
