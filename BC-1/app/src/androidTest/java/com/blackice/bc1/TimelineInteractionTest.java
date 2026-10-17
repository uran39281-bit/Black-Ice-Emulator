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
import java.lang.reflect.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class TimelineInteractionTest {
    final Instrumentation inst=InstrumentationRegistry.getInstrumentation();
    static Object get(Object a,String name){try{Field f=a.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(a);}catch(Exception e){throw new AssertionError(e);}}
    static void set(Object a,String name,Object value){try{Field f=a.getClass().getDeclaredField(name);f.setAccessible(true);f.set(a,value);}catch(Exception e){throw new AssertionError(e);}}
    static void call(Object a,String name){try{Method m=a.getClass().getDeclaredMethod(name);m.setAccessible(true);m.invoke(a);}catch(Exception e){throw new AssertionError(e);}}
    static EditProject project(EditorActivity a){return (EditProject)get(a,"project");}
    static View described(View v,String s){if(s.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=described(((ViewGroup)v).getChildAt(i),s);if(found!=null)return found;}return null;}
    static TextView text(View v,String s){if(v instanceof TextView&&s.contentEquals(((TextView)v).getText()))return (TextView)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){TextView found=text(((ViewGroup)v).getChildAt(i),s);if(found!=null)return found;}return null;}
    String shell(String command)throws Exception{try(ParcelFileDescriptor fd=inst.getUiAutomation().executeShellCommand(command);InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){return new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}}
    String override(String value){for(String line:value.split("\n"))if(line.startsWith("Override"))return line.substring(line.indexOf(':')+1).trim();return "reset";}
    void waitLoaded(ActivityScenario<EditorActivity> scenario)throws Exception{AtomicBoolean loaded=new AtomicBoolean();long deadline=SystemClock.elapsedRealtime()+15000;while(!loaded.get()&&SystemClock.elapsedRealtime()<deadline){scenario.onActivity(a->loaded.set(!(Boolean)get(a,"loading")));Thread.sleep(50);}assertTrue(loaded.get());inst.waitForIdleSync();}
    void touch(View v,float x,float y,float endX,float endY,boolean cancel){long t=SystemClock.uptimeMillis();for(int i=0;i<3;i++){int action=i==0?MotionEvent.ACTION_DOWN:i==1?MotionEvent.ACTION_MOVE:cancel?MotionEvent.ACTION_CANCEL:MotionEvent.ACTION_UP;MotionEvent e=MotionEvent.obtain(t,t+i*40,action,i==0?x:endX,i==0?y:endY,0);v.dispatchTouchEvent(e);e.recycle();}}
    float dp(EditorActivity a,float value){return value*a.getResources().getDisplayMetrics().density;}
    void timelineTap(EditorActivity a,float x,float y){View scroll=(View)get(a,"timelineScroll");touch(scroll,dp(a,x),dp(a,y),dp(a,x),dp(a,y),false);}
    // The playhead is centred: content x (time) maps to viewport x by subtracting the scroll offset.
    static TimelineView timeline(EditorActivity a){return (TimelineView)get(a,"timeline");}
    static float vx(EditorActivity a,long ms){return timeline(a).x(ms)-((View)get(a,"timelineScroll")).getScrollX();}
    float rowY(EditorActivity a,int track){return timeline(a).rowTopOfTrack(track)+dp(a,TimelineView.ROW_DP/2f);}
    float musicY(EditorActivity a){java.util.List<TimelineView.Row> rows=timeline(a).rows();return rows.get(rows.size()-1).top+dp(a,TimelineView.ROW_DP/2f);}
    void pinch(View view,float x,float y,float span){long t=SystemClock.uptimeMillis();MotionEvent.PointerProperties[] properties={new MotionEvent.PointerProperties(),new MotionEvent.PointerProperties()};for(int i=0;i<2;i++){properties[i].id=i;properties[i].toolType=MotionEvent.TOOL_TYPE_FINGER;}
        for(int step=0;step<6;step++){int count=step==0||step==5?1:2,action=step==0?MotionEvent.ACTION_DOWN:step==1?MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT):step==4?MotionEvent.ACTION_POINTER_UP|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT):step==5?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE;float spread=step<2?span:step==2?span*1.4f:span*2;MotionEvent.PointerCoords[] coords={new MotionEvent.PointerCoords(),new MotionEvent.PointerCoords()};for(int i=0;i<2;i++){coords[i].x=x+(i==0?-spread:spread);coords[i].y=y;coords[i].pressure=1;coords[i].size=1;}MotionEvent e=MotionEvent.obtain(t,t+step*40,action,count,properties,coords,0,0,1,1,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);view.dispatchTouchEvent(e);e.recycle();}
    }
    void capture(String name)throws Exception{inst.waitForIdleSync();Bitmap b=inst.getUiAutomation().takeScreenshot();assertNotNull(b);File qa=new File(inst.getTargetContext().getFilesDir(),"qa");qa.mkdirs();try(OutputStream out=new FileOutputStream(new File(qa,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
    @Test public void phoneAndDexGesturesHistoryAndSaveReopen()throws Exception{
        String size=override(shell("wm size")),density=override(shell("wm density"));
        try{for(boolean wide:new boolean[]{false,true}){
            shell("wm size "+(wide?"1920x1080":"1080x1920"));shell("wm density "+(wide?"160":"320"));
            EditProject p=new EditProject();p.name="Timeline interaction QA";File file=new File(inst.getTargetContext().getFilesDir(),"timeline-interaction.png");Bitmap b=Bitmap.createBitmap(320,180,Bitmap.Config.ARGB_8888);b.eraseColor(0xFF348BC0);try(OutputStream out=new FileOutputStream(file)){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();
            for(int i=0;i<2;i++){EditProject.Clip c=new EditProject.Clip();c.name="Picture "+i;c.image=true;c.uri=android.net.Uri.fromFile(file).toString();c.outMs=c.sourceDurationMs=3000;p.clips.add(c);}new ProjectStore(inst.getTargetContext()).save(p);
            inst.getTargetContext().getSharedPreferences(EditorActivity.PREFS,0).edit().putString("last-project",p.id).putString("mode","AUTO").putBoolean("ui-locked",false).putBoolean("snapping",false).putInt("workspace-focus",0).commit();
            try(ActivityScenario<EditorActivity> scenario=ActivityScenario.launch(EditorActivity.class)){
                waitLoaded(scenario);
                scenario.onActivity(a->{View root=a.getWindow().getDecorView();text(root,"Edit").performClick();assertNull(text(root,"Opacity"));assertNull(text(root,"Split"));View scroll=(View)get(a,"timelineScroll");float y=rowY(a,0);
                    assertEquals("Centred playhead: time zero sits under the viewport centre",scroll.getWidth()/2f,vx(a,0),1);
                    touch(scroll,vx(a,1500),y,vx(a,1500),y,false);assertEquals(0,get(a,"selected"));assertNotNull(text(root,"Duration: 3.00 s"));assertNotNull(text(root,"Opacity"));assertNull("No slip for pictures",text(root,"Slip"));assertNull("No video-only tools",text(root,"Extract Audio"));
                    float scale=timeline(a).timelineScale();touch(scroll,vx(a,3000),y,vx(a,3000)+scale/2,y,false);assertEquals("Selected picture extends by half a second",3500,project(a).clips.get(0).durationMs());assertEquals(1,((java.util.Deque<?>)get(a,"undo")).size());
                    described(root,"Undo").performClick();assertEquals(3000,project(a).clips.get(0).durationMs());described(root,"Redo").performClick();assertEquals(3500,project(a).clips.get(0).durationMs());
                    touch(scroll,vx(a,3500),y,vx(a,3500)+scale/2,y,true);assertEquals("Cancelled trim is not committed",3500,project(a).clips.get(0).durationMs());assertEquals(1,((java.util.Deque<?>)get(a,"undo")).size());
                    described(root,"Jump to end of selected clip").performClick();assertEquals(3500,project(a).playheadMs);described(root,"Jump to beginning of selected clip").performClick();assertEquals(0,project(a).playheadMs);
                });
                Thread.sleep(400);scenario.onActivity(a->{View scroll=(View)get(a,"timelineScroll");float x=scroll.getWidth()-dp(a,20);touch(scroll,x,musicY(a),x,musicY(a),false);assertEquals("Tapping empty space deselects",-1,get(a,"selected"));assertNull(text(a.getWindow().getDecorView(),"Opacity"));described(a.getWindow().getDecorView(),"Jump to end of project").performClick();assertEquals(6500,project(a).playheadMs);described(a.getWindow().getDecorView(),"Jump to beginning of project").performClick();});
                Thread.sleep(400);scenario.onActivity(a->{View root=a.getWindow().getDecorView();long duration=project(a).durationMs();float before=(Float)get(a,"pixelsPerSecond");text(root,"+").performClick();assertTrue((Float)get(a,"pixelsPerSecond")>before);assertEquals(duration,project(a).durationMs());});
                // A wide viewport can show the whole short project; zoom until there is content to scroll.
                for(int i=0;i<10;i++){AtomicBoolean fits=new AtomicBoolean();Thread.sleep(250);scenario.onActivity(a->{HorizontalScrollView scroll=(HorizontalScrollView)get(a,"timelineScroll");fits.set(scroll.getChildAt(0).getWidth()<=scroll.getWidth()+dp(a,120));if(fits.get())text(a.getWindow().getDecorView(),"+").performClick();});if(!fits.get())break;}
                Thread.sleep(250);scenario.onActivity(a->{HorizontalScrollView scroll=(HorizontalScrollView)get(a,"timelineScroll");assertTrue("Zoomed timeline exceeds the viewport",scroll.getChildAt(0).getWidth()>scroll.getWidth());a.seek(0);float width=scroll.getWidth();touch(scroll,width-dp(a,30),musicY(a),width-dp(a,120),musicY(a),false);
                    assertTrue("Empty-space drag scrubs the playhead",project(a).playheadMs>0);assertEquals("The timeline follows the centred playhead",Math.round(project(a).playheadMs/1000f*timeline(a).timelineScale()),scroll.getScrollX(),2);assertEquals(6500,project(a).durationMs());a.seek(0);});
                scenario.onActivity(a->{float before=(Float)get(a,"pixelsPerSecond");View scroll=(View)get(a,"timelineScroll");float span=Math.max(dp(a,35),ViewConfiguration.get(a).getScaledMinimumScalingSpan()*.6f);pinch(scroll,scroll.getWidth()/2f,dp(a,70),span);assertTrue("Pinch changes display scale",(Float)get(a,"pixelsPerSecond")>before);assertEquals("Pinch never edits duration",6500,project(a).durationMs());set(a,"selected",0);set(a,"tab",1);a.seek(500);call(a,"renderInspector");});
                AtomicBoolean ready=new AtomicBoolean();long deadline=SystemClock.elapsedRealtime()+60000;while(!ready.get()&&SystemClock.elapsedRealtime()<deadline){scenario.onActivity(a->ready.set(!(Boolean)get(a,"previewPending")&&get(a,"player")!=null));Thread.sleep(100);}assertTrue("Prepared preview available for positioning",ready.get());
                scenario.onActivity(a->{View controls=(View)get(a,"previewControls");float x=controls.getWidth()/2f,y=controls.getHeight()/2f;touch(controls,x,y,x+dp(a,25),y,false);assertTrue("Drag changes selected layer",project(a).clips.get(0).x>.05);assertEquals("Other layer remains unchanged",0,project(a).clips.get(1).x,0);described(a.getWindow().getDecorView(),"Undo").performClick();assertEquals(0,project(a).clips.get(0).x,0);call(a,"saveNow");});
                Thread.sleep(600);capture(wide?"v11-wide-selected":"v11-phone-selected");
                scenario.recreate();waitLoaded(scenario);scenario.onActivity(a->{assertEquals(3500,project(a).clips.get(0).durationMs());assertEquals(6500,project(a).durationMs());assertTrue((Float)get(a,"pixelsPerSecond")>48);assertEquals(0,get(a,"selected"));});
                // Text/audio compatibility is tested using the actual contextual toolbar.
                scenario.onActivity(a->{EditProject current=project(a);EditProject.Clip audio=new EditProject.Clip();audio.name="Audio";audio.uri=current.clips.get(0).uri;audio.audioOnly=true;audio.track=4;audio.sourceDurationMs=5000;audio.inMs=500;audio.outMs=3500;current.clips.add(audio);set(a,"selected",2);call(a,"renderInspector");View root=a.getWindow().getDecorView();assertNotNull(text(root,"Slip"));assertNotNull(text(root,"Volume"));assertNull(text(root,"Opacity"));assertNull(text(root,"Chroma Key"));assertNull(text(root,"Extract Audio"));current.clips.remove(2);
                    EditProject.Overlay title=new EditProject.Overlay();title.text="ICE";current.overlays.add(title);set(a,"selected",-1);set(a,"selectedOverlay",0);call(a,"renderInspector");assertNotNull(text(root,"Edit Text"));assertNotNull(text(root,"Font"));assertNotNull(text(root,"Size"));assertNotNull(text(root,"Color"));assertNotNull(text(root,"Alignment"));assertNull(text(root,"Slip"));call(a,"saveNow");});
                Thread.sleep(500);capture(wide?"v11-wide-text-tools":"v11-phone-text-tools");
            }
        }}finally{shell("wm size "+size);shell("wm density "+density);inst.getTargetContext().getSharedPreferences(EditorActivity.PREFS,0).edit().putString("mode","AUTO").putBoolean("ui-locked",false).putInt("workspace-focus",0).putBoolean("snapping",true).commit();}
    }
}
