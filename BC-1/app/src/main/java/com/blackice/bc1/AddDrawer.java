package com.blackice.bc1;

import android.graphics.Bitmap;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import com.blackice.bc1.editor.*;
import java.util.*;

/** The unified "+ Add" drawer: Media, Audio, Text, Shapes and Saved Elements. The project stays
 * visible above (phone) or beside (DeX) the drawer while browsing. */
final class AddDrawer {
    static final String[] CATEGORIES={"Media","Audio","Text","Shapes","Saved"};
    private final EditorActivity a;
    private final DeviceMedia gallery;
    int category;
    boolean atStart,asLayer;
    private String album="All albums";
    private List<DeviceMedia.Item> items=null;
    private boolean loading,audioItems;
    AddDrawer(EditorActivity activity){a=activity;gallery=new DeviceMedia(activity);}

    void render(LinearLayout into){
        LinearLayout tabs=a.row();
        for(int i=0;i<CATEGORIES.length;i++){final int index=i;String icon=i==0?"Media":i==1?"Audio":i==2?"Text":i==3?"Shapes":"Saved";
            TextView b=a.iconButton(CATEGORIES[i],v->{category=index;items=null;album="All albums";a.renderInspector();});b.setTextColor(i==category?EditorActivity.CYAN:EditorActivity.TEXT);b.setBackground(a.navBackground(i==category));b.setSelected(i==category);
            b.setCompoundDrawables(null,a.icon(icon,i==category?EditorActivity.CYAN:EditorActivity.TEXT),null,null);b.setContentDescription("Add "+CATEGORIES[i]);
            tabs.addView(b,new LinearLayout.LayoutParams(0,a.dp(60),1));}
        into.addView(tabs);
        if(category<=1)placement(into);
        switch(category){
            case 0:media(into);break;
            case 1:audio(into);break;
            case 2:text(into);break;
            case 3:shapes(into);break;
            default:saved(into);break;
        }
    }
    /** Where new media lands: at the playhead (default) or project start; main sequence or a new layer. */
    private void placement(LinearLayout into){
        LinearLayout row=a.row();
        row.addView(choice("At playhead",!atStart,v->{atStart=false;a.renderInspector();}),new LinearLayout.LayoutParams(0,a.dp(48),1));
        row.addView(choice("At project start",atStart,v->{atStart=true;a.renderInspector();}),new LinearLayout.LayoutParams(0,a.dp(48),1));
        into.addView(row);
        if(category==0){LinearLayout where=a.row();
            where.addView(choice("Main sequence",!asLayer,v->{asLayer=false;a.renderInspector();}),new LinearLayout.LayoutParams(0,a.dp(48),1));
            where.addView(choice("New layer above",asLayer,v->{asLayer=true;a.renderInspector();}),new LinearLayout.LayoutParams(0,a.dp(48),1));
            into.addView(where);}
    }
    private TextView choice(String title,boolean on,View.OnClickListener l){TextView b=a.button((on?"● ":"")+title,l);b.setTextSize(11);b.setSelected(on);b.setTextColor(on?EditorActivity.CYAN:EditorActivity.TEXT);b.setBackground(a.buttonBackground(on));return b;}
    long insertTime(){return atStart?0:a.project.playheadMs;}

