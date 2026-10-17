package com.blackice.bc1;

import android.app.Instrumentation;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.blackice.bc1.editor.*;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Selection-based workspace on phone 9:16 and DeX 16:9: Add drawer, contextual top bar, focused panels
 * with Back, keyframe diamonds, layer locks / visibility, multi-select groups and save/reopen. */
@RunWith(AndroidJUnit4.class)
public class WorkspaceV2Test {
    final Instrumentation inst=InstrumentationRegistry.getInstrumentation();
    static View described(View v,String s){if(s.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=described(((ViewGroup)v).getChildAt(i),s);if(found!=null)return found;}return null;}
    static TextView text(View v,String s){if(v instanceof TextView&&s.contentEquals(((TextView)v).getText()))return (TextView)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){TextView found=text(((ViewGroup)v).getChildAt(i),s);if(found!=null)return found;}return null;}
    static TextView startsWith(View v,String s){if(v instanceof TextView&&((TextView)v).getText().toString().startsWith(s))return (TextView)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){TextView found=startsWith(((ViewGroup)v).getChildAt(i),s);if(found!=null)return found;}return null;}
    static EditText field(View v,String description){View f=described(v,description);return f instanceof EditText?(EditText)f:null;}
    String shell(String command)throws Exception{try(ParcelFileDescriptor fd=inst.getUiAutomation().executeShellCommand(command);InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){return new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}}
    String override(String value){for(String line:value.split("\n"))if(line.startsWith("Override"))return line.substring(line.indexOf(':')+1).trim();return "reset";}
    void capture(String name)throws Exception{inst.waitForIdleSync();Bitmap b=inst.getUiAutomation().takeScreenshot();assertNotNull(b);File qa=new File(inst.getTargetContext().getFilesDir(),"qa");qa.mkdirs();try(OutputStream out=new FileOutputStream(new File(qa,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
    void waitLoaded(ActivityScenario<EditorActivity> scenario)throws Exception{AtomicBoolean loaded=new AtomicBoolean();long deadline=SystemClock.elapsedRealtime()+15000;while(!loaded.get()&&SystemClock.elapsedRealtime()<deadline){scenario.onActivity(a->loaded.set(!a.loading));Thread.sleep(50);}assertTrue(loaded.get());inst.waitForIdleSync();}

    @Test public void addDrawerPanelsLayersAndKeyframesOnPhoneAndDex()throws Exception{
        String size=override(shell("wm size")),density=override(shell("wm density"));
        try{for(boolean wide:new boolean[]{false,true}){
            shell("wm size "+(wide?"1920x1080":"1080x1920"));shell("wm density "+(wide?"160":"320"));
            EditProject p=new EditProject();p.name="V2 workspace QA";File file=new File(inst.getTargetContext().getFilesDir(),"v2-picture.png");Bitmap b=Bitmap.createBitmap(320,180,Bitmap.Config.ARGB_8888);b.eraseColor(0xFF1C5D85);try(OutputStream out=new FileOutputStream(file)){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();
            EditProject.Clip c=new EditProject.Clip();c.name="Backdrop";c.image=true;c.uri=android.net.Uri.fromFile(file).toString();c.outMs=c.sourceDurationMs=4000;p.clips.add(c);new ProjectStore(inst.getTargetContext()).save(p);
            inst.getTargetContext().getSharedPreferences(EditorActivity.PREFS,0).edit().putString("last-project",p.id).putString("mode","AUTO").putBoolean("ui-locked",false).putInt("workspace-focus",0).commit();
            try(ActivityScenario<EditorActivity> scenario=ActivityScenario.launch(EditorActivity.class)){
                waitLoaded(scenario);
                // Browse state: project name, Settings and Export; clip tools hidden.
                scenario.onActivity(a->{View root=a.getWindow().getDecorView();assertNotNull(text(root,"Settings"));assertNotNull(text(root,"Export"));assertNull(text(root,"Split"));
                    text(root,"+ Add").performClick();for(String category:AddDrawer.CATEGORIES)assertNotNull("Add drawer category "+category,described(root,"Add "+category));
                    assertNotNull("Insert at playhead by default",text(root,"● At playhead"));described(root,"Add Shapes").performClick();});
                Thread.sleep(300);capture(wide?"v2-wide-add-drawer":"v2-phone-add-drawer");
                scenario.onActivity(a->{View root=a.getWindow().getDecorView();a.seek(1000);text(root,"+ Rectangle").performClick();
                    assertEquals(1,a.project.overlays.size());assertEquals("Inserted at the playhead",1000,a.project.overlays.get(0).startMs);assertEquals(0,a.selectedOverlay);
                    // Selected layer: name, Duplicate, Delete, More replace Settings / Export.
                    assertNotNull(text(root,"Rectangle"));assertNull(text(root,"Settings"));assertNotNull(described(root,"Duplicate"));assertNotNull(described(root,"More actions"));
                    assertNotNull("Shape tools",text(root,"Color"));assertNull("No slip for shapes",text(root,"Slip"));assertNotNull(text(root,"Move & Transform"));
                    text(root,"Opacity").performClick();assertNotNull("Focused panel",text(root,"‹  Back"));
                    View diamond=described(root,"Opacity keyframe: not animated • tap to animate");assertNotNull(diamond);diamond.performClick();
                    assertEquals("Diamond enables animation with a key at the playhead",1,a.project.overlays.get(0).motion.curves.get(Motion.OPACITY).size());
                    assertNotNull(described(a.getWindow().getDecorView(),"Opacity keyframe: keyframe at playhead • tap to remove"));
                    a.seek(2500);a.renderInspector();EditText exact=field(a.getWindow().getDecorView(),"Opacity exact value in %");assertNotNull(exact);exact.setText("40");text(a.getWindow().getDecorView(),"Apply").performClick();
                    assertEquals("Edit while animated creates a second keyframe",2,a.project.overlays.get(0).motion.curves.get(Motion.OPACITY).size());
                    assertEquals(.4f,a.project.overlays.get(0).motion.value(Motion.OPACITY,1500,1),1e-3);
                    text(a.getWindow().getDecorView(),"‹  Back").performClick();assertEquals("Back keeps the selection",0,a.selectedOverlay);assertEquals("Back keeps the playhead",2500,a.project.playheadMs);assertNotNull(text(a.getWindow().getDecorView(),"Color"));
                    text(a.getWindow().getDecorView(),"Move & Transform").performClick();});
                Thread.sleep(300);capture(wide?"v2-wide-transform":"v2-phone-transform");
                scenario.onActivity(a->{View root=a.getWindow().getDecorView();
                    // Per-layer lock blocks edits and is separate from the workspace lock.
                    a.moreAction("Lock layer");assertTrue(a.project.overlays.get(0).locked);assertFalse(a.uiLocked);
                    described(root,"Duplicate").performClick();assertEquals("Locked layers cannot be duplicated",1,a.project.overlays.size());
                    a.nudge(10,0);assertEquals("Locked layers cannot move",0,a.project.overlays.get(0).x,0);
                    a.moreAction("Unlock layer");a.nudge(10,0);assertEquals(10*TransformMath.pixelX(1920),a.project.overlays.get(0).x,1e-5);
                    described(a.getWindow().getDecorView(),"Delete").performClick();assertTrue(a.project.overlays.isEmpty());a.undo(false);assertEquals("Delete is undoable",1,a.project.overlays.size());
                    a.deselect();text(a.getWindow().getDecorView(),"+ Add").performClick();described(a.getWindow().getDecorView(),"Add Text").performClick();text(a.getWindow().getDecorView(),"+ Title").performClick();
                    assertEquals(2,a.project.overlays.size());assertEquals("BLACK ICE",a.project.overlays.get(1).text);
                    assertNotNull("Text tools",text(a.getWindow().getDecorView(),"Edit Text"));assertNotNull(text(a.getWindow().getDecorView(),"Font"));
                    // Multi-select and group.
                    a.toggleMulti(-1,0);assertEquals(2,a.multiCount());assertNotNull(text(a.getWindow().getDecorView(),"2 layers selected"));a.multiAction("Group");
                    assertNotNull(a.project.overlays.get(0).group);assertEquals(a.project.overlays.get(0).group,a.project.overlays.get(1).group);
                    a.deselect();a.tab=3;a.renderInspector();View hide=described(a.getWindow().getDecorView(),"Hide "+a.project.overlays.get(1).label());assertNotNull(hide);hide.performClick();
                    assertTrue("Eye toggle hides the layer",a.project.overlays.get(1).hidden);
                    View solo=described(a.getWindow().getDecorView(),"Solo Main");assertNotNull(solo);solo.performClick();assertTrue(a.project.tracks[0].solo);a.saveNow();});
                Thread.sleep(500);capture(wide?"v2-wide-layers":"v2-phone-layers");
                scenario.recreate();waitLoaded(scenario);
                scenario.onActivity(a->{assertEquals("Saved project reopens with layers",2,a.project.overlays.size());assertTrue(a.project.overlays.get(1).hidden);assertTrue(a.project.tracks[0].solo);
                    assertEquals(2,a.project.overlays.get(0).motion.curves.get(Motion.OPACITY).size());assertNotNull(a.project.overlays.get(1).group);
                    a.project.tracks[0].solo=false;a.saveNow();});
                Thread.sleep(300);
            }
        }}finally{shell("wm size "+size);shell("wm density "+density);inst.getTargetContext().getSharedPreferences(EditorActivity.PREFS,0).edit().putString("mode","AUTO").putBoolean("ui-locked",false).putInt("workspace-focus",0).commit();}
    }
}
