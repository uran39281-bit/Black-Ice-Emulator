package com.blackice.bc1;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.blackice.bc1.engine.BcRenderer;
import com.blackice.bc1.engine.EngineSettings;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/** Black Ice V1-inspired dual-mode WORKSPACE for the independent BC-1 effects lab.
 * This is not a finished timeline editor: the tracks visualize two still inputs.
 */
public final class WorkbenchActivity extends Activity {
    private static final int IMAGE_PICKER = 405;
    private static final int BACKGROUND = 0xFF090F19;
    private static final int PANEL = 0xFF111C2A;
    private static final int PANEL_LIGHT = 0xFF172638;
    private static final int CYAN = 0xFF40BFFF;
    private static final int TEXT = 0xFFE6F5FF;
    private static final int MUTED = 0xFF91A7BC;
    private static final String PREF_FILE = "bc1-workspace";
    private static final String PREF_MODE = "workspace-mode";

    private final EngineSettings engine = EngineSettings.defaults();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private GLSurfaceView glView;
    private BcRenderer renderer;
    private TextView status, play, progressText, modeText;
    private SeekBar progressBar;
    private TimelineView timeline;
    private LinearLayout panelContent;
    private LayoutPolicy.Preference preference = LayoutPolicy.Preference.AUTO;
    private LayoutPolicy.Workspace currentWorkspace;
    private int activeTab = 1; // 0 = Media, 1 = Edit, 2 = Effects, 3 = Settings
    private boolean playing;
    private int requestedImageSlot;
    private long lastFrameNs;
    private boolean resumed;

    private final Runnable playback = new Runnable() {
        @Override public void run() {
            if (!resumed) return;
            long now = System.nanoTime();
            if (playing && lastFrameNs != 0) {
                float seconds = Math.min(0.10f, (now - lastFrameNs) / 1_000_000_000f);
                engine.progress += seconds / 3.5f;
                if (engine.progress >= 1.0f) engine.progress -= 1.0f;
                syncProgress();
            }
            lastFrameNs = now;
            handler.postDelayed(this, 33);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(BACKGROUND);
        String saved = getSharedPreferences(PREF_FILE, MODE_PRIVATE).getString(PREF_MODE, "AUTO");
        try { preference = LayoutPolicy.Preference.valueOf(saved); }
        catch (IllegalArgumentException ex) { preference = LayoutPolicy.Preference.AUTO; }
        if (state != null) {
            engine.progress = state.getFloat("progress", 0f);
            engine.transition = state.getInt("transition", 0);
            engine.effect = state.getInt("effect", 0);
            engine.quality = state.getInt("quality", 1);
            engine.intensity = state.getFloat("intensity", .6f);
            activeTab = state.getInt("tab", 1);
        }
        glView = new GLSurfaceView(this);
        glView.setEGLContextClientVersion(3);
        glView.setPreserveEGLContextOnPause(true);
        renderer = new BcRenderer(this, engine, (gpu, fps, w, h) -> {
            if (status != null) status.setText("GPU: " + gpu + "    •    " + fps + " FPS    •    " + w + "×" + h);
        });
        glView.setRenderer(renderer);
        glView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        showWorkspace();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putFloat("progress", engine.progress);
        out.putInt("transition", engine.transition);
        out.putInt("effect", engine.effect);
        out.putInt("quality", engine.quality);
        out.putFloat("intensity", engine.intensity);
        out.putInt("tab", activeTab);
        super.onSaveInstanceState(out);
    }
    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        showWorkspace();
    }
    @Override protected void onResume() {
        super.onResume();
        glView.onResume();
        resumed = true;
        lastFrameNs = 0;
        handler.post(playback);
    }
    @Override protected void onPause() {
        resumed = false;
        handler.removeCallbacks(playback);
        glView.onPause();
        super.onPause();
    }
    @Override protected void onDestroy() {
        handler.removeCallbacks(playback);
        super.onDestroy();
    }

    private void showWorkspace() {
        Configuration c = getResources().getConfiguration();
        int width = c.screenWidthDp;
        int height = c.screenHeightDp;
        Display display = getWindowManager().getDefaultDisplay();
        boolean onExternal = display != null && display.getDisplayId() != Display.DEFAULT_DISPLAY;
        LayoutPolicy.Workspace next = LayoutPolicy.resolve(preference, width, height, c.smallestScreenWidthDp, onExternal);
        currentWorkspace = next;
        progressBar = null;
        timeline = null;
        play = null;
        status = null;
        panelContent = null;
        LinearLayout root = column();
        root.setBackgroundColor(BACKGROUND);
        root.addView(topBar(), height(60));
        if (next == LayoutPolicy.Workspace.WIDE) buildWide(root, width);
        else buildPhone(root);
        setContentView(root);
        syncProgress();
    }

