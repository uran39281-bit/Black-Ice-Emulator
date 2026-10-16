package com.blackice.bc1;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.effect.MultipleInputVideoGraph;
import androidx.media3.transformer.CompositionPlayer;
import com.blackice.bc1.editor.*;
import com.blackice.bc1.engine.EngineSettings;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Black Ice V1 workspace, now backed by a persistent, non-destructive video timeline. */
@androidx.annotation.OptIn(markerClass = {androidx.media3.common.util.UnstableApi.class, androidx.media3.common.util.ExperimentalApi.class})
public final class EditorActivity extends Activity {
    private static final int BG=0xFF090F19,PANEL=0xFF111C2A,CARD=0xFF172638,CYAN=0xFF40BFFF,TEXT=0xFFE6F5FF,MUTED=0xFF91A7BC;
    private static final int IMPORT=701,MUSIC=702,OVERLAY=703,EXPORT=704;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private ProjectStore store;
    private EditProject project=new EditProject();
    private CompositionPlayer player;
    private SurfaceView surface;
    private FrameLayout monitor;
    private LinearLayout inspector;
    private TextView status,time,play,projectLabel,exportButton;
    private TimelineCanvas timeline;
    private HorizontalScrollView timelineScroll;
    private LayoutPolicy.Preference mode=LayoutPolicy.Preference.AUTO;
    private int selected=0,tab=0;
    private float pixelsPerSecond=48;
    private boolean active,importing,loading=true,snapping=true,exportLaunching,wasExporting;
    private long lastExportSeen;
    private String message="Opening workspace…";
    private final ArrayDeque<String> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
    private final Runnable autosave=()->saveNow();
    private final Runnable pulse=new Runnable(){@Override public void run(){
        if(!active)return;
        if(player!=null && player.isPlaying()) {project.playheadMs=player.getCurrentPosition();syncPosition();}
        if(play!=null)play.setText(player!=null&&player.isPlaying()?"Ⅱ  Pause":"▶  Play");
        android.content.SharedPreferences export=getSharedPreferences(ExportService.PREFS,MODE_PRIVATE);
        boolean running=export.getBoolean("running",false);
        long completed=export.getLong("finishedAt",0);
        if(!running&&!exportLaunching&&!loading&&(wasExporting||completed>lastExportSeen)){lastExportSeen=completed;tell(export.getString("status","Export ended"));if(wasExporting)preparePreview(false);}
        wasExporting=running;
        if(exportButton!=null)exportButton.setText(running?"Cancel":"Export");
        if(status!=null)status.setText(running?export.getString("status","Exporting…"):message);
        main.postDelayed(this,100);
    }};
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        store=new ProjectStore(this);
        android.content.SharedPreferences exportState=getSharedPreferences(ExportService.PREFS,MODE_PRIVATE);
        if(exportState.getBoolean("running",false)&&!ExportService.isRunning())exportState.edit().putBoolean("running",false)
                .putBoolean("success",false).putString("status","Previous export was interrupted. Export again to create a complete MP4.").putLong("finishedAt",System.currentTimeMillis()).apply();
        try {mode=LayoutPolicy.Preference.valueOf(getPreferences(MODE_PRIVATE).getString("mode","AUTO"));}catch(Exception ignored){}
        if(state!=null){selected=state.getInt("selected",0);tab=state.getInt("tab",0);}
        buildWorkspace();
        String id=getPreferences(MODE_PRIVATE).getString("last-project",null);
        io.execute(()->{
            EditProject loaded=null;String failure=null;
            if(id!=null)try {loaded=store.load(id);}catch(Exception e){failure="Could not reopen the last project. Its file was preserved.";}
            EditProject result=loaded;String error=failure;
            main.post(()->{if(isDestroyed())return;loading=false;if(result!=null)project=result;
                message=error==null?(project.clips.isEmpty()?"Import videos or images to begin.":"Project restored • "+project.clips.size()+" clips"):error;
                refreshProject();preparePreview(false);
            });
        });
    }
    @Override protected void onStart(){super.onStart();active=true;main.post(pulse);if(!loading)preparePreview(false);}
    @Override protected void onStop(){active=false;main.removeCallbacks(pulse);saveNow();releasePreview();super.onStop();}
    @Override protected void onDestroy(){main.removeCallbacks(autosave);io.shutdown();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle state){state.putInt("selected",selected);state.putInt("tab",tab);saveNow();super.onSaveInstanceState(state);}
    @Override public void onConfigurationChanged(Configuration config){super.onConfigurationChanged(config);buildWorkspace();}
    private boolean exporting(){return getSharedPreferences(ExportService.PREFS,MODE_PRIVATE).getBoolean("running",false)||exportLaunching;}
    private boolean editable(){if(loading||importing){tell("Wait for the current import to finish.");return false;}if(exporting()){tell("Wait for export to finish, or cancel it to continue editing.");return false;}return true;}
    private void releasePreview(){if(player!=null){project.playheadMs=player.getCurrentPosition();player.release();player=null;}}
    private void preparePreview(boolean playWhenReady) {
        if(!active||loading||exporting())return;
        long position=project.playheadMs;releasePreview();project.playheadMs=position;
        if(project.clips.isEmpty()){syncPosition();return;}
        try {
            EditProject snapshot=ProjectStore.snapshot(project);
            player=new CompositionPlayer.Builder(this).setVideoGraphFactory(new MultipleInputVideoGraph.Factory(CompositionFactory.frameProcessor()))
                    .setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true).build();
            player.addListener(new Player.Listener(){
                @Override public void onPlayerError(PlaybackException error){tell("Preview error: "+error.getMessage()+". Check the selected media or try a lower preview quality.");}
                @Override public void onPlaybackStateChanged(int state){if(state==Player.STATE_ENDED && player!=null){project.playheadMs=project.durationMs();syncPosition();}}
            });
            player.setVideoSurfaceView(surface);player.setComposition(CompositionFactory.build(this,snapshot,false),Math.min(position,Math.max(0,project.durationMs()-1)));
            player.prepare();player.setPlayWhenReady(playWhenReady);
        } catch(Exception e){tell("Cannot prepare preview: "+e.getMessage());}
    }
    private void remember(){try {undo.addLast(ProjectStore.encode(project));while(undo.size()>30)undo.removeFirst();redo.clear();}catch(Exception e){tell(e.getMessage());}}
    private void changed(String text) {
        project.validate();message=text;refreshProject();preparePreview(false);main.removeCallbacks(autosave);main.postDelayed(autosave,300);
    }
    private void saveNow() {
        main.removeCallbacks(autosave);if(loading)return;
        if(player!=null)project.playheadMs=player.getCurrentPosition();
        EditProject snapshot=ProjectStore.snapshot(project);
        io.execute(()->{try{store.save(snapshot);main.post(()->getPreferences(MODE_PRIVATE).edit().putString("last-project",snapshot.id).apply());}
            catch(Exception e){main.post(()->tell("Save failed: "+e.getMessage()));}});
    }
    private void undo(boolean forward) {
        if(!editable())return;ArrayDeque<String> from=forward?redo:undo,to=forward?undo:redo;
        if(from.isEmpty()){tell(forward?"Nothing to redo.":"Nothing to undo.");return;}
        try {to.addLast(ProjectStore.encode(project));project=ProjectStore.decode(from.removeLast());changed(forward?"Edit restored":"Edit undone");}
        catch(Exception e){tell(e.getMessage());}
    }
    private void refreshProject() {
        selected=Math.max(0,Math.min(selected,project.clips.size()-1));
        if(projectLabel!=null)projectLabel.setText(project.name+"  ▾");
        if(timeline!=null){timeline.requestLayout();timeline.invalidate();}
        if(monitor!=null)monitor.requestLayout();renderInspector();syncPosition();
    }
    private void syncPosition(){if(time!=null)time.setText(clock(project.playheadMs)+"  /  "+clock(project.durationMs()));if(timeline!=null)timeline.invalidate();}
    private void seek(long ms){project.playheadMs=Math.max(0,Math.min(ms,project.durationMs()));if(player!=null){player.pause();player.seekTo(Math.min(project.playheadMs,Math.max(0,project.durationMs()-1)));}syncPosition();}
    private void togglePlay(){if(player==null){tell("Import a video or image first.");return;}if(player.isPlaying())player.pause();else{if(player.getPlaybackState()==Player.STATE_ENDED||project.playheadMs>=project.durationMs())player.seekTo(0);player.play();}}

    private void buildWorkspace() {
        Configuration c=getResources().getConfiguration();android.view.Display display=getWindowManager().getDefaultDisplay();
        boolean wide=LayoutPolicy.resolve(mode,c.screenWidthDp,c.screenHeightDp,c.smallestScreenWidthDp,display!=null&&display.getDisplayId()!=0)==LayoutPolicy.Workspace.WIDE;
        LinearLayout root=column();root.setBackgroundColor(BG);
        LinearLayout header=row();header.setPadding(dp(10),0,dp(8),0);header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout brand=column();brand.addView(label("△  BLACK ICE",15,CYAN,true));
        projectLabel=label(project.name+"  ▾",11,TEXT,false);projectLabel.setMaxLines(1);brand.addView(projectLabel);brand.setOnClickListener(v->projectsMenu());brand.setContentDescription("Project menu");brand.setFocusable(true);brand.setMinimumHeight(dp(52));
        header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        header.addView(button(mode.name(),v->chooseMode()));exportButton=button("Export",v->exportMenu());header.addView(exportButton);
        root.addView(header,new LinearLayout.LayoutParams(-1,dp(56)));
        if(wide) {
            LinearLayout body=row();root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
            LinearLayout rail=column();rail.setPadding(dp(4),dp(6),dp(4),0);
            String[] names={"Media","Edit","Effects","Layers","Setup"};
            for(int i=0;i<names.length;i++){final int index=i;TextView b=button(names[i],v->{tab=index;renderInspector();});b.setTextSize(11);rail.addView(b,new LinearLayout.LayoutParams(-1,dp(52)));}
            body.addView(rail,new LinearLayout.LayoutParams(dp(76),-1));
            LinearLayout center=column();center.setPadding(dp(4),dp(4),dp(6),dp(4));body.addView(center,new LinearLayout.LayoutParams(0,-1,1));
            center.addView(buildMonitor(),new LinearLayout.LayoutParams(-1,0,1));center.addView(transport());center.addView(buildTimeline(),new LinearLayout.LayoutParams(-1,dp(c.screenHeightDp<550?140:176)));
            ScrollView tools=new ScrollView(this);tools.setFillViewport(false);inspector=column();inspector.setPadding(dp(10),dp(10),dp(10),dp(12));tools.addView(inspector);tools.setBackgroundColor(PANEL);
            body.addView(tools,new LinearLayout.LayoutParams(dp(c.screenWidthDp<1000?250:300),-1));
        } else {
            LinearLayout content=column();content.setPadding(dp(8),0,dp(8),0);
            boolean compact=c.screenHeightDp<700;
            if(compact){ScrollView all=new ScrollView(this);all.setFillViewport(true);all.addView(content);root.addView(all,new LinearLayout.LayoutParams(-1,0,1));}
            else root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
            content.addView(buildMonitor(),new LinearLayout.LayoutParams(-1,dp(c.screenHeightDp<780?170:210)));
            content.addView(transport());content.addView(buildTimeline(),new LinearLayout.LayoutParams(-1,dp(170)));
            inspector=column();inspector.setPadding(dp(8),dp(10),dp(8),dp(14));
            if(compact)content.addView(inspector);else{ScrollView tools=new ScrollView(this);tools.addView(inspector);content.addView(tools,new LinearLayout.LayoutParams(-1,0,1));}
            LinearLayout tabs=row();String[] names={"Media","Edit","Effects","Layers","Setup"};
            for(int i=0;i<names.length;i++){final int index=i;TextView b=button(names[i],v->{tab=index;renderInspector();});b.setTextSize(11);tabs.addView(b,new LinearLayout.LayoutParams(0,dp(52),1));}root.addView(tabs);
        }
        status=label(message,10,MUTED,false);status.setSingleLine(true);status.setPadding(dp(10),0,dp(10),dp(3));status.setOnClickListener(v->new AlertDialog.Builder(this).setMessage(status.getText()).setPositiveButton("OK",null).show());root.addView(status,new LinearLayout.LayoutParams(-1,dp(24)));
        root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets.consumeSystemWindowInsets();});
        setContentView(root);root.requestApplyInsets();if(player!=null)player.setVideoSurfaceView(surface);renderInspector();syncPosition();
    }
    private View buildMonitor() {
        if(surface==null){surface=new SurfaceView(this);surface.setContentDescription("Video project preview");}
        if(surface.getParent()!=null)((android.view.ViewGroup)surface.getParent()).removeView(surface);
        monitor=new FrameLayout(this){@Override protected void onMeasure(int wSpec,int hSpec){
            int w=View.MeasureSpec.getSize(wSpec),h=View.MeasureSpec.getSize(hSpec);float ratio=project.portrait?9f/16:16f/9;
            int sw=Math.min(w,(int)(h*ratio)),sh=(int)(sw/ratio);
            surface.setLayoutParams(new FrameLayout.LayoutParams(Math.max(1,sw),Math.max(1,sh),Gravity.CENTER));super.onMeasure(wSpec,hSpec);
        }};
        monitor.setBackgroundColor(Color.BLACK);monitor.addView(surface,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
        return monitor;
    }
    private View transport() {
        LinearLayout controls=row();controls.setGravity(Gravity.CENTER_VERTICAL);
        play=button("▶  Play",v->togglePlay());controls.addView(play);time=label("",11,TEXT,false);time.setGravity(Gravity.CENTER);controls.addView(time,new LinearLayout.LayoutParams(0,dp(48),1));
        controls.addView(button("−",v->{pixelsPerSecond=Math.max(8,pixelsPerSecond/1.5f);timeline.requestLayout();}));
        controls.addView(button("+",v->{pixelsPerSecond=Math.min(240,pixelsPerSecond*1.5f);timeline.requestLayout();}));return controls;
    }
    private View buildTimeline(){timelineScroll=new HorizontalScrollView(this);timelineScroll.setFillViewport(true);timeline=new TimelineCanvas();timelineScroll.addView(timeline,new HorizontalScrollView.LayoutParams(-2,-1));return timelineScroll;}
    private EditProject.Clip clip(){return project.clips.isEmpty()?null:project.clips.get(selected);}
    private void renderInspector() {
        if(inspector==null)return;inspector.removeAllViews();
        String[] headings={"MEDIA LIBRARY","CLIP EDITOR","GPU EFFECTS","OVERLAYS & MUSIC","PROJECT SETTINGS"};
        inspector.addView(label(headings[tab],12,CYAN,true));
        if(tab==0) {
            inspector.addView(note("Videos and images are copied into this app so saved projects can reopen reliably."));
            addAction("+ Import video / image",()->pick(IMPORT,"*/*"));
            addAction("+ Import music",()->pick(MUSIC,"audio/*"));
            for(int i=0;i<project.clips.size();i++){final int index=i;EditProject.Clip c=project.clips.get(i);
                addAction((i==selected?"●  ":"")+(i+1)+"  "+c.name+"  ·  "+clock(c.durationMs()),()->{selected=index;tab=1;seek(project.placements().get(index).startMs);renderInspector();});}
        } else if(tab==1) {
            EditProject.Clip c=clip();if(c==null){inspector.addView(note("Import a clip from Media to start editing."));return;}
            inspector.addView(note((selected+1)+" / "+project.clips.size()+"  •  "+c.name));
            inspector.addView(note("In "+seconds(c.inMs)+" s   Out "+seconds(c.outMs)+" s"));
            addAction("Trim / image duration",()->trimDialog());
            addAction("Split at playhead",()->{if(!editable())return;try{long position=project.playheadMs;remember();project.split(selected,position);changed("Clip split");}catch(Exception e){tell(e.getMessage());}});
            LinearLayout order=row();order.addView(button("← Earlier",v->moveClip(-1)),new LinearLayout.LayoutParams(0,dp(48),1));order.addView(button("Later →",v->moveClip(1)),new LinearLayout.LayoutParams(0,dp(48),1));inspector.addView(order);
            addAction("Duplicate clip",()->{if(!editable())return;if(project.clips.size()>=100){tell("This preview supports up to 100 clips.");return;}remember();project.clips.add(selected+1,c.copy());selected++;changed("Clip duplicated");});
            addAction("Remove clip",()->{if(!editable())return;remember();project.clips.remove(selected);changed("Clip removed • Undo is available");});
            slider("Clip volume",c.volume,value->{remember();c.volume=value;changed("Clip volume updated");});
            addAction("Undo",()->undo(false));addAction("Redo",()->undo(true));
        } else if(tab==2) {
            EditProject.Clip c=clip();if(c==null){inspector.addView(note("Select a clip to apply an effect."));return;}
            inspector.addView(note("Selected: "+c.name));
            select("Effect",EngineSettings.EFFECTS,c.effect,index->{remember();c.effect=index;changed("Effect applied");});
            slider("Intensity",c.intensity,value->{remember();c.intensity=value;changed("Effect strength updated");});
            slider("Opacity",c.opacity,value->{remember();c.opacity=value;changed("Opacity updated");});
            String[] transitions=new String[EngineSettings.TRANSITIONS.length+1];transitions[0]="Cut (no overlap)";System.arraycopy(EngineSettings.TRANSITIONS,0,transitions,1,EngineSettings.TRANSITIONS.length);
            select("Transition to next clip",transitions,c.transition+1,index->{remember();c.transition=index-1;changed("Transition updated");});
            addAction("Transition length: "+seconds(c.transitionMs)+" s",()->numberDialog("Transition length (seconds)",seconds(c.transitionMs),value->{long ms=parseMs(value);if(ms<0||ms>5000)throw new IllegalArgumentException("Use 0 to 5 seconds.");remember();c.transitionMs=ms;changed("Transition duration updated");}));
            select("Motion",EngineSettings.EASINGS,c.easing,index->{remember();c.easing=index;changed("Motion curve updated");});
            inspector.addView(note("Overlap is limited to half of either neighboring clip. Last-clip transitions wait for a following clip."));
        } else if(tab==3) {
            addAction("+ Add text overlay",()->overlayDialog(null));addAction("+ Add image overlay",()->pick(OVERLAY,"image/*"));
            for(EditProject.Overlay o:project.overlays)addAction((o.imageUri==null?"Text: "+o.text:"Image overlay")+" · "+seconds(o.startMs)+"–"+seconds(o.endMs)+" s",()->overlayDialog(o));
            addAction(project.music==null?"+ Import music":"Music: "+project.music.name,()->{if(project.music==null)pick(MUSIC,"audio/*");else musicDialog();});
        } else {
            select("Project canvas",new String[]{"Landscape 16:9","Portrait 9:16"},project.portrait?1:0,index->{remember();project.portrait=index==1;changed("Project aspect updated");});
            select("Preview resolution (short edge)",new String[]{"360p • Performance","540p • Balanced","1080p • Quality"},project.previewHeight==360?0:project.previewHeight==540?1:2,index->{remember();project.previewHeight=new int[]{360,540,1080}[index];changed("Preview quality updated");});
            addAction("Workspace: "+mode.name(),()->chooseMode());
            addAction("Snap to clip edges: "+(snapping?"ON":"OFF"),()->{snapping=!snapping;renderInspector();});
            addAction("Save project",()->{saveNow();tell("Saving project…");});
            addAction("Projects / New / Rename",()->projectsMenu());
            addAction("Open v0.3 effects lab",()->startActivity(new Intent(this,WorkbenchActivity.class)));
            inspector.addView(note("BC-1 v0.4 Preview\nExport: H.264 MP4 + AAC, 1080p, up to 30 fps. Lower-rate sources keep their cadence.\nSpace: play/pause • Ctrl+Z: undo • Ctrl+S: save\n3D Page Curl remains excluded."));
        }
    }
    private void moveClip(int delta){if(!editable())return;int next=selected+delta;if(next<0||next>=project.clips.size())return;remember();project.move(selected,delta);selected=next;changed("Clip moved");}
    private void trimDialog() {
        EditProject.Clip c=clip();if(c==null)return;
        LinearLayout form=form();EditText start=field(form,"Source in (seconds)",seconds(c.inMs));EditText end=field(form,c.image?"Source out / duration (seconds)":"Source out (seconds)",seconds(c.outMs));
        form.addView(note("Source length: "+seconds(c.sourceDurationMs)+" s. Edits do not change the imported file."));
        dialog("Trim clip",form,()->{long in=parseMs(start.getText().toString()),out=parseMs(end.getText().toString());
            if(in<0||out>c.sourceDurationMs||out-in<100)throw new IllegalArgumentException("Choose a valid source range of at least 0.10 seconds.");remember();project.trim(selected,in,out);changed("Trim applied");},null);
    }
    private void overlayDialog(EditProject.Overlay existing) {
        if(!editable())return;if(existing==null&&project.overlays.size()>=20){tell("This preview supports up to 20 overlays.");return;}EditProject.Overlay o=existing==null?new EditProject.Overlay():existing;
        if(existing==null){o.startMs=project.playheadMs;o.endMs=Math.max(o.startMs+100,Math.min(project.durationMs(),o.startMs+3000));}
        LinearLayout form=form();EditText text=o.imageUri==null?field(form,"Text (up to 200 characters)",o.text):null;
        EditText start=field(form,"Start (seconds)",seconds(o.startMs)),end=field(form,"End (seconds)",seconds(o.endMs));
        EditText x=field(form,"Horizontal: −1 left, 0 center, 1 right",String.valueOf(o.x));
        EditText y=field(form,"Vertical: −1 bottom, 0 center, 1 top",String.valueOf(o.y));
        EditText scale=field(form,"Size: 0.1 to 2",String.valueOf(o.scale));EditText opacity=field(form,"Opacity: 0 to 1",String.valueOf(o.opacity));
        dialog(existing==null?"Add overlay":"Edit overlay",form,()->{
            long s=parseMs(start.getText().toString()),e=parseMs(end.getText().toString());
            float xx=parseFloat(x),yy=parseFloat(y),ss=parseFloat(scale),aa=parseFloat(opacity);String title=text==null?o.text:text.getText().toString();
            if(s<0||e<=s||Math.abs(xx)>1||Math.abs(yy)>1||ss<.1f||ss>2||aa<0||aa>1||title.length()>200)throw new IllegalArgumentException("Check the time range and position / size limits.");
            remember();o.startMs=s;o.endMs=e;o.x=xx;o.y=yy;o.scale=ss;o.opacity=aa;o.text=title;if(existing==null)project.overlays.add(o);changed("Overlay saved");
        },existing==null?null:()->{remember();project.overlays.remove(o);changed("Overlay removed");});
    }
    private void musicDialog() {
        EditProject.Music m=project.music;LinearLayout form=form();form.addView(note(m.name));
        EditText in=field(form,"Source in (seconds)",seconds(m.inMs)),out=field(form,"Source out (seconds)",seconds(m.outMs));
        EditText start=field(form,"Start on timeline (seconds)",seconds(m.startMs)),volume=field(form,"Music volume: 0 to 1",String.valueOf(m.volume));
        CheckBox loop=new CheckBox(this);loop.setText("Loop trimmed music to end of project");loop.setTextColor(TEXT);loop.setChecked(m.loop);form.addView(loop);
        dialog("Music track",form,()->{long a=parseMs(in.getText().toString()),b=parseMs(out.getText().toString()),s=parseMs(start.getText().toString());float v=parseFloat(volume);
            if(a<0||b>m.sourceDurationMs||b-a<100||s<0||v<0||v>1)throw new IllegalArgumentException("Choose a valid audio range and volume.");
            remember();m.inMs=a;m.outMs=b;m.startMs=s;m.volume=v;m.loop=loop.isChecked();changed("Music updated");
        },()->{remember();project.music=null;changed("Music removed");});
    }
    private void projectsMenu() {
        if(!editable())return;new AlertDialog.Builder(this).setTitle("Black Ice projects").setItems(new String[]{"New project","Recent projects","Rename project","Save project","Undo","Redo"},(d,index)->{
            if(index==0)numberDialog("New project name","Untitled project",name->{if(name.isBlank()||name.length()>120)throw new IllegalArgumentException("Use a name from 1 to 120 characters.");saveNow();releasePreview();project=new EditProject();project.name=name;undo.clear();redo.clear();selected=0;changed("New project ready");});
            if(index==1){saveNow();io.execute(()->{java.util.List<ProjectStore.Entry> recent=store.recent();main.post(()->{if(isDestroyed())return;
                if(recent.isEmpty()){tell("No saved projects yet.");return;}
                new AlertDialog.Builder(this).setTitle("Recent projects").setItems(recent.stream().map(e->e.name).toArray(String[]::new),(x,i)->openProject(recent.get(i).id)).show();});});}
            if(index==2)numberDialog("Project name",project.name,name->{if(name.isBlank()||name.length()>120)throw new IllegalArgumentException("Use a name from 1 to 120 characters.");remember();project.name=name;changed("Project renamed");});
            if(index==3){saveNow();tell("Saving project…");}if(index==4)undo(false);if(index==5)undo(true);
        }).show();
    }
    private void openProject(String id){loading=true;releasePreview();io.execute(()->{try{EditProject loaded=store.load(id);main.post(()->{if(isDestroyed())return;loading=false;project=loaded;undo.clear();redo.clear();selected=0;changed("Project opened");});}catch(Exception e){main.post(()->{loading=false;tell("Cannot open project: "+e.getMessage());});}});}
    private void chooseMode(){new AlertDialog.Builder(this).setTitle("Workspace layout").setSingleChoiceItems(new String[]{"Auto • phone / tablet / DeX","Phone portrait workspace","Wide tablet / DeX workspace"},mode.ordinal(),(d,i)->{mode=LayoutPolicy.Preference.values()[i];getPreferences(MODE_PRIVATE).edit().putString("mode",mode.name()).apply();d.dismiss();buildWorkspace();}).show();}
    private void pick(int request,String type) {
        if(!editable())return;if(request==OVERLAY&&project.overlays.size()>=20){tell("This preview supports up to 20 overlays.");return;}Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType(type).addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if(request==IMPORT){intent.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"video/*","image/*"});intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);}
        try{startActivityForResult(intent,request);}catch(ActivityNotFoundException e){tell("No document picker is installed.");}
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null)return;
        if(request==EXPORT){if(data.getData()!=null)startExport(data.getData());return;}
        if(request!=IMPORT&&request!=MUSIC&&request!=OVERLAY)return;
        java.util.List<Uri> uris=new ArrayList<>();if(data.getClipData()!=null){for(int i=0;i<data.getClipData().getItemCount();i++)uris.add(data.getClipData().getItemAt(i).getUri());}else if(data.getData()!=null)uris.add(data.getData());
        if(uris.isEmpty())return;importing=true;tell("Importing media copies…");
        io.execute(()->{
            java.util.List<EditProject.Clip> imported=new ArrayList<>();java.util.List<String> errors=new ArrayList<>();
            for(Uri uri:uris)try{imported.add(new MediaImporter(this).importClip(uri,request==MUSIC,request==OVERLAY?1024:2048));}catch(Exception e){errors.add(e.getMessage());}
            main.post(()->{importing=false;if(isDestroyed())return;remember();
                for(EditProject.Clip c:imported){
                    if(request==MUSIC){EditProject.Music m=new EditProject.Music();m.uri=c.uri;m.name=c.name;m.sourceDurationMs=c.sourceDurationMs;m.outMs=c.outMs;project.music=m;tab=3;}
                    else if(request==OVERLAY){EditProject.Overlay o=new EditProject.Overlay();o.imageUri=c.uri;o.startMs=project.playheadMs;o.endMs=Math.max(o.startMs+100,Math.min(project.durationMs(),o.startMs+3000));project.overlays.add(o);tab=3;}
                    else {if(project.clips.size()>=100){errors.add("The 100-clip project limit was reached.");new File(Uri.parse(c.uri).getPath()).delete();continue;}project.clips.add(c);selected=project.clips.size()-1;}
                }
                changed(errors.isEmpty()?"Imported "+imported.size()+" item(s)":"Import issue: "+String.join("; ",errors));
                if(!errors.isEmpty())new AlertDialog.Builder(this).setTitle("Import result").setMessage(message).setPositiveButton("OK",null).show();
            });
        });
    }
    private void exportMenu() {
        if(exporting()){new AlertDialog.Builder(this).setTitle("Cancel current export?").setMessage(getSharedPreferences(ExportService.PREFS,MODE_PRIVATE).getString("status","Export running")).setPositiveButton("Cancel export",(d,w)->startService(new Intent(this,ExportService.class).setAction(ExportService.ACTION_CANCEL))).setNegativeButton("Keep exporting",null).show();return;}
        if(!editable()||project.clips.isEmpty()){tell("Import at least one video or image before exporting.");return;}
        new AlertDialog.Builder(this).setTitle("Export MP4").setMessage(project.name+"\n"+(project.portrait?"1080 × 1920":"1920 × 1080")+" • H.264 + AAC • up to 30 fps\n"+clock(project.durationMs())+"\n\nChoose where to save the finished video. Export can continue in the background.")
                .setPositiveButton("Choose destination",(d,w)->{Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("video/mp4").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,project.name.replaceAll("[^a-zA-Z0-9 ._-]","_")+".mp4");startActivityForResult(intent,EXPORT);}).setNegativeButton("Cancel",null).show();
    }
    private void startExport(Uri destination) {
        if(exporting()){tell("An export is already running.");return;}saveNow();releasePreview();exportLaunching=true;
        EditProject snapshot=ProjectStore.snapshot(project);io.execute(()->{try{
            File file=new File(getCacheDir(),"export-project-"+System.currentTimeMillis()+".json");try(FileOutputStream out=new FileOutputStream(file)){out.write(ProjectStore.encode(snapshot).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
            main.post(()->{exportLaunching=false;try{startForegroundService(new Intent(this,ExportService.class).putExtra("project",file.getAbsolutePath()).putExtra("destination",destination.toString()));message="Export started";}catch(Exception e){file.delete();tell("Cannot start export: "+e.getMessage());}});
        }catch(Exception e){main.post(()->{exportLaunching=false;tell("Cannot prepare export: "+e.getMessage());});}});
    }
    @Override public boolean onKeyDown(int key,KeyEvent event){if(event.isCtrlPressed()&&key==KeyEvent.KEYCODE_S){saveNow();return true;}if(event.isCtrlPressed()&&key==KeyEvent.KEYCODE_Z){undo(event.isShiftPressed());return true;}if(key==KeyEvent.KEYCODE_SPACE&&event.getRepeatCount()==0){togglePlay();return true;}return super.onKeyDown(key,event);}

    private final class TimelineCanvas extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private float downX;private boolean dragging;
        TimelineCanvas(){super(EditorActivity.this);setContentDescription("Timeline: tap a clip to select and seek; drag across the ruler to scrub. Horizontal scroll and plus/minus zoom.");setFocusable(true);}
        private float timelineScale(){return Math.min(dp(pixelsPerSecond),dp(30000)/Math.max(1f,project.durationMs()/1000f));}
        private float x(long ms){return dp(12)+ms/1000f*timelineScale();}
        @Override protected void onMeasure(int w,int h){setMeasuredDimension(Math.max(dp(280),Math.min(dp(30000),(int)x(project.durationMs())+dp(30))),View.MeasureSpec.getSize(h));}
        @Override protected void onDraw(Canvas canvas){
            canvas.drawColor(PANEL);int height=getHeight();float mainY=dp(35),clipH=dp(42);
            paint.setTextSize(dp(10));paint.setTypeface(Typeface.DEFAULT);paint.setColor(MUTED);
            long interval=1000;while(interval/1000f*timelineScale()<dp(55))interval*=2;
            for(long ms=0;ms<=project.durationMs();ms+=interval){float xx=x(ms);canvas.drawText(seconds(ms)+"s",xx,dp(16),paint);canvas.drawLine(xx,dp(21),xx,height,paint);}
            java.util.List<EditProject.Placement> placements=project.placements();
            for(EditProject.Placement p:placements){float left=x(p.startMs),right=x(p.endMs);paint.setColor(p.index==selected?0xFF226D99:0xFF204057);canvas.drawRoundRect(left,mainY,right,mainY+clipH,dp(5),dp(5),paint);
                paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(p.index==selected?2:1));paint.setColor(p.index==selected?CYAN:0xFF3B677C);canvas.drawRoundRect(left,mainY,right,mainY+clipH,dp(5),dp(5),paint);paint.setStyle(Paint.Style.FILL);
                canvas.save();canvas.clipRect(left+dp(4),mainY,right-dp(3),mainY+clipH);paint.setColor(TEXT);paint.setTextSize(dp(11));canvas.drawText((p.index+1)+"  "+p.clip.name,left+dp(7),mainY+dp(24),paint);canvas.restore();
                if(p.outgoingMs>0){paint.setColor(0x9940BFFF);canvas.drawRect(x(p.endMs-p.outgoingMs),mainY,right,mainY+clipH,paint);}
            }
            paint.setColor(MUTED);paint.setTextSize(dp(10));if(placements.isEmpty())canvas.drawText("IMPORT MEDIA TO BUILD YOUR TIMELINE",dp(12),mainY+dp(23),paint);
            float overlayY=mainY+clipH+dp(9);
            for(EditProject.Overlay o:project.overlays){paint.setColor(0xFF72549B);canvas.drawRoundRect(x(o.startMs),overlayY,x(Math.min(o.endMs,project.durationMs())),overlayY+dp(22),dp(3),dp(3),paint);}
            if(project.overlays.isEmpty()){paint.setColor(MUTED);canvas.drawText("OVERLAYS",dp(12),overlayY+dp(15),paint);}
            float audioY=overlayY+dp(28);
            if(project.music!=null){paint.setColor(0xFF277A79);long end=project.music.loop?project.durationMs():Math.min(project.durationMs(),project.music.startMs+project.music.outMs-project.music.inMs);canvas.drawRoundRect(x(project.music.startMs),audioY,x(end),audioY+dp(22),dp(3),dp(3),paint);paint.setColor(TEXT);canvas.drawText("♫  "+project.music.name,x(project.music.startMs)+dp(5),audioY+dp(15),paint);}
            else{paint.setColor(MUTED);canvas.drawText("MUSIC",dp(12),audioY+dp(15),paint);}
            paint.setColor(CYAN);paint.setStrokeWidth(dp(2));canvas.drawLine(x(project.playheadMs),0,x(project.playheadMs),height,paint);
        }
        @Override public boolean onTouchEvent(android.view.MotionEvent event){
            if(event.getAction()==MotionEvent.ACTION_DOWN){downX=event.getX();dragging=event.getY()<dp(32);if(dragging)getParent().requestDisallowInterceptTouchEvent(true);return true;}
            if(event.getAction()==MotionEvent.ACTION_MOVE&&dragging){long target=(long)((event.getX()-dp(12))/timelineScale()*1000);seek(snapping?project.snap(target,(long)(8000/pixelsPerSecond)):target);return true;}
            if(event.getAction()==MotionEvent.ACTION_UP){long target=(long)((event.getX()-dp(12))/timelineScale()*1000);
                if(Math.abs(event.getX()-downX)<dp(10)||dragging){
                    if(!dragging&&event.getY()>=dp(35)&&event.getY()<dp(78)){java.util.List<EditProject.Placement> placements=project.placements();for(int i=placements.size()-1;i>=0;i--){EditProject.Placement p=placements.get(i);if(target>=p.startMs&&target<p.endMs){selected=i;break;}}renderInspector();}
                    seek(snapping?project.snap(target,(long)(8000/pixelsPerSecond)):target);performClick();}
                dragging=false;getParent().requestDisallowInterceptTouchEvent(false);return true;}
            if(event.getAction()==MotionEvent.ACTION_CANCEL){dragging=false;getParent().requestDisallowInterceptTouchEvent(false);}return true;
        }
        @Override public boolean performClick(){super.performClick();return true;}
    }
    private void tell(String text){message=text==null?"Something went wrong.":text;if(status!=null)status.setText(message);}
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);return l;}
    private TextView label(String text,float size,int color,boolean bold){TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setTextColor(color);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    private TextView note(String text){TextView v=label(text,12,MUTED,false);v.setPadding(0,dp(7),0,dp(10));return v;}
    private TextView button(String title,View.OnClickListener listener){TextView b=label(title,12,TEXT,true);b.setGravity(Gravity.CENTER);b.setPadding(dp(12),dp(6),dp(12),dp(6));b.setMinWidth(dp(48));b.setMinHeight(dp(48));b.setMaxLines(2);b.setFocusable(true);b.setContentDescription(title);GradientDrawable bg=new GradientDrawable();bg.setColor(CARD);bg.setCornerRadius(dp(7));bg.setStroke(dp(1),0xFF294357);b.setBackground(bg);b.setOnClickListener(listener);return b;}
    private void addAction(String title,Runnable action){TextView b=button(title,v->action.run());LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.topMargin=dp(6);inspector.addView(b,params);}
    private interface IndexAction{void apply(int index);}
    private interface ValueAction{void apply(float value);}
    private interface TextAction{void apply(String text);}
    private void select(String label,String[] values,int current,IndexAction action){addAction(label+": "+values[current],()->new AlertDialog.Builder(this).setTitle(label).setSingleChoiceItems(values,current,(d,i)->{d.dismiss();if(editable())action.apply(i);}).show());}
    private void slider(String name,float value,ValueAction action){TextView title=note(name+"  "+Math.round(value*100)+"%");inspector.addView(title);SeekBar slider=new SeekBar(this);slider.setMax(100);slider.setProgress(Math.round(value*100));slider.setContentDescription(name);inspector.addView(slider,new LinearLayout.LayoutParams(-1,dp(48)));slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean user){title.setText(name+"  "+p+"%");}public void onStartTrackingTouch(SeekBar s){if(player!=null)player.pause();}public void onStopTrackingTouch(SeekBar s){if(editable())action.apply(s.getProgress()/100f);}});}
    private LinearLayout form(){LinearLayout l=column();l.setPadding(dp(18),dp(8),dp(18),dp(10));return l;}
    private EditText field(LinearLayout form,String title,String value){form.addView(note(title));EditText e=new EditText(this);e.setText(value);e.setTextColor(TEXT);e.setSingleLine(false);e.setMinHeight(dp(48));form.addView(e);return e;}
    private void dialog(String title,LinearLayout form,Runnable apply,Runnable delete){ScrollView scroll=new ScrollView(this);scroll.addView(form);AlertDialog.Builder b=new AlertDialog.Builder(this).setTitle(title).setView(scroll).setPositiveButton("Apply",null).setNegativeButton("Cancel",null);if(delete!=null)b.setNeutralButton("Remove",(d,w)->delete.run());AlertDialog dialog=b.create();dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{if(!editable())return;try{apply.run();dialog.dismiss();}catch(Exception e){Toast.makeText(this,e.getMessage()==null?"Check the entered values.":e.getMessage(),Toast.LENGTH_LONG).show();}}));dialog.show();}
    private void numberDialog(String title,String value,TextAction action){LinearLayout form=form();EditText input=field(form,title,value);dialog(title,form,()->action.apply(input.getText().toString().trim()),null);}
    private static long parseMs(String value){double seconds=Double.parseDouble(value);if(!Double.isFinite(seconds)||Math.abs(seconds)>7_200_000)throw new IllegalArgumentException("Enter a valid time in seconds.");return Math.round(seconds*1000);}
    private static float parseFloat(EditText field){float value=Float.parseFloat(field.getText().toString());if(!Float.isFinite(value))throw new IllegalArgumentException("Enter a finite number.");return value;}
    private static String seconds(long ms){return String.format(Locale.US,"%.2f",ms/1000d);}
    private static String clock(long ms){return String.format(Locale.US,"%02d:%02d.%02d",ms/60000,(ms/1000)%60,(ms%1000)/10);}
}
