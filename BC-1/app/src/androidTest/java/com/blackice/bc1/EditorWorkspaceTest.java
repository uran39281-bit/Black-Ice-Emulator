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
    private View described(View view,String value){if(value.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=described(((ViewGroup)view).getChildAt(i),value);if(found!=null)return found;}return null;}
    private SurfaceView surface(View view){if(view instanceof SurfaceView)return (SurfaceView)view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){SurfaceView found=surface(((ViewGroup)view).getChildAt(i));if(found!=null)return found;}return null;}
    private String shell(String command)throws Exception{try(ParcelFileDescriptor fd=inst.getUiAutomation().executeShellCommand(command);InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){return new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}}
    private String override(String value){for(String line:value.split("\n"))if(line.startsWith("Override"))return line.substring(line.indexOf(':')+1).trim();return "reset";}
    private void capture(String name)throws Exception{inst.waitForIdleSync();Thread.sleep(1000);Bitmap b=inst.getUiAutomation().takeScreenshot();assertNotNull(b);File dir=new File(inst.getTargetContext().getFilesDir(),"qa");dir.mkdirs();try(OutputStream out=new FileOutputStream(new File(dir,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
    private void assertVisiblePreview(ActivityScenario<EditorActivity> scenario)throws Exception{
        long deadline=SystemClock.elapsedRealtime()+90000;
        while(SystemClock.elapsedRealtime()<deadline){
                java.util.concurrent.atomic.AtomicReference<SurfaceView> preview=new java.util.concurrent.atomic.AtomicReference<>();
                scenario.onActivity(a->preview.set(surface(a.getWindow().getDecorView())));
                Bitmap pixels=Bitmap.createBitmap(preview.get().getWidth(),preview.get().getHeight(),Bitmap.Config.ARGB_8888);
                java.util.concurrent.CountDownLatch copied=new java.util.concurrent.CountDownLatch(1);java.util.concurrent.atomic.AtomicInteger result=new java.util.concurrent.atomic.AtomicInteger(-1);
                inst.runOnMainSync(()->{try{PixelCopy.request(preview.get(),pixels,status->{result.set(status);copied.countDown();},new Handler(Looper.getMainLooper()));}catch(IllegalArgumentException e){copied.countDown();}});
                assertTrue(copied.await(10,java.util.concurrent.TimeUnit.SECONDS));
                int color=pixels.getPixel(pixels.getWidth()/4,pixels.getHeight()/2);pixels.recycle();
                if(result.get()==PixelCopy.SUCCESS&&android.graphics.Color.blue(color)>30)return;
                Thread.sleep(250);
        }
        fail("Preview must render real image pixels without pressing Play");
    }
    @Test public void rapidSearchDuringDecodeUpdatesCurrentThumbnail()throws Exception{
        assumeTrue(Build.HARDWARE.contains("ranchu")||Build.HARDWARE.contains("goldfish"));
        String size=override(shell("wm size")),density=override(shell("wm density"));
        java.util.concurrent.CountDownLatch decodeAllowed=new java.util.concurrent.CountDownLatch(1),workerBlocked=new java.util.concurrent.CountDownLatch(1);
        EditProject project=new EditProject();project.name="Thumbnail stress";
        File source=new File(inst.getTargetContext().getFilesDir(),"thumbnail-stress.png");
        Bitmap sourcePixels=Bitmap.createBitmap(160,90,Bitmap.Config.ARGB_8888);sourcePixels.eraseColor(android.graphics.Color.rgb(24,120,210));
        try(OutputStream out=new FileOutputStream(source)){sourcePixels.compress(Bitmap.CompressFormat.PNG,100,out);}sourcePixels.recycle();
        for(String name:new String[]{"ICE / A","ICE / B"}){EditProject.Clip clip=new EditProject.Clip();clip.name=name;clip.uri=android.net.Uri.fromFile(source).toString();clip.image=true;clip.sourceDurationMs=2000;clip.outMs=2000;project.clips.add(clip);}
        new ProjectStore(inst.getTargetContext()).save(project);
        inst.getTargetContext().getSharedPreferences(EditorActivity.PREFS,0).edit().putString("last-project",project.id).putString("mode","WIDE").putBoolean("ui-locked",false).commit();
        java.util.concurrent.atomic.AtomicReference<ThumbnailCache> cache=new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<View> currentCard=new java.util.concurrent.atomic.AtomicReference<>();
        try{
            shell("wm size 1920x1080");shell("wm density 160");
            try(ActivityScenario<EditorActivity> scenario=ActivityScenario.launch(EditorActivity.class)){
                long deadline=SystemClock.elapsedRealtime()+15000;java.util.concurrent.atomic.AtomicBoolean loaded=new java.util.concurrent.atomic.AtomicBoolean();
                while(SystemClock.elapsedRealtime()<deadline&&!loaded.get()){scenario.onActivity(a->loaded.set(described(a.getWindow().getDecorView(),"Select ICE / B")!=null));if(!loaded.get())Thread.sleep(100);}
                assertTrue("Stress fixture loaded",loaded.get());
                scenario.onActivity(a->{try{
                    java.lang.reflect.Field field=EditorActivity.class.getDeclaredField("thumbnails");field.setAccessible(true);((ThumbnailCache)field.get(a)).close();
                    ThumbnailCache fresh=new ThumbnailCache(a);cache.set(fresh);field.set(a,fresh);
                    java.lang.reflect.Field executor=ThumbnailCache.class.getDeclaredField("worker");executor.setAccessible(true);
                    ((java.util.concurrent.ExecutorService)executor.get(fresh)).execute(()->{workerBlocked.countDown();try{decodeAllowed.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});
                }catch(ReflectiveOperationException e){throw new AssertionError(e);}});
                assertTrue("Decoder barrier entered",workerBlocked.await(5,java.util.concurrent.TimeUnit.SECONDS));
                scenario.onActivity(a->{try{
                    java.lang.reflect.Method render=EditorActivity.class.getDeclaredMethod("renderMediaBrowser");render.setAccessible(true);render.invoke(a);
                    android.widget.EditText search=(android.widget.EditText)described(a.getWindow().getDecorView(),"Search project media");
                    for(int i=0;i<32;i++)search.setText(i%2==0?"ICE":"ICE / B");
                    ViewGroup card=(ViewGroup)described(a.getWindow().getDecorView(),"Select ICE / B");
                    currentCard.set(card);
                    assertFalse("Current card starts with a placeholder",((android.widget.ImageView)card.getChildAt(0)).getDrawable() instanceof android.graphics.drawable.BitmapDrawable);
                }catch(ReflectiveOperationException e){throw new AssertionError(e);}});
                decodeAllowed.countDown();deadline=SystemClock.elapsedRealtime()+15000;
                while(cache.get().peek(project.clips.get(1))==null&&SystemClock.elapsedRealtime()<deadline)Thread.sleep(100);
                assertNotNull("Decode actually completed",cache.get().peek(project.clips.get(1)));inst.waitForIdleSync();
                scenario.onActivity(a->{View root=a.getWindow().getDecorView();ViewGroup card=(ViewGroup)described(root,"Select ICE / B");android.graphics.drawable.Drawable drawable=((android.widget.ImageView)card.getChildAt(0)).getDrawable();
                    assertSame("Decode updates the existing card in place",currentCard.get(),card);
                    assertTrue("Latest media card receives decoded pixels after rapid search",drawable instanceof android.graphics.drawable.BitmapDrawable);
                    Bitmap pixels=((android.graphics.drawable.BitmapDrawable)drawable).getBitmap();assertEquals(android.graphics.Color.rgb(24,120,210),pixels.getPixel(pixels.getWidth()/2,pixels.getHeight()/2));assertNotNull(text(root,"1 / 2 clips"));
                });capture("v103-thumbnails-loaded");
            }
        }finally{decodeAllowed.countDown();shell("wm size "+size);shell("wm density "+density);inst.getTargetContext().getSharedPreferences(EditorActivity.PREFS,0).edit().putString("mode","AUTO").commit();}
    }
    @Test public void editorAdaptsAndTabsKeepVideoSurface()throws Exception{
        assumeTrue(Build.HARDWARE.contains("ranchu")||Build.HARDWARE.contains("goldfish"));String size=override(shell("wm size")),density=override(shell("wm density"));
        EditProject p=new EditProject();p.name="Ice • First Cut";
        File image=new File(inst.getTargetContext().getFilesDir(),"workspace-demo.png");Bitmap b=Bitmap.createBitmap(640,360,Bitmap.Config.ARGB_8888);android.graphics.Canvas c=new android.graphics.Canvas(b);android.graphics.Paint paint=new android.graphics.Paint();paint.setShader(new android.graphics.LinearGradient(0,0,640,360,0xFF12364B,0xFF54BDE1,android.graphics.Shader.TileMode.CLAMP));c.drawPaint(paint);try(OutputStream out=new FileOutputStream(image)){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();
        for(int i=0;i<2;i++){EditProject.Clip clip=new EditProject.Clip();clip.name=i==0?"ICE / OPENING":"ICE / DETAIL";clip.uri=android.net.Uri.fromFile(image).toString();clip.image=true;clip.sourceDurationMs=3000;clip.outMs=3000;clip.transition=0;p.clips.add(clip);}
        p.clips.get(1).track=1;p.clips.get(1).startMs=750;p.clips.get(1).scale=.6f;p.clips.get(1).x=.4f;p.clips.get(1).motion.put(3,new Motion.Key(0,0));p.clips.get(1).motion.put(3,new Motion.Key(3000,30));
        EditProject.Overlay title=new EditProject.Overlay();title.text="BLACK ICE";title.y=0;p.overlays.add(title);new ProjectStore(inst.getTargetContext()).save(p);
        inst.getTargetContext().getSharedPreferences(EditorActivity.PREFS,0).edit().putString("last-project",p.id).putString("mode","AUTO").putBoolean("ui-locked",false).commit();
        try{
            shell("wm size 1080x1920");shell("wm density 320");
            try(ActivityScenario<EditorActivity> scenario=ActivityScenario.launch(EditorActivity.class)){
                Thread.sleep(3500);inst.waitForIdleSync();
                scenario.onActivity(a->{View root=a.getWindow().getDecorView();assertNull("Clip tools stay hidden until selection",text(root,"Split"));text(root,"Settings").performClick();text(root,"Lock editing and layout: OFF").performClick();a.onKeyDown(KeyEvent.KEYCODE_S,new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_S));text(root,"Lock editing and layout: ON").performClick();text(root,"Lock editing and layout: OFF").performClick();text(root,"Add").performClick();text(root,"1  ICE / OPENING  ·  00:03.00").performClick();
                    // A selected layer replaces Settings / Export in the top bar with its name, Duplicate, Delete and More.
                    assertNull("Selection shows layer actions instead of Settings",text(root,"Settings"));assertNotNull(described(root,"More actions"));assertNotNull(described(root,"Delete"));
                    a.onKeyDown(KeyEvent.KEYCODE_D,new KeyEvent(0,0,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_D,0,KeyEvent.META_CTRL_ON));assertNotNull("Settings lock guards edits",text(root,"UI locked • unlock editing in Settings"));
                    described(root,"Deselect").performClick();assertNotNull("Deselecting restores the project bar",text(root,"Settings"));text(root,"Settings").performClick();text(root,"Lock editing and layout: ON").performClick();text(root,"Add").performClick();});
                assertVisiblePreview(scenario);
                scenario.onActivity(a->assertNotNull("First frame stays paused until Play",text(a.getWindow().getDecorView(),"▶  Play")));
                capture("v10-paused-first-frame");
                scenario.onActivity(a->assertNotNull("Preparation must not advance the playhead",text(a.getWindow().getDecorView(),"00:00.00  /  00:03.75")));
                scenario.onActivity(a->a.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT,new KeyEvent(0,0,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT,0,KeyEvent.META_SHIFT_ON)));
                Thread.sleep(500);assertVisiblePreview(scenario);
                scenario.onActivity(a->{assertNotNull("Paused seek updates the playhead",text(a.getWindow().getDecorView(),"00:01.00  /  00:03.75"));a.onKeyDown(KeyEvent.KEYCODE_DPAD_LEFT,new KeyEvent(0,0,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_LEFT,0,KeyEvent.META_SHIFT_ON));});
                scenario.onActivity(a->a.onKeyDown(KeyEvent.KEYCODE_SPACE,new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_SPACE)));
                Thread.sleep(1500);
                scenario.onActivity(a->a.onKeyDown(KeyEvent.KEYCODE_SPACE,new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_SPACE)));
                assertVisiblePreview(scenario);
                scenario.onActivity(a->{View root=a.getWindow().getDecorView();assertNotNull("Sample project must be loaded before visual checks",text(root,"Ice • First Cut  ▾"));SurfaceView video=surface(root);assertNotNull(video);assertTrue(video.getWidth()>0&&video.getHeight()>0);View parent=(View)video.getParent();text(root,"Effects").performClick();assertSame(parent,video.getParent());assertNotNull(text(root,"GPU EFFECTS"));});
                scenario.onActivity(a->{View root=a.getWindow().getDecorView();for(int i=0;i<3;i++)text(root,"+").performClick();});
                Thread.sleep(500);
                scenario.onActivity(a->{android.widget.HorizontalScrollView scroll=(android.widget.HorizontalScrollView)described(a.getWindow().getDecorView(),"Timeline scroll");scroll.scrollTo(140,0);assertTrue("Timeline scroll is available after zoom",scroll.getScrollX()>0);});capture("v10-phone-9x16");
                shell("wm size 1920x1080");shell("wm density 160");Thread.sleep(1800);inst.waitForIdleSync();scenario.onActivity(a->{assertTrue(surface(a.getWindow().getDecorView()).getWidth()>500);});assertVisiblePreview(scenario);capture("v10-wide-16x9");
                scenario.onActivity(a->{
                    View root=a.getWindow().getDecorView();View first=described(root,"Select ICE / OPENING"),second=described(root,"Select ICE / DETAIL");
                    assertNotNull(first);assertNotNull(second);int[] p1=new int[2],p2=new int[2];first.getLocationOnScreen(p1);second.getLocationOnScreen(p2);
                    assertEquals("V1 desktop cards share a row",p1[1],p2[1]);assertTrue("Two readable media columns",p2[0]>p1[0]&&first.getWidth()>100);
                    View previewParent=(View)surface(root).getParent();
                    described(root,"Filter media: Video").performClick();assertNotNull(text(root,"No matching media"));assertNotNull(text(root,"0 / 2 clips"));
                    described(root,"Filter media: Audio").performClick();assertNotNull(text(root,"No matching media"));
                    described(root,"Filter media: Image").performClick();assertNotNull(text(root,"2 / 2 clips"));
                    ((android.widget.EditText)described(root,"Search project media")).setText(" detail ");
                    assertNotNull(text(root,"1 / 2 clips"));assertNull(described(root,"Select ICE / OPENING"));
                    described(root,"Select ICE / DETAIL").performClick();
                    assertNotNull("Filtered index maps to the original clip",text(root,"Picture  •  ICE / DETAIL"));
                    assertTrue(described(root,"Select ICE / DETAIL").isSelected());
                    assertSame("Filtering never recreates the preview surface",previewParent,surface(root).getParent());
                });
                scenario.recreate();Thread.sleep(2500);inst.waitForIdleSync();assertVisiblePreview(scenario);
                scenario.onActivity(a->{View root=a.getWindow().getDecorView();assertEquals(" detail ",((android.widget.EditText)described(root,"Search project media")).getText().toString());assertTrue(described(root,"Filter media: Image").isSelected());assertNotNull(text(root,"1 / 2 clips"));assertTrue(described(root,"Select ICE / DETAIL").isSelected());});capture("v102-media-filtered");
                scenario.onActivity(a->{View root=a.getWindow().getDecorView();((android.widget.EditText)described(root,"Search project media")).setText("");described(root,"Filter media: All").performClick();assertNotNull(text(root,"2 / 2 clips"));text(root,"Add").performClick();text(root,"1  ICE / OPENING  ·  00:03.00").performClick();assertTrue("Inspector selection updates the browser highlight",described(root,"Select ICE / OPENING").isSelected());assertFalse(described(root,"Select ICE / DETAIL").isSelected());text(root,"Effects").performClick();});
                inst.getTargetContext().getSharedPreferences(EditorActivity.PREFS,0).edit().putString("mode","WIDE").commit();shell("wm size 780x420");scenario.recreate();Thread.sleep(2500);inst.waitForIdleSync();scenario.onActivity(a->{View deselect=described(a.getWindow().getDecorView(),"Deselect");if(deselect!=null)deselect.performClick();text(a.getWindow().getDecorView(),"Settings").performClick();assertNotNull("Wide preference must be restored",text(a.getWindow().getDecorView(),"Workspace layout: Wide tablet / DeX"));SurfaceView video=surface(a.getWindow().getDecorView());assertTrue(video.getWidth()>150&&video.getHeight()>60);});assertVisiblePreview(scenario);capture("v10-compact-wide");
            }
        }finally{shell("wm size "+size);shell("wm density "+density);inst.getTargetContext().getSharedPreferences(EditorActivity.PREFS,0).edit().putString("mode","AUTO").putBoolean("ui-locked",false).commit();}
    }
}
