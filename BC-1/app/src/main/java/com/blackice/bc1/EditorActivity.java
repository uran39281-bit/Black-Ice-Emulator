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
import androidx.media3.exoplayer.ExoPlayer;
import com.blackice.bc1.editor.*;
import com.blackice.bc1.engine.EngineSettings;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Black Ice V1 workspace, now backed by a persistent, non-destructive video timeline. */
@androidx.annotation.OptIn(markerClass = {androidx.media3.common.util.UnstableApi.class, androidx.media3.common.util.ExperimentalApi.class})
public final class EditorActivity extends Activity {
    private static final int BG=0xFF080F15,PANEL=0xFF0D1721,CARD=0xFF13212F,CYAN=0xFF40BFFF,TEXT=0xFFE6F5FF,MUTED=0xFF91A7BC;
    public static final String PREFS="bc1-editor";
    private static final int IMPORT=701,MUSIC=702,OVERLAY=703,EXPORT=704;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private ProjectStore store;
    private EditProject project=new EditProject();
    private Player player;
    private PreviewProxy previewProxy;
    private SurfaceView surface;
    private FrameLayout monitor;
    private LinearLayout inspector,mediaBrowser;
    private ThumbnailCache thumbnails;
    private boolean uiLocked,previewPending,previewAutoplay;
    private TextView lockButton;
    private SeekBar previewScrubber;
    private boolean scrubbing;
    private static final int AUDIO_LAYER=110,BACKUP=111,RESTORE=112;
    private final java.util.Map<String,float[]> waveforms=new java.util.HashMap<>();
    private int inspectorWidth=300;
    private TextView status,time,play,projectLabel,exportButton,emptyPreview;
    private TimelineCanvas timeline;
    private HorizontalScrollView timelineScroll;
    private LayoutPolicy.Preference mode=LayoutPolicy.Preference.AUTO;
    private int selected=0,tab=0;
    private float pixelsPerSecond=48;
    private boolean active,importing,loading=true,snapping=true,exportLaunching,wasExporting;
    private long lastExportSeen;
    private String message="Opening workspace…";
    private final ArrayList<TextView> navTabs=new ArrayList<>();
    private final ArrayDeque<String> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
    private final Runnable autosave=()->saveNow();
    private final Runnable pulse=new Runnable(){@Override public void run(){
        if(!active)return;
        if(player!=null && player.isPlaying()) {project.playheadMs=Math.min(project.durationMs(),Math.max(0,player.getCurrentPosition()));syncPosition();}
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
        thumbnails=new ThumbnailCache(this);previewProxy=new PreviewProxy(this);uiLocked=getSharedPreferences(PREFS,MODE_PRIVATE).getBoolean("ui-locked",false);
        store=new ProjectStore(this);inspectorWidth=getSharedPreferences(PREFS,MODE_PRIVATE).getInt("panel-width",300);
        android.content.SharedPreferences exportState=getSharedPreferences(ExportService.PREFS,MODE_PRIVATE);
        if(exportState.getBoolean("running",false)&&!ExportService.isRunning())exportState.edit().putBoolean("running",false)
                .putBoolean("success",false).putString("status","Previous export was interrupted. Export again to create a complete MP4.").putLong("finishedAt",System.currentTimeMillis()).apply();
        try {mode=LayoutPolicy.Preference.valueOf(getSharedPreferences(PREFS,MODE_PRIVATE).getString("mode","AUTO"));}catch(Exception ignored){}
        if(state!=null){selected=state.getInt("selected",0);tab=state.getInt("tab",0);}
        buildWorkspace();
        String id=getSharedPreferences(PREFS,MODE_PRIVATE).getString("last-project",null);
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
    @Override protected void onDestroy(){main.removeCallbacks(autosave);io.shutdown();if(thumbnails!=null)thumbnails.close();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle state){state.putInt("selected",selected);state.putInt("tab",tab);saveNow();super.onSaveInstanceState(state);}
    @Override public void onConfigurationChanged(Configuration config){super.onConfigurationChanged(config);buildWorkspace();}
    private boolean exporting(){return getSharedPreferences(ExportService.PREFS,MODE_PRIVATE).getBoolean("running",false)||exportLaunching;}
    private boolean editable(){if(uiLocked){tell("UI locked • tap Unlock to edit");return false;}if(loading||importing){tell("Wait for the current import to finish.");return false;}if(exporting()){tell("Wait for export to finish, or cancel it to continue editing.");return false;}return true;}
    private void releasePreview(){
        previewPending=false;previewAutoplay=false;if(previewProxy!=null)previewProxy.cancel();
        if(player!=null){project.playheadMs=Math.min(project.durationMs(),Math.max(0,player.getCurrentPosition()));player.release();player=null;}
    }
    private void previewHint(String text){if(emptyPreview!=null){emptyPreview.setText(text);emptyPreview.setVisibility(View.VISIBLE);}}
    private void attachPreview(Player prepared){
        player=prepared;prepared.setVideoSurfaceView(surface);
        prepared.addListener(new Player.Listener(){
            @Override public void onRenderedFirstFrame(){if(player==prepared&&emptyPreview!=null)emptyPreview.setVisibility(View.GONE);}
            @Override public void onPlayerError(PlaybackException error){if(player!=prepared)return;prepared.pause();tell("Preview error: "+error.getMessage());previewHint("Preview unavailable • tap to retry");}
            @Override public void onPlaybackStateChanged(int state){if(state==Player.STATE_ENDED&&player==prepared){project.playheadMs=project.durationMs();syncPosition();}}
        });
    }
    private void preparePreview(boolean playWhenReady) {
        if(!active||loading||exporting())return;
        long position=project.playheadMs;releasePreview();project.playheadMs=position;
        if(project.clips.isEmpty()){syncPosition();return;}
        try {
            EditProject snapshot=ProjectStore.snapshot(project);
            previewHint("Preparing preview…");
            if(PreviewProxy.required(snapshot)){
                previewPending=true;previewAutoplay=playWhenReady;
                previewProxy.prepare(snapshot,new PreviewProxy.Listener(){
                    @Override public void onProgress(int percent){if(!active||!previewPending)return;previewHint((percent<0?"Preparing preview…":"Preparing preview • "+percent+"%")+"\nTap to cancel");}
                    @Override public void onReady(File file){
                        if(!active||!previewPending)return;previewPending=false;
                        ExoPlayer prepared=new ExoPlayer.Builder(EditorActivity.this).build();attachPreview(prepared);
                        prepared.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true);
                        prepared.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)),Math.min(project.playheadMs,Math.max(0,project.durationMs()-1)));
                        prepared.prepare();prepared.setPlayWhenReady(previewAutoplay);previewAutoplay=false;
                    }
                    @Override public void onError(String error){previewPending=false;previewAutoplay=false;tell("Preview preparation failed: "+error);previewHint("Preview unavailable • tap to retry");}
                });
            }else{
                CompositionPlayer prepared=new CompositionPlayer.Builder(this)
                        .setVideoGraphFactory(new MultipleInputVideoGraph.Factory(CompositionFactory.frameProcessor()))
                        .setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true).build();
                attachPreview(prepared);prepared.setComposition(CompositionFactory.build(this,snapshot,false),Math.min(position,Math.max(0,project.durationMs()-1)));
                prepared.prepare();prepared.setPlayWhenReady(playWhenReady);
            }
        }catch(Exception e){releasePreview();tell("Cannot prepare preview: "+e.getMessage());previewHint("Preview unavailable • tap to retry");}
    }
    private void remember(){try {undo.addLast(ProjectStore.encode(project));while(undo.size()>30)undo.removeFirst();redo.clear();}catch(Exception e){tell(e.getMessage());}}
    private void changed(String text) {
        project.validate();message=text;refreshProject();preparePreview(false);main.removeCallbacks(autosave);main.postDelayed(autosave,300);
    }
    private void saveNow() {
        main.removeCallbacks(autosave);if(loading)return;
        if(player!=null)project.playheadMs=Math.min(project.durationMs(),Math.max(0,player.getCurrentPosition()));
        EditProject snapshot=ProjectStore.snapshot(project);
        io.execute(()->{try{store.save(snapshot);main.post(()->getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString("last-project",snapshot.id).apply());}
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
        if(timeline!=null){timeline.getLayoutParams().height=dp(40+lanes().size()*52+56);timeline.requestLayout();timeline.invalidate();}
        if(monitor!=null)monitor.requestLayout();if(emptyPreview!=null)emptyPreview.setVisibility(project.clips.isEmpty()?View.VISIBLE:View.GONE);renderInspector();renderMediaBrowser();syncPosition();
    }
    private void syncPosition(){if(time!=null)time.setText(clock(project.playheadMs)+"  /  "+clock(project.durationMs()));if(timeline!=null)timeline.invalidate();if(previewScrubber!=null&&!scrubbing)previewScrubber.setProgress((int)(1000*project.playheadMs/Math.max(1,project.durationMs())));}
    private void seek(long ms){project.playheadMs=Math.max(0,Math.min(ms,project.durationMs()));if(player!=null){player.pause();player.seekTo(Math.min(project.playheadMs,Math.max(0,project.durationMs()-1)));}syncPosition();}
    private void togglePlay(){if(previewPending){previewAutoplay=!previewAutoplay;tell(previewAutoplay?"Playback will start when the preview is ready.":"Preview will stay paused when ready.");return;}if(player==null){if(project.clips.isEmpty())tell("Import a video or image first.");else preparePreview(true);return;}if(player.getPlayWhenReady())player.pause();else{if(player.getPlaybackState()==Player.STATE_ENDED||project.playheadMs>=project.durationMs())player.seekTo(0);player.play();}}

    private void buildWorkspace() {
        Configuration c=getResources().getConfiguration();android.view.Display display=getWindowManager().getDefaultDisplay();
        boolean wide=LayoutPolicy.resolve(mode,c.screenWidthDp,c.screenHeightDp,c.smallestScreenWidthDp,display!=null&&display.getDisplayId()!=0)==LayoutPolicy.Workspace.WIDE;
        navTabs.clear();mediaBrowser=null;previewScrubber=null;scrubbing=false;LinearLayout root=column();root.setBackgroundColor(BG);
        LinearLayout header=row();header.setPadding(dp(10),0,dp(8),0);header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.ic_black_ice);logo.setContentDescription("Black Ice mountain mark");header.addView(logo,new LinearLayout.LayoutParams(dp(36),dp(36)));
        LinearLayout brand=column();brand.setPadding(dp(5),0,dp(4),0);brand.addView(label(wide||c.screenWidthDp>=460?"Black Ice Editor":"Black Ice",wide?17:14,TEXT,true));
        projectLabel=label(project.name+"  ▾",11,TEXT,false);projectLabel.setMaxLines(1);brand.addView(projectLabel);brand.setOnClickListener(v->projectsMenu());brand.setContentDescription("Project menu");brand.setFocusable(true);brand.setMinimumHeight(dp(52));
        header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        if(wide&&c.screenWidthDp>=1100){header.addView(iconButton("Undo",v->undo(false)));header.addView(iconButton("Redo",v->undo(true)));header.addView(iconButton("Save",v->{saveNow();tell("Saving project…");}));}
        lockButton=button(uiLocked?"Unlock":"Lock",v->toggleLock());lockButton.setContentDescription("UI Lock");header.addView(lockButton);
        header.addView(button(mode.name(),v->chooseMode()));exportButton=button("Export",v->exportMenu());header.addView(exportButton);
        root.addView(header,new LinearLayout.LayoutParams(-1,dp(56)));
        if(wide) {
            LinearLayout body=row();root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
            LinearLayout rail=column();rail.setPadding(dp(4),dp(6),dp(4),0);
            String[] names={"Media","Edit","Effects","Layers","Setup"};
            for(int i=0;i<names.length;i++){final int index=i;TextView b=iconButton(names[i],v->{tab=index;renderInspector();});navTabs.add(b);rail.addView(b,new LinearLayout.LayoutParams(-1,dp(c.screenHeightDp<550?52:64)));}
            body.addView(rail,new LinearLayout.LayoutParams(dp(76),-1));
            if(c.screenWidthDp>=1100){ScrollView library=new ScrollView(this);mediaBrowser=column();mediaBrowser.setPadding(dp(10),dp(10),dp(10),dp(10));library.addView(mediaBrowser);library.setBackgroundColor(PANEL);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(220),-1);lp.rightMargin=dp(6);body.addView(library,lp);renderMediaBrowser();}
            LinearLayout center=column();center.setPadding(dp(4),dp(4),dp(6),dp(4));body.addView(center,new LinearLayout.LayoutParams(0,-1,1));
            center.addView(buildMonitor(),new LinearLayout.LayoutParams(-1,0,1));center.addView(transport());if(c.screenHeightDp>=550)center.addView(quickTools());center.addView(buildTimeline(),new LinearLayout.LayoutParams(-1,dp(c.screenHeightDp<550?140:Math.min(300,c.screenHeightDp*.28f))));
            ScrollView tools=new ScrollView(this);tools.setFillViewport(false);inspector=column();inspector.setPadding(dp(10),dp(10),dp(10),dp(12));tools.addView(inspector);tools.setBackgroundColor(PANEL);
            View divider=new View(this);divider.setBackgroundColor(0xFF284B60);divider.setContentDescription("Drag to resize tool panel");body.addView(divider,new LinearLayout.LayoutParams(dp(8),-1));
            body.addView(tools,new LinearLayout.LayoutParams(dp(Math.min(inspectorWidth,c.screenWidthDp<1000?250:500)),-1));
            divider.setOnTouchListener(new View.OnTouchListener(){float down;int initial;public boolean onTouch(View v,MotionEvent e){if(uiLocked)return true;if(e.getAction()==MotionEvent.ACTION_DOWN){down=e.getRawX();initial=tools.getWidth();return true;}if(e.getAction()==MotionEvent.ACTION_MOVE){int width=Math.max(dp(180),Math.min(body.getWidth()/2,initial+(int)((getSharedPreferences(PREFS,MODE_PRIVATE).getBoolean("panel-left",false)?-1:1)*(down-e.getRawX()))));tools.getLayoutParams().width=width;tools.requestLayout();inspectorWidth=Math.round(width/getResources().getDisplayMetrics().density);return true;}if(e.getAction()==MotionEvent.ACTION_UP)getSharedPreferences(PREFS,MODE_PRIVATE).edit().putInt("panel-width",inspectorWidth).apply();return true;}});
            if(getSharedPreferences(PREFS,MODE_PRIVATE).getBoolean("panel-left",false)){body.removeView(tools);body.removeView(divider);body.addView(tools,1);body.addView(divider,2);}
            body.setOnDragListener((v,event)->{if(uiLocked||event.getLocalState()!=tools)return false;if(event.getAction()==android.view.DragEvent.ACTION_DROP){boolean left=event.getX()<body.getWidth()/2f;body.removeView(tools);body.removeView(divider);if(left){body.addView(tools,1);body.addView(divider,2);}else{body.addView(divider);body.addView(tools);}getSharedPreferences(PREFS,MODE_PRIVATE).edit().putBoolean("panel-left",left).apply();return true;}return true;});
        } else {
            LinearLayout content=column();content.setPadding(dp(8),0,dp(8),0);
            boolean compact=c.screenHeightDp<700;
            if(compact){ScrollView all=new ScrollView(this);all.setFillViewport(true);all.addView(content);root.addView(all,new LinearLayout.LayoutParams(-1,0,1));}
            else root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
            content.addView(buildMonitor(),new LinearLayout.LayoutParams(-1,dp(c.screenHeightDp<780?170:210)));
            content.addView(transport());content.addView(buildTimeline(),new LinearLayout.LayoutParams(-1,dp(c.screenHeightDp>=780?220:176)));if(c.screenHeightDp>=780)content.addView(quickTools());
            inspector=column();inspector.setPadding(dp(8),dp(10),dp(8),dp(14));
            if(compact)content.addView(inspector);else{ScrollView tools=new ScrollView(this);tools.addView(inspector);content.addView(tools,new LinearLayout.LayoutParams(-1,0,1));}
            LinearLayout tabs=row();String[] names={"Media","Edit","Effects","Layers","Setup"};
            for(int i=0;i<names.length;i++){final int index=i;TextView b=iconButton(names[i],v->{tab=index;renderInspector();});navTabs.add(b);tabs.addView(b,new LinearLayout.LayoutParams(0,dp(62),1));}root.addView(tabs);
        }
        status=label(message,10,MUTED,false);status.setSingleLine(true);status.setPadding(dp(10),0,dp(10),dp(3));status.setOnClickListener(v->new AlertDialog.Builder(this).setMessage(status.getText()).setPositiveButton("OK",null).show());root.addView(status,new LinearLayout.LayoutParams(-1,dp(24)));
        root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets.consumeSystemWindowInsets();});
        setContentView(root);root.requestApplyInsets();if(player!=null)player.setVideoSurfaceView(surface);renderInspector();syncPosition();if(previewPending)previewHint("Preparing preview…\nTap to cancel");
    }
    private View buildMonitor() {
        if(surface==null){surface=new SurfaceView(this);surface.setContentDescription("Video project preview");}
        if(surface.getParent()!=null)((android.view.ViewGroup)surface.getParent()).removeView(surface);
        monitor=new FrameLayout(this){@Override protected void onMeasure(int wSpec,int hSpec){
            int w=View.MeasureSpec.getSize(wSpec),h=View.MeasureSpec.getSize(hSpec);float ratio=project.portrait?9f/16:16f/9;
            int sw=Math.min(w,(int)(h*ratio)),sh=(int)(sw/ratio);
            FrameLayout.LayoutParams params=(FrameLayout.LayoutParams)surface.getLayoutParams();
            if(params.width!=Math.max(1,sw)||params.height!=Math.max(1,sh)){params.width=Math.max(1,sw);params.height=Math.max(1,sh);surface.setLayoutParams(params);}super.onMeasure(wSpec,hSpec);
        }};
        monitor.setBackgroundColor(Color.BLACK);monitor.addView(surface,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
        emptyPreview=label("START YOUR FIRST CUT\n\n+ Import video or images",14,CYAN,true);emptyPreview.setGravity(Gravity.CENTER);emptyPreview.setContentDescription("Import video or images");emptyPreview.setFocusable(true);emptyPreview.setBackgroundColor(Color.BLACK);emptyPreview.setOnClickListener(v->{if(project.clips.isEmpty())pick(IMPORT,"*/*");else if(previewPending){releasePreview();previewHint("Preview cancelled • tap to prepare");}else preparePreview(false);});emptyPreview.setVisibility(project.clips.isEmpty()?View.VISIBLE:View.GONE);monitor.addView(emptyPreview,new FrameLayout.LayoutParams(-1,-1));
        return monitor;
    }
    private View transport() {
        LinearLayout controls=row();controls.setGravity(Gravity.CENTER_VERTICAL);
        play=button("▶  Play",v->togglePlay());controls.addView(play);time=label("",11,TEXT,false);time.setGravity(Gravity.CENTER);controls.addView(time,new LinearLayout.LayoutParams(0,dp(48),1));
        controls.addView(button("−",v->{pixelsPerSecond=Math.max(8,pixelsPerSecond/1.5f);timeline.requestLayout();}));
        controls.addView(button("+",v->{pixelsPerSecond=Math.min(240,pixelsPerSecond*1.5f);timeline.requestLayout();}));
        LinearLayout transport=column();
        if(getResources().getConfiguration().screenHeightDp>=550){
            previewScrubber=new SeekBar(this);previewScrubber.setMax(1000);previewScrubber.setContentDescription("Preview scrubber");
            previewScrubber.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                public void onProgressChanged(SeekBar slider,int position,boolean user){if(user&&time!=null)time.setText(clock(position*project.durationMs()/1000)+"  /  "+clock(project.durationMs()));}
                public void onStartTrackingTouch(SeekBar slider){scrubbing=true;if(player!=null)player.pause();}
                public void onStopTrackingTouch(SeekBar slider){scrubbing=false;seek(slider.getProgress()*project.durationMs()/1000);}
            });transport.addView(previewScrubber,new LinearLayout.LayoutParams(-1,dp(28)));
        }
        transport.addView(controls);return transport;
    }
    private View quickTools(){
        HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);scroll.setContentDescription("Editing shortcuts");scroll.setBackgroundColor(PANEL);
        LinearLayout bar=row();bar.setPadding(dp(2),dp(4),dp(2),dp(4));bar.setBackgroundColor(PANEL);
        String[] names={"Split","Trim","Duplicate","Opacity","Chroma Key","Extract Audio","Volume","Effects","Delete"};
        for(String name:names){TextView control=iconButton(name,v->{if(clip()==null){tell("Select a clip first.");return;}if(!editable())return;if(name.equals("Extract Audio")&&clip().image){tell("Images do not contain an audio track.");return;}
            switch(name){
                case "Split":onKeyDown(KeyEvent.KEYCODE_S,new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_S));break;
                case "Trim":trimDialog();break;
                case "Duplicate":duplicateSelected();break;
                case "Chroma Key":chromaDialog();break;
                case "Extract Audio":extractAudio();break;
                case "Opacity":clipPercent(false);break;
                case "Volume":clipPercent(true);break;
                case "Effects":tab=2;renderInspector();break;
                case "Delete":onKeyDown(KeyEvent.KEYCODE_FORWARD_DEL,new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_FORWARD_DEL));break;
            }
        });bar.addView(control,new LinearLayout.LayoutParams(dp(name.length()>10?80:66),dp(56)));}
        scroll.addView(bar);return scroll;
    }
    private void clipPercent(boolean volume){EditProject.Clip selectedClip=clip();if(selectedClip==null)return;numberDialog(volume?"Volume (%)":"Opacity (%)",String.valueOf(Math.round((volume?selectedClip.volume:selectedClip.opacity)*100)),value->{float percent=Float.parseFloat(value);if(!Float.isFinite(percent)||percent<0||percent>100)throw new IllegalArgumentException("Use a value from 0 to 100.");remember();if(volume)selectedClip.volume=percent/100;else selectedClip.opacity=percent/100;changed(volume?"Volume updated":"Opacity updated");});}
    private java.util.List<Integer> lanes(){java.util.List<Integer> list=new ArrayList<>();list.add(0);for(int i=1;i<8;i++){final int t=i;if(project.clips.stream().anyMatch(c->c.track==t))list.add(i);}return list;}
    private View buildTimeline(){ScrollView vertical=new ScrollView(this);timelineScroll=new HorizontalScrollView(this);timelineScroll.setFillViewport(true);timelineScroll.setContentDescription("Timeline scroll");timeline=new TimelineCanvas();timelineScroll.setOnScrollChangeListener((v,x,y,oldX,oldY)->timeline.invalidate());vertical.setOnScrollChangeListener((v,x,y,oldX,oldY)->timeline.invalidate());timelineScroll.addView(timeline,new HorizontalScrollView.LayoutParams(-2,dp(40+lanes().size()*52+56)));vertical.addView(timelineScroll);return vertical;}
    private EditProject.Clip clip(){return project.clips.isEmpty()?null:project.clips.get(selected);}
    private void renderInspector() {
        if(inspector==null)return;inspector.removeAllViews();
        for(int i=0;i<navTabs.size();i++){navTabs.get(i).setTextColor(i==tab?CYAN:TEXT);navTabs.get(i).setBackground(navBackground(i==tab));navTabs.get(i).setSelected(i==tab);for(android.graphics.drawable.Drawable icon:navTabs.get(i).getCompoundDrawables())if(icon!=null)icon.setTint(i==tab?CYAN:TEXT);}
        String[] headings={"MEDIA LIBRARY","CLIP EDITOR","GPU EFFECTS","OVERLAYS & MUSIC","PROJECT SETTINGS"};
        TextView heading=label(headings[tab],12,CYAN,true);inspector.addView(heading);heading.setContentDescription(headings[tab]+". Long press and drag to dock the tool panel in wide mode.");heading.setOnLongClickListener(v->{if(uiLocked)return false;if(inspector.getParent() instanceof ScrollView){v.startDragAndDrop(ClipData.newPlainText("panel","tools"),new View.DragShadowBuilder(v),inspector.getParent(),0);return true;}return false;});
        if(tab==0) {
            inspector.addView(note("Videos and images are copied into this app so saved projects can reopen reliably."));
            addAction("+ Import video / image",()->pick(IMPORT,"*/*"));
            addAction("+ Import music",()->pick(MUSIC,"audio/*"));addAction("+ Import independent audio layer",()->pick(AUDIO_LAYER,"audio/*"));
            for(int i=0;i<project.clips.size();i++){final int index=i;EditProject.Clip c=project.clips.get(i);
                addAction((i==selected?"●  ":"")+(i+1)+"  "+c.name+"  ·  "+clock(c.durationMs()),()->{selected=index;tab=1;seek(project.placements().get(index).startMs);renderInspector();});}
        } else if(tab==1) {
            EditProject.Clip c=clip();if(c==null){inspector.addView(note("Import a clip from Media to start editing."));return;}
            inspector.addView(note((selected+1)+" / "+project.clips.size()+"  •  "+c.name));
            inspector.addView(note("In "+seconds(c.inMs)+" s   Out "+seconds(c.outMs)+" s"));
            addAction("Trim / image duration",()->trimDialog());
            addAction("Layer / timeline start",()->placementDialog());
            addAction("Transform / keyframes",()->transformDialog());
            if(!c.image){addAction("Extract audio from video",()->extractAudio());addAction("Audio fades",()->fadeDialog());addAction("Load audio waveform",()->loadWaveform(c));}
            addAction("Split at playhead",()->{if(!editable())return;try{long position=project.playheadMs;remember();project.split(selected,position);changed("Clip split");}catch(Exception e){tell(e.getMessage());}});
            LinearLayout order=row();order.addView(button("← Earlier",v->moveClip(-1)),new LinearLayout.LayoutParams(0,dp(48),1));order.addView(button("Later →",v->moveClip(1)),new LinearLayout.LayoutParams(0,dp(48),1));inspector.addView(order);
            addAction("Duplicate clip",()->{if(!editable())return;if(project.clips.size()>=100){tell("This preview supports up to 100 clips.");return;}remember();EditProject.Clip copy=c.copy();if(copy.track>0)copy.startMs=project.placements().get(selected).endMs;project.clips.add(selected+1,copy);try{project.validate();}catch(Exception e){project.clips.remove(copy);tell(e.getMessage());return;}selected++;changed("Clip duplicated");});
            addAction("Remove clip",()->{if(!editable())return;remember();project.clips.remove(selected);changed("Clip removed • Undo is available");});
            slider("Clip volume",c.volume,value->{remember();c.volume=value;changed("Clip volume updated");});
            addAction("Undo",()->undo(false));addAction("Redo",()->undo(true));
        } else if(tab==2) {
            EditProject.Clip c=clip();if(c==null){inspector.addView(note("Select a clip to apply an effect."));return;}
            inspector.addView(note("Selected: "+c.name));
            addAction("Animate transform / effect",()->transformDialog());
            addAction("Chroma key / edge controls",()->chromaDialog());
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
            for(EditProject.Overlay o:project.overlays)addAction((o.imageUri==null?"Text: "+o.text:"Image overlay")+" · "+seconds(o.startMs)+"–"+seconds(o.endMs)+" s",()->new AlertDialog.Builder(this).setTitle("Overlay").setItems(new String[]{"Edit text / image","Animate keyframes"},(d,i)->{if(i==0)overlayDialog(o);else motionDialog(o.motion,Math.max(0,project.playheadMs-o.startMs),o.endMs-o.startMs,new float[]{o.x,o.y,o.scale,0,o.opacity,0},false);}).show());
            addAction(project.music==null?"+ Import music":"Music: "+project.music.name,()->{if(project.music==null)pick(MUSIC,"audio/*");else musicDialog();});
        } else {
            select("Project canvas",new String[]{"Landscape 16:9","Portrait 9:16"},project.portrait?1:0,index->{remember();project.portrait=index==1;changed("Project aspect updated");});
            select("Preview resolution (short edge)",new String[]{"360p • Performance","540p • Balanced","1080p • Quality"},project.previewHeight==360?0:project.previewHeight==540?1:2,index->{remember();project.previewHeight=new int[]{360,540,1080}[index];changed("Preview quality updated");});
            addAction("Workspace: "+mode.name(),()->chooseMode());
            addAction("Snap to clip edges: "+(snapping?"ON":"OFF"),()->{snapping=!snapping;renderInspector();});
            addAction("Save project",()->{saveNow();tell("Saving project…");});
            addAction("Export project + media bundle",()->{if(!editable())return;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/zip").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"Black-Ice-project.bc1zip");startActivityForResult(i,BACKUP);});
            addAction("Restore project bundle",()->pick(RESTORE,"*/*"));
            addAction("Projects / New / Rename",()->projectsMenu());
            addAction("Open v0.3 effects lab",()->startActivity(new Intent(this,WorkbenchActivity.class)));
            inspector.addView(note("BC-1 v1.0.1\nExport: H.264 MP4 + AAC, 1080p, up to 30 fps. Lower-rate sources keep their cadence.\nSpace: play • Ctrl+Z/Y: undo/redo • Ctrl+S: save\nS: split • arrows: frame step • Ctrl+D: duplicate\nCtrl+wheel: zoom • Shift+wheel: scroll • right-click: clip menu\n3D Page Curl remains excluded."));
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
    private void chooseMode(){if(uiLocked){tell("Unlock to change workspace layout");return;}new AlertDialog.Builder(this).setTitle("Workspace layout").setSingleChoiceItems(new String[]{"Auto • phone / tablet / DeX","Phone portrait workspace","Wide tablet / DeX workspace"},mode.ordinal(),(d,i)->{mode=LayoutPolicy.Preference.values()[i];getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString("mode",mode.name()).apply();d.dismiss();buildWorkspace();}).show();}
    private void pick(int request,String type) {
        if(!editable())return;if(request==OVERLAY&&project.overlays.size()>=20){tell("This preview supports up to 20 overlays.");return;}Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType(type).addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if(request==IMPORT){intent.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"video/*","image/*"});intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);}
        try{startActivityForResult(intent,request);}catch(ActivityNotFoundException e){tell("No document picker is installed.");}
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null)return;
        if(request==BACKUP||request==RESTORE){if(data.getData()!=null)bundle(request,data.getData());return;}
        if(request==EXPORT){if(data.getData()!=null){
            try{getContentResolver().takePersistableUriPermission(data.getData(),Intent.FLAG_GRANT_WRITE_URI_PERMISSION);}catch(SecurityException ignored){/* Some providers grant access for the current task only. */}
            startExport(data.getData());}return;}
        if(request!=IMPORT&&request!=MUSIC&&request!=OVERLAY&&request!=AUDIO_LAYER)return;
        java.util.List<Uri> uris=new ArrayList<>();if(data.getClipData()!=null){for(int i=0;i<data.getClipData().getItemCount();i++)uris.add(data.getClipData().getItemAt(i).getUri());}else if(data.getData()!=null)uris.add(data.getData());
        if(uris.isEmpty())return;importing=true;tell("Importing media copies…");
        io.execute(()->{
            java.util.List<EditProject.Clip> imported=new ArrayList<>();java.util.List<String> errors=new ArrayList<>();
            for(Uri uri:uris)try{imported.add(new MediaImporter(this).importClip(uri,(request==MUSIC||request==AUDIO_LAYER),request==OVERLAY?1024:2048));}catch(Exception e){errors.add(e.getMessage());}
            main.post(()->{importing=false;if(isDestroyed())return;remember();
                for(EditProject.Clip c:imported){
                    if(request==AUDIO_LAYER){try{if(project.clips.size()>=100)throw new IllegalArgumentException("The 100-clip limit was reached");c.track=project.freeAudioTrack(project.playheadMs,c.durationMs());c.startMs=project.playheadMs;project.clips.add(c);selected=project.clips.size()-1;tab=1;}catch(Exception e){errors.add(e.getMessage());}}
                    else if(request==MUSIC){EditProject.Music m=new EditProject.Music();m.uri=c.uri;m.name=c.name;m.sourceDurationMs=c.sourceDurationMs;m.outMs=c.outMs;project.music=m;tab=3;}
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
        if(loading||importing)return;if(project.clips.isEmpty()){tell("Import at least one video or image before exporting.");return;}
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
    @Override public boolean onKeyDown(int key,KeyEvent event){
        if(getCurrentFocus() instanceof EditText)return super.onKeyDown(key,event);
        if(event.isCtrlPressed()&&key==KeyEvent.KEYCODE_S){saveNow();return true;}
        if(event.isCtrlPressed()&&(key==KeyEvent.KEYCODE_Z||key==KeyEvent.KEYCODE_Y)){undo(key==KeyEvent.KEYCODE_Y||event.isShiftPressed());return true;}
        if(key==KeyEvent.KEYCODE_SPACE&&event.getRepeatCount()==0){togglePlay();return true;}
        if(key==KeyEvent.KEYCODE_D&&event.isCtrlPressed()){duplicateSelected();return true;}
        if(key==KeyEvent.KEYCODE_S&&!event.isCtrlPressed()){if(editable()&&clip()!=null)try{remember();project.split(selected,project.playheadMs);changed("Clip split");}catch(Exception e){tell(e.getMessage());}return true;}
        if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT){seek(project.playheadMs+(key==KeyEvent.KEYCODE_DPAD_LEFT?-1:1)*(event.isShiftPressed()?1000:33));return true;}
        if(key==KeyEvent.KEYCODE_FORWARD_DEL){if(editable()&&clip()!=null){remember();project.clips.remove(selected);changed("Clip removed");}return true;}
        return super.onKeyDown(key,event);
    }
    private void duplicateSelected(){EditProject.Clip c=clip();if(c==null||!editable())return;if(project.clips.size()>=100){tell("The 100-clip limit was reached");return;}EditProject.Clip copy=c.copy();if(c.track>0)copy.startMs=project.placements().get(selected).endMs;remember();project.clips.add(selected+1,copy);try{project.validate();selected++;changed("Clip duplicated");}catch(Exception e){project.clips.remove(copy);tell(e.getMessage());}}
    private void placementDialog(){EditProject.Clip c=clip();if(c==null||!editable())return;LinearLayout f=form();EditText track=field(f,"0: main sequence · 1–3: video layers · 4–7: audio",String.valueOf(c.track)),start=field(f,"Timeline start (seconds; independent layers)",seconds(project.placements().get(selected).startMs));dialog("Layer placement",f,()->{int t=Integer.parseInt(track.getText().toString());long ms=parseMs(start.getText().toString());remember();project.setPlacement(selected,t,ms);changed("Layer placement updated");},null);}
    private void fadeDialog(){EditProject.Clip c=clip();LinearLayout f=form();EditText in=field(f,"Fade in (seconds)",seconds(c.fadeInMs)),out=field(f,"Fade out (seconds)",seconds(c.fadeOutMs));dialog("Audio fades",f,()->{long a=parseMs(in.getText().toString()),b=parseMs(out.getText().toString());if(a<0||b<0||a>c.durationMs()||b>c.durationMs())throw new IllegalArgumentException("Fades must fit inside the clip");remember();c.fadeInMs=a;c.fadeOutMs=b;changed("Audio fades updated");},null);}
    private void extractAudio(){EditProject.Clip source=clip();if(source==null||source.image||!editable())return;
        CheckBox mute=new CheckBox(this);mute.setText("Mute original video");mute.setTextColor(TEXT);mute.setChecked(true);
        new AlertDialog.Builder(this).setTitle("Extract independent audio").setView(mute).setPositiveButton("Extract",(d,w)->{long start=project.placements().get(selected).startMs;importing=true;io.execute(()->{try{if(!AudioWaveform.hasAudio(this,source.uri))throw new IllegalArgumentException("This source has no audio track");main.post(()->{importing=false;if(isDestroyed())return;try{if(project.clips.size()>=100)throw new IllegalArgumentException("The 100-clip limit was reached");EditProject.Clip audio=source.copy();audio.name=source.name+" • audio";audio.track=project.freeAudioTrack(start,audio.durationMs());audio.startMs=start;audio.transition=-1;remember();project.clips.add(audio);if(mute.isChecked())source.volume=0;selected=project.clips.size()-1;changed("Audio extracted independently");loadWaveform(audio);}catch(Exception e){tell(e.getMessage());}});}catch(Exception e){main.post(()->{importing=false;tell(e.getMessage());});}});}).setNegativeButton("Cancel",null).show();
    }
    private void loadWaveform(EditProject.Clip c){if(waveforms.containsKey(c.uri)){tell("Waveform ready");return;}tell("Decoding audio waveform…");io.execute(()->{try{float[] peaks=AudioWaveform.read(this,c.uri,c.sourceDurationMs);main.post(()->{waveforms.put(c.uri,peaks);if(timeline!=null)timeline.invalidate();tell("Waveform ready");});}catch(Exception e){main.post(()->tell("Waveform: "+e.getMessage()));}});}
    private void bundle(int request,Uri uri){if(!editable())return;saveNow();EditProject snapshot=ProjectStore.snapshot(project);importing=true;tell(request==BACKUP?"Saving project and media…":"Restoring project and media…");io.execute(()->{try{if(request==BACKUP){ProjectBundle.write(this,snapshot,getContentResolver().openOutputStream(uri,"wt"));main.post(()->{importing=false;tell("Project bundle saved");});}else{EditProject restored=ProjectBundle.read(this,getContentResolver().openInputStream(uri));main.post(()->{importing=false;if(isDestroyed())return;releasePreview();project=restored;undo.clear();redo.clear();selected=0;changed("Project restored as a new copy");});}}catch(Exception e){if(request==BACKUP)try{android.provider.DocumentsContract.deleteDocument(getContentResolver(),uri);}catch(Exception ignored){}main.post(()->{importing=false;tell("Bundle failed: "+e.getMessage());});}});}
    private void chromaDialog(){EditProject.Clip c=clip();if(c==null||!editable())return;LinearLayout f=form();CheckBox enabled=new CheckBox(this);enabled.setText("Enable chroma key");enabled.setTextColor(TEXT);enabled.setChecked(c.chroma);f.addView(enabled);EditText color=field(f,"Key color (#RRGGBB)",String.format(java.util.Locale.ROOT,"#%06X",c.keyColor&0xFFFFFF)),t=field(f,"Tolerance 0–1",String.valueOf(c.keyTolerance)),soft=field(f,"Edge softness 0.001–1",String.valueOf(c.keySoftness)),spill=field(f,"Spill removal 0–1",String.valueOf(c.keySpill));dialog("Chroma key",f,()->{int rgb=Color.parseColor(color.getText().toString());float a=parseFloat(t),b=parseFloat(soft),z=parseFloat(spill);if(a<0||a>1||b<.001f||b>1||z<0||z>1)throw new IllegalArgumentException("Check key limits");remember();c.chroma=enabled.isChecked();c.keyColor=rgb;c.keyTolerance=a;c.keySoftness=b;c.keySpill=z;changed("Chroma key updated");},null);}
    private void transformDialog(){EditProject.Clip c=clip();if(c==null||!editable())return;new AlertDialog.Builder(this).setTitle("Transform & motion").setItems(new String[]{"Static transform","Keyframes / curve editor"},(d,i)->{
        if(i==1){motionDialog(c.motion,Math.max(c.inMs,Math.min(c.outMs,project.playheadMs-project.placements().get(selected).startMs+c.inMs)),c.sourceDurationMs,new float[]{c.x,c.y,c.scale,c.rotation,c.opacity,c.intensity},true);return;}
        LinearLayout f=form();EditText x=field(f,"X: −2 to 2",String.valueOf(c.x)),y=field(f,"Y: −2 to 2",String.valueOf(c.y)),scale=field(f,"Scale: 0.05 to 4",String.valueOf(c.scale)),rotation=field(f,"Rotation (degrees)",String.valueOf(c.rotation));dialog("Transform",f,()->{float a=parseFloat(x),b=parseFloat(y),z=parseFloat(scale),r=parseFloat(rotation);if(!Motion.allowed(0,a)||!Motion.allowed(1,b)||!Motion.allowed(2,z)||!Motion.allowed(3,r))throw new IllegalArgumentException("Transform out of range");remember();c.x=a;c.y=b;c.scale=z;c.rotation=r;changed("Transform updated");},null);
    }).show();}
    private void motionDialog(Motion motion,long current,long limit,float[] defaults,boolean effects){String[] properties=effects?Motion.PROPERTIES:java.util.Arrays.copyOf(Motion.PROPERTIES,5);new AlertDialog.Builder(this).setTitle("Animate property").setItems(properties,(d,property)->{
        java.util.List<Motion.Key> keys=motion.curves.get(property);String[] choices=new String[keys.size()+1];choices[0]="+ Key at playhead";for(int n=0;n<keys.size();n++)choices[n+1]=seconds(keys.get(n).timeMs)+" s → "+keys.get(n).value;
        new AlertDialog.Builder(this).setTitle(properties[property]+" • source-time keys").setItems(choices,(dialog,index)->keyDialog(motion,property,index==0?new Motion.Key(current,motion.value(property,current,defaults[property])):keys.get(index-1).copy(),limit,index==0?null:keys.get(index-1))).show();
    }).show();}
    private void keyDialog(Motion motion,int property,Motion.Key key,long limit,Motion.Key existing){LinearLayout f=form();EditText time=field(f,"Source time (seconds)",seconds(key.timeMs)),value=field(f,Motion.PROPERTIES[property]+" value",String.valueOf(key.value));
        EditText x1=field(f,"Curve X1 (0–1)",String.valueOf(key.x1)),y1=field(f,"Curve Y1 (0–1)",String.valueOf(key.y1)),x2=field(f,"Curve X2 (0–1)",String.valueOf(key.x2)),y2=field(f,"Curve Y2 (0–1)",String.valueOf(key.y2));
        CurveView curve=new CurveView(key,()->{x1.setText(String.valueOf(key.x1));y1.setText(String.valueOf(key.y1));x2.setText(String.valueOf(key.x2));y2.setText(String.valueOf(key.y2));});f.addView(curve,new LinearLayout.LayoutParams(-1,dp(180)));
        LinearLayout presets=row();String[] names={"Linear","Ease in","Ease out","Smooth"};float[][] points={{0,0,1,1},{.42f,0,1,1},{0,0,.58f,1},{.42f,0,.58f,1}};for(int i=0;i<names.length;i++){final int n=i;presets.addView(button(names[i],v->{float[] a=points[n];key.x1=a[0];key.y1=a[1];key.x2=a[2];key.y2=a[3];x1.setText(""+a[0]);y1.setText(""+a[1]);x2.setText(""+a[2]);y2.setText(""+a[3]);curve.invalidate();}),new LinearLayout.LayoutParams(0,dp(48),1));}f.addView(presets);f.addView(note("Drag the blue handles. This curve controls the interval after this key."));
        dialog("Keyframe / curve",f,()->{long t=parseMs(time.getText().toString());float val=parseFloat(value);Motion.Key candidate=new Motion.Key(t,val);candidate.x1=parseFloat(x1);candidate.y1=parseFloat(y1);candidate.x2=parseFloat(x2);candidate.y2=parseFloat(y2);Motion check=motion.copy();if(existing!=null)check.curves.get(property).removeIf(k->k.timeMs==existing.timeMs);check.put(property,candidate);check.validate();if(t>limit)throw new IllegalArgumentException("Key must fit inside the source");remember();if(existing!=null)motion.curves.get(property).remove(existing);motion.put(property,candidate);changed("Keyframe saved");},existing==null?null:()->{remember();motion.curves.get(property).remove(existing);changed("Keyframe removed");});
    }
    private final class CurveView extends View{final Motion.Key key;final Runnable update;final Paint p=new Paint(3);int handle;CurveView(Motion.Key k,Runnable u){super(EditorActivity.this);key=k;update=u;setContentDescription("Cubic Bézier curve: drag either handle");}protected void onDraw(Canvas c){float w=getWidth()-dp(24),h=getHeight()-dp(24),m=dp(12);c.drawColor(PANEL);p.setColor(MUTED);p.setStrokeWidth(dp(1));c.drawLine(m,h+m,w+m,m,p);p.setColor(CYAN);p.setStrokeWidth(dp(2));float px=m,py=h+m;for(int i=1;i<=80;i++){float x=i/80f,y=Motion.ease(x,key);c.drawLine(px,py,m+x*w,m+(1-y)*h,p);px=m+x*w;py=m+(1-y)*h;}c.drawCircle(m+key.x1*w,m+(1-key.y1)*h,dp(6),p);c.drawCircle(m+key.x2*w,m+(1-key.y2)*h,dp(6),p);}public boolean onTouchEvent(MotionEvent e){float x=Math.max(0,Math.min(1,(e.getX()-dp(12))/(getWidth()-dp(24)))),y=Math.max(0,Math.min(1,1-(e.getY()-dp(12))/(getHeight()-dp(24))));if(e.getAction()==MotionEvent.ACTION_DOWN){handle=Math.hypot(x-key.x1,y-key.y1)<Math.hypot(x-key.x2,y-key.y2)?1:2;getParent().requestDisallowInterceptTouchEvent(true);}if(handle==1){key.x1=x;key.y1=y;}else{key.x2=x;key.y2=y;}update.run();invalidate();if(e.getAction()==MotionEvent.ACTION_UP){getParent().requestDisallowInterceptTouchEvent(false);performClick();}return true;}public boolean performClick(){super.performClick();return true;}}

    private final class TimelineCanvas extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Rect visibleBounds=new Rect();
        private final RectF thumbnailBounds=new RectF();
        private final Runnable thumbnailReady=()->{if(!isDestroyed())invalidate();};
        private float downX;private boolean dragging,gutterTouch;
        TimelineCanvas(){super(EditorActivity.this);setContentDescription("Timeline: tap a clip to select and seek; drag across the ruler to scrub. Horizontal scroll and plus/minus zoom.");setFocusable(true);}
        private float timelineScale(){return Math.min(dp(pixelsPerSecond),(dp(30000)-dp(94))/Math.max(1f,project.durationMs()/1000f));}
        private float x(long ms){return dp(64)+ms/1000f*timelineScale();}
        @Override protected void onMeasure(int w,int h){setMeasuredDimension(View.resolveSize(Math.max(dp(280),Math.min(dp(30000),(int)x(project.durationMs())+dp(30))),w),View.MeasureSpec.getSize(h));}
        @Override protected void onDraw(Canvas canvas){
            canvas.getClipBounds(visibleBounds);visibleBounds.left=timelineScroll.getScrollX();visibleBounds.right=Math.min(getWidth(),visibleBounds.left+timelineScroll.getWidth());if(timelineScroll.getParent() instanceof ScrollView){ScrollView viewport=(ScrollView)timelineScroll.getParent();visibleBounds.top=viewport.getScrollY();visibleBounds.bottom=Math.min(getHeight(),visibleBounds.top+viewport.getHeight());}canvas.drawColor(PANEL);int height=getHeight();float mainY=dp(35),clipH=dp(48);java.util.List<Integer> laneList=lanes();
            paint.setTextSize(dp(10));paint.setTypeface(Typeface.DEFAULT);paint.setColor(MUTED);
            long interval=1000;while(interval/1000f*timelineScale()<dp(55))interval*=2;
            for(long ms=0;ms<=project.durationMs();ms+=interval){float xx=x(ms);canvas.drawText(seconds(ms)+"s",xx,dp(16),paint);canvas.drawLine(xx,dp(21),xx,height,paint);}
            java.util.List<EditProject.Placement> placements=project.placements();
            for(EditProject.Placement p:placements){mainY=dp(35+laneList.indexOf(p.clip.track)*52);float left=x(p.startMs),right=x(p.endMs);if(right<=visibleBounds.left||left>=visibleBounds.right||mainY+clipH<=visibleBounds.top||mainY>=visibleBounds.bottom)continue;paint.setColor(p.index==selected?0xFF226D99:0xFF204057);canvas.drawRoundRect(left,mainY,right,mainY+clipH,dp(5),dp(5),paint);
                if(p.clip.track<4){Bitmap thumb=thumbnails.get(p.clip,thumbnailReady);if(thumb!=null){canvas.save();canvas.clipRect(left+dp(1),mainY+dp(1),right-dp(1),mainY+clipH-dp(1));float tw=clipH*thumb.getWidth()/thumb.getHeight();float first=left+Math.max(0,(float)Math.floor((visibleBounds.left-left)/tw))*tw;for(float tx=first;tx<Math.min(right,visibleBounds.right);tx+=tw){thumbnailBounds.set(tx,mainY,tx+tw,mainY+clipH);canvas.drawBitmap(thumb,null,thumbnailBounds,paint);}paint.setColor(0xAA08131F);canvas.drawRect(left,mainY+clipH-dp(21),right,mainY+clipH,paint);canvas.restore();}}
                paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(p.index==selected?2:1));paint.setColor(p.index==selected?CYAN:0xFF3B677C);canvas.drawRoundRect(left,mainY,right,mainY+clipH,dp(5),dp(5),paint);paint.setStyle(Paint.Style.FILL);
                canvas.save();canvas.clipRect(left+dp(4),mainY,right-dp(3),mainY+clipH);paint.setColor(TEXT);paint.setTextSize(dp(11));canvas.drawText(p.clip.name,left+dp(7),mainY+clipH-dp(6),paint);canvas.restore();
                float[] peaks=waveforms.get(p.clip.uri);if(peaks!=null){paint.setColor(0xFF74D3C1);for(float xx=Math.max(left,visibleBounds.left);xx<Math.min(right,visibleBounds.right);xx+=dp(3)){long source=p.clip.inMs+(long)((xx-left)/(right-left)*p.clip.durationMs());int bin=(int)Math.min(511,source*512/Math.max(1,p.clip.sourceDurationMs));float amplitude=peaks[Math.max(0,bin)]*dp(10);canvas.drawLine(xx,mainY+clipH/2-amplitude,xx,mainY+clipH/2+amplitude,paint);}}
                if(p.outgoingMs>0){paint.setColor(0x9940BFFF);canvas.drawRect(x(p.endMs-p.outgoingMs),mainY,right,mainY+clipH,paint);}
            }
            paint.setColor(MUTED);paint.setTextSize(dp(10));if(placements.isEmpty())canvas.drawText("IMPORT MEDIA TO BUILD YOUR TIMELINE",dp(64),mainY+dp(23),paint);
            float overlayY=dp(35+laneList.size()*52+4);
            for(EditProject.Overlay o:project.overlays){paint.setColor(0xFF72549B);canvas.drawRoundRect(x(o.startMs),overlayY,x(Math.min(o.endMs,project.durationMs())),overlayY+dp(22),dp(3),dp(3),paint);}
            if(project.overlays.isEmpty()){paint.setColor(MUTED);canvas.drawText("OVERLAYS",dp(12),overlayY+dp(15),paint);}
            float audioY=overlayY+dp(28);
            if(project.music!=null){paint.setColor(0xFF277A79);long end=project.music.loop?project.durationMs():Math.min(project.durationMs(),project.music.startMs+project.music.outMs-project.music.inMs);canvas.drawRoundRect(x(project.music.startMs),audioY,x(end),audioY+dp(22),dp(3),dp(3),paint);paint.setColor(TEXT);canvas.drawText("♫  "+project.music.name,x(project.music.startMs)+dp(5),audioY+dp(15),paint);}
            else{paint.setColor(MUTED);canvas.drawText("MUSIC",dp(12),audioY+dp(15),paint);}
            paint.setColor(CYAN);paint.setStrokeWidth(dp(2));canvas.drawLine(x(project.playheadMs),0,x(project.playheadMs),height,paint);
            // Track labels remain pinned while the clips scroll horizontally.
            float gutter=visibleBounds.left;paint.setColor(BG);canvas.drawRect(gutter,0,gutter+dp(56),height,paint);
            paint.setColor(MUTED);paint.setTextSize(dp(10));
            for(int row=0;row<laneList.size();row++){int lane=laneList.get(row);float y=dp(35+row*52);canvas.drawText(lane<4?"Video":"Audio",gutter+dp(5),y+dp(18),paint);canvas.drawText(lane<4?"V"+lane:"A"+(lane-3),gutter+dp(5),y+dp(34),paint);}
            canvas.drawText("Overlay",gutter+dp(5),overlayY+dp(15),paint);canvas.drawText("Music",gutter+dp(5),audioY+dp(15),paint);
            paint.setColor(0xFF263A48);canvas.drawLine(gutter+dp(56),0,gutter+dp(56),height,paint);
        }
        @Override public boolean onGenericMotionEvent(MotionEvent event){if(event.getAction()==MotionEvent.ACTION_SCROLL){float wheel=event.getAxisValue(MotionEvent.AXIS_VSCROLL);if((event.getMetaState()&KeyEvent.META_CTRL_ON)!=0){pixelsPerSecond=Math.max(8,Math.min(240,pixelsPerSecond*(wheel>0?1.2f:.8333f)));requestLayout();invalidate();}else timelineScroll.smoothScrollBy((int)(-wheel*dp(70)),0);return true;}return super.onGenericMotionEvent(event);}
        @Override public boolean onTouchEvent(android.view.MotionEvent event){
            if(event.getAction()==MotionEvent.ACTION_DOWN){gutterTouch=event.getX()<visibleBounds.left+dp(56);if(gutterTouch)return true;}
            else if(gutterTouch){if(event.getAction()==MotionEvent.ACTION_UP||event.getAction()==MotionEvent.ACTION_CANCEL)gutterTouch=false;return true;}
            if(event.getAction()==MotionEvent.ACTION_DOWN&&event.isButtonPressed(MotionEvent.BUTTON_SECONDARY)){long target=(long)((event.getX()-dp(64))/timelineScale()*1000);int lane=(int)((event.getY()-dp(35))/dp(52));if(lane>=0&&lane<lanes().size())for(EditProject.Placement p:project.placements())if(p.clip.track==lanes().get(lane)&&target>=p.startMs&&target<p.endMs)selected=p.index;seek(target);new AlertDialog.Builder(EditorActivity.this).setTitle("Clip actions").setItems(new String[]{"Edit / trim","Layer placement","Keyframes","Duplicate"},(d,i)->{if(i==0)trimDialog();if(i==1)placementDialog();if(i==2)transformDialog();if(i==3)duplicateSelected();}).show();return true;}
            if(event.getAction()==MotionEvent.ACTION_DOWN){downX=event.getX();dragging=event.getY()<dp(32);if(dragging)getParent().requestDisallowInterceptTouchEvent(true);return true;}
            if(event.getAction()==MotionEvent.ACTION_MOVE&&dragging){long target=(long)((event.getX()-dp(64))/timelineScale()*1000);seek(snapping?project.snap(target,(long)(8000/pixelsPerSecond)):target);return true;}
            if(event.getAction()==MotionEvent.ACTION_UP){long target=(long)((event.getX()-dp(64))/timelineScale()*1000);
                if(Math.abs(event.getX()-downX)<dp(10)||dragging){
                    if(!dragging&&event.getY()>=dp(35)&&event.getY()<dp(35+lanes().size()*52)){java.util.List<EditProject.Placement> placements=project.placements();for(int i=placements.size()-1;i>=0;i--){EditProject.Placement p=placements.get(i);if(p.clip.track==lanes().get(Math.min(lanes().size()-1,(int)((event.getY()-dp(35))/dp(52))))&&target>=p.startMs&&target<p.endMs){selected=i;break;}}renderInspector();}
                    seek(snapping?project.snap(target,(long)(8000/pixelsPerSecond)):target);performClick();}
                dragging=false;getParent().requestDisallowInterceptTouchEvent(false);return true;}
            if(event.getAction()==MotionEvent.ACTION_CANCEL){dragging=false;getParent().requestDisallowInterceptTouchEvent(false);}return true;
        }
        @Override public boolean performClick(){super.performClick();return true;}
    }
    private void toggleLock(){uiLocked=!uiLocked;getSharedPreferences(PREFS,MODE_PRIVATE).edit().putBoolean("ui-locked",uiLocked).apply();lockButton.setText(uiLocked?"Unlock":"Lock");lockButton.setSelected(uiLocked);lockButton.setTextColor(uiLocked?CYAN:TEXT);tell(uiLocked?"UI locked • playback and seeking remain available":"UI unlocked");}
    private void renderMediaBrowser(){if(mediaBrowser==null)return;mediaBrowser.removeAllViews();mediaBrowser.addView(label("MEDIA LIBRARY",12,CYAN,true));mediaBrowser.addView(note(project.clips.size()+" clips • originals kept in project"));mediaBrowser.addView(button("+ Import media",v->pick(IMPORT,"*/*")));for(int i=0;i<project.clips.size();i++){final int index=i;EditProject.Clip clip=project.clips.get(i);LinearLayout card=column();card.setPadding(dp(6),dp(6),dp(6),dp(6));card.setBackground(buttonBackground(i==selected));if(clip.track<4){ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);card.addView(image,new LinearLayout.LayoutParams(-1,dp(96)));Bitmap thumb=thumbnails.get(clip,()->{if(!isDestroyed()&&mediaBrowser!=null){Bitmap loaded=thumbnails.peek(clip);if(loaded!=null)image.setImageBitmap(loaded);}});if(thumb!=null)image.setImageBitmap(thumb);}TextView name=label(clip.name,12,TEXT,true);name.setMaxLines(2);card.addView(name);card.addView(note((clip.track>=4?"A"+(clip.track-3):"V"+clip.track)+" • "+clock(clip.durationMs())));card.setFocusable(true);card.setContentDescription("Select "+clip.name);card.setOnClickListener(v->{selected=index;tab=1;seek(project.placements().get(index).startMs);renderInspector();renderMediaBrowser();});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(8);mediaBrowser.addView(card,lp);}}
    private void tell(String text){message=text==null?"Something went wrong.":text;if(status!=null)status.setText(message);}
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);return l;}
    private TextView label(String text,float size,int color,boolean bold){TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setTextColor(color);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    private TextView note(String text){TextView v=label(text,12,MUTED,false);v.setPadding(0,dp(7),0,dp(10));return v;}
    private TextView button(String title,View.OnClickListener listener){TextView b=label(title,12,TEXT,true);b.setGravity(Gravity.CENTER);b.setPadding(dp(12),dp(6),dp(12),dp(6));b.setMinWidth(dp(48));b.setMinHeight(dp(48));b.setMaxLines(2);b.setFocusable(true);b.setContentDescription(title);b.setBackground(buttonBackground(false));b.setOnClickListener(listener);return b;}
    private TextView iconButton(String name,View.OnClickListener action){TextView view=button(name,action);view.setTextSize(10);view.setTypeface(Typeface.DEFAULT);view.setPadding(dp(6),dp(3),dp(6),dp(3));V1Icon icon=new V1Icon(name,TEXT);icon.setBounds(0,0,dp(22),dp(22));view.setCompoundDrawables(null,icon,null,null);view.setCompoundDrawablePadding(dp(3));view.setBackground(navBackground(false));return view;}
    private GradientDrawable navBackground(boolean selected){GradientDrawable bg=new GradientDrawable();bg.setColor(selected?0xFF103047:BG);bg.setCornerRadius(dp(5));bg.setStroke(dp(1),selected?CYAN:0xFF172630);return bg;}
    private GradientDrawable buttonBackground(boolean selected){GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{selected?0xFF16486A:CARD,selected?0xFF102B42:PANEL});bg.setCornerRadius(dp(7));bg.setStroke(dp(1),selected?CYAN:0xFF294357);return bg;}
    private void addAction(String title,Runnable action){TextView b=button(title,v->action.run());LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.topMargin=dp(6);inspector.addView(b,params);}
    private interface IndexAction{void apply(int index);}
    private interface ValueAction{void apply(float value);}
    private interface TextAction{void apply(String text);}
    private void select(String label,String[] values,int current,IndexAction action){addAction(label+": "+values[current],()->new AlertDialog.Builder(this).setTitle(label).setSingleChoiceItems(values,current,(d,i)->{d.dismiss();if(editable())action.apply(i);}).show());}
    private void slider(String name,float value,ValueAction action){TextView title=note(name+"  "+Math.round(value*100)+"%");inspector.addView(title);SeekBar slider=new SeekBar(this);slider.setMax(100);slider.setProgress(Math.round(value*100));slider.setContentDescription(name);inspector.addView(slider,new LinearLayout.LayoutParams(-1,dp(48)));slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean user){title.setText(name+"  "+p+"%");}public void onStartTrackingTouch(SeekBar s){if(player!=null)player.pause();}public void onStopTrackingTouch(SeekBar s){if(editable())action.apply(s.getProgress()/100f);}});}
    private LinearLayout form(){LinearLayout l=column();l.setPadding(dp(18),dp(8),dp(18),dp(10));return l;}
    private EditText field(LinearLayout form,String title,String value){form.addView(note(title));EditText e=new EditText(this);e.setText(value);e.setTextColor(TEXT);e.setSingleLine(false);e.setMinHeight(dp(48));form.addView(e);return e;}
    private void dialog(String title,LinearLayout form,Runnable apply,Runnable delete){ScrollView scroll=new ScrollView(this);scroll.addView(form);AlertDialog.Builder b=new AlertDialog.Builder(this).setTitle(title).setView(scroll).setPositiveButton("Apply",null).setNegativeButton("Cancel",null);if(delete!=null)b.setNeutralButton("Remove",(d,w)->{if(editable())delete.run();});AlertDialog dialog=b.create();dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{if(!editable())return;try{apply.run();dialog.dismiss();}catch(Exception e){Toast.makeText(this,e.getMessage()==null?"Check the entered values.":e.getMessage(),Toast.LENGTH_LONG).show();}}));dialog.show();}
    private void numberDialog(String title,String value,TextAction action){LinearLayout form=form();EditText input=field(form,title,value);dialog(title,form,()->action.apply(input.getText().toString().trim()),null);}
    private static long parseMs(String value){double seconds=Double.parseDouble(value);if(!Double.isFinite(seconds)||Math.abs(seconds)>7_200_000)throw new IllegalArgumentException("Enter a valid time in seconds.");return Math.round(seconds*1000);}
    private static float parseFloat(EditText field){float value=Float.parseFloat(field.getText().toString());if(!Float.isFinite(value))throw new IllegalArgumentException("Enter a finite number.");return value;}
    private static String seconds(long ms){return String.format(Locale.US,"%.2f",ms/1000d);}
    private static String clock(long ms){return String.format(Locale.US,"%02d:%02d.%02d",ms/60000,(ms/1000)%60,(ms%1000)/10);}
}
