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
    static final int BG=0xFF080F15,PANEL=0xFF0D1721,CARD=0xFF13212F,CYAN=0xFF40BFFF,TEXT=0xFFE6F5FF,MUTED=0xFF91A7BC;
    public static final String PREFS="bc1-editor";
    static final int IMPORT=701,MUSIC=702,OVERLAY=703,EXPORT=704,REPLACE=705,MEDIA_ACCESS=113;
    static final String[] NAV={"Add","Edit","Effects","Layers"};
    final ToolPanels panels=new ToolPanels(this);
    final TransformPanel transformPanel=new TransformPanel(this);
    AddDrawer addDrawer;
    PresetStore presets;
    /** Multi-selection (includes the primary selection when non-empty). */
    final java.util.TreeSet<Integer> multiClips=new java.util.TreeSet<>(),multiOverlays=new java.util.TreeSet<>();
    boolean wideLayout;
    LinearLayout header,gridRow;
    String headerKey="";
    int gridCount;
    Attributes copiedAttributes;
    Keyframes.Clip copiedKeys;
    float previewMoveX=Float.NaN,previewMoveY=Float.NaN,previewZoom=1,previewPanX,previewPanY;
    String compareSnapshot;
    final Handler main=new Handler(Looper.getMainLooper());
    final ExecutorService io=Executors.newSingleThreadExecutor();
    ProjectStore store;
    EditProject project=new EditProject();
    Player player;
    PreviewProxy previewProxy;
    SurfaceView surface;
    FrameLayout monitor;
    LinearLayout inspector,mediaBrowser,mediaGrid,contextualRow;
    int contextualCount;
    TextView mediaCount;
    int mediaColumns=1;
    String mediaQuery="";
    MediaFilter.Kind mediaKind=MediaFilter.Kind.ALL;
    final ArrayList<TextView> mediaFilterButtons=new ArrayList<>();
    ThumbnailCache thumbnails;
    // Stable observers do not retain obsolete cards/canvases or exhaust the bounded listener list.
    final Runnable mediaThumbnailsReady=this::refreshMediaThumbnails;
    boolean uiLocked,previewPending,previewAutoplay;
    TextView lockButton;
    SeekBar previewScrubber;
    boolean scrubbing;
    static final int AUDIO_LAYER=110,BACKUP=111,RESTORE=112;
    final java.util.Map<String,float[]> waveforms=new java.util.HashMap<>();
    int inspectorWidth=300;
    TextView status,time,play,projectLabel,exportButton,emptyPreview;
    TimelineView timeline;
    final Runnable timelineThumbnailsReady=()->{if(!isDestroyed()&&timeline!=null)timeline.invalidate();};
    HorizontalScrollView timelineScroll;
    LayoutPolicy.Preference mode=LayoutPolicy.Preference.AUTO;
    int selected=-1,selectedOverlay=-1,tab=0,workspaceFocus=0;
    View previewControls;
    boolean comparing;
    TextView jumpStart,jumpEnd;
    float pixelsPerSecond=48;
    boolean active,importing,loading=true,snapping=true,exportLaunching,wasExporting;
    long lastExportSeen;
    String message="Opening workspace…";
    final ArrayList<TextView> navTabs=new ArrayList<>();
    final ArrayDeque<String> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
    final Runnable autosave=()->saveNow();
    final Runnable pulse=new Runnable(){@Override public void run(){
        if(!active)return;
        if(player!=null && player.isPlaying()) {project.playheadMs=Math.min(project.durationMs(),Math.max(0,player.getCurrentPosition()));
            // Preview-only loop range.
            if(project.loopInMs>=0&&project.playheadMs>=project.loopOutMs){player.seekTo(project.loopInMs);project.playheadMs=project.loopInMs;}
            syncPosition();}
        if(play!=null)play.setText(player!=null&&player.isPlaying()?"Ⅱ  Pause":"▶  Play");
        android.content.SharedPreferences export=getSharedPreferences(ExportService.PREFS,MODE_PRIVATE);
        boolean running=export.getBoolean("running",false);
        long completed=export.getLong("finishedAt",0);
        if(!running&&!exportLaunching&&!loading&&(wasExporting||completed>lastExportSeen)){lastExportSeen=completed;tell(export.getString("status","Export ended"));if(wasExporting)preparePreview(false);}
        wasExporting=running;
        if(exportButton!=null)exportButton.setText(running?"Cancel":"Export");
        if(status!=null)status.setText(running?export.getString("status","Exporting…"):message);
        main.postDelayed(this,player!=null&&player.isPlaying()?33:100);
    }};
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        thumbnails=new ThumbnailCache(this);previewProxy=new PreviewProxy(this);addDrawer=new AddDrawer(this);presets=new PresetStore(this);uiLocked=getSharedPreferences(PREFS,MODE_PRIVATE).getBoolean("ui-locked",false);
        store=new ProjectStore(this);inspectorWidth=getSharedPreferences(PREFS,MODE_PRIVATE).getInt("panel-width",300);
        android.content.SharedPreferences exportState=getSharedPreferences(ExportService.PREFS,MODE_PRIVATE);
        if(exportState.getBoolean("running",false)&&!ExportService.isRunning())exportState.edit().putBoolean("running",false)
                .putBoolean("success",false).putString("status","Previous export was interrupted. Export again to create a complete MP4.").putLong("finishedAt",System.currentTimeMillis()).apply();
        try {mode=LayoutPolicy.Preference.valueOf(getSharedPreferences(PREFS,MODE_PRIVATE).getString("mode","AUTO"));}catch(Exception ignored){}
        snapping=getSharedPreferences(PREFS,MODE_PRIVATE).getBoolean("snapping",true);
        workspaceFocus=getSharedPreferences(PREFS,MODE_PRIVATE).getInt("workspace-focus",0);
        if(state!=null){selected=state.getInt("selected",-1);selectedOverlay=state.getInt("selected-overlay",-1);pixelsPerSecond=state.getFloat("timeline-zoom",48);tab=state.getInt("tab",0);mediaQuery=state.getString("media-query","");try{mediaKind=MediaFilter.Kind.valueOf(state.getString("media-kind","ALL"));}catch(IllegalArgumentException ignored){}}
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
    @Override protected void onSaveInstanceState(Bundle state){state.putInt("selected",selected);state.putInt("selected-overlay",selectedOverlay);state.putFloat("timeline-zoom",pixelsPerSecond);state.putInt("tab",tab);state.putString("media-query",mediaQuery);state.putString("media-kind",mediaKind.name());saveNow();super.onSaveInstanceState(state);}
    @Override public void onConfigurationChanged(Configuration config){super.onConfigurationChanged(config);buildWorkspace();}
    boolean exporting(){return getSharedPreferences(ExportService.PREFS,MODE_PRIVATE).getBoolean("running",false)||exportLaunching;}
    boolean editable(){if(uiLocked){tell("UI locked • unlock editing in Settings");return false;}if(loading||importing){tell("Wait for the current import to finish.");return false;}if(exporting()){tell("Wait for export to finish, or cancel it to continue editing.");return false;}return true;}
    void releasePreview(){
        previewPending=false;previewAutoplay=false;if(previewProxy!=null)previewProxy.cancel();
        if(player!=null){project.playheadMs=Math.min(project.durationMs(),Math.max(0,player.getCurrentPosition()));player.release();player=null;}
    }
    void previewHint(String text){if(emptyPreview!=null){emptyPreview.setText(text);emptyPreview.setVisibility(View.VISIBLE);}}
    void attachPreview(Player prepared){
        player=prepared;prepared.setVideoSurfaceView(surface);
        prepared.addListener(new Player.Listener(){
            @Override public void onRenderedFirstFrame(){if(player==prepared&&emptyPreview!=null)emptyPreview.setVisibility(View.GONE);}
            @Override public void onPlayerError(PlaybackException error){if(player!=prepared)return;prepared.pause();tell("Preview error: "+error.getMessage());previewHint("Preview unavailable • tap to retry");}
            @Override public void onPlaybackStateChanged(int state){if(state==Player.STATE_ENDED&&player==prepared){project.playheadMs=project.durationMs();syncPosition();}}
        });
    }
    void preparePreview(boolean playWhenReady) {
        if(!active||loading||exporting())return;
        long position=project.playheadMs;releasePreview();project.playheadMs=position;
        if(project.clips.isEmpty()){syncPosition();return;}
        try {
            EditProject snapshot=ProjectStore.snapshot(project);if(comparing&&compareSnapshot!=null){snapshot=ProjectStore.decode(compareSnapshot);snapshot.playheadMs=project.playheadMs;}
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
    void remember(){try {undo.addLast(ProjectStore.encode(project));while(undo.size()>30)undo.removeFirst();redo.clear();}catch(Exception e){tell(e.getMessage());}}
    void changed(String text) {
        project.validate();message=text;refreshProject();preparePreview(false);main.removeCallbacks(autosave);main.postDelayed(autosave,300);
    }
    void saveNow() {
        main.removeCallbacks(autosave);if(loading)return;
        if(player!=null)project.playheadMs=Math.min(project.durationMs(),Math.max(0,player.getCurrentPosition()));
        EditProject snapshot=ProjectStore.snapshot(project);
        io.execute(()->{try{store.save(snapshot);main.post(()->getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString("last-project",snapshot.id).apply());}
            catch(Exception e){main.post(()->tell("Save failed: "+e.getMessage()));}});
    }
    void undo(boolean forward) {
        if(!editable())return;ArrayDeque<String> from=forward?redo:undo,to=forward?undo:redo;
        if(from.isEmpty()){tell(forward?"Nothing to redo.":"Nothing to undo.");return;}
        try {to.addLast(ProjectStore.encode(project));project=ProjectStore.decode(from.removeLast());changed(forward?"Edit restored":"Edit undone");}
        catch(Exception e){tell(e.getMessage());}
    }
    void refreshProject() {
        if(selected>=project.clips.size())selected=-1;if(selectedOverlay>=project.overlays.size())selectedOverlay=-1;
        if(projectLabel!=null)projectLabel.setText(project.name+"  ▾");
        if(timeline!=null){timeline.getLayoutParams().height=Math.round(timeline.contentHeight());timeline.requestLayout();timeline.invalidate();}
        for(EditProject.Clip clip:project.clips)if(clip.track>=4)loadWaveform(clip,true);
        if(monitor!=null)monitor.requestLayout();if(emptyPreview!=null)emptyPreview.setVisibility(project.clips.isEmpty()?View.VISIBLE:View.GONE);renderInspector();renderMediaBrowser();syncPosition();
    }
    void syncPosition(){if(previewControls!=null)previewControls.invalidate();if(time!=null)time.setText(clock(project.playheadMs)+"  /  "+clock(project.durationMs()));if(timeline!=null)timeline.syncScroll();if(previewScrubber!=null&&!scrubbing)previewScrubber.setProgress((int)(1000*project.playheadMs/Math.max(1,project.durationMs())));}
    void seek(long ms){project.playheadMs=Math.max(0,Math.min(ms,project.durationMs()));if(player!=null){player.pause();player.seekTo(Math.min(project.playheadMs,Math.max(0,project.durationMs()-1)));}syncPosition();}
    void togglePlay(){if(previewPending){previewAutoplay=!previewAutoplay;tell(previewAutoplay?"Playback will start when the preview is ready.":"Preview will stay paused when ready.");return;}if(player==null){if(project.clips.isEmpty())tell("Import a video or image first.");else preparePreview(true);return;}if(player.getPlayWhenReady())player.pause();else{if(player.getPlaybackState()==Player.STATE_ENDED||project.playheadMs>=project.durationMs())player.seekTo(0);player.play();}}

    void buildWorkspace() {
        // Layout switches keep selection, playhead and zoom (fields) and the layer list's vertical scroll.
        int layerScroll=timelineScroll!=null&&timelineScroll.getParent() instanceof ScrollView?((ScrollView)timelineScroll.getParent()).getScrollY():0;
        Configuration c=getResources().getConfiguration();android.view.Display display=getWindowManager().getDefaultDisplay();
        boolean wide=LayoutPolicy.resolve(mode,c.screenWidthDp,c.screenHeightDp,c.smallestScreenWidthDp,display!=null&&display.getDisplayId()!=0)==LayoutPolicy.Workspace.WIDE;
        navTabs.clear();mediaFilterButtons.clear();mediaBrowser=null;mediaGrid=null;mediaCount=null;previewScrubber=null;scrubbing=false;LinearLayout root=column();root.setBackgroundColor(BG);
        wideLayout=wide;header=row();header.setPadding(dp(10),0,dp(8),0);header.setGravity(Gravity.CENTER_VERTICAL);headerKey="";
        root.addView(header,new LinearLayout.LayoutParams(-1,dp(56)));renderHeader();
        if(wide) {
            LinearLayout body=row();root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
            LinearLayout rail=column();rail.setPadding(dp(4),dp(6),dp(4),0);
            String[] names=NAV;
            for(int i=0;i<names.length;i++){final int index=i;TextView b=iconButton(names[i],v->{tab=index;renderInspector();});navTabs.add(b);rail.addView(b,new LinearLayout.LayoutParams(-1,dp(c.screenHeightDp<550?52:64)));}
            body.addView(rail,new LinearLayout.LayoutParams(dp(76),-1));
            if(c.screenWidthDp>=1100){int width=Math.min(340,Math.max(220,Math.round(c.screenWidthDp*.20f)));mediaColumns=width>=260?2:1;ScrollView library=new ScrollView(this);mediaBrowser=column();mediaBrowser.setPadding(dp(10),dp(10),dp(10),dp(10));library.addView(mediaBrowser);library.setBackgroundColor(PANEL);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(width),-1);lp.rightMargin=dp(6);body.addView(library,lp);renderMediaBrowser();}
            LinearLayout center=column();center.setPadding(dp(4),dp(4),dp(6),dp(4));body.addView(center,new LinearLayout.LayoutParams(0,-1,1));
            center.addView(buildMonitor(),new LinearLayout.LayoutParams(-1,0,1));center.addView(transport(c.screenHeightDp<550));center.addView(buildTimeline(),new LinearLayout.LayoutParams(-1,dp(timelineHeight(c,true))));
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
            content.addView(buildMonitor(),new LinearLayout.LayoutParams(-1,dp(workspaceFocus==1?110:workspaceFocus==2?280:c.screenHeightDp<780?170:210)));
            content.addView(transport(false));content.addView(buildTimeline(),new LinearLayout.LayoutParams(-1,dp(timelineHeight(c,false))));
            inspector=column();inspector.setPadding(dp(8),dp(10),dp(8),dp(14));
            if(compact)content.addView(inspector);else{ScrollView tools=new ScrollView(this);tools.addView(inspector);content.addView(tools,new LinearLayout.LayoutParams(-1,0,1));}
            LinearLayout tabs=row();String[] names=NAV;
            for(int i=0;i<names.length;i++){final int index=i;TextView b=iconButton(names[i],v->{tab=index;renderInspector();});navTabs.add(b);tabs.addView(b,new LinearLayout.LayoutParams(0,dp(62),1));}root.addView(tabs);
        }
        status=label(message,10,MUTED,false);status.setSingleLine(true);status.setPadding(dp(10),0,dp(10),dp(3));status.setOnClickListener(v->new AlertDialog.Builder(this).setMessage(status.getText()).setPositiveButton("OK",null).show());root.addView(status,new LinearLayout.LayoutParams(-1,dp(24)));
        root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets.consumeSystemWindowInsets();});
        setContentView(root);root.requestApplyInsets();if(layerScroll>0&&timelineScroll!=null)timelineScroll.post(()->{if(timelineScroll.getParent() instanceof ScrollView)((ScrollView)timelineScroll.getParent()).scrollTo(0,layerScroll);});if(player!=null)player.setVideoSurfaceView(surface);renderInspector();syncPosition();if(previewPending)previewHint("Preparing preview…\nTap to cancel");
    }
    View buildMonitor() {
        if(surface==null){surface=new SurfaceView(this);surface.setContentDescription("Video project preview");}
        if(surface.getParent()!=null)((android.view.ViewGroup)surface.getParent()).removeView(surface);
        monitor=new FrameLayout(this){@Override protected void onMeasure(int wSpec,int hSpec){
            int w=View.MeasureSpec.getSize(wSpec),h=View.MeasureSpec.getSize(hSpec);float ratio=project.portrait?9f/16:16f/9;
            int fit=Math.min(w,(int)(h*ratio));
            // Navigation zoom / pan changes only the view of the frame, never a layer's scale or position.
            int sw=Math.round(fit*previewZoom),sh=(int)(sw/ratio),offX=Math.round(previewPanX*sw),offY=Math.round(previewPanY*sh);
            FrameLayout.LayoutParams params=(FrameLayout.LayoutParams)surface.getLayoutParams();
            if(params.width!=Math.max(1,sw)||params.height!=Math.max(1,sh)||params.leftMargin!=offX||params.topMargin!=offY){params.width=Math.max(1,sw);params.height=Math.max(1,sh);params.leftMargin=offX;params.topMargin=offY;params.rightMargin=0;params.bottomMargin=0;surface.setLayoutParams(params);}super.onMeasure(wSpec,hSpec);
        }};
        monitor.setBackgroundColor(Color.BLACK);monitor.addView(surface,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
        emptyPreview=label("START YOUR FIRST CUT\n\n+ Import video or images",14,CYAN,true);emptyPreview.setGravity(Gravity.CENTER);emptyPreview.setContentDescription("Import video or images");emptyPreview.setFocusable(true);emptyPreview.setBackgroundColor(Color.BLACK);emptyPreview.setOnClickListener(v->{if(project.clips.isEmpty())pick(IMPORT,"*/*");else if(previewPending){releasePreview();previewHint("Preview cancelled • tap to prepare");}else preparePreview(false);});emptyPreview.setVisibility(project.clips.isEmpty()?View.VISIBLE:View.GONE);monitor.addView(emptyPreview,new FrameLayout.LayoutParams(-1,-1));
        previewControls=new PreviewEditor(this);monitor.addView(previewControls,new FrameLayout.LayoutParams(-1,-1));
        TextView compare=button("Compare",null);compare.setTextSize(10);compare.setContentDescription("Hold to compare with the layer before the current adjustments");compare.setTooltipText(compare.getContentDescription());
        compare.setOnTouchListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_DOWN)compare(true);else if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL)compare(false);return true;});
        monitor.addView(compare,new FrameLayout.LayoutParams(-2,dp(40),Gravity.BOTTOM|Gravity.END));
        return monitor;
    }
    View transport(boolean singleRow) {
        LinearLayout controls=row();controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.addView(compactButton("↶","Undo",v->undo(false)),new LinearLayout.LayoutParams(0,dp(48),1));
        jumpStart=compactButton("‹","Jump to beginning",v->jump(false));controls.addView(jumpStart,new LinearLayout.LayoutParams(0,dp(48),1));
        play=button("▶  Play",v->togglePlay());controls.addView(play,new LinearLayout.LayoutParams(0,dp(48),1.5f));
        jumpEnd=compactButton("›","Jump to end",v->jump(true));controls.addView(jumpEnd,new LinearLayout.LayoutParams(0,dp(48),1));
        controls.addView(compactButton("↷","Redo",v->undo(true)),new LinearLayout.LayoutParams(0,dp(48),1));
        // Glyph-only transport buttons need a larger face to read as clear icons.
        for(int i=0;i<controls.getChildCount();i++)if(controls.getChildAt(i)!=play)((TextView)controls.getChildAt(i)).setTextSize(20);
        LinearLayout navigation=row();time=label("",11,TEXT,false);time.setGravity(Gravity.CENTER);navigation.addView(time,new LinearLayout.LayoutParams(0,dp(40),1));
        navigation.addView(compactButton("−","Zoom out timeline",v->zoomTimeline(1/1.5f,Float.NaN)));
        navigation.addView(compactButton("+","Zoom in timeline",v->zoomTimeline(1.5f,Float.NaN)));
        navigation.addView(compactButton("Fit","Fit entire project",v->fitTimeline()));
        navigation.addView(compactButton("Sel","Zoom to selection",v->zoomToSelection()));
        navigation.addView(compactButton("View","Workspace space",v->chooseSpace()));
        LinearLayout transport=column();
        if(getResources().getConfiguration().screenHeightDp>=780){
            previewScrubber=new SeekBar(this);previewScrubber.setMax(1000);previewScrubber.setContentDescription("Preview scrubber");
            previewScrubber.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                public void onProgressChanged(SeekBar slider,int position,boolean user){if(user&&time!=null)time.setText(clock(position*project.durationMs()/1000)+"  /  "+clock(project.durationMs()));}
                public void onStartTrackingTouch(SeekBar slider){scrubbing=true;if(player!=null)player.pause();}
                public void onStopTrackingTouch(SeekBar slider){scrubbing=false;seek(slider.getProgress()*project.durationMs()/1000);}
            });transport.addView(previewScrubber,new LinearLayout.LayoutParams(-1,dp(28)));
        }
        // Short wide windows keep playback and zoom on one row so the preview retains usable height.
        if(singleRow){time.setTextSize(10);time.setMaxLines(2);LinearLayout combined=row();combined.setGravity(Gravity.CENTER_VERTICAL);combined.addView(controls,new LinearLayout.LayoutParams(0,dp(48),3));combined.addView(navigation,new LinearLayout.LayoutParams(0,dp(48),2));transport.addView(combined);return transport;}
        transport.addView(controls);transport.addView(navigation);return transport;
    }
    void clipPercent(boolean volume){EditProject.Clip c=clip();if(c==null)return;percentPanel(volume?"Volume":"Opacity",volume?c.volume:c.opacity,volume?.7f:1f,value->{if(volume)c.volume=value;else c.opacity=value;});}
    void percentPanel(String title,float initial,float defaultValue,ValueAction action){
        if(!editable())return;LinearLayout f=form();EditText numeric=field(f,title+" (%)",String.valueOf(Math.round(initial*100)));numeric.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        SeekBar slider=new SeekBar(this);slider.setMax(100);slider.setProgress(Math.round(initial*100));slider.setContentDescription(title);f.addView(slider,new LinearLayout.LayoutParams(-1,dp(48)));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean user){if(user)numeric.setText(String.valueOf(p));}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});
        f.addView(button("Reset",v->{slider.setProgress(Math.round(defaultValue*100));numeric.setText(String.valueOf(Math.round(defaultValue*100)));}));
        dialog(title,f,()->{float value=parseFloat(numeric);if(value<0||value>100)throw new IllegalArgumentException("Use 0 to 100%.");edit(title+" updated",()->action.apply(value/100));},null);
    }
    void durationDialog(){EditProject.Clip c=clip();if(c==null||!editable())return;numberDialog("Duration (seconds)",seconds(c.durationMs()),value->edit("Duration updated",()->TimelineEdits.duration(project,selected,parseMs(value))));}
    void slipDialog(){EditProject.Clip c=clip();if(c==null||c.image||!editable())return;numberDialog("Slip: source in (seconds)",seconds(c.inMs),value->edit("Source segment slipped",()->TimelineEdits.slip(project,selected,parseMs(value))));}
    void effectsDialog(){EditProject.Clip c=clip();if(c==null||c.track>=4||!editable())return;new AlertDialog.Builder(this).setTitle("Effect").setSingleChoiceItems(EngineSettings.EFFECTS,c.effect,(d,i)->{d.dismiss();edit("Effect applied",()->c.effect=i);}).setNegativeButton("Back",null).show();}
    void splitSelected(){if(clip()!=null)edit("Clip split",()->project.split(selected,project.playheadMs));}
    /** Failed/no-op commands do not consume undo or discard redo. */
    void edit(String message,Runnable operation){
        if(!editable())return;String before;try{before=ProjectStore.encode(project);}catch(Exception e){tell(e.getMessage());return;}
        int selection=selected;
        try{operation.run();project.validate();if(!before.equals(ProjectStore.encode(project))){pushUndo(before);changed(message);}}
        catch(Exception e){try{project=ProjectStore.decode(before);selected=selection;}catch(Exception impossible){throw new IllegalStateException(impossible);}tell(e.getMessage());}
    }
    void pushUndo(String snapshot){undo.addLast(snapshot);while(undo.size()>30)undo.removeFirst();redo.clear();}
    TextView compactButton(String title,String description,View.OnClickListener listener){TextView b=button(title,listener);b.setPadding(dp(5),0,dp(5),0);b.setContentDescription(description);b.setTooltipText(description);return b;}
    void jump(boolean end){EditProject.Clip c=clip();EditProject.Overlay o=overlay();long target=o!=null?(end?o.endMs:o.startMs):c==null?(end?project.durationMs():0):(end?project.placements().get(selected).endMs:project.placements().get(selected).startMs);seek(target);}
    void updateJumpLabels(){String noun=clip()==null&&overlay()==null?"project":"selected clip";if(jumpStart!=null){jumpStart.setContentDescription("Jump to beginning of "+noun);jumpStart.setTooltipText(jumpStart.getContentDescription());jumpEnd.setContentDescription("Jump to end of "+noun);jumpEnd.setTooltipText(jumpEnd.getContentDescription());}}
    int timelineHeight(Configuration c,boolean wide){int normal=wide?(c.screenHeightDp<550?120:Math.min(280,Math.round(c.screenHeightDp*.28f))):(c.screenHeightDp>=780?200:160);return workspaceFocus==1?Math.min(normal+100,Math.round(c.screenHeightDp*.45f)):workspaceFocus==2?100:normal;}
    void chooseSpace(){new AlertDialog.Builder(this).setTitle("Workspace space").setSingleChoiceItems(new String[]{"Balanced","Expand timeline","Expand preview"},workspaceFocus,(d,i)->{if(uiLocked){tell("Unlock editing in Settings to change layout");return;}workspaceFocus=i;getSharedPreferences(PREFS,MODE_PRIVATE).edit().putInt("workspace-focus",i).apply();d.dismiss();buildWorkspace();}).setNegativeButton("Back",null).show();}
    /** Display scale only; media timing never changes. The playhead stays centred, so zoom never jumps. */
    void zoomTimeline(float factor,float anchor){
        if(timeline==null||!Float.isFinite(factor)||factor<=0)return;pixelsPerSecond=Math.max(8,Math.min(2400,pixelsPerSecond*factor));
        timeline.requestLayout();timeline.invalidate();timelineScroll.post(()->{if(timeline!=null)timeline.syncScroll();});
    }
    /** Fit Entire Project: the whole project is visible without moving the playhead. */
    void fitTimeline(){
        if(timeline==null)return;long reach=Math.max(project.playheadMs,project.durationMs()-project.playheadMs);float half=(timeline.viewport()-timeline.headerWidth())/2f-dp(16);
        if(reach<=0)return;pixelsPerSecond=Math.max(8,Math.min(2400,half/dp(1)/(reach/1000f)));timeline.requestLayout();timelineScroll.post(()->timeline.syncScroll());
    }
    /** Zoom to Selection: frames the selected layers around the playhead (moved to the selection start if outside). */
    void zoomToSelection(){
        if(timeline==null)return;long start=Long.MAX_VALUE,end=0;for(int i:selectedClips()){EditProject.Placement pl=project.placements().get(i);start=Math.min(start,pl.startMs);end=Math.max(end,pl.endMs);}
        for(int i:selectedOverlays()){EditProject.Overlay o=project.overlays.get(i);start=Math.min(start,o.startMs);end=Math.max(end,o.endMs);}
        if(start==Long.MAX_VALUE){tell("Select a layer to zoom to it.");return;}
        if(project.playheadMs<start||project.playheadMs>end)seek(start);long reach=Math.max(project.playheadMs-start,end-project.playheadMs);
        float half=(timeline.viewport()-timeline.headerWidth())/2f-dp(16);pixelsPerSecond=Math.max(8,Math.min(2400,half/dp(1)/(Math.max(100,reach)/1000f)));timeline.requestLayout();timelineScroll.post(()->timeline.syncScroll());
    }
    void toggleMarkerAt(long time){if(!editable())return;long tolerance=Math.max(1,Math.round(dp(6)*1000/timeline.timelineScale()));edit("Marker toggled",()->LayerOps.toggleMarker(project,Math.max(0,time),tolerance));}
    /** Row-handle reordering: moves one row to the target position, one swap at a time, in one undo step. */
    void reorderRows(int from,int to){
        java.util.List<int[]> rows=layerRows();if(from<0||to<0||from>=rows.size()||to>=rows.size())return;int[] row=rows.get(from),target=rows.get(to);
        if(row[0]!=target[0]||(row[0]==0&&((row[1]<4)!=(target[1]<4)||target[1]==0))){tell(row[0]==1?"Text, shape and image layers stay above video layers.":"Reorder within visual layers 1–3 or audio layers; the main sequence stays at the bottom.");return;}
        if(!editable())return;
        edit("Layer order changed",()->{
            if(row[0]==1){LayerOps.moveOverlay(project,row[1],target[1]);selectedOverlay=-1;}
            else if(row[1]<4){int step=target[1]>row[1]?1:-1;for(int t=row[1];t!=target[1];t+=step){if(project.tracks[t].locked||project.tracks[t+step].locked)throw new IllegalArgumentException("Unlock the layers to reorder them.");LayerOps.swapTracks(project,t,t+step);}}
            else{int step=target[1]>row[1]?1:-1;for(int t=row[1];t!=target[1];t+=step)LayerOps.swapTracks(project,t,t+step);}
        });
    }
    View buildTimeline(){ScrollView vertical=new ScrollView(this);timelineScroll=new HorizontalScrollView(this){@Override public boolean onInterceptTouchEvent(MotionEvent e){return false;}};timelineScroll.setFillViewport(true);timelineScroll.setContentDescription("Timeline scroll");timeline=new TimelineView(this);timelineScroll.setOnScrollChangeListener((v,x,y,oldX,oldY)->timeline.invalidate());vertical.setOnScrollChangeListener((v,x,y,oldX,oldY)->timeline.invalidate());timelineScroll.addView(timeline,new HorizontalScrollView.LayoutParams(-2,Math.round(timeline.contentHeight())));timelineScroll.setHorizontalScrollBarEnabled(false);timelineScroll.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(r-l!=or-ol)timelineScroll.post(()->{if(timeline!=null){timeline.requestLayout();timeline.syncScroll();}});});vertical.addView(timelineScroll);return vertical;}
    EditProject.Clip clip(){return selected<0||selected>=project.clips.size()?null:project.clips.get(selected);}
    void renderInspector() {
        if(inspector==null)return;if(selected>=0)selectedOverlay=-1;pruneSelection();renderHeader();updateMediaSelection();if(timeline!=null)timeline.invalidate();if(previewControls!=null)previewControls.invalidate();updateJumpLabels();inspector.removeAllViews();contextualRow=null;contextualCount=0;gridRow=null;gridCount=0;
        for(int i=0;i<navTabs.size();i++){navTabs.get(i).setTextColor(i==tab?CYAN:TEXT);navTabs.get(i).setBackground(navBackground(i==tab));navTabs.get(i).setSelected(i==tab);for(android.graphics.drawable.Drawable icon:navTabs.get(i).getCompoundDrawables())if(icon!=null)icon.setTint(i==tab?CYAN:TEXT);}
        String[] headings={"ADD","CLIP EDITOR","GPU EFFECTS","LAYERS","PROJECT SETTINGS"};
        TextView heading=label(headings[tab],12,CYAN,true);inspector.addView(heading);heading.setContentDescription(headings[tab]+". Long press and drag to dock the tool panel in wide mode.");heading.setOnLongClickListener(v->{if(uiLocked)return false;if(inspector.getParent() instanceof ScrollView){v.startDragAndDrop(ClipData.newPlainText("panel","tools"),new View.DragShadowBuilder(v),inspector.getParent(),0);return true;}return false;});
        if(tab==0)addDrawer.render(inspector);
        else if(tab==1||tab==2)panels.render(inspector);
        else if(tab==3)layersPanel();
        else settingsPanel();
    }
    /** Project settings open on demand from the top bar instead of occupying a permanent tab. */
    void settingsPanel(){
        inspector.addView(button("‹  Back to editing",v->{tab=selected>=0||selectedOverlay>=0?1:0;renderInspector();}));
            select("Project canvas",new String[]{"Landscape 16:9","Portrait 9:16"},project.portrait?1:0,index->{remember();project.portrait=index==1;changed("Project aspect updated");});
            select("Preview resolution (short edge)",new String[]{"360p • Performance","540p • Balanced","1080p • Quality"},project.previewHeight==360?0:project.previewHeight==540?1:2,index->{remember();project.previewHeight=new int[]{360,540,1080}[index];changed("Preview quality updated");});
            addAction("Workspace layout: "+new String[]{"Auto • phone / tablet / DeX","Phone portrait","Wide tablet / DeX"}[mode.ordinal()],()->chooseMode());
            select("Frame rate",new String[]{"24 fps","25 fps","30 fps"},project.frameRate==24?0:project.frameRate==25?1:2,index->edit("Frame rate updated",()->project.frameRate=new int[]{24,25,30}[index]));
            addAction("Lock editing and layout: "+(uiLocked?"ON":"OFF"),this::toggleLock);
            addAction("Preview / timeline space",this::chooseSpace);
            addAction("Snap to playhead, clip edges and markers: "+(snapping?"ON":"OFF"),()->{snapping=!snapping;getSharedPreferences(PREFS,MODE_PRIVATE).edit().putBoolean("snapping",snapping).apply();renderInspector();});
            addAction("Save project",()->{saveNow();tell("Saving project…");});
            addAction("Export project + media bundle",()->{if(!editable())return;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/zip").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"Black-Ice-project.bc1zip");startActivityForResult(i,BACKUP);});
            addAction("Restore project bundle",()->pick(RESTORE,"*/*"));
            addAction("Projects / New / Rename",()->projectsMenu());
            addAction("Export diagnostics",()->exportDiagnostics());
            addAction("Open v0.3 effects lab",()->startActivity(new Intent(this,WorkbenchActivity.class)));
            inspector.addView(note("BC-1 v2.0.0\nExport: H.264 MP4 + AAC, 1080p, up to 30 fps. Lower-rate sources keep their cadence.\nSpace: play • Ctrl+Z/Y: undo/redo • Ctrl+S: save\nS: split • arrows: frame step • Ctrl+D: duplicate\nCtrl+wheel: zoom • Shift+wheel: scroll • right-click: clip menu\n3D Page Curl remains excluded."));
    }
    /** Layer rows, topmost first: text/shape/image layers, video layers 3–1, the main sequence, then audio. */
    java.util.List<int[]> layerRows(){
        java.util.List<int[]> rows=new ArrayList<>();
        java.util.Set<String> shown=new java.util.HashSet<>();
        for(int i=project.overlays.size()-1;i>=0;i--){String g=project.overlays.get(i).group;if(g!=null&&collapsedGroups.contains(g)){if(!shown.add(g))continue;}rows.add(new int[]{1,i});}
        for(int t=3;t>=0;t--){final int track=t;if(t==0||project.clips.stream().anyMatch(c->c.track==track))rows.add(new int[]{0,t});}
        for(int t=4;t<=7;t++){final int track=t;if(project.clips.stream().anyMatch(c->c.track==track))rows.add(new int[]{0,t});}
        return rows;
    }
    String rowName(int[] row){if(row[0]==1){EditProject.Overlay o=project.overlays.get(row[1]);if(o.group!=null&&collapsedGroups.contains(o.group))return "Group ("+groupSize(o.group)+")";return o.label();}return project.tracks[row[1]].name;}
    /** Collapsed text/shape/image groups take one timeline row; members still move and transform together. */
    final java.util.Set<String> collapsedGroups=new java.util.HashSet<>();
    int groupSize(String group){int n=0;for(EditProject.Overlay o:project.overlays)if(group.equals(o.group))n++;for(EditProject.Clip c:project.clips)if(group.equals(c.group))n++;return n;}
    void toggleGroupCollapsed(String group){if(!collapsedGroups.remove(group))collapsedGroups.add(group);refreshProject();}
    void layersPanel(){
        inspector.addView(note("Higher rows render above lower rows; transparent areas reveal layers underneath. The eye hides a layer in preview and export; Solo isolates it in the preview only. Locks prevent moving, trimming, editing and deletion."));
        for(int[] r:layerRows()){
            boolean overlayRow=r[0]==1;EditProject.Overlay o=overlayRow?project.overlays.get(r[1]):null;EditProject.Track t=overlayRow?null:project.tracks[r[1]];
            boolean audio=!overlayRow&&r[1]>=4,hidden=overlayRow?o.hidden:audio?t.muted:t.hidden,solo=overlayRow?o.solo:t.solo,locked=overlayRow?o.locked:t.locked;
            LinearLayout row=row();row.setGravity(Gravity.CENTER_VERTICAL);row.setAlpha(hidden?.55f:1);row.setPadding(0,dp(2),0,dp(2));
            View swatch=new View(this);android.graphics.drawable.GradientDrawable sw=new android.graphics.drawable.GradientDrawable();sw.setColor(overlayRow?o.color:t.color);sw.setCornerRadius(dp(3));swatch.setBackground(sw);swatch.setContentDescription("Layer color: tap to change");swatch.setFocusable(true);
            swatch.setOnClickListener(v->layerColor(r));row.addView(swatch,new LinearLayout.LayoutParams(dp(10),dp(44)));
            TextView name=label(rowName(r)+(overlayRow?"":"  ·  "+(r[1]==0?"main":audio?"audio":"video")),12,TEXT,true);name.setPadding(dp(8),0,dp(4),0);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);name.setMinHeight(dp(48));name.setGravity(Gravity.CENTER_VERTICAL);
            name.setContentDescription("Layer "+rowName(r)+": tap to select, long-press to rename");name.setOnClickListener(v->{if(overlayRow)selectOverlay(r[1]);else{for(int i=0;i<project.clips.size();i++)if(project.clips.get(i).track==r[1]){selectClip(i,false);return;}}});
            name.setOnLongClickListener(v->{renameRow(r);return true;});
            if(overlayRow&&o.group!=null){boolean collapsed=collapsedGroups.contains(o.group);TextView fold=compactButton(collapsed?"▸":"▾",(collapsed?"Expand":"Collapse")+" group",v->toggleGroupCollapsed(o.group));row.addView(fold,new LinearLayout.LayoutParams(dp(40),dp(48)));}
            row.addView(name,new LinearLayout.LayoutParams(0,-2,1));
            row.addView(rowToggle(audio?"Mute":hidden?"Hidden":"Eye",(audio?(hidden?"Unmute ":"Mute "):(hidden?"Show ":"Hide "))+rowName(r),hidden,v->rowFlag(r,"hide")));
            row.addView(rowToggle("Solo",(solo?"Stop soloing ":"Solo ")+rowName(r),solo,v->rowFlag(r,"solo")));
            row.addView(rowToggle(locked?"Lock":"Unlock",(locked?"Unlock ":"Lock ")+rowName(r),locked,v->rowFlag(r,"lock")));
            row.addView(compactButton("▲","Move "+rowName(r)+" up",v->moveRow(r,true)),new LinearLayout.LayoutParams(dp(44),dp(48)));
            row.addView(compactButton("▼","Move "+rowName(r)+" down",v->moveRow(r,false)),new LinearLayout.LayoutParams(dp(44),dp(48)));
            inspector.addView(row);
        }
        if(project.music!=null)inspector.addView(note("Music: "+project.music.name+" (always below the timeline layers)"));
        inspector.addView(note("Drag the ≡ handle in the timeline's layer column to reorder without changing timing."));
    }
    TextView rowToggle(String icon,String description,boolean on,View.OnClickListener l){TextView b=compactButton("",description,l);b.setCompoundDrawables(icon(icon,on?CYAN:TEXT),null,null,null);b.setPadding(dp(11),0,dp(11),0);b.setSelected(on);b.setBackground(buttonBackground(on));b.setMinWidth(dp(44));return b;}
    void rowFlag(int[] r,String flag){
        if(!editable())return;
        edit(flag.equals("solo")?"Solo changed (preview only)":flag.equals("lock")?"Layer lock changed":"Layer visibility changed",()->{
            if(r[0]==1){EditProject.Overlay o=project.overlays.get(r[1]);if(flag.equals("solo"))o.solo=!o.solo;else if(flag.equals("lock"))o.locked=!o.locked;else o.hidden=!o.hidden;}
            else{EditProject.Track t=project.tracks[r[1]];if(flag.equals("solo"))t.solo=!t.solo;else if(flag.equals("lock"))t.locked=!t.locked;else if(r[1]>=4)t.muted=!t.muted;else t.hidden=!t.hidden;}});
    }
    void renameRow(int[] r){if(!editable())return;numberDialog("Layer name",rowName(r),name->{if(name.isBlank()||name.length()>60)throw new IllegalArgumentException("Use a name of 1 to 60 characters.");edit("Layer renamed",()->{if(r[0]==1)project.overlays.get(r[1]).name=name;else project.tracks[r[1]].name=name;});});}
    static final int[] LAYER_COLORS={0xFF40BFFF,0xFF9FEAFF,0xFF6BE675,0xFFFFC145,0xFFFF4D6D,0xFFB388FF,0xFF8CA3B8};
    void layerColor(int[] r){if(!editable())return;String[] names={"Ice blue","Frost","Green","Amber","Red","Violet","Slate"};
        new AlertDialog.Builder(this).setTitle("Layer color").setItems(names,(d,i)->edit("Layer color changed",()->{if(r[0]==1){EditProject.Overlay o=project.overlays.get(r[1]);if(o.shape>0||o.imageUri==null)tell("Text and shape layers use their own fill color; row color follows it.");}else project.tracks[r[1]].color=LAYER_COLORS[i];})).show();}
    /** Reorders rows without touching timing: visual layers 1–3 swap tracks, overlays change stacking order. */
    void moveRow(int[] r,boolean up){
        if(!editable())return;
        if(r[0]==1){int to=up?r[1]+1:r[1]-1;if(to<0||to>=project.overlays.size()){tell(up?"Already the top layer.":"Text, shape and image layers stay above video layers.");return;}
            if(project.overlays.get(r[1]).locked){tell("Layer locked • unlock it to reorder");return;}
            edit("Layer order changed",()->{LayerOps.moveOverlay(project,r[1],to);if(selectedOverlay==r[1])selectedOverlay=to;else if(selectedOverlay==to)selectedOverlay=r[1];});return;}
        int track=r[1];int target=track>=4?(up?track-1:track+1):(up?track+1:track-1);
        if(track==0||target<1||(track<4&&target>3)||(track>=4&&(target<4||target>7))){tell(track==0?"The main sequence stays at the bottom of the visual stack.":"No further layer in that direction.");return;}
        if(project.tracks[track].locked||project.tracks[target].locked){tell("Unlock both layers to reorder them.");return;}
        edit("Layer order changed",()->LayerOps.swapTracks(project,track,target));
    }
    // ---- Selection, top bar and contextual helpers --------------------------------------------------
    int multiCount(){return multiClips.size()+multiOverlays.size();}
    void pruneSelection(){multiClips.removeIf(i->i>=project.clips.size());multiOverlays.removeIf(i->i>=project.overlays.size());if(multiCount()==1){multiClips.clear();multiOverlays.clear();}}
    void deselect(){compareSnapshot=null;selected=-1;selectedOverlay=-1;multiClips.clear();multiOverlays.clear();panels.reset();renderInspector();}
    void baseline(){try{compareSnapshot=ProjectStore.encode(project);}catch(Exception e){compareSnapshot=null;}}
    void selectClip(int index,boolean seekToStart){if(index!=selected)baseline();selected=index;selectedOverlay=-1;multiClips.clear();multiOverlays.clear();tab=1;if(seekToStart)seek(project.placements().get(index).startMs);renderInspector();}
    void selectOverlay(int index){if(selected>=0||index!=selectedOverlay)baseline();selected=-1;selectedOverlay=index;multiClips.clear();multiOverlays.clear();tab=1;renderInspector();}
    /** Ctrl/Shift-click or long-press: add or remove a layer from the multi-selection; groups select together. */
    void toggleMulti(int clipIndex,int overlayIndex){
        if(multiCount()==0){if(selected>=0)multiClips.add(selected);if(selectedOverlay>=0)multiOverlays.add(selectedOverlay);}
        java.util.Set<Integer> set=clipIndex>=0?multiClips:multiOverlays;int index=clipIndex>=0?clipIndex:overlayIndex;
        if(!set.remove(index)){set.add(index);LayerOps.expandGroups(project,multiClips,multiOverlays);}
        if(clipIndex>=0){selected=multiClips.contains(clipIndex)?clipIndex:multiClips.isEmpty()?-1:multiClips.first();if(selected>=0)selectedOverlay=-1;}
        else if(selected<0)selectedOverlay=multiOverlays.contains(overlayIndex)?overlayIndex:multiOverlays.isEmpty()?-1:multiOverlays.first();
        if(multiCount()==1){selected=multiClips.isEmpty()?-1:multiClips.first();selectedOverlay=multiOverlays.isEmpty()?-1:multiOverlays.first();multiClips.clear();multiOverlays.clear();}
        tab=1;panels.reset();renderInspector();
    }
    java.util.List<Integer> selectedClips(){java.util.List<Integer> l=new ArrayList<>(multiClips);if(l.isEmpty()&&selected>=0)l.add(selected);return l;}
    java.util.List<Integer> selectedOverlays(){java.util.List<Integer> l=new ArrayList<>(multiOverlays);if(l.isEmpty()&&selectedOverlay>=0&&selected<0)l.add(selectedOverlay);return l;}
    /** Workspace lock (Settings) and per-layer locks are separate; both block edits. */
    boolean layerEditable(){if(!editable())return false;for(int i:selectedClips())if(LayerOps.locked(project,i)){tell("Layer locked • unlock it in Layers to edit");return false;}for(int i:selectedOverlays())if(project.overlays.get(i).locked){tell("Layer locked • unlock it in Layers to edit");return false;}return true;}
    void pausePlayback(){if(player!=null)player.pause();}
    /** Rebuilds the top bar: project / Settings / Export normally; layer name / Duplicate / Delete / More when selected. */
    void renderHeader(){
        if(header==null)return;EditProject.Clip c=clip();EditProject.Overlay o=overlay();int count=multiCount();
        String key=count>1?"multi"+count:c!=null?"clip"+selected+c.name:o!=null?"overlay"+selectedOverlay+o.label():"none"+project.name+wideLayout;
        if(key.equals(headerKey))return;headerKey=key;header.removeAllViews();Configuration cfg=getResources().getConfiguration();
        if(count>1||c!=null||o!=null){
            header.addView(compactButton("✕","Deselect",v->deselect()),new LinearLayout.LayoutParams(dp(48),dp(48)));
            TextView name=label(count>1?count+" layers selected":(c!=null?c.name:o.label()),14,TEXT,true);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);name.setPadding(dp(8),0,dp(4),0);
            name.setContentDescription(count>1?name.getText():"Selected layer "+name.getText()+". Tap to rename.");name.setMinHeight(dp(48));name.setGravity(Gravity.CENTER_VERTICAL);if(count<=1)name.setOnClickListener(v->renameSelection());
            header.addView(name,new LinearLayout.LayoutParams(0,-2,1));
            header.addView(headerIcon(count>1?"Group":"Duplicate",count>1?"Group selected layers":"Duplicate",v->{if(count>1)multiAction("Group");else duplicateSelected();}));
            header.addView(headerIcon("Delete","Delete",v->deleteSelection()));
            header.addView(headerIcon("More","More actions",v->moreMenu()));
            exportButton=null;projectLabel=null;return;
        }
        ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.ic_black_ice);logo.setContentDescription("Black Ice mountain mark");header.addView(logo,new LinearLayout.LayoutParams(dp(36),dp(36)));
        LinearLayout brand=column();brand.setPadding(dp(5),0,dp(4),0);brand.addView(label(wideLayout||cfg.screenWidthDp>=460?"Black Ice Editor":"Black Ice",wideLayout?17:14,TEXT,true));
        projectLabel=label(project.name+"  ▾",11,TEXT,false);projectLabel.setMaxLines(1);brand.addView(projectLabel);brand.setOnClickListener(v->projectsMenu());brand.setContentDescription("Project menu");brand.setFocusable(true);brand.setMinimumHeight(dp(52));
        header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        if(wideLayout&&cfg.screenWidthDp>=1100){header.addView(iconButton("Undo",v->undo(false)));header.addView(iconButton("Redo",v->undo(true)));header.addView(iconButton("Save",v->{saveNow();tell("Saving project…");}));}
        TextView add=button("+ Add",v->{tab=0;panels.reset();renderInspector();});add.setContentDescription("Add media, audio, text, shapes or saved elements");add.setTextColor(CYAN);header.addView(add);
        header.addView(button("Settings",v->{tab=4;renderInspector();}));exportButton=button("Export",v->exportMenu());header.addView(exportButton);
    }
    TextView headerIcon(String icon,String description,View.OnClickListener l){TextView b=compactButton("",description,l);b.setCompoundDrawables(icon(icon,TEXT),null,null,null);b.setPadding(dp(12),0,dp(12),0);b.setMinWidth(dp(48));return b;}
    android.graphics.drawable.Drawable icon(String name,int color){V1Icon icon=new V1Icon(name,color);icon.setBounds(0,0,dp(22),dp(22));return icon;}
    /** Two-column (three in wide mode) grid of equal tool buttons. */
    void gridAction(String title,Runnable action){int columns=wideLayout?3:2;if(gridRow==null||gridCount%columns==0){gridRow=row();LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.topMargin=dp(5);inspector.addView(gridRow,rp);}
        TextView b=button(title,v->action.run());b.setTextSize(11);LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(0,dp(54),1);cell.rightMargin=dp(4);gridRow.addView(b,cell);gridCount++;}
    void flushGrid(){if(gridRow!=null){int columns=wideLayout?3:2;while(gridCount%columns!=0){gridRow.addView(new View(this),new LinearLayout.LayoutParams(0,dp(54),1));gridCount++;}}gridRow=null;gridCount=0;}
    void setStatic(int property,float value){EditProject.Clip c=clip();EditProject.Overlay o=overlay();
        if(c!=null)switch(property){case Motion.X:c.x=value;break;case Motion.Y:c.y=value;break;case Motion.SCALE:c.scale=value;break;case Motion.ROTATION:c.rotation=value;break;case Motion.OPACITY:c.opacity=value;break;case Motion.INTENSITY:c.intensity=value;break;case Motion.SKEW_X:c.skewX=value;break;case Motion.SKEW_Y:c.skewY=value;break;case Motion.ANCHOR_X:c.anchorX=value;break;case Motion.ANCHOR_Y:c.anchorY=value;break;}
        else if(o!=null)switch(property){case Motion.X:o.x=value;break;case Motion.Y:o.y=value;break;case Motion.SCALE:o.scale=value;break;case Motion.ROTATION:o.rotation=value;break;case Motion.OPACITY:o.opacity=value;break;case Motion.SKEW_X:o.skewX=value;break;case Motion.SKEW_Y:o.skewY=value;break;case Motion.ANCHOR_X:o.anchorX=value;break;case Motion.ANCHOR_Y:o.anchorY=value;break;}
    }
    void renameSelection(){EditProject.Clip c=clip();EditProject.Overlay o=overlay();if((c==null&&o==null)||!layerEditable())return;
        numberDialog("Layer name",c!=null?c.name:o.label(),name->{if(name.isBlank()||name.length()>60)throw new IllegalArgumentException("Use a name of 1 to 60 characters.");edit("Layer renamed",()->{if(c!=null)c.name=name;else o.name=name;});});}
    void deleteSelection(){
        java.util.List<Integer> clips=selectedClips(),overlays=selectedOverlays();if(clips.isEmpty()&&overlays.isEmpty()||!layerEditable())return;
        edit((clips.size()+overlays.size())>1?"Layers deleted • Undo is available":"Layer deleted • Undo is available",()->{
            java.util.List<Integer> cs=new ArrayList<>(clips),os=new ArrayList<>(overlays);cs.sort(java.util.Collections.reverseOrder());os.sort(java.util.Collections.reverseOrder());
            for(int i:cs)project.clips.remove(i);for(int i:os)project.overlays.remove(i);selected=-1;selectedOverlay=-1;multiClips.clear();multiOverlays.clear();});
        panels.reset();
    }
    void moreMenu(){
        EditProject.Clip c=clip();EditProject.Overlay o=overlay();boolean multi=multiCount()>1;java.util.List<String> items=new ArrayList<>();
        if(multi)java.util.Collections.addAll(items,"Align starts","Align ends","Match duration","Close gaps on selected tracks","Group","Ungroup","Copy attributes…","Paste attributes…","Save as element","Duplicate","Delete");
        else{
            items.add("Rename");if(c!=null&&c.track<4||o!=null&&o.imageUri!=null)items.add("Replace media…");
            java.util.Collections.addAll(items,"Copy attributes…","Paste attributes…","Copy keyframes at playhead","Paste keyframes at playhead","Save as element","Duplicate Above","Duplicate After");
            if(c!=null)items.add("Move to layer / start time…");
            if(c!=null&&c.track>0||o!=null)items.add("Close gaps on this track");
            items.add(selectionLocked()?"Unlock layer":"Lock layer");items.add(selectionHidden()?"Show layer":(c!=null&&c.track>=4?"Mute layer":"Hide layer"));
            if(c==null||c.track<4)java.util.Collections.addAll(items,"Locate selected layer","Center on canvas");
            items.add("Select multiple…");items.add("Delete");
        }
        String[] names=items.toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle(multi?multiCount()+" layers":"More").setItems(names,(d,i)->moreAction(names[i])).setNegativeButton("Close",null).show();
    }
    boolean selectionLocked(){EditProject.Clip c=clip();EditProject.Overlay o=overlay();return c!=null?project.tracks[c.track].locked:o!=null&&o.locked;}
    boolean selectionHidden(){EditProject.Clip c=clip();EditProject.Overlay o=overlay();return c!=null?(c.track>=4?project.tracks[c.track].muted:project.tracks[c.track].hidden):o!=null&&o.hidden;}
    void moreAction(String name){
        EditProject.Clip c=clip();EditProject.Overlay o=overlay();
        switch(name){
            case "Rename":renameSelection();break;
            case "Replace media…":if(layerEditable())pick(REPLACE,c!=null?(c.image?"image/*":"video/*"):"image/*");break;
            case "Copy attributes…":copyAttributes();break;
            case "Paste attributes…":pasteAttributes();break;
            case "Copy keyframes at playhead":copyKeys();break;
            case "Paste keyframes at playhead":pasteKeys();break;
            case "Save as element":saveElement();break;
            case "Duplicate Above":duplicate(true);break;
            case "Duplicate After":case "Duplicate":duplicateSelected();break;
            case "Move to layer / start time…":placementDialog();break;
            case "Close gaps on this track":if(layerEditable()){if(c!=null)edit("Gaps closed",()->LayerOps.closeGaps(project,java.util.List.of(c.track)));else tell("Text, shape and image layers each have their own row.");}break;
            case "Lock layer":case "Unlock layer":toggleTrackFlag(c,o,"lock");break;
            case "Hide layer":case "Show layer":case "Mute layer":toggleTrackFlag(c,o,"hide");break;
            case "Locate selected layer":locateSelected();break;
            case "Center on canvas":if(layerEditable())edit("Centered on canvas",()->alignLayer(0,0,true,true));break;
            case "Select multiple…":tell("Long-press timeline clips (or Ctrl/Shift-click) to add them to the selection.");if(multiCount()==0){if(selected>=0)multiClips.add(selected);else if(selectedOverlay>=0)multiOverlays.add(selectedOverlay);}renderInspector();break;
            case "Delete":deleteSelection();break;
            default:multiAction(name);break;
        }
    }
    /** Layer-row flags are edits too (undoable), but are allowed on locked rows so they can be unlocked. */
    void toggleTrackFlag(EditProject.Clip c,EditProject.Overlay o,String flag){
        if(!editable())return;
        edit(flag.equals("lock")?"Layer lock changed":"Layer visibility changed",()->{
            if(c!=null){EditProject.Track t=project.tracks[c.track];if(flag.equals("lock"))t.locked=!t.locked;else if(c.track>=4)t.muted=!t.muted;else t.hidden=!t.hidden;}
            else if(o!=null){if(flag.equals("lock"))o.locked=!o.locked;else o.hidden=!o.hidden;}});
    }
    void multiAction(String name){
        java.util.List<Integer> clips=selectedClips(),overlays=selectedOverlays();if(!layerEditable())return;
        long reference=clips.isEmpty()?project.overlays.get(overlays.get(0)).startMs:project.placements().get(selected>=0?selected:clips.get(0)).startMs;
        long referenceEnd=clips.isEmpty()?project.overlays.get(overlays.get(0)).endMs:project.placements().get(selected>=0?selected:clips.get(0)).endMs;
        switch(name){
            case "Align starts":edit("Starts aligned",()->LayerOps.align(project,clips,overlays,false,reference));break;
            case "Align ends":edit("Ends aligned",()->LayerOps.align(project,clips,overlays,true,referenceEnd));break;
            case "Match duration":edit("Durations matched",()->{java.util.List<String> skipped=LayerOps.matchDuration(project,clips,overlays,referenceEnd-reference);if(!skipped.isEmpty())main.post(()->tell("Kept source limits for: "+String.join(", ",skipped)));});break;
            case "Close gaps":case "Close gaps on selected tracks":{java.util.Set<Integer> tracks=new java.util.TreeSet<>();for(int i:clips)tracks.add(project.clips.get(i).track);edit("Gaps closed on "+tracks.size()+" track(s)",()->LayerOps.closeGaps(project,tracks));break;}
            case "Group":edit("Layers grouped",()->LayerOps.group(project,clips,overlays));break;
            case "Ungroup":edit("Layers ungrouped",()->LayerOps.ungroup(project,clips,overlays));break;
            case "Save as element":saveElement();break;
            case "Copy attributes…":copyAttributes();break;
            case "Paste attributes…":pasteAttributes();break;
            case "Duplicate":edit("Layers duplicated",()->{java.util.List<Integer> cs=new ArrayList<>(clips);cs.sort(java.util.Collections.reverseOrder());for(int i:cs)LayerOps.duplicateAbove(project,i);java.util.List<Integer> os=new ArrayList<>(overlays);os.sort(java.util.Collections.reverseOrder());for(int i:os)LayerOps.duplicateOverlay(project,i,false);multiClips.clear();multiOverlays.clear();});break;
            case "Delete":deleteSelection();break;
        }
    }
    static final String[] ATTRIBUTE_NAMES=Attributes.NAMES;
    void copyAttributes(){
        EditProject.Clip c=clip();EditProject.Overlay o=overlay();if(c==null&&o==null){tell("Select a layer to copy from.");return;}
        boolean[] chosen={true,true,o!=null,false};
        new AlertDialog.Builder(this).setTitle("Copy attributes").setMultiChoiceItems(ATTRIBUTE_NAMES,chosen,(d,i,on)->chosen[i]=on).setPositiveButton("Copy",(d,w)->{
            int mask=0;for(int i=0;i<4;i++)if(chosen[i])mask|=1<<i;copiedAttributes=c!=null?Attributes.of(c,mask):Attributes.of(o,mask);tell("Copied: "+Attributes.names(copiedAttributes.categories));}).setNegativeButton("Cancel",null).show();
    }
    void pasteAttributes(){
        if(copiedAttributes==null){tell("Copy attributes from a layer first.");return;}if(!layerEditable())return;
        java.util.List<String> names=new ArrayList<>();java.util.List<Integer> masks=new ArrayList<>();for(int i=0;i<4;i++)if((copiedAttributes.categories&(1<<i))!=0){names.add(ATTRIBUTE_NAMES[i]);masks.add(1<<i);}
        boolean[] chosen=new boolean[names.size()];java.util.Arrays.fill(chosen,true);
        new AlertDialog.Builder(this).setTitle("Paste attributes").setMultiChoiceItems(names.toArray(new String[0]),chosen,(d,i,on)->chosen[i]=on).setPositiveButton("Paste",(d,w)->{
            int mask=0;for(int i=0;i<chosen.length;i++)if(chosen[i])mask|=masks.get(i);int requested=mask;
            edit("Attributes pasted",()->{int applied=0;for(int i:selectedClips())applied|=copiedAttributes.applyTo(project.clips.get(i),requested);for(int i:selectedOverlays())applied|=copiedAttributes.applyTo(project.overlays.get(i),requested);if(applied==0)throw new IllegalArgumentException("None of the copied attributes apply to this layer.");});}).setNegativeButton("Cancel",null).show();
    }
    Motion selectedMotion(){EditProject.Clip c=clip();EditProject.Overlay o=overlay();return c!=null?c.motion:o!=null?o.motion:null;}
    void copyKeys(){Motion m=selectedMotion();if(m==null)return;copiedKeys=Keyframes.copy(m,panels.motionTime(),panels.tolerance(),Keyframes.all());tell(copiedKeys.isEmpty()?"No keyframes to copy on this layer.":"Keyframes copied ("+copiedKeys.keys.size()+" properties)");}
    void pasteKeys(){Motion m=selectedMotion();if(m==null)return;if(copiedKeys==null||copiedKeys.isEmpty()){tell("Copy keyframes first.");return;}if(!layerEditable())return;long t=panels.motionTime();edit("Keyframes pasted",()->Keyframes.paste(m,copiedKeys,t));}
    void jumpKeyframe(boolean next){
        Motion m=selectedMotion();if(m==null){tell("Select an animated layer to step between keyframes.");return;}
        long t=Keyframes.adjacent(m,panels.motionTime(),next,Keyframes.all());if(t<0){tell(next?"No later keyframe":"No earlier keyframe");return;}
        EditProject.Clip c=clip();EditProject.Overlay o=overlay();long timeline=c!=null?project.placements().get(selected).startMs+Math.round((t-c.inMs)/(double)c.speed):o.startMs+t;
        seek(timeline);renderInspector();
    }
    void savePreset(String kind,int categories){
        EditProject.Clip c=clip();EditProject.Overlay o=overlay();if(c==null&&o==null)return;Attributes attributes=c!=null?Attributes.of(c,categories):Attributes.of(o,categories);
        if(attributes.categories==0){tell("Nothing to save for this layer.");return;}
        numberDialog("Preset name",kind.equals(PresetStore.ANIMATION)?"My animation":"My look",name->{presets.save(name,kind,attributes.encode());tell("Preset saved");renderInspector();});
    }
    void deletePreset(PresetStore.Preset preset){new AlertDialog.Builder(this).setTitle("Delete “"+preset.name+"”?").setPositiveButton("Delete",(d,w)->{try{presets.delete(preset.id);renderInspector();}catch(IllegalArgumentException e){tell(e.getMessage());}}).setNegativeButton("Cancel",null).show();}
    void saveElement(){
        java.util.List<Integer> clips=new ArrayList<>(),overlays=new ArrayList<>();for(int i:selectedClips())if(project.clips.get(i).track<4)clips.add(i);overlays.addAll(selectedOverlays());
        if(clips.isEmpty()&&overlays.isEmpty()){tell("Select visual layers to save as an element.");return;}
        String payload=PresetStore.element(project,clips,overlays);numberDialog("Element name","My element",name->{presets.save(name,PresetStore.ELEMENT,payload);tell("Saved element • find it in + Add → Saved");});
    }
    void insertElement(PresetStore.Preset preset,long at){edit("Element added",()->{int added=PresetStore.insertElement(project,preset.payload,at);selected=-1;selectedOverlay=-1;multiClips.clear();multiOverlays.clear();tell("Added "+added+" layer(s)");});}
    void addOverlay(int shape,String text,float scale){
        if(!editable())return;if(project.overlays.size()>=20){tell("Up to 20 text, shape and image layers.");return;}
        long at=addDrawer.insertTime();
        edit(shape>0?EditProject.SHAPES[shape]+" added":"Text added",()->{EditProject.Overlay o=new EditProject.Overlay();o.shape=shape;if(shape==0)o.text=text;else{o.text=EditProject.SHAPES[shape];o.y=0;o.color=0xFF40BFFF;}o.scale=Math.max(.1f,Math.min(2,scale*.7f));
            o.startMs=at;o.endMs=at+Math.max(EditProject.MIN_CLIP_MS,Math.min(3000,Math.max(project.durationMs()-at,3000)));project.overlays.add(o);selected=-1;selectedOverlay=project.overlays.size()-1;tab=1;});
    }
    void toggleMarker(){if(!editable())return;long tolerance=Math.max(1,Math.round(dp(6)*1000/(timeline==null?dp(48):timeline.timelineScale())));long t=project.playheadMs;edit("Marker toggled",()->LayerOps.toggleMarker(project,t,tolerance));}
    void jumpMarker(boolean next){long now=project.playheadMs,best=-1;for(long m:project.markers){if(next&&m>now&&(best<0||m<best))best=m;if(!next&&m<now&&(best<0||m>best))best=m;}if(best<0){tell(next?"No later marker":"No earlier marker");return;}seek(best);}
    /** Approximate beat markers from the selected audio clip (or the music track), added in one undo step. */
    void detectBeats(){
        EditProject.Clip c=clip();boolean useClip=c!=null&&!c.image;if(!useClip&&project.music==null){tell("Select a video or audio clip, or add music, to detect beats.");return;}if(!editable())return;
        String uri=useClip?c.uri:project.music.uri;long sourceDuration=useClip?c.sourceDurationMs:project.music.sourceDurationMs;tell("Detecting beats…");
        long in=useClip?c.inMs:project.music.inMs,out=useClip?c.outMs:project.music.outMs,start=useClip?project.placements().get(selected).startMs:project.music.startMs;float speed=useClip?c.speed:1;boolean loop=!useClip&&project.music.loop;
        io.execute(()->{try{float[] peaks=waveforms.containsKey(uri)?waveforms.get(uri):AudioWaveform.read(this,uri,sourceDuration);java.util.List<Long> beats=Beats.detect(peaks,sourceDuration,250);
            main.post(()->{waveforms.put(uri,peaks);long duration=project.durationMs();java.util.TreeSet<Long> times=new java.util.TreeSet<>(project.markers);
                for(long offset=start;offset<duration;offset+=Math.max(EditProject.MIN_CLIP_MS,Math.round((out-in)/speed))){for(long b:beats)if(b>=in&&b<out){long t=offset+Math.round((b-in)/speed);if(t<=duration)times.add(t);}if(!loop)break;}
                java.util.List<Long> list=new ArrayList<>(times);if(list.size()>500)list=list.subList(0,500);java.util.List<Long> result=list;
                edit("Beat markers added ("+Math.max(0,result.size()-project.markers.size())+")",()->{project.markers.clear();project.markers.addAll(result);});});
        }catch(Exception e){main.post(()->tell("Beat detection: "+e.getMessage()));}});
    }
    void loopDialog(){
        if(project.loopInMs>=0){edit("Loop range cleared",()->{project.loopInMs=-1;project.loopOutMs=-1;});return;}
        EditProject.Clip c=clip();long start=c!=null?project.placements().get(selected).startMs:project.playheadMs,end=c!=null?project.placements().get(selected).endMs:Math.min(project.durationMs(),project.playheadMs+2000);
        LinearLayout f=form();EditText in=field(f,"Loop start (seconds)",seconds(start)),out=field(f,"Loop end (seconds)",seconds(end));f.addView(note("Playback repeats this range in the preview. Export is unaffected."));
        dialog("Loop range",f,()->{long a=parseMs(in.getText().toString().trim()),b=parseMs(out.getText().toString().trim());edit("Loop range set",()->{project.loopInMs=a;project.loopOutMs=b;});},null);
    }
    void openAppSettings(){try{startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.fromParts("package",getPackageName(),null)));}catch(ActivityNotFoundException e){tell("Open Android Settings → Apps → Black Ice to manage media access.");}}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==MEDIA_ACCESS){addDrawer.refresh();renderInspector();}}
    /** Replaces media, keeping timing, transforms, effects and animation; explains source-length conflicts. */
    void replaceMedia(Uri uri){
        EditProject.Clip c=clip();EditProject.Overlay o=overlay();if(c==null&&o==null)return;importing=true;tell("Copying replacement media…");
        io.execute(()->{try{EditProject.Clip fresh=new MediaImporter(this).importClip(uri,false,o!=null?1024:2048);main.post(()->{importing=false;if(isDestroyed())return;
            if(o!=null){edit("Media replaced",()->o.imageUri=fresh.uri);return;}
            if(c.image!=fresh.image){tell(c.image?"Replace a picture with another picture.":"Replace a video with another video.");return;}
            long needed=c.outMs-c.inMs;
            Runnable apply=()->edit("Media replaced",()->{c.uri=fresh.uri;c.name=fresh.name;c.width=fresh.width;c.height=fresh.height;
                if(c.image){c.sourceDurationMs=Math.max(fresh.sourceDurationMs,c.outMs);return;}
                c.sourceDurationMs=fresh.sourceDurationMs;if(c.outMs>fresh.sourceDurationMs){long length=Math.min(needed,fresh.sourceDurationMs);c.inMs=Math.max(0,fresh.sourceDurationMs-length);if(c.outMs-c.inMs>length||c.outMs>fresh.sourceDurationMs){c.inMs=0;c.outMs=length;}}});
            if(!c.image&&fresh.sourceDurationMs<needed)new AlertDialog.Builder(this).setTitle("Replacement is shorter").setMessage("The new video is "+seconds(fresh.sourceDurationMs)+" s, but this clip uses "+seconds(needed)+" s of source. Replacing will shorten the clip to "+seconds(fresh.sourceDurationMs)+" s; later main-sequence clips move earlier. Transforms, effects and animation are kept.")
                .setPositiveButton("Shorten and replace",(d,w)->apply.run()).setNegativeButton("Cancel",null).show();
            else apply.run();});}catch(Exception e){main.post(()->{importing=false;tell("Replace failed: "+e.getMessage());});}});
    }
    // ---- Transform helpers used by TransformPanel and preview editing -------------------------------
    int[] exportSize(){return project.portrait?new int[]{1080,1920}:new int[]{1920,1080};}
    double frameAspect(){int[] s=exportSize();return s[0]/(double)s[1];}
    float aspectOf(){return transformPanel.current().aspect;}
    void setAspect(float aspect){EditProject.Clip c=clip();EditProject.Overlay o=overlay();if(c!=null)c.aspect=aspect;else if(o!=null)o.aspect=aspect;}
    void setFlip(boolean horizontal){EditProject.Clip c=clip();EditProject.Overlay o=overlay();if(c!=null){if(horizontal)c.flipH=!c.flipH;else c.flipV=!c.flipV;}else if(o!=null){if(horizontal)o.flipH=!o.flipH;else o.flipV=!o.flipV;}}
    /** Local content half-size in frame units (before the layer's own transform). */
    double[] layerHalf(){return layerHalf(clip(),overlay());}
    double[] layerHalf(EditProject.Clip c,EditProject.Overlay o){
        double A=frameAspect();
        if(c!=null)return c.width>0&&c.height>0?TransformMath.fittedHalf(A,c.width/(double)c.height):new double[]{A/2,.5};
        if(o==null)return new double[]{A/2,.5};
        if(o.shape>0){double h=Math.min(A,1)*.2;return new double[]{h,o.shape==4?Math.max(.004,o.borderWidth*.025):h};}
        if(o.imageUri!=null)return new double[]{A*.225,A*.225*.75};
        double size=Math.min(A,1)*.065;String[] lines=o.text.split("\n",-1);int longest=1;for(String l:lines)longest=Math.max(longest,l.length());
        return new double[]{Math.min(A*.45,size*.3*longest),size*.65*lines.length};
    }
    void moveAnchor(float ax,float ay){TransformMath.Transform t=transformPanel.current();float[] p=TransformMath.repositionForAnchor(t,frameAspect(),ax,ay);transformPanel.write(Motion.X,p[0]);transformPanel.write(Motion.Y,p[1]);transformPanel.write(Motion.ANCHOR_X,ax);transformPanel.write(Motion.ANCHOR_Y,ay);}
    void alignLayer(int horizontal,int vertical,boolean x,boolean y){double[] half=layerHalf();float[] p=TransformMath.align(transformPanel.current(),frameAspect(),half[0],half[1],horizontal,vertical,x,y);if(x)transformPanel.write(Motion.X,p[0]);if(y)transformPanel.write(Motion.Y,p[1]);}
    void nudge(int pixelsX,int pixelsY){if(clip()==null&&overlay()==null||!layerEditable())return;int[] size=exportSize();TransformMath.Transform t=transformPanel.current();float x=t.x+pixelsX*TransformMath.pixelX(size[0]),y=t.y+pixelsY*TransformMath.pixelY(size[1]);edit("Nudged",()->{transformPanel.write(Motion.X,x);transformPanel.write(Motion.Y,y);});}
    /** Fit (0), Fill (1) or Original Size (2); source dimensions are measured once when unknown. */
    void fitLayer(int mode){
        EditProject.Clip c=clip();EditProject.Overlay o=overlay();if(c==null&&o==null||!layerEditable())return;
        if(c!=null&&(c.width<=0||c.height<=0)){tell("Measuring source size…");io.execute(()->{int[] size=MediaImporter.measure(this,c.uri,c.image);main.post(()->{if(size==null){tell("Cannot read this media's size.");return;}c.width=size[0];c.height=size[1];fitLayer(mode);});});return;}
        double A=frameAspect();int[] export=exportSize();
        edit(new String[]{"Fit","Fill","Original size"}[mode]+" applied",()->{
            float scale;
            if(c!=null){double content=c.width/(double)c.height;scale=mode==0?1:mode==1?TransformMath.fillScale(A,content):TransformMath.originalScale(export[0],export[1],c.width,c.height);}
            else{double[] half=layerHalf();double fit=Math.min(A/2/half[0],.5/half[1]),fill=Math.max(A/2/half[0],.5/half[1]);scale=(float)(transformPanel.current().scale*(mode==0?fit:mode==1?fill:1/transformPanel.current().scale));}
            transformPanel.write(Motion.SCALE,TransformMath.clamp(scale,.05f,4));setAspect(1);
            if(mode<2){transformPanel.write(Motion.X,0);transformPanel.write(Motion.Y,0);}
        });
    }
    /** Pans the preview so an offscreen layer is visible; the layer itself is not moved. */
    void locateSelected(){
        if(clip()==null&&overlay()==null)return;double[] half=layerHalf();TransformMath.Transform t=transformPanel.current();double A=frameAspect();
        // Bounds in a view where the frame spans [0,A] × [0,1]; zoom out until frame and layer both fit.
        float[] b=TransformMath.bounds(TransformMath.corners(t,(float)A,1,half[0],half[1]));
        float left=Math.min(0,b[0]),top=Math.min(0,b[1]),right=Math.max((float)A,b[2]),bottom=Math.max(1,b[3]);
        previewZoom=TransformMath.clamp(Math.min((float)A/(right-left),1/(bottom-top)),.2f,1);
        previewPanX=(float)(.5-(left+right)/2/A);previewPanY=.5f-(top+bottom)/2;if(monitor!=null)monitor.requestLayout();
        tell(TransformMath.offscreen(t,A,half[0],half[1])?"Layer is outside the frame • preview zoomed out to show it (tap Fit view to reset)":"Selected layer is in view");if(previewControls!=null)previewControls.invalidate();
    }
    void resetPreviewView(){previewZoom=1;previewPanX=0;previewPanY=0;if(monitor!=null)monitor.requestLayout();if(previewControls!=null)previewControls.invalidate();}
    /** Before/after: hold Compare to preview the project as it was when the current layer was selected. */
    void compare(boolean start){
        if(start){if(compareSnapshot==null){tell("Select a layer and adjust it to compare before and after.");return;}comparing=true;preparePreview(false);}
        else if(comparing){comparing=false;preparePreview(false);}
        if(previewControls!=null)previewControls.invalidate();
    }
    void previewMove(float x,float y){previewMoveX=x;previewMoveY=y;if(previewControls!=null)previewControls.invalidate();}
    EditProject.Overlay overlay(){return selected<0&&selectedOverlay>=0&&selectedOverlay<project.overlays.size()?project.overlays.get(selectedOverlay):null;}
    void overlayTools(){EditProject.Overlay o=overlay();inspector.addView(note(o.imageUri==null?"Text: "+o.text:"Image overlay"));
        if(o.imageUri==null){addAction("Edit Text",()->numberDialog("Edit Text",o.text,value->edit("Text updated",()->o.text=value)));
            addAction("Font",()->new AlertDialog.Builder(this).setTitle("Font").setItems(new String[]{"Sans serif","Serif","Monospace"},(d,i)->edit("Font updated",()->o.font=new String[]{"sans-serif-medium","serif","monospace"}[i])).show());
            addAction("Size",()->numberDialog("Text size (0.1 to 2)",String.valueOf(o.scale),value->edit("Text size updated",()->o.scale=Float.parseFloat(value))));
            addAction("Color",()->numberDialog("Text color (#RRGGBB)",String.format(Locale.US,"#%06X",o.color&0xFFFFFF),value->edit("Text color updated",()->o.color=Color.parseColor(value))));
            addAction("Alignment",()->new AlertDialog.Builder(this).setTitle("Alignment at text anchor").setItems(new String[]{"Left","Center","Right"},(d,i)->edit("Alignment updated",()->o.alignment=i)).show());}
        addAction("Duration: "+seconds(o.endMs-o.startMs)+" s",()->numberDialog("Duration (seconds)",seconds(o.endMs-o.startMs),value->edit("Duration updated",()->{long length=parseMs(value);if(length<100)throw new IllegalArgumentException("Keep at least 0.10 seconds");o.endMs=Math.addExact(o.startMs,length);})));
        addAction("Transform",()->{LinearLayout f=form();EditText x=field(f,"X (−1 to 1)",String.valueOf(o.x)),y=field(f,"Y (−1 to 1)",String.valueOf(o.y)),size=field(f,"Scale (0.1 to 2)",String.valueOf(o.scale));dialog("Overlay transform",f,()->edit("Transform updated",()->{o.x=parseFloat(x);o.y=parseFloat(y);o.scale=parseFloat(size);}),null);});
        addAction("Opacity",()->percentPanel("Opacity",o.opacity,1,value->o.opacity=value));
        addAction("Keyframes",()->motionDialog(o.motion,Math.max(0,project.playheadMs-o.startMs),o.endMs-o.startMs,new float[]{o.x,o.y,o.scale,0,o.opacity,0},false));
        addAction("Timing / advanced",()->overlayDialog(o));
        addAction("Remove overlay",()->edit("Overlay removed",()->{project.overlays.remove(o);selectedOverlay=-1;}));
    }
    void exportDiagnostics(){
        android.content.SharedPreferences prefs=getSharedPreferences(ExportService.PREFS,MODE_PRIVATE);
        String report="BC-1 v2.0.0 • Android API "+android.os.Build.VERSION.SDK_INT+"\n\n"+
                prefs.getString(ExportService.LAST_FAILURE,"No export failure recorded.")+"\n\n"+
                "Last failure is retained after successful exports. This summary excludes media names, paths and exception messages.";
        new AlertDialog.Builder(this).setTitle("Export diagnostics").setMessage(report)
                .setPositiveButton("Copy report",(d,w)->{((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE))
                    .setPrimaryClip(android.content.ClipData.newPlainText("BC-1 export diagnostics",report));tell("Export diagnostics copied");})
                .setNegativeButton("Close",null).show();
    }
    void moveClip(int delta){if(!editable())return;int next=selected+delta;if(next<0||next>=project.clips.size())return;remember();project.move(selected,delta);selected=next;changed("Clip moved");}
    void trimDialog() {
        EditProject.Clip c=clip();if(c==null||!editable())return;if(c.image){durationDialog();return;}
        LinearLayout form=form();EditText start=field(form,"Source in (seconds)",seconds(c.inMs));EditText end=field(form,c.image?"Source out / duration (seconds)":"Source out (seconds)",seconds(c.outMs));
        form.addView(note("Source length: "+seconds(c.sourceDurationMs)+" s. Edits do not change the imported file."));
        dialog("Trim clip",form,()->{long in=parseMs(start.getText().toString()),out=parseMs(end.getText().toString());
            edit("Trim applied",()->project.trim(selected,in,out));},null);
    }
    void overlayDialog(EditProject.Overlay existing) {
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
    void musicDialog() {
        EditProject.Music m=project.music;LinearLayout form=form();form.addView(note(m.name));
        EditText in=field(form,"Source in (seconds)",seconds(m.inMs)),out=field(form,"Source out (seconds)",seconds(m.outMs));
        EditText start=field(form,"Start on timeline (seconds)",seconds(m.startMs)),volume=field(form,"Music volume: 0 to 1",String.valueOf(m.volume));
        CheckBox loop=new CheckBox(this);loop.setText("Loop trimmed music to end of project");loop.setTextColor(TEXT);loop.setChecked(m.loop);form.addView(loop);
        dialog("Music track",form,()->{long a=parseMs(in.getText().toString()),b=parseMs(out.getText().toString()),s=parseMs(start.getText().toString());float v=parseFloat(volume);
            if(a<0||b>m.sourceDurationMs||b-a<100||s<0||v<0||v>1)throw new IllegalArgumentException("Choose a valid audio range and volume.");
            remember();m.inMs=a;m.outMs=b;m.startMs=s;m.volume=v;m.loop=loop.isChecked();changed("Music updated");
        },()->{remember();project.music=null;changed("Music removed");});
    }
    void projectsMenu() {
        if(!editable())return;new AlertDialog.Builder(this).setTitle("Black Ice projects").setItems(new String[]{"New project","Recent projects","Rename project","Save project","Undo","Redo"},(d,index)->{
            if(index==0)numberDialog("New project name","Untitled project",name->{if(name.isBlank()||name.length()>120)throw new IllegalArgumentException("Use a name from 1 to 120 characters.");saveNow();releasePreview();project=new EditProject();project.name=name;undo.clear();redo.clear();selected=-1;selectedOverlay=-1;changed("New project ready");});
            if(index==1){saveNow();io.execute(()->{java.util.List<ProjectStore.Entry> recent=store.recent();main.post(()->{if(isDestroyed())return;
                if(recent.isEmpty()){tell("No saved projects yet.");return;}
                new AlertDialog.Builder(this).setTitle("Recent projects").setItems(recent.stream().map(e->e.name).toArray(String[]::new),(x,i)->openProject(recent.get(i).id)).show();});});}
            if(index==2)numberDialog("Project name",project.name,name->{if(name.isBlank()||name.length()>120)throw new IllegalArgumentException("Use a name from 1 to 120 characters.");remember();project.name=name;changed("Project renamed");});
            if(index==3){saveNow();tell("Saving project…");}if(index==4)undo(false);if(index==5)undo(true);
        }).show();
    }
    void openProject(String id){loading=true;releasePreview();io.execute(()->{try{EditProject loaded=store.load(id);main.post(()->{if(isDestroyed())return;loading=false;project=loaded;undo.clear();redo.clear();selected=-1;selectedOverlay=-1;changed("Project opened");});}catch(Exception e){main.post(()->{loading=false;tell("Cannot open project: "+e.getMessage());});}});}
    void chooseMode(){if(uiLocked){tell("Unlock to change workspace layout");return;}new AlertDialog.Builder(this).setTitle("Workspace layout").setSingleChoiceItems(new String[]{"Auto • phone / tablet / DeX","Phone portrait workspace","Wide tablet / DeX workspace"},mode.ordinal(),(d,i)->{mode=LayoutPolicy.Preference.values()[i];getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString("mode",mode.name()).apply();d.dismiss();buildWorkspace();}).show();}
    void pick(int request,String type) {
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
        if(request==REPLACE){if(data.getData()!=null)replaceMedia(data.getData());return;}
        if(request!=IMPORT&&request!=MUSIC&&request!=OVERLAY&&request!=AUDIO_LAYER)return;
        java.util.List<Uri> uris=new ArrayList<>();if(data.getClipData()!=null){for(int i=0;i<data.getClipData().getItemCount();i++)uris.add(data.getClipData().getItemAt(i).getUri());}else if(data.getData()!=null)uris.add(data.getData());
        importUris(uris,request);
    }
    /** Copies media into the app, then inserts it at the playhead (or project start) in one undo step. */
    void importUris(java.util.List<Uri> uris,int request){
        if(uris.isEmpty()||!editable())return;importing=true;tell("Importing media copies…");long at=addDrawer.insertTime();boolean asLayer=addDrawer.asLayer;
        io.execute(()->{
            java.util.List<EditProject.Clip> imported=new ArrayList<>();java.util.List<String> errors=new ArrayList<>();
            for(Uri uri:uris)try{imported.add(new MediaImporter(this).importClip(uri,(request==MUSIC||request==AUDIO_LAYER),request==OVERLAY?1024:2048));}catch(Exception e){errors.add(e.getMessage());}
            main.post(()->{importing=false;if(isDestroyed())return;remember();long cursor=at;
                for(EditProject.Clip c:imported){
                    if(request==AUDIO_LAYER){try{if(project.clips.size()>=100)throw new IllegalArgumentException("The 100-clip limit was reached");c.track=project.freeAudioTrack(cursor,c.durationMs());c.startMs=cursor;project.clips.add(c);selected=project.clips.size()-1;tab=1;cursor+=c.durationMs();}catch(Exception e){errors.add(e.getMessage());}}
                    else if(request==MUSIC){EditProject.Music m=new EditProject.Music();m.uri=c.uri;m.name=c.name;m.sourceDurationMs=c.sourceDurationMs;m.outMs=c.outMs;m.startMs=at;project.music=m;}
                    else if(request==OVERLAY){EditProject.Overlay o=new EditProject.Overlay();o.imageUri=c.uri;o.startMs=cursor;o.endMs=Math.max(o.startMs+100,Math.min(Math.max(project.durationMs(),o.startMs+3000),o.startMs+3000));project.overlays.add(o);selected=-1;selectedOverlay=project.overlays.size()-1;}
                    else {if(project.clips.size()>=100){errors.add("The 100-clip project limit was reached.");new File(Uri.parse(c.uri).getPath()).delete();continue;}
                        try{selected=insertClip(c,cursor,asLayer);cursor=project.placements().get(selected).endMs;}catch(IllegalArgumentException e){errors.add(e.getMessage());new File(Uri.parse(c.uri).getPath()).delete();}}
                }
                if(selected>=0||selectedOverlay>=0)tab=1;
                changed(errors.isEmpty()?"Imported "+imported.size()+" item(s)":"Import issue: "+String.join("; ",errors));
                if(!errors.isEmpty())new AlertDialog.Builder(this).setTitle("Import result").setMessage(message).setPositiveButton("OK",null).show();
            });
        });
    }
    /** Main sequence: at the clip boundary at/after the time. New layer: first free visual layer at the time. */
    int insertClip(EditProject.Clip c,long at,boolean asLayer){
        if(asLayer||c.audioOnly){int first=c.audioOnly?4:1,last=c.audioOnly?7:3;
            for(int t=first;t<=last;t++)if(LayerOps.free(project,t,at,at+c.durationMs(),-1)){c.track=t;c.startMs=at;project.clips.add(c);return project.clips.size()-1;}
            throw new IllegalArgumentException("No free layer at "+seconds(at)+" s for "+c.name+".");}
        int index=project.clips.size();
        for(EditProject.Placement p:project.placements())if(p.clip.track==0){if(at>p.startMs&&at<p.endMs){index=p.index+1;break;}if(p.startMs>=at){index=p.index;break;}}
        c.track=0;project.clips.add(index,c);return index;
    }
    void exportMenu() {
        if(exporting()){new AlertDialog.Builder(this).setTitle("Cancel current export?").setMessage(getSharedPreferences(ExportService.PREFS,MODE_PRIVATE).getString("status","Export running")).setPositiveButton("Cancel export",(d,w)->startService(new Intent(this,ExportService.class).setAction(ExportService.ACTION_CANCEL))).setNegativeButton("Keep exporting",null).show();return;}
        if(loading||importing)return;if(project.clips.isEmpty()){tell("Import at least one video or image before exporting.");return;}
        new AlertDialog.Builder(this).setTitle("Export MP4").setMessage(project.name+"\n"+(project.portrait?"1080 × 1920":"1920 × 1080")+" • H.264 + AAC • up to 30 fps\n"+clock(project.durationMs())+"\n\nChoose where to save the finished video. Export can continue in the background.")
                .setPositiveButton("Choose destination",(d,w)->{Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("video/mp4").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,project.name.replaceAll("[^a-zA-Z0-9 ._-]","_")+".mp4");startActivityForResult(intent,EXPORT);}).setNegativeButton("Cancel",null).show();
    }
    void startExport(Uri destination) {
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
        if(event.isCtrlPressed()&&key==KeyEvent.KEYCODE_C){copyAttributes();return true;}
        if(event.isCtrlPressed()&&key==KeyEvent.KEYCODE_V){pasteAttributes();return true;}
        if(key==KeyEvent.KEYCODE_S&&!event.isCtrlPressed()){splitSelected();return true;}
        if(key==KeyEvent.KEYCODE_ESCAPE){deselect();return true;}
        if(key==KeyEvent.KEYCODE_MOVE_HOME||key==KeyEvent.KEYCODE_MOVE_END){jump(key==KeyEvent.KEYCODE_MOVE_END);return true;}
        if(event.isCtrlPressed()&&(key==KeyEvent.KEYCODE_EQUALS||key==KeyEvent.KEYCODE_MINUS)){zoomTimeline(key==KeyEvent.KEYCODE_EQUALS?1.5f:1/1.5f,Float.NaN);return true;}
        boolean arrow=key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT||key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN;
        // Transform mode: arrows nudge the selected layer by one project pixel, Shift by ten.
        if(arrow&&"Move & Transform".equals(panels.title())&&panels.showing()){int step=event.isShiftPressed()?10:1;nudge(key==KeyEvent.KEYCODE_DPAD_LEFT?-step:key==KeyEvent.KEYCODE_DPAD_RIGHT?step:0,key==KeyEvent.KEYCODE_DPAD_UP?step:key==KeyEvent.KEYCODE_DPAD_DOWN?-step:0);return true;}
        if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT){long frame=Math.round(1000.0/project.frameRate);seek(project.playheadMs+(key==KeyEvent.KEYCODE_DPAD_LEFT?-1:1)*(event.isShiftPressed()?1000:frame));return true;}
        if(key==KeyEvent.KEYCODE_FORWARD_DEL||key==KeyEvent.KEYCODE_DEL){deleteSelection();return true;}
        if(key==KeyEvent.KEYCODE_M&&!event.isCtrlPressed()){toggleMarker();return true;}
        if(key==KeyEvent.KEYCODE_COMMA||key==KeyEvent.KEYCODE_PERIOD){jumpMarker(key==KeyEvent.KEYCODE_PERIOD);return true;}
        if(key==KeyEvent.KEYCODE_LEFT_BRACKET||key==KeyEvent.KEYCODE_RIGHT_BRACKET){jumpKeyframe(key==KeyEvent.KEYCODE_RIGHT_BRACKET);return true;}
        return super.onKeyDown(key,event);
    }
    /** Duplicate After by default: a sequential copy on the same row. */
    void duplicateSelected(){if(multiCount()>1){multiAction("Duplicate");return;}duplicate(false);}
    void duplicate(boolean above){EditProject.Clip c=clip();EditProject.Overlay o=overlay();if(c==null&&o==null||!layerEditable())return;
        if(c!=null)edit(above?"Duplicated above":"Clip duplicated",()->{selected=above?LayerOps.duplicateAbove(project,selected):LayerOps.duplicateAfter(project,selected);});
        else edit(above?"Duplicated above":"Layer duplicated",()->selectedOverlay=LayerOps.duplicateOverlay(project,selectedOverlay,!above));}
    void placementDialog(){EditProject.Clip c=clip();if(c==null||!editable())return;LinearLayout f=form();EditText track=field(f,"0: main sequence · 1–3: video layers · 4–7: audio",String.valueOf(c.track)),start=field(f,"Timeline start (seconds; independent layers)",seconds(project.placements().get(selected).startMs));dialog("Layer placement",f,()->{int t=Integer.parseInt(track.getText().toString());long ms=parseMs(start.getText().toString());remember();project.setPlacement(selected,t,ms);changed("Layer placement updated");},null);}
    void fadeDialog(){EditProject.Clip c=clip();LinearLayout f=form();EditText in=field(f,"Fade in (seconds)",seconds(c.fadeInMs)),out=field(f,"Fade out (seconds)",seconds(c.fadeOutMs));dialog("Audio fades",f,()->{long a=parseMs(in.getText().toString()),b=parseMs(out.getText().toString());if(a<0||b<0||a>c.durationMs()||b>c.durationMs())throw new IllegalArgumentException("Fades must fit inside the clip");remember();c.fadeInMs=a;c.fadeOutMs=b;changed("Audio fades updated");},null);}
    void extractAudio(boolean muteOriginal){EditProject.Clip source=clip();if(source==null||source.image||!layerEditable())return;
        {long start=project.placements().get(selected).startMs;importing=true;io.execute(()->{try{if(!AudioWaveform.hasAudio(this,source.uri))throw new IllegalArgumentException("This source has no audio track");main.post(()->{importing=false;if(isDestroyed())return;try{if(project.clips.size()>=100)throw new IllegalArgumentException("The 100-clip limit was reached");EditProject.Clip audio=source.copy();audio.name=source.name+" • audio";audio.track=project.freeAudioTrack(start,audio.durationMs());audio.startMs=start;audio.transition=-1;remember();project.clips.add(audio);if(muteOriginal)source.volume=0;audio.fadeInMs=0;audio.fadeOutMs=0;selected=project.clips.size()-1;panels.reset();changed("Audio extracted independently");loadWaveform(audio);}catch(Exception e){tell(e.getMessage());}});}catch(Exception e){main.post(()->{importing=false;tell(e.getMessage());});}});}
    }
    final java.util.Set<String> waveformsLoading=new java.util.HashSet<>();
    void loadWaveform(EditProject.Clip c){loadWaveform(c,false);}
    /** Audio layers show real PCM waveforms; decoding runs once per source on the worker thread. */
    void loadWaveform(EditProject.Clip c,boolean quiet){if(waveforms.containsKey(c.uri)||!waveformsLoading.add(c.uri))return;if(!quiet)tell("Decoding audio waveform…");String uri=c.uri;long duration=c.sourceDurationMs;io.execute(()->{try{float[] peaks=AudioWaveform.read(this,uri,duration);main.post(()->{waveformsLoading.remove(uri);waveforms.put(uri,peaks);if(timeline!=null)timeline.invalidate();if(!quiet)tell("Waveform ready");});}catch(Exception e){main.post(()->{waveformsLoading.remove(uri);if(!quiet)tell("Waveform: "+e.getMessage());});}});}
    void bundle(int request,Uri uri){if(!editable())return;saveNow();EditProject snapshot=ProjectStore.snapshot(project);importing=true;tell(request==BACKUP?"Saving project and media…":"Restoring project and media…");io.execute(()->{try{if(request==BACKUP){ProjectBundle.write(this,snapshot,getContentResolver().openOutputStream(uri,"wt"));main.post(()->{importing=false;tell("Project bundle saved");});}else{EditProject restored=ProjectBundle.read(this,getContentResolver().openInputStream(uri));main.post(()->{importing=false;if(isDestroyed())return;releasePreview();project=restored;undo.clear();redo.clear();selected=-1;selectedOverlay=-1;changed("Project restored as a new copy");});}}catch(Exception e){if(request==BACKUP)try{android.provider.DocumentsContract.deleteDocument(getContentResolver(),uri);}catch(Exception ignored){}main.post(()->{importing=false;tell("Bundle failed: "+e.getMessage());});}});}
    void chromaDialog(){EditProject.Clip c=clip();if(c==null||!editable())return;LinearLayout f=form();CheckBox enabled=new CheckBox(this);enabled.setText("Enable chroma key");enabled.setTextColor(TEXT);enabled.setChecked(c.chroma);f.addView(enabled);EditText color=field(f,"Key color (#RRGGBB)",String.format(java.util.Locale.ROOT,"#%06X",c.keyColor&0xFFFFFF)),t=field(f,"Tolerance 0–1",String.valueOf(c.keyTolerance)),soft=field(f,"Edge softness 0.001–1",String.valueOf(c.keySoftness)),spill=field(f,"Spill removal 0–1",String.valueOf(c.keySpill));dialog("Chroma key",f,()->{int rgb=Color.parseColor(color.getText().toString());float a=parseFloat(t),b=parseFloat(soft),z=parseFloat(spill);if(a<0||a>1||b<.001f||b>1||z<0||z>1)throw new IllegalArgumentException("Check key limits");remember();c.chroma=enabled.isChecked();c.keyColor=rgb;c.keyTolerance=a;c.keySoftness=b;c.keySpill=z;changed("Chroma key updated");},null);}
    void transformDialog(){EditProject.Clip c=clip();if(c==null||!editable())return;new AlertDialog.Builder(this).setTitle("Transform & motion").setItems(new String[]{"Static transform","Keyframes / curve editor"},(d,i)->{
        if(i==1){motionDialog(c.motion,Math.max(c.inMs,Math.min(c.outMs,Math.round((project.playheadMs-project.placements().get(selected).startMs)*c.speed)+c.inMs)),c.sourceDurationMs,new float[]{c.x,c.y,c.scale,c.rotation,c.opacity,c.intensity},true);return;}
        LinearLayout f=form();EditText x=field(f,"X: −2 to 2",String.valueOf(c.x)),y=field(f,"Y: −2 to 2",String.valueOf(c.y)),scale=field(f,"Scale: 0.05 to 4",String.valueOf(c.scale)),rotation=field(f,"Rotation (degrees)",String.valueOf(c.rotation));dialog("Transform",f,()->{float a=parseFloat(x),b=parseFloat(y),z=parseFloat(scale),r=parseFloat(rotation);if(!Motion.allowed(0,a)||!Motion.allowed(1,b)||!Motion.allowed(2,z)||!Motion.allowed(3,r))throw new IllegalArgumentException("Transform out of range");remember();c.x=a;c.y=b;c.scale=z;c.rotation=r;changed("Transform updated");},null);
    }).show();}
    void motionDialog(Motion motion,long current,long limit,float[] defaults,boolean effects){String[] properties=effects?Motion.PROPERTIES:java.util.Arrays.copyOf(Motion.PROPERTIES,5);new AlertDialog.Builder(this).setTitle("Animate property").setItems(properties,(d,property)->{
        java.util.List<Motion.Key> keys=motion.curves.get(property);String[] choices=new String[keys.size()+1];choices[0]="+ Key at playhead";for(int n=0;n<keys.size();n++)choices[n+1]=seconds(keys.get(n).timeMs)+" s → "+keys.get(n).value;
        new AlertDialog.Builder(this).setTitle(properties[property]+" • source-time keys").setItems(choices,(dialog,index)->keyDialog(motion,property,index==0?new Motion.Key(current,motion.value(property,current,defaults[property])):keys.get(index-1).copy(),limit,index==0?null:keys.get(index-1))).show();
    }).show();}
    void keyDialog(Motion motion,int property,Motion.Key key,long limit,Motion.Key existing){LinearLayout f=form();EditText time=field(f,"Source time (seconds)",seconds(key.timeMs)),value=field(f,Motion.PROPERTIES[property]+" value",String.valueOf(key.value));
        EditText x1=field(f,"Curve X1 (0–1)",String.valueOf(key.x1)),y1=field(f,"Curve Y1 (0–1)",String.valueOf(key.y1)),x2=field(f,"Curve X2 (0–1)",String.valueOf(key.x2)),y2=field(f,"Curve Y2 (0–1)",String.valueOf(key.y2));
        CurveView curve=new CurveView(key,()->{x1.setText(String.valueOf(key.x1));y1.setText(String.valueOf(key.y1));x2.setText(String.valueOf(key.x2));y2.setText(String.valueOf(key.y2));});f.addView(curve,new LinearLayout.LayoutParams(-1,dp(180)));
        LinearLayout presets=row();String[] names={"Linear","Ease in","Ease out","Smooth"};float[][] points={{0,0,1,1},{.42f,0,1,1},{0,0,.58f,1},{.42f,0,.58f,1}};for(int i=0;i<names.length;i++){final int n=i;presets.addView(button(names[i],v->{float[] a=points[n];key.x1=a[0];key.y1=a[1];key.x2=a[2];key.y2=a[3];x1.setText(""+a[0]);y1.setText(""+a[1]);x2.setText(""+a[2]);y2.setText(""+a[3]);curve.invalidate();}),new LinearLayout.LayoutParams(0,dp(48),1));}f.addView(presets);f.addView(note("Drag the blue handles. This curve controls the interval after this key."));
        dialog("Keyframe / curve",f,()->{long t=parseMs(time.getText().toString());float val=parseFloat(value);Motion.Key candidate=new Motion.Key(t,val);candidate.x1=parseFloat(x1);candidate.y1=parseFloat(y1);candidate.x2=parseFloat(x2);candidate.y2=parseFloat(y2);Motion check=motion.copy();if(existing!=null)check.curves.get(property).removeIf(k->k.timeMs==existing.timeMs);check.put(property,candidate);check.validate();if(t>limit)throw new IllegalArgumentException("Key must fit inside the source");remember();if(existing!=null)motion.curves.get(property).remove(existing);motion.put(property,candidate);changed("Keyframe saved");},existing==null?null:()->{remember();motion.curves.get(property).remove(existing);changed("Keyframe removed");});
    }
    final class CurveView extends View{final Motion.Key key;final Runnable update;final Paint p=new Paint(3);int handle;CurveView(Motion.Key k,Runnable u){super(EditorActivity.this);key=k;update=u;setContentDescription("Cubic Bézier curve: drag either handle");}protected void onDraw(Canvas c){float w=getWidth()-dp(24),h=getHeight()-dp(24),m=dp(12);c.drawColor(PANEL);p.setColor(MUTED);p.setStrokeWidth(dp(1));c.drawLine(m,h+m,w+m,m,p);p.setColor(CYAN);p.setStrokeWidth(dp(2));float px=m,py=h+m;for(int i=1;i<=80;i++){float x=i/80f,y=Motion.ease(x,key);c.drawLine(px,py,m+x*w,m+(1-y)*h,p);px=m+x*w;py=m+(1-y)*h;}c.drawCircle(m+key.x1*w,m+(1-key.y1)*h,dp(6),p);c.drawCircle(m+key.x2*w,m+(1-key.y2)*h,dp(6),p);}public boolean onTouchEvent(MotionEvent e){float x=Math.max(0,Math.min(1,(e.getX()-dp(12))/(getWidth()-dp(24)))),y=Math.max(0,Math.min(1,1-(e.getY()-dp(12))/(getHeight()-dp(24))));if(e.getAction()==MotionEvent.ACTION_DOWN){handle=Math.hypot(x-key.x1,y-key.y1)<Math.hypot(x-key.x2,y-key.y2)?1:2;getParent().requestDisallowInterceptTouchEvent(true);}if(handle==1){key.x1=x;key.y1=y;}else{key.x2=x;key.y2=y;}update.run();invalidate();if(e.getAction()==MotionEvent.ACTION_UP){getParent().requestDisallowInterceptTouchEvent(false);performClick();}return true;}public boolean performClick(){super.performClick();return true;}}

    void toggleLock(){uiLocked=!uiLocked;getSharedPreferences(PREFS,MODE_PRIVATE).edit().putBoolean("ui-locked",uiLocked).apply();renderInspector();tell(uiLocked?"UI locked • playback and seeking remain available":"UI unlocked");}
    void renderMediaBrowser(){
        if(mediaBrowser==null)return;
        mediaBrowser.removeAllViews();mediaFilterButtons.clear();
        mediaBrowser.addView(label("PROJECT MEDIA",12,CYAN,true));
        mediaBrowser.addView(button("+ Import media",v->pick(IMPORT,"*/*")));
        LinearLayout filters=row();filters.setBaselineAligned(false);
        String[] names={"All","Video","Audio","Image"};
        for(MediaFilter.Kind kind:MediaFilter.Kind.values()){
            TextView filter=button(names[kind.ordinal()],v->{mediaKind=kind;renderMediaGrid();});
            filter.setTextSize(10);filter.setPadding(dp(3),0,dp(3),0);
            filter.setContentDescription("Filter media: "+names[kind.ordinal()]);
            mediaFilterButtons.add(filter);filters.addView(filter,new LinearLayout.LayoutParams(0,dp(48),1));
        }
        mediaBrowser.addView(filters);
        EditText search=new EditText(this);search.setSingleLine(true);search.setTextSize(12);
        search.setTextColor(TEXT);search.setHintTextColor(MUTED);search.setHint("Search media");
        search.setContentDescription("Search project media");search.setText(mediaQuery);
        search.setPadding(dp(8),0,dp(8),0);search.setBackground(buttonBackground(false));
        mediaBrowser.addView(search,new LinearLayout.LayoutParams(-1,dp(48)));
        mediaCount=note("");mediaCount.setTextSize(10);mediaBrowser.addView(mediaCount);
        mediaGrid=column();mediaGrid.setContentDescription("Project media grid");mediaBrowser.addView(mediaGrid);
        search.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){mediaQuery=s.toString();renderMediaGrid();}
            public void afterTextChanged(android.text.Editable value){}
        });
        renderMediaGrid();
    }
    void renderMediaGrid(){
        if(mediaGrid==null)return;
        mediaGrid.removeAllViews();
        for(int i=0;i<mediaFilterButtons.size();i++){
            TextView filter=mediaFilterButtons.get(i);boolean chosen=i==mediaKind.ordinal();
            filter.setSelected(chosen);filter.setTextColor(chosen?CYAN:TEXT);filter.setBackground(navBackground(chosen));
        }
        ArrayList<Integer> matches=new ArrayList<>();
        for(int i=0;i<project.clips.size();i++)if(MediaFilter.matches(project.clips.get(i),mediaKind,mediaQuery))matches.add(i);
        mediaCount.setText(matches.size()+" / "+project.clips.size()+" clips");
        if(matches.isEmpty()){
            mediaGrid.addView(note(project.clips.isEmpty()?"Import media to build your project.":"No matching media"));
            return;
        }
        for(int start=0;start<matches.size();start+=mediaColumns){
            LinearLayout row=row();row.setBaselineAligned(false);
            for(int column=0;column<mediaColumns;column++){
                LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(0,dp(mediaColumns==2?136:160),1);
                if(column>0)cell.leftMargin=dp(6);
                if(start+column<matches.size())row.addView(mediaCard(matches.get(start+column)),cell);
                else row.addView(new View(this),cell);
            }
            LinearLayout.LayoutParams line=new LinearLayout.LayoutParams(-1,-2);line.bottomMargin=dp(8);mediaGrid.addView(row,line);
        }
    }
    View mediaCard(int index){
        EditProject.Clip clip=project.clips.get(index);
        LinearLayout card=column();card.setPadding(dp(5),dp(5),dp(5),dp(5));
        card.setTag(index);
        card.setBackground(buttonBackground(index==selected));card.setSelected(index==selected);
        ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setTag(clip);
        image.setImageDrawable(new V1Icon(clip.track>=4?"Volume":"Media",MUTED));
        card.addView(image,new LinearLayout.LayoutParams(-1,dp(mediaColumns==2?72:96)));
        if(clip.track<4){
            Bitmap thumb=thumbnails.get(clip,mediaThumbnailsReady);
            if(thumb!=null)image.setImageBitmap(thumb);
        }
        TextView name=label(clip.name,11,TEXT,true);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);card.addView(name,new LinearLayout.LayoutParams(-1,0,1));
        String kind=clip.track>=4?"Audio":clip.image?"Image":"Video";
        TextView detail=label(kind+" · "+clock(clip.durationMs()),10,MUTED,false);detail.setSingleLine(true);detail.setEllipsize(android.text.TextUtils.TruncateAt.END);card.addView(detail);
        card.setFocusable(true);card.setContentDescription("Select "+clip.name);
        card.setOnClickListener(v->{selected=index;tab=1;seek(project.placements().get(index).startMs);renderInspector();renderMediaGrid();});
        return card;
    }
    void updateMediaSelection(){
        if(mediaGrid==null)return;
        for(int row=0;row<mediaGrid.getChildCount();row++)if(mediaGrid.getChildAt(row) instanceof ViewGroup){
            ViewGroup cells=(ViewGroup)mediaGrid.getChildAt(row);
            for(int i=0;i<cells.getChildCount();i++){
                View card=cells.getChildAt(i);if(!(card.getTag() instanceof Integer))continue;
                boolean chosen=((Integer)card.getTag())==selected;
                if(card.isSelected()!=chosen){card.setSelected(chosen);card.setBackground(buttonBackground(chosen));}
            }
        }
    }
    void refreshMediaThumbnails(){
        if(isDestroyed()||mediaGrid==null)return;
        // Update current images in place; decoding must not recreate cards, focus or scroll state.
        for(int row=0;row<mediaGrid.getChildCount();row++)if(mediaGrid.getChildAt(row) instanceof ViewGroup){
            ViewGroup cells=(ViewGroup)mediaGrid.getChildAt(row);
            for(int i=0;i<cells.getChildCount();i++)if(cells.getChildAt(i) instanceof ViewGroup){
                ViewGroup card=(ViewGroup)cells.getChildAt(i);
                if(card.getChildCount()==0||!(card.getChildAt(0) instanceof ImageView))continue;
                ImageView image=(ImageView)card.getChildAt(0);
                if(!(image.getTag() instanceof EditProject.Clip))continue;
                EditProject.Clip clip=(EditProject.Clip)image.getTag();if(clip.track>=4)continue;
                Bitmap bitmap=thumbnails.peek(clip);if(bitmap!=null)image.setImageBitmap(bitmap);
            }
        }
    }
    void tell(String text){message=text==null?"Something went wrong.":text;if(status!=null)status.setText(message);}
    int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);return l;}
    TextView label(String text,float size,int color,boolean bold){TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setTextColor(color);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    TextView note(String text){TextView v=label(text,12,MUTED,false);v.setPadding(0,dp(7),0,dp(10));return v;}
    TextView button(String title,View.OnClickListener listener){TextView b=label(title,12,TEXT,true);b.setGravity(Gravity.CENTER);b.setPadding(dp(12),dp(6),dp(12),dp(6));b.setMinWidth(dp(48));b.setMinHeight(dp(48));b.setMaxLines(2);b.setFocusable(true);b.setContentDescription(title);b.setBackground(buttonBackground(false));b.setOnClickListener(listener);return b;}
    TextView iconButton(String name,View.OnClickListener action){TextView view=button(name,action);view.setTextSize(10);view.setTypeface(Typeface.DEFAULT);view.setPadding(dp(6),dp(3),dp(6),dp(3));V1Icon icon=new V1Icon(name,TEXT);icon.setBounds(0,0,dp(22),dp(22));view.setCompoundDrawables(null,icon,null,null);view.setCompoundDrawablePadding(dp(3));view.setBackground(navBackground(false));return view;}
    GradientDrawable navBackground(boolean selected){GradientDrawable bg=new GradientDrawable();bg.setColor(selected?0xFF103047:BG);bg.setCornerRadius(dp(5));bg.setStroke(dp(1),selected?CYAN:0xFF172630);return bg;}
    GradientDrawable buttonBackground(boolean selected){GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{selected?0xFF16486A:CARD,selected?0xFF102B42:PANEL});bg.setCornerRadius(dp(7));bg.setStroke(dp(1),selected?CYAN:0xFF294357);return bg;}
    void addAction(String title,Runnable action){TextView b=button(title,v->action.run());if(tab==1&&!title.startsWith("Duration:")){if(contextualRow==null||contextualCount%2==0){contextualRow=row();LinearLayout.LayoutParams rowParams=new LinearLayout.LayoutParams(-1,-2);rowParams.topMargin=dp(6);inspector.addView(contextualRow,rowParams);}LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(0,dp(56),1);cell.rightMargin=dp(4);contextualRow.addView(b,cell);contextualCount++;return;}LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.topMargin=dp(6);inspector.addView(b,params);}
    interface IndexAction{void apply(int index);}
    interface ValueAction{void apply(float value);}
    interface TextAction{void apply(String text);}
    void select(String label,String[] values,int current,IndexAction action){addAction(label+": "+values[current],()->new AlertDialog.Builder(this).setTitle(label).setSingleChoiceItems(values,current,(d,i)->{d.dismiss();if(editable())action.apply(i);}).show());}
    void slider(String name,float value,ValueAction action){TextView title=note(name+"  "+Math.round(value*100)+"%");inspector.addView(title);SeekBar slider=new SeekBar(this);slider.setMax(100);slider.setProgress(Math.round(value*100));slider.setContentDescription(name);inspector.addView(slider,new LinearLayout.LayoutParams(-1,dp(48)));slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean user){title.setText(name+"  "+p+"%");}public void onStartTrackingTouch(SeekBar s){if(player!=null)player.pause();}public void onStopTrackingTouch(SeekBar s){if(editable())action.apply(s.getProgress()/100f);}});}
    LinearLayout form(){LinearLayout l=column();l.setPadding(dp(18),dp(8),dp(18),dp(10));return l;}
    EditText field(LinearLayout form,String title,String value){form.addView(note(title));EditText e=new EditText(this);e.setText(value);e.setTextColor(TEXT);e.setSingleLine(false);e.setMinHeight(dp(48));form.addView(e);return e;}
    void dialog(String title,LinearLayout form,Runnable apply,Runnable delete){ScrollView scroll=new ScrollView(this);scroll.addView(form);AlertDialog.Builder b=new AlertDialog.Builder(this).setTitle(title).setView(scroll).setPositiveButton("Apply",null).setNegativeButton("Cancel",null);if(delete!=null)b.setNeutralButton("Remove",(d,w)->{if(editable())delete.run();});AlertDialog dialog=b.create();dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{if(!editable())return;try{apply.run();dialog.dismiss();}catch(Exception e){Toast.makeText(this,e.getMessage()==null?"Check the entered values.":e.getMessage(),Toast.LENGTH_LONG).show();}}));dialog.show();}
    void numberDialog(String title,String value,TextAction action){LinearLayout form=form();EditText input=field(form,title,value);dialog(title,form,()->action.apply(input.getText().toString().trim()),null);}
    static long parseMs(String value){double seconds=Double.parseDouble(value);if(!Double.isFinite(seconds)||Math.abs(seconds)>7_200_000)throw new IllegalArgumentException("Enter a valid time in seconds.");return Math.round(seconds*1000);}
    static float parseFloat(EditText field){float value=Float.parseFloat(field.getText().toString());if(!Float.isFinite(value))throw new IllegalArgumentException("Enter a finite number.");return value;}
    static String seconds(long ms){return String.format(Locale.US,"%.2f",ms/1000d);}
    static String clock(long ms){return String.format(Locale.US,"%02d:%02d.%02d",ms/60000,(ms/1000)%60,(ms%1000)/10);}
}
