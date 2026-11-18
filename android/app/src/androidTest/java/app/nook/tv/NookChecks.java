package app.nook.tv;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;
import android.widget.LinearLayout;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Collections;

/** Exercises native navigation, private storage and photo decoding on a TV. */
public final class NookChecks extends Instrumentation {
    private boolean smoke;
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);smoke=arguments!=null&&"true".equals(arguments.getString("smoke"));start();}
    private void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private View find(View view,String label){
        if(label.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;
        if(view instanceof TextView&&label.contentEquals(((TextView)view).getText()))return view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=find(((ViewGroup)view).getChildAt(i),label);if(found!=null)return found;}
        return null;
    }
    private AccessibilityNodeInfo node(AccessibilityNodeInfo root,String label,boolean editable){
        if(root==null)return null;
        if((label.contentEquals(root.getText()==null?"":root.getText())||label.contentEquals(root.getContentDescription()==null?"":root.getContentDescription()))&&(!editable||root.isEditable()))return root;
        for(int i=0;i<root.getChildCount();i++){AccessibilityNodeInfo found=node(root.getChild(i),label,editable);if(found!=null)return found;}return null;
    }
    private AccessibilityNodeInfo awaitNode(String label,boolean editable){
        long end=SystemClock.uptimeMillis()+5000;
        while(SystemClock.uptimeMillis()<end){AccessibilityNodeInfo found=node(getUiAutomation().getRootInActiveWindow(),label,editable);if(found!=null)return found;SystemClock.sleep(100);}
        throw new AssertionError("Missing UI control: "+label);
    }
    private void click(String label){AccessibilityNodeInfo found=awaitNode(label,false);found.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId());check(found.performAction(AccessibilityNodeInfo.ACTION_CLICK),"Cannot click: "+label);waitForIdleSync();}
    private void input(String label,String value){Bundle arguments=new Bundle();arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value);check(awaitNode(label,true).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,arguments),"Cannot enter: "+label);}
    private void back(){long now=SystemClock.uptimeMillis();getUiAutomation().injectInputEvent(new KeyEvent(now,now,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK,0),true);getUiAutomation().injectInputEvent(new KeyEvent(now,SystemClock.uptimeMillis(),KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK,0),true);waitForIdleSync();}
    private void addStop(String name,String code){addStop(name,code,"10, 20");}
    private void addStop(String name,String code,String services){input("Stop name",name);input("Five-digit stop code",code);input("Bus services, separated by commas",services);click("Save stop");back();back();waitForIdleSync();}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try{
            Context context=getTargetContext();
            if(smoke){
                check((context.getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)==0,"TV build must be non-debuggable");
                check(new SecretStore(context).read()!=null,"Existing LTA key must remain readable after updating");
                check(!new NookStore(context).stops.isEmpty(),"Existing bus stops must remain after updating");
                Activity home=startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
                long end=SystemClock.uptimeMillis()+22000;boolean[] live={false};
                while(SystemClock.uptimeMillis()<end&&!live[0]){runOnMainSync(()->live[0]=hasLiveFooter(home.getWindow().getDecorView()));if(!live[0])SystemClock.sleep(250);}
                check(live[0],"Live LTA arrivals must refresh after the TV update");
                runOnMainSync(()->find(home.getWindow().getDecorView(),"BAY65 settings").performClick());waitForIdleSync();
                long now=SystemClock.uptimeMillis();getUiAutomation().injectInputEvent(new KeyEvent(now,now,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_HOME,0),true);getUiAutomation().injectInputEvent(new KeyEvent(now,SystemClock.uptimeMillis(),KeyEvent.ACTION_UP,KeyEvent.KEYCODE_HOME,0),true);
                boolean[] focused={false};long homeDeadline=SystemClock.uptimeMillis()+5000;
                while(SystemClock.uptimeMillis()<homeDeadline&&!focused[0]){runOnMainSync(()->focused[0]=home.hasWindowFocus());if(!focused[0])SystemClock.sleep(100);}
                check(focused[0],"Home must close Nook settings and return to its home screen");
                result.putString("stream","PASS: non-debuggable TV build, saved key/stops retained, live LTA response, and Home returns from Settings. No personal values logged.\n");finish(Activity.RESULT_OK,result);return;
            }
            String savedStops=context.getSharedPreferences("nook",Context.MODE_PRIVATE).getString("stops","[]");
            check((context.getApplicationInfo().flags&ApplicationInfo.FLAG_ALLOW_BACKUP)==0,"Cloud backup must be disabled");
            // Everywhere Mode: internal components are not exposed, and "Ask BAY65" stays hidden until turned on.
            android.content.pm.PackageManager pm=context.getPackageManager();
            check(!pm.getActivityInfo(new android.content.ComponentName(context,AssistantActivity.class),0).exported,"Assistant panel must not be exported");
            check(!pm.getServiceInfo(new android.content.ComponentName(context,OrbOverlayService.class),0).exported,"Overlay service must not be exported");
            check(!pm.getReceiverInfo(new android.content.ComponentName(context,EverywhereBootReceiver.class),0).exported,"Restart receiver must not be exported");
            if(!context.getSharedPreferences("nook",Context.MODE_PRIVATE).getBoolean(Everywhere.ENABLED,false))
                check(pm.getComponentEnabledSetting(new android.content.ComponentName(context.getPackageName(),"app.nook.tv.AskBay65"))!=android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,"Ask BAY65 must stay hidden until Everywhere Mode is on");
            SecretStore secret=new SecretStore(context);String savedKey=secret.read();boolean testSecret=savedKey==null;
            if(testSecret){String dummy="dummy-instrumentation-key";try{secret.save(dummy);check(dummy.equals(secret.read()),"Encrypted key must survive a read");String stored=context.getSharedPreferences("nook_secret",Context.MODE_PRIVATE).getString("encrypted","");check(!stored.contains(dummy),"Preferences must not store the plaintext key");}finally{secret.clear();}}
            NookStore store=new NookStore(context);
            File sample=new File(context.getCacheDir(),"nook-test-photo.png");
            Bitmap bitmap=Bitmap.createBitmap(48,80,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.rgb(90,130,100));try(FileOutputStream out=new FileOutputStream(sample)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}
            PhotoManager manager=new PhotoManager(context,store);PhotoManager.ImportResult imported=manager.importFiles(Collections.singletonList(Uri.fromFile(sample)),0);
            try{check(imported.added.size()==1,"PNG import should create a private copy");check(manager.decode(imported.added.get(0))!=null,"Imported PNG must decode without EXIF");}finally{for(NookStore.Photo p:imported.added)new File(store.photoDirectory,p.file).delete();sample.delete();}
            Intent intent=new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);Activity activity=startActivitySync(intent);waitForIdleSync();
            runOnMainSync(()->{View content=activity.getWindow().getDecorView();check(find(content,"BAY65")!=null,"Home screen should render");View settings=find(content,"BAY65 settings");check(settings!=null,"Settings gear should be accessible");settings.performClick();});waitForIdleSync();
            // Dialog windows are checked through their accessibility surface by the
            // external device checks; this runner also verifies the home focus path.
            back();
            runOnMainSync(()->{View network=find(activity.getWindow().getDecorView(),"BAY65 settings");check(network!=null,"Home should remain available after closing Settings");});
            runOnMainSync(()->{ViewGroup content=(ViewGroup)activity.getWindow().getDecorView();clickNetwork(content);});waitForIdleSync();
            long networkDeadline=SystemClock.uptimeMillis()+5000;boolean openedNative=false;
            while(SystemClock.uptimeMillis()<networkDeadline){AccessibilityNodeInfo top=getUiAutomation().getRootInActiveWindow();if(top!=null&&!context.getPackageName().contentEquals(top.getPackageName())){openedNative=true;break;}SystemClock.sleep(100);}
            check(openedNative,"Network shortcut must open native settings");
            for(int i=0;i<4;i++){back();AccessibilityNodeInfo top=getUiAutomation().getRootInActiveWindow();if(top!=null&&context.getPackageName().contentEquals(top.getPackageName()))break;}
            context.getSharedPreferences("nook_layout_test",Context.MODE_PRIVATE).edit().clear().commit();
            Intent layoutIntent=new Intent().setClassName(context,"app.nook.tv.LayoutProbeActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Activity layoutActivity=startActivitySync(layoutIntent);waitForIdleSync();
            checkShelfSound(layoutActivity);
            checkSettingsScrollSound(layoutActivity);
            {
                click("Add a bus stop");addStop("Sample stop A","00001");
                final int[] firstY={0},firstHeight={0};runOnMainSync(()->{int[] xy=new int[2];find(layoutActivity.getWindow().getDecorView(),"Your favourites").getLocationOnScreen(xy);firstY[0]=xy[1];firstHeight[0]=layoutActivity.getWindow().getDecorView().findViewWithTag("bus-viewport").getHeight();});
                runOnMainSync(()->find(layoutActivity.getWindow().getDecorView(),"BAY65 settings").performClick());waitForIdleSync();click("BAY65 preferences");click("Add a bus stop");addStop("Sample stop B","00002","10,20,30,40,50,60,70,100A");
                runOnMainSync(()->{View content=layoutActivity.getWindow().getDecorView();int[] xy=new int[2];find(content,"Your favourites").getLocationOnScreen(xy);check(firstY[0]==xy[1],"Growing bus cards must not move app icons down");check(content.findViewWithTag("bus-viewport").getHeight()>firstHeight[0],"Bus cards should borrow free space for more services");
                    View mark=content.findViewWithTag("bay65-mark"),wordmark=find(content,"BAY65");int[] markXY=new int[2],wordXY=new int[2];mark.getLocationOnScreen(markXY);wordmark.getLocationOnScreen(wordXY);check(wordXY[0]-markXY[0]-mark.getWidth()>=Math.round(12*activity.getResources().getDisplayMetrics().density),"Logo and wordmark need a visible gap");check(find(content,"Sample stop A")!=null&&find(content,"Sample stop B")!=null,"Both stops must be visible together");});
                runOnMainSync(()->find(layoutActivity.getWindow().getDecorView(),"BAY65 settings").performClick());waitForIdleSync();click("BAY65 preferences");
                java.lang.reflect.Field dialogField=MainActivity.class.getDeclaredField("settingsDialog"),scrollField=MainActivity.class.getDeclaredField("settingsScroll");dialogField.setAccessible(true);scrollField.setAccessible(true);
                android.app.Dialog testDialog=(android.app.Dialog)dialogField.get(layoutActivity);android.widget.ScrollView testScroll=(android.widget.ScrollView)scrollField.get(layoutActivity);
                runOnMainSync(()->{View dialogRoot=testDialog.getWindow().getDecorView();TextView close=(TextView)find(dialogRoot,"Close"),backButton=(TextView)find(dialogRoot,"‹ Back");check(close.getGravity()==android.view.Gravity.CENTER&&backButton.getGravity()==android.view.Gravity.CENTER,"Back and Close text must be centered");check(!testScroll.isVerticalScrollBarEnabled(),"Settings scrollbar should be hidden while keeping its ScrollView");View connection=find(dialogRoot,"Connect LTA");check(((LinearLayout.LayoutParams)connection.getLayoutParams()).topMargin>=Math.round(16*layoutActivity.getResources().getDisplayMetrics().density),"LTA connection needs space after stop controls");});
                runOnMainSync(layoutActivity::finish);waitForIdleSync();
                context.getSharedPreferences("nook_layout_test",Context.MODE_PRIVATE).edit().clear().commit();
            }
            check(savedStops.equals(context.getSharedPreferences("nook",Context.MODE_PRIVATE).getString("stops","[]")),"User bus configuration must be preserved");
            check(savedKey==null?secret.read()==null:savedKey.equals(secret.read()),"User key must be preserved");
            result.putString("stream","PASS: home/settings navigation, native network shortcut, encrypted key storage, backup exclusion, private PNG import/decoding, multi-stop row and stable app positioning. Existing user configuration preserved.\n");finish(Activity.RESULT_OK,result);
        }catch(Throwable error){result.putString("stream","FAIL: "+error.getClass().getSimpleName()+": "+error.getMessage()+"\n");finish(Activity.RESULT_CANCELED,result);}
    }
    private void checkShelfSound(Activity activity){
        final ShelfScrollView[] shelf={null};final TextView[] first={null};final int[] sounds={0};
        runOnMainSync(()->{
            shelf[0]=new ShelfScrollView(activity){@Override public void playSoundEffect(int effect){sounds[0]++;}};
            LinearLayout items=new LinearLayout(activity);items.setOrientation(LinearLayout.HORIZONTAL);
            first[0]=new TextView(activity);first[0].setFocusable(true);items.addView(first[0],new LinearLayout.LayoutParams(150,60));
            TextView second=new TextView(activity);second.setFocusable(true);items.addView(second,new LinearLayout.LayoutParams(150,60));
            items.addView(new View(activity),new LinearLayout.LayoutParams(3000,60));shelf[0].addView(items);
            ((ViewGroup)activity.getWindow().getDecorView()).addView(shelf[0],new ViewGroup.LayoutParams(500,80));
        });waitForIdleSync();
        runOnMainSync(()->{
            first[0].requestFocus();shelf[0].executeKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT));check(sounds[0]==1,"A consumed focus move needs exactly one sound");
            shelf[0].executeKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_RIGHT));check(sounds[0]==1,"Key up must not repeat the sound");
            shelf[0].executeKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_LEFT));check(sounds[0]==2,"Moving back should sound once");
            shelf[0].executeKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_LEFT));check(sounds[0]==2,"An edge without focus movement must be silent");
            shelf[0].setSoundEffectsEnabled(false);shelf[0].executeKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT));check(sounds[0]==2,"Disabled sound effects must remain silent");
            ((ViewGroup)shelf[0].getParent()).removeView(shelf[0]);
        });waitForIdleSync();
    }
    private void checkSettingsScrollSound(Activity activity){
        final SettingsScrollView[] scroll={null};final TextView[] first={null};final int[] sounds={0};
        runOnMainSync(()->{
            scroll[0]=new SettingsScrollView(activity){@Override public void playSoundEffect(int effect){sounds[0]++;}};
            LinearLayout items=new LinearLayout(activity);items.setOrientation(LinearLayout.VERTICAL);
            first[0]=new TextView(activity);first[0].setFocusable(true);items.addView(first[0],new LinearLayout.LayoutParams(300,70));
            TextView second=new TextView(activity);second.setFocusable(true);items.addView(second,new LinearLayout.LayoutParams(300,70));
            items.addView(new View(activity),new LinearLayout.LayoutParams(300,3000));scroll[0].addView(items);
            ((ViewGroup)activity.getWindow().getDecorView()).addView(scroll[0],new ViewGroup.LayoutParams(500,300));
        });waitForIdleSync();
        runOnMainSync(()->{
            check(!scroll[0].isVerticalScrollBarEnabled(),"Settings sound must not restore the scrollbar");
            first[0].requestFocus();scroll[0].executeKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_DOWN));check(sounds[0]==1,"A consumed vertical focus move needs one sound");
            scroll[0].executeKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_DOWN));check(sounds[0]==1,"Vertical key up must be silent");
            scroll[0].executeKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_UP));check(sounds[0]==2,"Moving up should sound once");
            scroll[0].executeKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_UP));check(sounds[0]==2,"The top edge must not repeat a sound");
            scroll[0].setSoundEffectsEnabled(false);scroll[0].executeKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_DOWN));check(sounds[0]==2,"Disabled vertical sound effects must remain silent");
            ((ViewGroup)scroll[0].getParent()).removeView(scroll[0]);
        });waitForIdleSync();
    }
    private boolean clickNetwork(View view){
        CharSequence description=view.getContentDescription();
        if(description!=null&&description.toString().startsWith("Network settings.")){check(view.performClick(),"Network icon must be clickable");return true;}
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)if(clickNetwork(((ViewGroup)view).getChildAt(i)))return true;
        return false;
    }
    private boolean hasLiveFooter(View view){
        if(view instanceof TextView&&((TextView)view).getText().toString().startsWith("LTA · Updated"))return true;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)if(hasLiveFooter(((ViewGroup)view).getChildAt(i)))return true;
        return false;
    }
}
