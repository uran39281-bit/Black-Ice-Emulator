package com.blackice.bc1;

import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static org.junit.Assume.*;

@RunWith(AndroidJUnit4.class)
public class WorkspaceTest {
    private final Instrumentation inst=InstrumentationRegistry.getInstrumentation();
    private TextView text(View view,String value) {
        if(view instanceof TextView && ((TextView)view).getText().toString().contains(value)) return (TextView)view;
        if(view instanceof ViewGroup) for(int i=0;i<((ViewGroup)view).getChildCount();i++) {
            TextView found=text(((ViewGroup)view).getChildAt(i),value);if(found!=null)return found;
        }
        return null;
    }
    private GLSurfaceView surface(View view) {
        if(view instanceof GLSurfaceView)return (GLSurfaceView)view;
        if(view instanceof ViewGroup) for(int i=0;i<((ViewGroup)view).getChildCount();i++) {
            GLSurfaceView found=surface(((ViewGroup)view).getChildAt(i));if(found!=null)return found;
        }
        return null;
    }
    private String shell(String command) throws Exception {
        try(ParcelFileDescriptor fd=inst.getUiAutomation().executeShellCommand(command);
            InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd);
            ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[1024];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
            return out.toString("UTF-8");
        }
    }
    private String override(String output) {
        for(String line:output.split("\n")) if(line.startsWith("Override"))return line.substring(line.indexOf(':')+1).trim();
        return "reset";
    }
    private void capture(String name) throws Exception {
        inst.waitForIdleSync();Thread.sleep(1500);
        Bitmap b=inst.getUiAutomation().takeScreenshot();assertNotNull(b);
        File dir=new File(inst.getTargetContext().getFilesDir(),"qa");assertTrue(dir.isDirectory()||dir.mkdirs());
        try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,out));}
        b.recycle();
    }
    @Test public void emulatorWorkspacesKeepMonitorAndToolsUsable() throws Exception {
        // Window overrides are confined to an emulator and restored even after a test failure.
        assumeTrue(Build.HARDWARE.contains("ranchu")||Build.HARDWARE.contains("goldfish"));
        String oldSize=override(shell("wm size")),oldDensity=override(shell("wm density"));
        try {
            shell("wm size 1080x1920");shell("wm density 320");
            inst.getTargetContext().getSharedPreferences("bc1-workspace",0).edit().putString("workspace-mode","AUTO").commit();
            try(ActivityScenario<WorkbenchActivity> scenario=ActivityScenario.launch(WorkbenchActivity.class)) {
                inst.waitForIdleSync();
                scenario.onActivity(a->{
                    View root=a.getWindow().getDecorView();GLSurfaceView gl=surface(root);assertNotNull(gl);
                    assertTrue(gl.getWidth()>0&&gl.getHeight()>0);
                    View parent=(View)gl.getParent();TextView effects=text(root,"EFFECTS");assertNotNull(effects);effects.performClick();
                    assertSame("Tab switch must retain GPU monitor",parent,gl.getParent());
                    assertNotNull(text(root,"EFFECT STRENGTH"));
                });
                capture("phone-9x16");
                shell("wm size 1920x1080");shell("wm density 160");Thread.sleep(1500);inst.waitForIdleSync();
                scenario.onActivity(a->{View root=a.getWindow().getDecorView();assertNotNull(text(root,"INSPECTOR"));assertNotNull(text(root,"PROJECT MEDIA"));assertTrue(surface(root).getWidth()>400);});
                capture("wide-16x9");
                shell("wm size 780x420");Thread.sleep(1500);inst.waitForIdleSync();
                inst.getTargetContext().getSharedPreferences("bc1-workspace",0).edit().putString("workspace-mode","WIDE").commit();
                scenario.recreate();inst.waitForIdleSync();
                capture("compact-wide");
                scenario.onActivity(a->{View root=a.getWindow().getDecorView();assertNotNull(text(root,"INSPECTOR"));assertNull(text(root,"PROJECT MEDIA"));assertTrue(surface(root).getWidth()>180);assertTrue(surface(root).getHeight()>90);});
            }
        } finally {
            shell("wm size "+oldSize);shell("wm density "+oldDensity);
            inst.getTargetContext().getSharedPreferences("bc1-workspace",0).edit().putString("workspace-mode","AUTO").commit();
        }
    }
}