    private void media(LinearLayout into){
        LinearLayout actions=a.row();
        actions.addView(a.button("Browse files…",v->a.pick(EditorActivity.IMPORT,"*/*")),new LinearLayout.LayoutParams(0,a.dp(48),1));
        actions.addView(a.button("Manage media access",v->manageAccess(false)),new LinearLayout.LayoutParams(0,a.dp(48),1));
        into.addView(actions);
        galleryGrid(into,false);
        into.addView(a.label("PROJECT MEDIA",12,EditorActivity.CYAN,true));
        for(int i=0;i<a.project.clips.size();i++){final int index=i;EditProject.Clip c=a.project.clips.get(i);
            a.addAction((i==a.selected?"●  ":"")+(i+1)+"  "+c.name+"  ·  "+EditorActivity.clock(c.durationMs()),()->a.selectClip(index,true));}
        if(a.project.clips.isEmpty())into.addView(a.note("Imported media appears here and is copied into the app so projects reopen reliably."));
    }
    private void audio(LinearLayout into){
        LinearLayout actions=a.row();
        actions.addView(a.button("Music track…",v->a.pick(EditorActivity.MUSIC,"audio/*")),new LinearLayout.LayoutParams(0,a.dp(48),1));
        actions.addView(a.button("Audio layer…",v->a.pick(EditorActivity.AUDIO_LAYER,"audio/*")),new LinearLayout.LayoutParams(0,a.dp(48),1));
        into.addView(actions);
        into.addView(a.button("Manage audio access",v->manageAccess(true)));
        galleryGrid(into,true);
        if(a.project.music!=null)a.addAction("Music: "+a.project.music.name,()->a.musicDialog());
    }
    private void galleryGrid(LinearLayout into,boolean audio){
        String access=gallery.access(audio);
        if(access.equals("none")){into.addView(a.note("Allow access to browse your "+(audio?"audio":"photos and videos")+" here, or use Browse files."));into.addView(a.button("Allow access",v->manageAccess(audio)));return;}
        if(access.equals("partial"))into.addView(a.note("Showing the photos and videos you selected. Use Manage media access to choose more."));
        if(items==null||audioItems!=audio){load(audio);into.addView(a.note("Loading device library…"));return;}
        List<String> albums=DeviceMedia.albums(items);albums.add(0,"All albums");
        HorizontalScrollView scroll=new HorizontalScrollView(a);scroll.setHorizontalScrollBarEnabled(false);LinearLayout chips=a.row();
        for(String name:albums){TextView chip=choice(name,name.equals(album),v->{album=name;a.renderInspector();});chip.setContentDescription("Album filter: "+name);chips.addView(chip,new LinearLayout.LayoutParams(-2,a.dp(44)));}
        scroll.addView(chips);into.addView(scroll);
        List<DeviceMedia.Item> shown=new ArrayList<>();for(DeviceMedia.Item i:items)if(album.equals("All albums")||album.equals(i.album))shown.add(i);
        if(shown.isEmpty()){into.addView(a.note("No "+(audio?"audio":"media")+" found."));return;}
        int columns=audio?1:a.wideLayout?3:3;
        for(int start=0;start<Math.min(shown.size(),90);start+=columns){
            LinearLayout row=a.row();
            for(int col=0;col<columns;col++){LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(0,a.dp(audio?52:96),1);cell.rightMargin=a.dp(4);cell.bottomMargin=a.dp(4);
                if(start+col<shown.size())row.addView(card(shown.get(start+col)),cell);else row.addView(new View(a),cell);}
            into.addView(row);
        }
    }
    private View card(DeviceMedia.Item item){
        FrameLayout card=new FrameLayout(a);card.setBackground(a.buttonBackground(false));card.setFocusable(true);
        card.setContentDescription("Add "+(item.audio?"audio ":item.video?"video ":"picture ")+item.name);
        if(item.audio){TextView name=a.label("♫  "+item.name+(item.durationMs>0?"  ·  "+EditorActivity.clock(item.durationMs):""),12,EditorActivity.TEXT,false);name.setGravity(Gravity.CENTER_VERTICAL);name.setPadding(a.dp(10),0,a.dp(10),0);name.setSingleLine(true);card.addView(name,new FrameLayout.LayoutParams(-1,-1));}
        else{ImageView image=new ImageView(a);image.setScaleType(ImageView.ScaleType.CENTER_CROP);Bitmap cached=gallery.cached(item.uri);if(cached!=null)image.setImageBitmap(cached);else{image.setImageDrawable(a.icon("Media",EditorActivity.MUTED));a.io.execute(()->{Bitmap b=gallery.thumbnail(item,256);if(b!=null)a.main.post(()->image.setImageBitmap(b));});}
            card.addView(image,new FrameLayout.LayoutParams(-1,-1));
            if(item.video){TextView badge=a.label("▶ "+EditorActivity.clock(item.durationMs),9,EditorActivity.TEXT,true);badge.setBackgroundColor(0xAA08131F);badge.setPadding(a.dp(4),a.dp(2),a.dp(4),a.dp(2));card.addView(badge,new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.START));}}
        card.setOnClickListener(v->a.importUris(java.util.List.of(item.uri),item.audio?EditorActivity.AUDIO_LAYER:EditorActivity.IMPORT));
        return card;
    }
    private void load(boolean audio){
        if(loading)return;loading=true;audioItems=audio;
        a.io.execute(()->{List<DeviceMedia.Item> found=gallery.query(audio,300);a.main.post(()->{loading=false;items=found;if(a.tab==0)a.renderInspector();});});
    }
    void refresh(){items=null;}
    private void manageAccess(boolean audio){
        String[] permissions=audio?DeviceMedia.audioPermissions():DeviceMedia.visualPermissions();
        if(gallery.access(audio).equals("full")){a.openAppSettings();return;}
        a.requestPermissions(permissions,EditorActivity.MEDIA_ACCESS);
    }
    private void text(LinearLayout into){
        into.addView(a.note("Text is added as its own layer at "+(atStart?"the project start":"the playhead")+"."));
        String[][] presets={{"Title","BLACK ICE","1.1"},{"Subtitle","Subtitle","0.7"},{"Caption","Caption text","0.5"}};
        for(String[] p:presets)a.gridAction("+ "+p[0],()->a.addOverlay(0,p[1],Float.parseFloat(p[2])));
        a.flushGrid();
    }
    private void shapes(LinearLayout into){
        into.addView(a.note("Shapes are vector layers with fill, border, shadow and blending."));
        for(int i=1;i<EditProject.SHAPES.length;i++){final int shape=i;a.gridAction("+ "+EditProject.SHAPES[i],()->a.addOverlay(shape,"",1));}
        a.flushGrid();
    }
    private void saved(LinearLayout into){
        into.addView(a.note("Saved elements are grouped layers you can reuse. Select layers and choose More → Save as element."));
        List<PresetStore.Preset> elements=a.presets.list(PresetStore.ELEMENT);
        if(elements.isEmpty())into.addView(a.note("No saved elements yet."));
        for(PresetStore.Preset p:elements){LinearLayout row=a.row();
            row.addView(a.button("+ "+p.name,v->a.insertElement(p,insertTime())),new LinearLayout.LayoutParams(0,a.dp(52),1));
            row.addView(a.compactButton("✕","Delete saved element "+p.name,v->a.deletePreset(p)),new LinearLayout.LayoutParams(a.dp(52),a.dp(52)));
            into.addView(row);}
        List<PresetStore.Preset> effects=a.presets.list(PresetStore.EFFECTS),animations=a.presets.list(PresetStore.ANIMATION);
        if(!effects.isEmpty()||!animations.isEmpty())into.addView(a.note(effects.size()+" effect preset(s) and "+animations.size()+" animation preset(s) are available under Presets for a selected layer."));
    }
}
