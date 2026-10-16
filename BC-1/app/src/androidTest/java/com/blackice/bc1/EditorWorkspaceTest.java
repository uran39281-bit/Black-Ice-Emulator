package com.blackice.bc1;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.blackice.bc1.editor.*;
import java.io.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static org.junit.Assume.*;

@RunWith(AndroidJUnit4.class)
public class EditorWorkspaceTest {
    private final Instrumentation inst=InstrumentationRegistry.getInstrumentation();
    private TextView text(View view,String value){if(view instanceof TextView&&((TextView)view).getText().toString().equals(value))return (TextView)view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){TextView found=text(((ViewGroup)view).getChildAt(i),value);if(found!=null)return found;}return null;}
    private SurfaceView surface(View view){if(view instanceof SurfaceView)return (SurfaceView)view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){SurfaceView found=surface(((ViewGroup)view).getChildAt(i));if(found!=null)return found;}return null;}
    private String shell(String command)throws Exception{try(ParcelFileDescriptor fd=inst.getUiAutomation().executeShellCommand(command);InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){return new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}}
    private String override(String value){for(String line:value.split("\n"))if(line.startsWith("Override"))return line.substring(line.indexOf(':')+1).trim();return "reset";}
    private void capture(String name)throws Exception{inst.waitForIdleSync();Thread.sleep(1000);Bitmap b=inst.getUiAutomation().takeScreenshot();assertNotNull(b);File dir=new File(inst.getTargetContext().getFilesDir(),"qa");dir.mkdirs();try(OutputStream out=new FileOutputStream(new File(dir,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
    @Test public void editorAdaptsAndTabsKeepVideoSurface()throws Exception{
        assumeTrue(Build.HARDWARE.contains("ranchu")||Build.HARDWARE.contains("goldfish"));String size=override(shell("wm size")),density=override(shell("wm density"));
        EditProject p=new EditProject();p.name="Ice • First Cut";
        File image=new File(inst.getTargetContext().getFilesDir(),"workspace-demo.png");Bitmap b=Bitmap.createBitmap(640,360,Bitmap.Config.ARGB_8888);android.graphics.Canvas c=new android.graphics.Canvas(b);android.graphics.Paint paint=new android.graphics.Paint();paint.setShader(new android.graphics.LinearGradient(0,0,640,360,0xFF12364B,0xFF54BDE1,android.graphics.Shader.TileMode.CLAMP));c.drawPaint(paint);try(OutputStream out=new FileOutputStream(image)){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();
        for(int i=0;i<2;i++){EditProject.Clip clip=new EditProject.Clip();clip.name=i==0?"ICE / OPENING":"ICE / DETAIL";clip.uri=android.net.Uri.fromFile(image).toString();clip.image=true;clip.sourceDurationMs=3000;clip.outMs=3000;clip.transition=0;p.clips.add(clip);}
        p.clips.get(1).track=1;p.clips.get(1).startMs=750;p.clips.get(1).scale=.6f;p.clips.get(1).x=.4f;p.clips.get(1).motion.put(3,new Motion.Key(0,0));p.clips.get(1).motion.put(3,new Motion.Key(3000,30));
        EditProject.Overlay title=new EditProject.Overlay();title.text="BLACK ICE";title.y=0;p.overlays.add(title);new ProjectStore(inst.getTargetContext()).save(p);
        inst.getTargetContext().getSharedPreferences(EditorActivity.PREFS,0).edit().putString("last-project",p.id).putString("mode","AUTO").commit();
        try{
            shell("wm size 1080x1920");shell("wm density 320");
            try(ActivityScenario<EditorActivity> scenario=ActivityScenario.launch(EditorActivity.class)){
                Thread.sleep(3500);inst.waitForIdleSync();scenario.onActivity(a->{View root=a.getWindow().getDecorView();assertNotNull("Sample project must be loaded before visual checks",text(root,"Ice • First Cut  ▾"));SurfaceView video=surface(root);assertNotNull(video);assertTrue(video.getWidth()>0&&video.getHeight()>0);View parent=(View)video.getParent();text(root,"Effects").performClick();assertSame(parent,video.getParent());assertNotNull(text(root,"GPU EFFECTS"));});capture("v05-phone-9x16");
                shell("wm size 1920x1080");shell("wm density 160");Thread.sleep(1800);inst.waitForIdleSync();scenario.onActivity(a->{assertTrue(surface(a.getWindow().getDecorView()).getWidth()>500);});capture("v05-wide-16x9");
                inst.getTargetContext().getSharedPreferences(EditorActivity.PREFS,0).edit().putString("mode","WIDE").commit();shell("wm size 780x420");scenario.recreate();Thread.sleep(2500);inst.waitForIdleSync();scenario.onActivity(a->{assertNotNull("Wide preference must be restored",text(a.getWindow().getDecorView(),"WIDE"));SurfaceView video=surface(a.getWindow().getDecorView());assertTrue(video.getWidth()>150&&video.getHeight()>60);});capture("v05-compact-wide");
            }
        }finally{shell("wm size "+size);shell("wm density "+density);inst.getTargetContext().getSharedPreferences(EditorActivity.PREFS,0).edit().putString("mode","AUTO").commit();}
    }
}
