package com.blackice.bc1;

import android.app.Instrumentation;
import android.content.*;
import android.graphics.Bitmap;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.blackice.bc1.editor.*;
import java.io.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ExportDiagnosticsUiTest {
    private TextView text(View v,String s){if(v instanceof TextView&&s.contentEquals(((TextView)v).getText()))return (TextView)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){TextView found=text(((ViewGroup)v).getChildAt(i),s);if(found!=null)return found;}return null;}
    @Test public void setupShowsAndCopiesRedactedReport()throws Exception {
        Instrumentation inst=InstrumentationRegistry.getInstrumentation();Context context=inst.getTargetContext();
        String report=ExportDiagnostics.describe("saving",new IOException("content://private/customer.mp4"),0);
        context.getSharedPreferences(ExportService.PREFS,0).edit().putString(ExportService.LAST_FAILURE,report).commit();
        context.getSharedPreferences(EditorActivity.PREFS,0).edit().remove("last-project").commit();
        try(ActivityScenario<EditorActivity> scenario=ActivityScenario.launch(EditorActivity.class)){
            scenario.onActivity(a->{View root=a.getWindow().getDecorView();text(root,"Settings").performClick();text(root,"Export diagnostics").performClick();});
            inst.waitForIdleSync();
            AccessibilityNodeInfo root=inst.getUiAutomation().getRootInActiveWindow();assertNotNull(root);
            java.util.List<AccessibilityNodeInfo> copy=root.findAccessibilityNodeInfosByText("Copy report");assertEquals(1,copy.size());
            Bitmap screenshot=inst.getUiAutomation().takeScreenshot();assertNotNull(screenshot);File qa=new File(context.getFilesDir(),"qa");qa.mkdirs();
            try(OutputStream out=new FileOutputStream(new File(qa,"v104-export-diagnostics.png"))){screenshot.compress(Bitmap.CompressFormat.PNG,100,out);}screenshot.recycle();
            assertTrue(copy.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK));inst.waitForIdleSync();
            scenario.onActivity(a->{ClipboardManager clipboard=(ClipboardManager)a.getSystemService(Context.CLIPBOARD_SERVICE);
                assertTrue(clipboard.hasPrimaryClip());String copied=clipboard.getPrimaryClip().getItemAt(0).coerceToText(a).toString();
                assertTrue(copied.contains(report));assertTrue(copied.contains("BC-1 v2.0.0"));assertFalse(copied.contains("customer.mp4"));
                clipboard.setPrimaryClip(ClipData.newPlainText("", ""));
            });
        }
    }
}