    private View topBar() {
        LinearLayout bar = row();
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(3), dp(10), dp(3));
        bar.setBackground(makeBackground(0xFF101A29, 0xFF0B131E, 0, 0));
        TextView mark = text("❄", 26, CYAN, true);
        bar.addView(mark, width(38));
        LinearLayout brand = column();
        brand.addView(text("BLACK ICE EDITOR", 15, TEXT, true));
        brand.addView(text("BC-1 ENGINE LAB  /  v0.2", 9, MUTED, false));
        bar.addView(brand, rowWeight(1));
        modeText = text(preference.name()+"  ▾", 11, CYAN, true);
        modeText.setPadding(dp(8), dp(10), dp(8), dp(10));
        modeText.setBackground(makeBackground(PANEL_LIGHT, PANEL, 9, 0xFF246999));
        modeText.setOnClickListener(v -> selectWorkspace());
        bar.addView(modeText);
        return bar;
    }

    private void buildPhone(LinearLayout root) {
        ScrollView scroll = scroll();
        scroll.setFillViewport(false);
        LinearLayout content = column();
        content.setPadding(dp(10), dp(10), dp(10), dp(8));
        scroll.addView(content);
        root.addView(scroll, columnWeight(1));
        content.addView(headerLine("PREVIEW  ·  16:9 COMPOSITION", "PHONE WORKSPACE"));
        View preview = preview();
        content.addView(preview, matchWrap());
        content.addView(transport());
        content.addView(gap(10));
        content.addView(headerLine("DEMO TIMELINE  •  A / B STILLS", "NOT VIDEO TRACKS"));
        timeline = new TimelineView(this);
        content.addView(timeline, height(184));
        content.addView(gap(9));
        content.addView(headerLine("TOOLS", "ENGINE PARAMETERS"));
        panelContent = column();
        content.addView(panelContent);
        populateTools();
        status = text("GPU initializing…", 10, MUTED, false);
        content.addView(status);
        content.addView(gap(8));
        LinearLayout nav = row();
        nav.setPadding(dp(6), dp(6), dp(6), dp(6));
        nav.setBackground(makeBackground(PANEL, BACKGROUND, 12, 0xFF263B53));
        String[] tabs = {"MEDIA", "EDIT", "EFFECTS", "SETTINGS"};
        String[] icons = {"▣", "✂", "✧", "⚙"};
        for (int i=0;i<tabs.length;i++) {
            final int index=i;
            TextView button = text(icons[i]+"\n"+tabs[i], 10, activeTab==i?CYAN:MUTED, activeTab==i);
            button.setGravity(Gravity.CENTER);
            button.setPadding(dp(2), dp(7), dp(2), dp(7));
            if (activeTab==i) button.setBackground(makeBackground(0xFF154464, 0xFF10253A, 10, 0xFF287EAB));
            button.setOnClickListener(v -> { activeTab=index; showWorkspace(); });
            nav.addView(button, rowWeight(1));
        }
        root.addView(nav, matchWrap());
    }

    private void buildWide(LinearLayout root, int widthDp) {
        LinearLayout workspace = row();
        root.addView(workspace, columnWeight(1));
        LinearLayout rail = column();
        rail.setPadding(dp(5), dp(8), dp(5), 0);
        rail.setBackgroundColor(0xFF0D1928);
        workspace.addView(rail, width(68));
        String[] railItems={"▣\nMEDIA", "✂\nEDIT", "✧\nFX", "⚙\nSET"};
        for (int i=0;i<4;i++) {
            final int idx=i;
            TextView tile=text(railItems[i],10,i==activeTab?CYAN:MUTED,i==activeTab);
            tile.setGravity(Gravity.CENTER);
            tile.setPadding(0,dp(14),0,dp(14));
            if (i==activeTab) tile.setBackground(makeBackground(PANEL_LIGHT, PANEL, 9, 0xFF287EAB));
            tile.setOnClickListener(v->{activeTab=idx;showWorkspace();});
            rail.addView(tile);
        }
        // Reduce side panels on small DeX windows instead of distorting the preview.
        int libraryWidth = widthDp < 940 ? 154 : 218;
        int inspectorWidth = widthDp < 940 ? 206 : 270;
        LinearLayout library=column();
        library.setPadding(dp(10),dp(12),dp(8),dp(10));
        library.setBackgroundColor(PANEL);
        workspace.addView(library,width(libraryWidth));
        library.addView(text("PROJECT MEDIA", 13, TEXT, true));
        library.addView(text("2 still-image inputs",10,MUTED,false));
        library.addView(gap(12));
        library.addView(mediaTile("A  /  ICE", "IMPORT IMAGE A", 0));
        library.addView(gap(10));
        library.addView(mediaTile("B  /  EMBER", "IMPORT IMAGE B", 1));
        library.addView(gap(16));
        TextView hint = text("BC-1 test assets\nNo imported videos or saved projects yet.",11,MUTED,false);
        library.addView(hint);

        LinearLayout center = column();
        center.setPadding(dp(10),dp(8),dp(10),dp(8));
        workspace.addView(center,rowWeightFill(1));
        center.addView(headerLine("PROGRAM MONITOR", "16:9"));
        FrameLayout previewHost = new FrameLayout(this);
        previewHost.setBackground(makeBackground(0xFF0A1523, 0xFF08121C, 12, 0xFF243E57));
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER);
        previewHost.addView(preview(),p);
        center.addView(previewHost,columnWeight(1));
        center.addView(transport());
        status=text("GPU initializing…",10,MUTED,false);
        center.addView(status);

        ScrollView inspectorScroll=scroll();
        inspectorScroll.setBackgroundColor(PANEL);
        LinearLayout inspector = column();
        inspector.setPadding(dp(10),dp(12),dp(10),dp(14));
        inspector.addView(text("INSPECTOR",13,CYAN,true));
        inspector.addView(gap(12));
        panelContent=column();
        inspector.addView(panelContent);
        populateTools();
        inspectorScroll.addView(inspector);
        workspace.addView(inspectorScroll,width(inspectorWidth));

        LinearLayout bottom = column();
        bottom.setPadding(dp(10),dp(5),dp(10),dp(7));
        bottom.setBackgroundColor(0xFF101B2A);
        root.addView(bottom,height(212));
        bottom.addView(headerLine("DEMO TIMELINE  /  STILL A → STILL B", "SCRUB TO PREVIEW"));
        timeline=new TimelineView(this);
        bottom.addView(timeline,columnWeight(1));
    }

    private View mediaTile(String title,String subtitle,int slot) {
        LinearLayout tile = column();
        tile.setPadding(dp(9),dp(15),dp(6),dp(15));
        tile.setBackground(makeBackground(0xFF15354D, 0xFF101B2B, 9, 0xFF325573));
        tile.addView(text(title,13,CYAN,true));
        tile.addView(text(subtitle,10,TEXT,false));
        tile.setOnClickListener(v->pickImage(slot));
        return tile;
    }

    private View preview() {
        detach(glView);
        AspectFrame frame=new AspectFrame(this);
        frame.setBackgroundColor(Color.BLACK);
        frame.addView(glView,new FrameLayout.LayoutParams(-1,-1));
        return frame;
    }
    private static void detach(View child) {
        if (child.getParent() instanceof ViewGroup) ((ViewGroup)child.getParent()).removeView(child);
    }

    private View transport() {
        LinearLayout strip = row();
        strip.setGravity(Gravity.CENTER_VERTICAL);
        strip.setPadding(dp(5),dp(6),dp(5),dp(5));
        progressText=text("00:00.0 / 00:03.5",11,MUTED,false);
        strip.addView(progressText);
        strip.addView(gapHorizontal(12));
        play=text(playing ? "Ⅱ PAUSE" : "▶ PLAY",13,CYAN,true);
        play.setGravity(Gravity.CENTER);
        play.setPadding(dp(12),dp(8),dp(12),dp(8));
        play.setBackground(makeBackground(PANEL_LIGHT,PANEL,10,0xFF254965));
        play.setOnClickListener(v -> {
            playing = !playing;
            lastFrameNs = System.nanoTime();
            updatePlayText();
        });
        strip.addView(play);
        return strip;
    }

    private void populateTools() {
        if (panelContent == null) return;
        panelContent.removeAllViews();
        switch (activeTab) {
            case 0:
                panelContent.addView(text("IMAGE SOURCES",13,CYAN,true));
                panelContent.addView(action("IMPORT IMAGE A",()->pickImage(0)));
                panelContent.addView(action("IMPORT IMAGE B",()->pickImage(1)));
                panelContent.addView(text("Tap a source to replace its demonstration image. Images are processed on the GPU.",11,MUTED,false));
                break;
            case 1:
                panelContent.addView(text("TRANSITION",12,CYAN,true));
                panelContent.addView(spinner(EngineSettings.TRANSITIONS,engine.transition,i->engine.transition=i));
                panelContent.addView(gap(7));
                panelContent.addView(text("TRANSITION PROGRESS",12,CYAN,true));
                progressBar=seek(Math.round(engine.progress*1000),1000,i->{engine.progress=i/1000f;syncProgress();});
                panelContent.addView(progressBar);
                panelContent.addView(text("Scrub the preview above or press Play to animate the A/B transition.",11,MUTED,false));
                break;
            case 2:
                panelContent.addView(text("EFFECT ON IMAGE A",12,CYAN,true));
                panelContent.addView(spinner(EngineSettings.EFFECTS,engine.effect,i->engine.effect=i));
                panelContent.addView(gap(8));
                panelContent.addView(text("EFFECT STRENGTH",12,CYAN,true));
                panelContent.addView(seek(Math.round(engine.intensity*100),100,i->engine.intensity=i/100f));
                panelContent.addView(text("Gaussian Blur · Glow · Chroma Key · Grayscale · Opacity",11,MUTED,false));
                break;
            default:
                panelContent.addView(text("RENDER QUALITY",12,CYAN,true));
                panelContent.addView(spinner(EngineSettings.QUALITIES,engine.quality,i->engine.quality=i));
                panelContent.addView(gap(10));
                panelContent.addView(action("CHOOSE UI LAYOUT",this::selectWorkspace));
                panelContent.addView(action("EXPORT (NOT YET IMPLEMENTED)", ()->
                        Toast.makeText(this,"Video export arrives in a later BC-1 release",Toast.LENGTH_LONG).show()));
                panelContent.addView(text("Preview composition remains 16:9 in both layouts. Changing UI mode never changes media or export settings.",11,MUTED,false));
        }
    }

    private void selectWorkspace() {
        String[] options={"Automatic (recommended)","Phone UI · portrait 9:16", "Wide UI · tablet / Samsung DeX"};
        int selected=preference.ordinal();
        new AlertDialog.Builder(this).setTitle("BC-1 workspace layout")
            .setSingleChoiceItems(options,selected,(dialog,which)->{
                preference=LayoutPolicy.Preference.values()[which];
                getSharedPreferences(PREF_FILE,MODE_PRIVATE).edit().putString(PREF_MODE,preference.name()).apply();
                dialog.dismiss();
                showWorkspace();
                if (preference==LayoutPolicy.Preference.WIDE && currentWorkspace!=LayoutPolicy.Workspace.WIDE)
                    Toast.makeText(this,"Rotate to landscape, expand the window, or connect DeX for Wide UI",Toast.LENGTH_LONG).show();
            }).setNegativeButton("Cancel",null).show();
    }

    private interface Choose { void accept(int index); }
    private Spinner spinner(String[] options, int selected, Choose whenSelected) {
        Spinner spinner=new Spinner(this);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,options);
        spinner.setAdapter(adapter);
        spinner.setSelection(selected);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent,View view,int position,long id) {whenSelected.accept(position);}
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        return spinner;
    }
    private SeekBar seek(int initial,int max,Choose changed) {
        SeekBar bar = new SeekBar(this);
        bar.setMax(max);
        bar.setProgress(initial);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(CYAN));
        bar.setThumbTintList(android.content.res.ColorStateList.valueOf(CYAN));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b,int value,boolean fromUser) {if(fromUser) changed.accept(value);}
            @Override public void onStartTrackingTouch(SeekBar b) { }
            @Override public void onStopTrackingTouch(SeekBar b) { }
        });
        return bar;
    }

    private void syncProgress() {
        if (progressBar != null && progressBar.getProgress()!=Math.round(engine.progress*1000))
            progressBar.setProgress(Math.round(engine.progress*1000));
        if (progressText != null) progressText.setText(String.format(Locale.US,"00:%04.1f / 00:03.5",engine.progress*3.5f));
        if (timeline != null) timeline.invalidate();
    }
    private void updatePlayText(){if(play!=null)play.setText(playing?"Ⅱ PAUSE":"▶ PLAY");}
    /** Simple hardware keyboard controls for DeX and future desktop UX tests. */
    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_SPACE) {
            playing = !playing;
            lastFrameNs = System.nanoTime();
            updatePlayText();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            playing = false;
            engine.progress = Math.max(0f,Math.min(1f,engine.progress +
                (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? .025f : -.025f)));
            updatePlayText();
            syncProgress();
            return true;
        }
        return super.onKeyDown(keyCode,event);
    }
    private void pickImage(int slot) {
        requestedImageSlot=slot;
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent,IMAGE_PICKER);
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if (request != IMAGE_PICKER || result != RESULT_OK || data == null || data.getData()==null) return;
        try {
            Bitmap src=readImage(data.getData());
            if (src==null) throw new IOException("Unsupported image");
            Bitmap fitted=fit16x9(src);
            src.recycle();
            int slot=requestedImageSlot;
            glView.queueEvent(()->renderer.setInput(slot,fitted));
            Toast.makeText(this,"Image "+(slot==0?"A":"B")+" loaded",Toast.LENGTH_SHORT).show();
        } catch (Exception e) {Toast.makeText(this,"Import failed: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }
    private Bitmap readImage(Uri uri) throws IOException {
        BitmapFactory.Options info=new BitmapFactory.Options();
        info.inJustDecodeBounds=true;
        try(InputStream s=getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(s,null,info);}
        int sample=1;
        while(info.outWidth/sample>2048 || info.outHeight/sample>2048) sample*=2;
        info.inJustDecodeBounds=false;
        info.inSampleSize=sample;
        info.inPreferredConfig=Bitmap.Config.ARGB_8888;
        try(InputStream s=getContentResolver().openInputStream(uri)){return BitmapFactory.decodeStream(s,null,info);}
    }
    private static Bitmap fit16x9(Bitmap bitmap) {
        int w=1024,h=576;
        Bitmap target=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(target);
        canvas.drawColor(Color.BLACK);
        float scale=Math.min((float)w/bitmap.getWidth(),(float)h/bitmap.getHeight());
        float bw=bitmap.getWidth()*scale,bh=bitmap.getHeight()*scale;
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(bitmap,null,new RectF((w-bw)/2,(h-bh)/2,(w+bw)/2,(h+bh)/2),p);
        return target;
    }

    private View action(String title,Runnable runnable) {
        TextView view=text(title,11,CYAN,true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(10),dp(13),dp(10),dp(13));
        view.setBackground(makeBackground(PANEL_LIGHT,PANEL,9,0xFF295375));
        view.setOnClickListener(v->runnable.run());
        LinearLayout wrap=column();
        wrap.addView(gap(5));
        wrap.addView(view);
        return wrap;
    }
    private View headerLine(String left,String right) {
        LinearLayout line=row();
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(dp(2),dp(5),dp(2),dp(8));
        line.addView(text(left,11,TEXT,true),rowWeight(1));
        line.addView(text(right,9,CYAN,false));
        return line;
    }
    private GradientDrawable makeBackground(int top,int bottom,int radius,int stroke) {
        GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{top,bottom});
        bg.setCornerRadius(dp(radius));
        if(stroke!=0)bg.setStroke(dp(1),stroke);
        return bg;
    }
    private TextView text(String value,int size,int color,boolean bold) {
        TextView t=new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private static LinearLayout column(Context context) {LinearLayout l=new LinearLayout(context);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout column() {return column(this);}
    private LinearLayout row() {LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);return l;}
    private ScrollView scroll() {ScrollView s=new ScrollView(this);s.setFillViewport(false);s.setVerticalScrollBarEnabled(false);return s;}
    private int dp(float value) {return Math.round(getResources().getDisplayMetrics().density*value);}
    private LinearLayout.LayoutParams rowWeight(float value) {return new LinearLayout.LayoutParams(0,-2,value);}
    private LinearLayout.LayoutParams rowWeightFill(float value) {return new LinearLayout.LayoutParams(0,-1,value);}
    private LinearLayout.LayoutParams columnWeight(float value) {return new LinearLayout.LayoutParams(-1,0,value);}
    private LinearLayout.LayoutParams width(int value) {return new LinearLayout.LayoutParams(dp(value),-1);}
    private LinearLayout.LayoutParams height(int value) {return new LinearLayout.LayoutParams(-1,dp(value));}
    private LinearLayout.LayoutParams matchWrap() {return new LinearLayout.LayoutParams(-1,-2);}
    private View gap(int dp) {View v=new View(this);v.setLayoutParams(height(dp));return v;}
    private View gapHorizontal(int dp) {View v=new View(this);v.setLayoutParams(new LinearLayout.LayoutParams(dp(dp),1));return v;}

    /** Measure a fullscreen preview inside available bounds, never stretch content. */
    private static final class AspectFrame extends FrameLayout {
        AspectFrame(Context context) {super(context);}
        @Override protected void onMeasure(int ws,int hs) {
            int width=MeasureSpec.getSize(ws);
            int height=MeasureSpec.getSize(hs);
            int wm=MeasureSpec.getMode(ws);
            int hm=MeasureSpec.getMode(hs);
            if (wm==MeasureSpec.UNSPECIFIED && hm==MeasureSpec.UNSPECIFIED) {width=640;height=360;}
            else if (hm==MeasureSpec.UNSPECIFIED) height=Math.max(1,Math.round(width*9f/16f));
            else if (wm==MeasureSpec.UNSPECIFIED) width=Math.max(1,Math.round(height*16f/9f));
            else if (width>0 && height>0) {
                if (width/(float)height>16f/9f) width=Math.max(1,Math.round(height*16f/9f));
                else height=Math.max(1,Math.round(width*9f/16f));
            }
            super.onMeasure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),
                            MeasureSpec.makeMeasureSpec(height,MeasureSpec.EXACTLY));
        }
    }

    /** An intentionally labeled demo, not an editable audio/video timeline. Touch to scrub. */
    private final class TimelineView extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        TimelineView(Context context){super(context);setBackground(makeBackground(PANEL,BACKGROUND,10,0xFF244160));}
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float d=getResources().getDisplayMetrics().density;
            float w=getWidth(),h=getHeight();
            float start=58*d, end=w-13*d;
            if(end<=start)return;
            p.setTypeface(Typeface.DEFAULT);
            p.setTextSize(9*d);
            p.setStrokeWidth(1*d);
            for(int i=0;i<=7;i++) {
                float x=start+(end-start)*i/7f;
                p.setColor(0xFF47657F);
                canvas.drawLine(x,16*d,x,25*d,p);
                p.setColor(MUTED);
                canvas.drawText(String.format(Locale.US,"%02d",i/2),x-5*d,12*d,p);
            }
            String[] labels={"IMAGE A","IMAGE B","WIPE"};
            int[] fills={0xFF215A80,0xFF19486B,0xFF613E9B};
            float trackHeight=Math.min(34*d,(h-42*d)/3f);
            for(int i=0;i<3;i++) {
                float y=31*d+i*(trackHeight+3*d);
                p.setColor(MUTED);
                p.setTextSize(8*d);
                canvas.drawText(labels[i],5*d,y+17*d,p);
                p.setColor(fills[i]);
                canvas.drawRoundRect(start,y,end,y+trackHeight,5*d,5*d,p);
                p.setColor(i==2?0xFFD5C0FF:TEXT);
                p.setTextSize(10*d);
                canvas.drawText(i==0?"ICE / INPUT A":i==1?"EMBER / INPUT B":EngineSettings.TRANSITIONS[engine.transition],start+9*d,y+trackHeight*.65f,p);
            }
            float x=start+(end-start)*engine.progress;
            p.setColor(CYAN);p.setStrokeWidth(2*d);
            canvas.drawLine(x,24*d,x,Math.min(h-5*d,34*d+3*(trackHeight+3*d)),p);
            canvas.drawCircle(x,25*d,4*d,p);
        }
        @Override public boolean onTouchEvent(MotionEvent event) {
            if(event.getAction()==MotionEvent.ACTION_DOWN||event.getAction()==MotionEvent.ACTION_MOVE){
                float d=getResources().getDisplayMetrics().density;
                float start=58*d,end=getWidth()-13*d;
                if(end>start){engine.progress=Math.max(0f,Math.min(1f,(event.getX()-start)/(end-start)));syncProgress();}
                playing=false;updatePlayText();return true;
            }
            return event.getAction()==MotionEvent.ACTION_UP || super.onTouchEvent(event);
        }
    }
}
