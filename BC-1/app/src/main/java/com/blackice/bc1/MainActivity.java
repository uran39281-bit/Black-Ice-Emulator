package com.blackice.bc1;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Color;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.AdapterView;

import com.blackice.bc1.engine.BcRenderer;
import com.blackice.bc1.engine.EngineSettings;
import java.io.IOException;
import java.io.InputStream;

/** Small independent test harness for the BC-1 GPU engine (NOT a finished editor). */
public final class MainActivity extends Activity {
    private static final int REQUEST_IMAGE = 405;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final EngineSettings settings = EngineSettings.defaults();
    private GLSurfaceView glView;
    private BcRenderer renderer;
    private SeekBar transitionSeek;
    private TextView progressLabel;
    private TextView statusLabel;
    private Button playButton;
    private boolean playing = false;
    private int requestedSlot = 0;
    private long lastTickNs;

    private final Runnable animator = new Runnable() {
        @Override public void run() {
            long now=System.nanoTime();
            if (playing) {
                if (lastTickNs != 0) {
                    float elapsed=(now-lastTickNs)/1_000_000_000f;
                    settings.progress += Math.min(.1f,elapsed)/3.5f;
                    if (settings.progress > 1f) settings.progress=0f;
                    transitionSeek.setProgress(Math.round(settings.progress*1000f));
                }
            }
            lastTickNs=now;
            handler.postDelayed(this,16);
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(10,14,23));
        getWindow().setNavigationBarColor(Color.rgb(10,14,23));
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(10,14,23));
        root.setPadding(dp(12),dp(8),dp(12),0);
        TextView heading=label("BC-1  /  ENGINE LAB",20,Color.rgb(149,226,255));
        heading.setTypeface(null,android.graphics.Typeface.BOLD);
        root.addView(heading);
        root.addView(label("v0.1  •  Snapdragon GPU prototype  •  OpenGL ES 3.0",12,Color.rgb(173,192,211)));

        glView=new GLSurfaceView(this);
        glView.setEGLContextClientVersion(3);
        glView.setPreserveEGLContextOnPause(true);
        renderer=new BcRenderer(this,settings,(gpu,fps,w,h)-> statusLabel.setText(
                "GPU: "+gpu+"   •   "+fps+" FPS   •   FBO: "+w+"×"+h));
        glView.setRenderer(renderer);
        glView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        LinearLayout.LayoutParams viewportParams=new LinearLayout.LayoutParams(-1,0,1f);
        viewportParams.topMargin=dp(8);
        viewportParams.bottomMargin=dp(4);
        root.addView(glView,viewportParams);
        statusLabel=label("Initializing GPU...",11,Color.rgb(135,188,216));
        root.addView(statusLabel);

        ScrollView scroller=new ScrollView(this);
        scroller.setFillViewport(false);
        LinearLayout tools=new LinearLayout(this);
        tools.setOrientation(LinearLayout.VERTICAL);
        tools.setPadding(0,dp(8),0,dp(14));
        scroller.addView(tools);
        LinearLayout.LayoutParams scrollParams=new LinearLayout.LayoutParams(-1,dp(276));
        root.addView(scroller,scrollParams);
        setContentView(root);

        LinearLayout files=row();
        Button importA=button("IMPORT A");
        Button importB=button("IMPORT B");
        files.addView(importA,weighted());
        files.addView(importB,weighted());
        tools.addView(files);
        importA.setOnClickListener(v->chooseImage(0));
        importB.setOnClickListener(v->chooseImage(1));

        tools.addView(label("TRANSITION",12,Color.rgb(126,195,222)));
        tools.addView(spinner(EngineSettings.TRANSITIONS,i->settings.transition=i));
        tools.addView(label("EFFECT ON LAYER A",12,Color.rgb(126,195,222)));
        tools.addView(spinner(EngineSettings.EFFECTS,i->settings.effect=i));

        progressLabel=label("Transition: 0%",13,Color.WHITE);
        tools.addView(progressLabel);
        transitionSeek=new SeekBar(this);
        transitionSeek.setMax(1000);
        transitionSeek.setProgress(0);
        transitionSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar,int progress,boolean fromUser) {
                if (fromUser) settings.progress=progress/1000f;
                progressLabel.setText("Transition: "+Math.round(settings.progress*100f)+"%");
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { playing=false; playButton.setText("PLAY"); }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        tools.addView(transitionSeek);
        tools.addView(label("Effect strength",12,Color.rgb(126,195,222)));
        SeekBar strength=new SeekBar(this);
        strength.setMax(100);
        strength.setProgress(60);
        strength.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar,int progress,boolean fromUser) { settings.intensity=progress/100f; }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        tools.addView(strength);
        tools.addView(label("Rendering quality",12,Color.rgb(126,195,222)));
        Spinner quality=spinner(EngineSettings.QUALITIES,i->settings.quality=i);
        quality.setSelection(1);
        tools.addView(quality);
        LinearLayout actions=row();
        playButton=button("PLAY");
        Button reset=button("RESET");
        actions.addView(playButton,weighted());
        actions.addView(reset,weighted());
        tools.addView(actions);
        playButton.setOnClickListener(v->{ playing=!playing;lastTickNs=System.nanoTime();playButton.setText(playing?"PAUSE":"PLAY"); });
        reset.setOnClickListener(v->{playing=false;playButton.setText("PLAY");settings.progress=0f;transitionSeek.setProgress(0);});
    }

    private interface Selected { void onSelected(int index); }
    private Spinner spinner(String[] values,Selected selected) {
        Spinner s=new Spinner(this);
        ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,values);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(a);
        s.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent,View view,int position,long id) {selected.onSelected(position);}
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        return s;
    }
    private Button button(String value) {
        Button b=new Button(this);
        b.setAllCaps(false);
        b.setText(value);
        return b;
    }
    private LinearLayout row() {LinearLayout line=new LinearLayout(this);line.setOrientation(LinearLayout.HORIZONTAL);return line;}
    private LinearLayout.LayoutParams weighted() {return new LinearLayout.LayoutParams(0,dp(46),1f);}
    private TextView label(String text,int sp,int color) {
        TextView t=new TextView(this);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(dp(3),dp(2),dp(3),dp(2));
        return t;
    }
    private int dp(int px) {return Math.round(px*getResources().getDisplayMetrics().density);}

    private void chooseImage(int slot) {
        requestedSlot=slot;
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(i,REQUEST_IMAGE);
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if (requestCode != REQUEST_IMAGE || resultCode != RESULT_OK || data == null) return;
        Uri uri=data.getData();
        if (uri == null) return;
        try {
            Bitmap b=loadDownsampled(uri);
            if (b == null) throw new IOException("Unsupported image");
            Bitmap fitted=fitToCanvas(b);
            b.recycle();
            final int slot=requestedSlot;
            glView.queueEvent(()->renderer.setInput(slot,fitted));
            android.widget.Toast.makeText(this,"Layer "+(slot==0?"A":"B")+" loaded",android.widget.Toast.LENGTH_SHORT).show();
        } catch(Exception ex) {
            android.widget.Toast.makeText(this,"Image import failed: "+ex.getMessage(),android.widget.Toast.LENGTH_LONG).show();
        }
    }
    private Bitmap loadDownsampled(Uri uri) throws IOException {
        BitmapFactory.Options options=new BitmapFactory.Options();
        options.inJustDecodeBounds=true;
        try(InputStream in=getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(in,null,options); }
        int sample=1;
        while (options.outWidth/sample>2048 || options.outHeight/sample>2048) sample*=2;
        options.inSampleSize=sample;
        options.inJustDecodeBounds=false;
        options.inPreferredConfig=Bitmap.Config.ARGB_8888;
        try(InputStream in=getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(in,null,options);
        }
    }
    /** Letterbox user images in 16:9 composition space rather than distorting aspect ratio. */
    private static Bitmap fitToCanvas(Bitmap source) {
        int width=1024, height=576;
        Bitmap result=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(result);
        canvas.drawColor(Color.BLACK);
        float fit=Math.min(width/(float)source.getWidth(),height/(float)source.getHeight());
        float w=source.getWidth()*fit, h=source.getHeight()*fit;
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(source,null,new RectF((width-w)*.5f,(height-h)*.5f,(width+w)*.5f,(height+h)*.5f),paint);
        return result;
    }
    @Override protected void onResume() {super.onResume();if(glView!=null)glView.onResume();lastTickNs=0;handler.post(animator);}
    @Override protected void onPause() {handler.removeCallbacks(animator);if(glView!=null)glView.onPause();super.onPause();}
}
