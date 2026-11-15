package app.nook.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native, remote-first launcher. No WebView or development server is required. */
public class MainActivity extends Activity {
    private static final int INK=0xffffffff,MUTED=0xffa1b8c7,PANEL=0xf20c1825,ACCENT=0xff53decf;
    private static final int PHOTO_REQUEST=41;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newFixedThreadPool(4);
    private final LtaClient lta=new LtaClient();
    private NookStore store;
    private SecretStore secrets;
    private AppRepository apps;
    private PhotoManager photoManager;
    private FrameLayout root;
    private ImageView wallpaper,networkButton;
    private LinearLayout busRow,favouritesRow,playtimeRow;
    private HorizontalScrollView busViewport;
    private View busSpacer;
    private TextView timeLabel,dateLabel;
    private Dialog settingsDialog;
    private LinearLayout page;
    private TextView dialogTitle;
    private ScrollView settingsScroll;
    private String settingsPage="menu",networkKind="offline",networkDescription="Disconnected";
    private boolean active,compact,firstResume=true;
    private int lifecycle=0,keyGeneration=0,photoGeneration=0,photoIndex=0;
    private JSONArray stopDirectory;
    private long stopDirectoryAccessed;
    private final Map<String,ArrivalState> arrivalStates=new HashMap<>();
    private final Map<String,BusCard> busCards=new HashMap<>();
    private final Set<String> pendingStops=new HashSet<>();
    private ConnectivityManager connectivity;
    private boolean observingNetwork;
    private String apiKey;
    private TextView dialogBack;
    private Runnable pageBack=this::showSettingsMenu;

    private static final class ArrivalState {
        JSONObject data;
        long updated;
        String error;
    }
    private final class BusCard {
        NookStore.Stop stop;
        final List<TextView[]> cells=new ArrayList<>();
        TextView status;
        LinearLayout container;
        ScrollView scroll;
        void update() {
            ArrivalState state=arrivalStates.get(stop.code);
            boolean fresh=state!=null&&state.data!=null&&System.currentTimeMillis()-state.updated<=60_000L;
            Map<String,JSONObject> services=new HashMap<>();
            if(fresh){JSONArray all=state.data.optJSONArray("Services");if(all!=null)for(int i=0;i<all.length();i++){JSONObject service=all.optJSONObject(i);if(service!=null)services.put(service.optString("ServiceNo"),service);}}
            boolean scheduled=false;
            for(int i=0;i<cells.size();i++){
                JSONObject service=services.get(stop.services.get(i));
                for(int n=0;n<3;n++){
                    JSONObject bus=service==null?null:service.optJSONObject(n==0?"NextBus":n==1?"NextBus2":"NextBus3");
                    String label=bus==null?"—":Rules.eta(bus.optString("EstimatedArrival"),bus.optInt("Monitored",1),System.currentTimeMillis());
                    Rules.Occupancy occupancy=Rules.occupancy(bus==null||label.equals("—")?null:bus.optString("Load"));
                    android.text.SpannableString display=new android.text.SpannableString(label);
                    if(!occupancy.label.isEmpty())display.setSpan(new android.text.style.ForegroundColorSpan(occupancy.color),0,display.length(),0);
                    cells.get(i)[n].setText(display);
                    cells.get(i)[n].setContentDescription(label+(occupancy.label.isEmpty()?"":" · "+occupancy.label));
                    scheduled|=label.startsWith("~");
                }
                ((View)cells.get(i)[0].getParent()).setContentDescription("Service "+stop.services.get(i)+", next "+cells.get(i)[0].getContentDescription()+", then "+cells.get(i)[1].getContentDescription()+", later "+cells.get(i)[2].getContentDescription());
            }
            String message;
            if(apiKey==null)message="Connect LTA in BAY65 preferences";
            else if(state==null)message="Loading arrivals…";
            else if(state.error!=null)message=fresh?"LTA · Delayed "+clock(state.updated,"dd MMM yy HH:mm:ss"):"Arrivals unavailable · Retrying";
            else message=fresh?"LTA · Updated "+clock(state.updated,"dd MMM yy HH:mm:ss"):"Outdated · Waiting for an update";
            status.setText(message+"\n— no estimate"+(scheduled?" · ~ scheduled":""));
            status.setContentDescription(state!=null&&state.error!=null?state.error:message);
        }
    }

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        store=new NookStore(this);secrets=new SecretStore(this);apiKey=secrets.read();apps=new AppRepository(this);photoManager=new PhotoManager(this,store);
        connectivity=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        compact=getResources().getDisplayMetrics().heightPixels/getResources().getDisplayMetrics().density<600;
        apps.refresh(store);buildHome();
    }
    @Override protected void onResume(){
        super.onResume();active=true;lifecycle++;
        apps.refresh(store);renderApps();refreshNetwork();
        if(!observingNetwork){try{connectivity.registerDefaultNetworkCallback(networkCallback);observingNetwork=true;}catch(RuntimeException ignored){}}
        main.removeCallbacks(tick);main.post(tick);main.removeCallbacks(poll);main.post(poll);restartPhotos();
        if(firstResume){firstResume=false;main.post(()->{if(favouritesRow.getChildCount()>0)favouritesRow.getChildAt(0).requestFocus();});}
    }
    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);setIntent(intent);
        if(Intent.ACTION_MAIN.equals(intent.getAction())){
            if(settingsDialog!=null)settingsDialog.dismiss();
            if(favouritesRow!=null&&favouritesRow.getChildCount()>0)favouritesRow.getChildAt(0).requestFocus();
        }
    }
    @Override protected void onStop(){
        active=false;lifecycle++;main.removeCallbacks(tick);main.removeCallbacks(poll);main.removeCallbacks(photoTick);
        if(observingNetwork){try{connectivity.unregisterNetworkCallback(networkCallback);}catch(RuntimeException ignored){}observingNetwork=false;}
        super.onStop();
    }
    @Override protected void onDestroy(){
        main.removeCallbacksAndMessages(null);io.shutdownNow();if(settingsDialog!=null)settingsDialog.dismiss();super.onDestroy();
    }
    @Override public void onBackPressed(){
        if(settingsDialog!=null&&settingsDialog.isShowing()){if(!settingsPage.equals("menu"))showSettingsMenu();else settingsDialog.dismiss();return;}
        View first=favouritesRow.getChildCount()>0?favouritesRow.getChildAt(0):networkButton;first.requestFocus();
    }
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private LinearLayout vertical(){LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);return layout;}
    private LinearLayout horizontal(){LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.HORIZONTAL);layout.setGravity(Gravity.CENTER_VERTICAL);return layout;}
    private TextView text(String value,float size,int color){TextView view=new TextView(this);view.setText(value);view.setTextSize(size);view.setTextColor(color);view.setFontFeatureSettings("tnum");return view;}
    private Drawable artwork(AppRepository.Entry entry){Drawable.ConstantState state=entry.artwork==null?null:entry.artwork.getConstantState();return state==null?entry.artwork:state.newDrawable(getResources()).mutate();}
    private GradientDrawable surface(int color,float radius){GradientDrawable drawable=new GradientDrawable();drawable.setColor(color);drawable.setCornerRadius(dp(radius));return drawable;}
    private void focusable(View view,float radius){
        view.setFocusable(true);view.setClickable(true);view.setBackground(surface(0x22ffffff,radius));
        view.setOnFocusChangeListener((v,focused)->{GradientDrawable shape=surface(focused?0x4dffffff:0x22ffffff,radius);shape.setStroke(dp(focused?2:1),focused?INK:0x25ffffff);v.setBackground(shape);v.setElevation(dp(focused?5:0));});
    }
    private TextView button(String label,Runnable action){
        TextView button=text(label,15,INK);button.setGravity(Gravity.CENTER_VERTICAL);button.setPadding(dp(16),dp(11),dp(16),dp(11));focusable(button,9);button.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.bottomMargin=dp(8);button.setLayoutParams(params);return button;
    }
    private void heading(String label){TextView h=text(label,16,INK);h.setTypeface(null,Typeface.BOLD);h.setPadding(0,dp(15),0,dp(10));page.addView(h);}
    private void paragraph(String value){TextView p=text(value,13,MUTED);p.setLineSpacing(dp(3),1);p.setPadding(0,0,0,dp(14));page.addView(p);}
    private void message(String value){Toast.makeText(this,value,Toast.LENGTH_LONG).show();}
    private String clock(long time,String pattern){SimpleDateFormat f=new SimpleDateFormat(pattern,Locale.UK);f.setTimeZone(TimeZone.getTimeZone("Asia/Singapore"));return f.format(new Date(time));}

    private void buildHome(){
        root=new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff183346,0xff112838,0xff0c1825,0xff0c1825}));
        wallpaper=new ImageView(this);wallpaper.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);root.addView(wallpaper,new FrameLayout.LayoutParams(-1,-1));
        View shade=new View(this);shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x14090f0f,0x35090f0f,0xf5090f0f}));root.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout content=vertical();content.setPadding(dp(30),dp(20),dp(30),dp(20));root.addView(content,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout header=horizontal();LinearLayout brand=vertical();TextView title=text("bay65",24,INK);title.setTypeface(null,Typeface.BOLD);android.text.SpannableString wordmark=new android.text.SpannableString("bay65");wordmark.setSpan(new android.text.style.ForegroundColorSpan(ACCENT),3,5,0);title.setText(wordmark);title.setContentDescription("BAY65");brand.addView(title);TextView subtitle=text("Home is your next stop.",8,MUTED);subtitle.setLetterSpacing(.15f);brand.addView(subtitle);LinearLayout identity=horizontal();ImageView mark=new ImageView(this);mark.setImageResource(R.drawable.bay65_icon);mark.setTag("bay65-mark");mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);identity.addView(mark,new LinearLayout.LayoutParams(dp(40),dp(40)));LinearLayout.LayoutParams brandParams=new LinearLayout.LayoutParams(-2,-2);brandParams.leftMargin=dp(12);identity.addView(brand,brandParams);header.addView(identity,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout time=vertical();time.setGravity(Gravity.END);timeLabel=text("",24,INK);dateLabel=text("",9,MUTED);time.addView(timeLabel);time.addView(dateLabel);LinearLayout.LayoutParams timeParams=new LinearLayout.LayoutParams(-2,-2);timeParams.rightMargin=dp(20);header.addView(time,timeParams);
        networkButton=iconButton("Network settings",()->openSystem(Settings.ACTION_WIFI_SETTINGS,Settings.ACTION_WIRELESS_SETTINGS,Settings.ACTION_SETTINGS));header.addView(networkButton);
        ImageView settings=iconButton("BAY65 settings",this::showSettingsMenu);settings.setImageDrawable(new SystemIcon("settings",INK));LinearLayout.LayoutParams gearParams=new LinearLayout.LayoutParams(dp(38),dp(38));gearParams.leftMargin=dp(12);header.addView(settings,gearParams);content.addView(header,new LinearLayout.LayoutParams(-1,dp(46)));
        HorizontalScrollView buses=new HorizontalScrollView(this);busViewport=buses;buses.setTag("bus-viewport");buses.setFillViewport(true);buses.setClipToPadding(false);buses.setHorizontalScrollBarEnabled(false);busRow=horizontal();busRow.setGravity(Gravity.END|Gravity.TOP);buses.addView(busRow,new HorizontalScrollView.LayoutParams(-1,-1));LinearLayout.LayoutParams busParams=new LinearLayout.LayoutParams(-1,dp(compact?144:180));busParams.topMargin=dp(14);content.addView(buses,busParams);
        busSpacer=new View(this);content.addView(busSpacer,new LinearLayout.LayoutParams(1,0,1));
        content.getViewTreeObserver().addOnGlobalLayoutListener(this::fitBusCardsToSpace);
        TextView favouriteHeading=text("Your favourites",compact?12:15,INK);favouriteHeading.setPadding(0,dp(8),0,dp(9));content.addView(favouriteHeading);
        HorizontalScrollView favourites=new ShelfScrollView(this);favourites.setClipToPadding(false);favourites.setHorizontalScrollBarEnabled(false);favouritesRow=horizontal();favourites.addView(favouritesRow,new HorizontalScrollView.LayoutParams(-2,-2));content.addView(favourites,new LinearLayout.LayoutParams(-1,dp(compact?102:140)));
        HorizontalScrollView games=new ShelfScrollView(this);games.setHorizontalScrollBarEnabled(false);playtimeRow=horizontal();games.addView(playtimeRow);LinearLayout.LayoutParams gamesParams=new LinearLayout.LayoutParams(-1,dp(48));gamesParams.topMargin=dp(12);content.addView(games,gamesParams);
        setContentView(root);renderBusCards();renderApps();refreshNetwork();
    }
    private ImageView iconButton(String label,Runnable action){ImageView button=new ImageView(this);button.setContentDescription(label);button.setPadding(dp(8),dp(8),dp(8),dp(8));focusable(button,30);button.setOnClickListener(v->action.run());button.setLayoutParams(new LinearLayout.LayoutParams(dp(38),dp(38)));return button;}
    private void renderApps(){
        if(favouritesRow==null)return;
        Object focus=getCurrentFocus()==null?null:getCurrentFocus().getTag();
        favouritesRow.removeAllViews();playtimeRow.removeAllViews();
        int available=Math.round(getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density)-60;
        int width=Math.min(190,(available-5*12)/6);
        for(AppRepository.Entry entry:apps.section(store,"favourites")){
            AppTile tile=new AppTile(this,artwork(entry),entry.label);tile.setTag(entry.packageName);tile.setOnClickListener(v->launch(entry));
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(dp(width),dp(width*9f/16f));params.rightMargin=dp(12);favouritesRow.addView(tile,params);
        }
        if(favouritesRow.getChildCount()==0)favouritesRow.addView(button("Choose favourites",this::showManageApps));
        List<AppRepository.Entry> games=apps.section(store,"playtime");
        if(!games.isEmpty()) {TextView label=text("Playtime",12,MUTED);label.setPadding(0,0,dp(18),0);playtimeRow.addView(label);}
        for(AppRepository.Entry entry:games){
            AppTile tile=new AppTile(this,artwork(entry),entry.label);tile.setTag(entry.packageName);tile.setOnClickListener(v->launch(entry));LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(dp(78),dp(44));params.rightMargin=dp(12);playtimeRow.addView(tile,params);
        }
        if(focus instanceof String){View previous=root.findViewWithTag(focus);if(previous!=null)previous.requestFocus();}
    }
    private void launch(AppRepository.Entry entry){try{startActivity(new Intent(entry.intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}catch(ActivityNotFoundException|SecurityException exception){message("This app cannot be opened. It may have been removed or disabled.");}}
    private void renderBusCards(){
        busCards.clear();busRow.removeAllViews();
        if(store.stops.isEmpty()){
            LinearLayout empty=vertical();empty.setGravity(Gravity.END);TextView label=text("BAY65 Transit",15,INK);label.setPadding(0,0,0,dp(9));empty.addView(label);empty.addView(button("Add a bus stop",()->showStopEditor(null)));busRow.addView(empty,new LinearLayout.LayoutParams(dp(220),-2));return;
        }
        int available=Math.round(getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density)-60;
        int width=Math.min(310,Math.max(180,(available-(store.stops.size()-1)*12)/store.stops.size()));
        for(NookStore.Stop stop:store.stops){
            BusCard card=new BusCard();card.stop=stop;
            LinearLayout container=vertical();card.container=container;container.setPadding(dp(12),dp(9),dp(12),dp(8));container.setBackground(surface(PANEL,14));
            LinearLayout header=horizontal();LinearLayout title=vertical();TextView name=text(stop.name,compact?13:16,INK);name.setMaxLines(1);name.setEllipsize(TextUtils.TruncateAt.END);title.addView(name);title.addView(text(stop.code,9,MUTED));header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
            TextView edit=text("⋮",20,MUTED);edit.setGravity(Gravity.CENTER);edit.setContentDescription("Edit "+stop.name);focusable(edit,6);edit.setOnClickListener(v->showStopEditor(stop));header.addView(edit,new LinearLayout.LayoutParams(dp(26),dp(29)));container.addView(header);
            LinearLayout headings=horizontal();headings.setPadding(0,dp(7),0,dp(3));for(String h:new String[]{"BUS","NEXT","THEN","LATER"}){TextView t=text(h,8,MUTED);t.setGravity(h.equals("BUS")?Gravity.START:Gravity.END);headings.addView(t,new LinearLayout.LayoutParams(0,-2,1));}container.addView(headings);
            ScrollView scroll=new ScrollView(this);card.scroll=scroll;scroll.setFillViewport(false);LinearLayout services=vertical();scroll.addView(services);container.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
            for(String service:stop.services){LinearLayout row=horizontal();row.setPadding(0,dp(3),0,dp(3));TextView number=text(service,16,INK);row.addView(number,new LinearLayout.LayoutParams(0,-2,1));TextView next=text("—",13,MUTED),then=text("—",12,MUTED),later=text("—",12,MUTED);next.setGravity(Gravity.END);then.setGravity(Gravity.END);later.setGravity(Gravity.END);row.addView(next,new LinearLayout.LayoutParams(0,-2,1));row.addView(then,new LinearLayout.LayoutParams(0,-2,1));row.addView(later,new LinearLayout.LayoutParams(0,-2,1));row.setFocusable(true);row.setContentDescription("Service "+service);services.addView(row);card.cells.add(new TextView[]{next,then,later});}
            card.status=text("",8,MUTED);card.status.setMaxLines(2);card.status.setPadding(0,dp(3),0,0);container.addView(card.status);LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(dp(width),-1);params.leftMargin=dp(busRow.getChildCount()==0?0:12);busRow.addView(container,params);busCards.put(stop.code,card);card.update();
        }
    }
    private void fitBusCardsToSpace(){
        if(busViewport==null||busSpacer==null||busViewport.getHeight()==0)return;
        int minimum=dp(compact?144:180),desired=minimum;
        for(BusCard card:busCards.values()){
            // ScrollView measures its contents at full height, even when only part is visible.
            desired=Math.max(desired,card.container.getMeasuredHeight()-card.scroll.getMeasuredHeight()+card.scroll.getChildAt(0).getMeasuredHeight());
        }
        int available=busViewport.getHeight()+busSpacer.getHeight();
        int height=Rules.busCardHeight(minimum,available,desired,dp(12));
        LinearLayout.LayoutParams params=(LinearLayout.LayoutParams)busViewport.getLayoutParams();
        if(params.height!=height){params.height=height;busViewport.setLayoutParams(params);}
    }
    private final Runnable tick=new Runnable(){@Override public void run(){if(!active)return;timeLabel.setText(clock(System.currentTimeMillis(),"h:mm a"));dateLabel.setText(clock(System.currentTimeMillis(),"EEEE, d MMMM"));for(BusCard card:busCards.values())card.update();main.postDelayed(this,1000);}};
    private final Runnable poll=new Runnable(){@Override public void run(){if(!active)return;pollArrivals();main.postDelayed(this,20000);}};
    private void pollArrivals(){
        final String key=apiKey;if(key==null)return;
        final int epoch=lifecycle,generation=keyGeneration;
        for(NookStore.Stop stop:new ArrayList<>(store.stops)){
            if(!pendingStops.add(stop.code))continue;
            io.execute(()->{JSONObject data=null;String failure=null;
                try{data=lta.arrivals(stop.code,key);}catch(LtaClient.Failure e){failure=e.getMessage();}
                final JSONObject result=data;final String error=failure;
                main.post(()->{pendingStops.remove(stop.code);if(!active||lifecycle!=epoch||keyGeneration!=generation||store.findStop(stop.code)==null)return;ArrivalState state=arrivalStates.computeIfAbsent(stop.code,c->new ArrivalState());if(result!=null){state.data=result;state.updated=System.currentTimeMillis();}state.error=error;BusCard card=busCards.get(stop.code);if(card!=null)card.update();});
            });
        }
    }
    private final ConnectivityManager.NetworkCallback networkCallback=new ConnectivityManager.NetworkCallback(){
        @Override public void onAvailable(Network network){main.post(()->{refreshNetwork();if(active)pollArrivals();});}
        @Override public void onLost(Network network){main.post(MainActivity.this::refreshNetwork);}
        @Override public void onCapabilitiesChanged(Network network,NetworkCapabilities capabilities){main.post(MainActivity.this::refreshNetwork);}
    };
    private void refreshNetwork(){
        if(networkButton==null)return;Network network=connectivity.getActiveNetwork();NetworkCapabilities capabilities=network==null?null:connectivity.getNetworkCapabilities(network);
        boolean internet=capabilities!=null&&capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        if(capabilities==null){networkKind="offline";networkDescription="Disconnected";}
        else if(capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)){networkKind="ethernet";networkDescription="Ethernet"+(internet?" · Internet available":" · No internet");}
        else if(capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)){networkKind="wifi";networkDescription="Wi-Fi"+(internet?" · Internet available":" · No internet");}
        else {networkKind="wifi";networkDescription=internet?"Connected · Internet available":"No internet";}
        networkButton.setImageDrawable(new SystemIcon(networkKind,internet?INK:0xffedc68f));networkButton.setContentDescription("Network settings. "+networkDescription);
    }
    private boolean trySystemAction(String action){
        Intent intent=new Intent(action);
        for(ResolveInfo handler:getPackageManager().queryIntentActivities(intent,android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)){
            android.content.pm.ActivityInfo info=handler.activityInfo;
            if(info==null||!info.exported||!info.enabled||Rules.settingsPlaceholder(info.packageName,info.name))continue;
            try{startActivity(new Intent(intent).setClassName(info.packageName,info.name));return true;}catch(ActivityNotFoundException|SecurityException ignored){}
        }
        return false;
    }
    private void openSystem(String... actions){
        for(String action:actions)if(trySystemAction(action))return;
        message("This TV does not expose that settings screen. Use the remote’s Settings button.");
    }
    private void showBluetooth(){
        if(trySystemAction(Settings.ACTION_BLUETOOTH_SETTINGS))return;
        openPage("bluetooth","Bluetooth & remotes");
        paragraph("Pair a new remote or accessory, or manage paired devices in Android settings → Remotes & accessories.");
        page.addView(button("Pair a remote or accessory",()->openSystem("com.google.android.intent.action.CONNECT_INPUT")));
        page.addView(button("Manage paired devices in Android settings",()->{message("Choose Remotes & accessories in Android settings.");openSystem(Settings.ACTION_SETTINGS);}));focusPage();
    }
    private void showAccessibility(){
        if(trySystemAction(Settings.ACTION_ACCESSIBILITY_SETTINGS))return;
        openPage("accessibility","Accessibility settings");
        paragraph("This TV does not provide a direct accessibility screen. Open Android settings and choose Device Preferences → Accessibility.");
        page.addView(button("Open Android settings",()->openSystem(Settings.ACTION_SETTINGS)));focusPage();
    }
    private void showLauncherChoice(){
        openPage("launcher","Choose launcher");
        android.app.role.RoleManager role=Build.VERSION.SDK_INT>=29?getSystemService(android.app.role.RoleManager.class):null;
        if(Build.VERSION.SDK_INT>=29&&role!=null&&role.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)){
            boolean current=role.isRoleHeld(android.app.role.RoleManager.ROLE_HOME);
            paragraph(current?"BAY65 is already your default home screen. You can choose another installed launcher in the system’s default home app settings.":"Use the system confirmation to choose BAY65 as your default home screen. Your current launcher stays unchanged until you confirm.");
            if(!current)page.addView(button("Use BAY65 as the home screen",()->{try{startActivityForResult(role.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME),42);}catch(ActivityNotFoundException|SecurityException unavailable){message("This TV does not provide a launcher chooser.");}}));
            page.addView(button("Open default home app settings",()->openSystem(Settings.ACTION_HOME_SETTINGS)));
        }else{
            paragraph("This TV does not provide a system launcher chooser. Your current default launcher is unchanged. Check Android settings for any launcher options provided by your TV.");
        }
        page.addView(button("Open Android settings",()->openSystem(Settings.ACTION_SETTINGS)));focusPage();
    }

    private void openPage(String id,String title){
        if(settingsDialog==null){
            settingsDialog=new Dialog(this);settingsDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            LinearLayout shell=vertical();shell.setPadding(dp(24),dp(19),dp(24),dp(18));shell.setBackground(surface(0xff0c1825,18));
            LinearLayout header=horizontal();dialogBack=button("‹ Back",()->pageBack.run());dialogBack.setGravity(Gravity.CENTER);LinearLayout.LayoutParams backParams=new LinearLayout.LayoutParams(dp(88),-2);backParams.rightMargin=dp(12);header.addView(dialogBack,backParams);
            dialogTitle=text("",22,INK);header.addView(dialogTitle,new LinearLayout.LayoutParams(0,-2,1));TextView close=button("Close",()->settingsDialog.dismiss());close.setGravity(Gravity.CENTER);header.addView(close,new LinearLayout.LayoutParams(dp(85),-2));shell.addView(header);
            settingsScroll=new SettingsScrollView(this);settingsScroll.setFillViewport(false);page=vertical();page.setPadding(0,dp(12),0,dp(20));settingsScroll.addView(page);shell.addView(settingsScroll,new LinearLayout.LayoutParams(-1,0,1));settingsDialog.setContentView(shell);
            settingsDialog.setOnKeyListener((dialog,key,event)->{if(key==KeyEvent.KEYCODE_BACK&&!settingsPage.equals("menu")){if(event.getAction()==KeyEvent.ACTION_UP)pageBack.run();return true;}return false;});
        }
        settingsPage=id;pageBack=this::showSettingsMenu;dialogTitle.setText(title);dialogBack.setVisibility(id.equals("menu")?View.GONE:View.VISIBLE);page.removeAllViews();settingsScroll.scrollTo(0,0);
        if(!settingsDialog.isShowing())settingsDialog.show();
        Window window=settingsDialog.getWindow();if(window!=null){window.setBackgroundDrawableResource(android.R.color.transparent);window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setDimAmount(.65f);window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE);window.setLayout(Math.min(dp(620),getResources().getDisplayMetrics().widthPixels-dp(70)),getResources().getDisplayMetrics().heightPixels-dp(50));}
    }
    private void focusPage(){main.post(()->{if(settingsDialog!=null&&settingsDialog.isShowing()){List<View> choices=page.getFocusables(View.FOCUS_FORWARD);if(!choices.isEmpty())choices.get(0).requestFocus();}});}
    private void showSettingsMenu(){
        openPage("menu","Settings");heading("Apps & home");
        page.addView(button("All apps",this::showAllApps));page.addView(button("Manage favourites",this::showManageApps));page.addView(button("BAY65 preferences",this::showPreferences));
        heading("TV & system");
        page.addView(button("Network settings · "+networkDescription,()->openSystem(Settings.ACTION_WIFI_SETTINGS,Settings.ACTION_WIRELESS_SETTINGS,Settings.ACTION_SETTINGS)));
        page.addView(button("Android settings",()->openSystem(Settings.ACTION_SETTINGS)));
        page.addView(button("Bluetooth & remotes",this::showBluetooth));
        page.addView(button("Accessibility settings",this::showAccessibility));
        page.addView(button("Choose launcher",this::showLauncherChoice));
        heading("About");page.addView(button("About BAY65",this::showAbout));focusPage();
    }
    private void showAllApps(){
        openPage("all-apps","All apps");paragraph("All installed launchable apps, including those not pinned to your home screen.");
        for(AppRepository.Entry entry:apps.entries)page.addView(appRow(entry,()->launch(entry)));
        if(apps.entries.isEmpty())paragraph("No launchable apps were found on this device.");focusPage();
    }
    private View appRow(AppRepository.Entry entry,Runnable action){
        LinearLayout row=horizontal();row.setPadding(dp(10),dp(7),dp(10),dp(7));focusable(row,9);row.setOnClickListener(v->action.run());row.setContentDescription(entry.label);
        ImageView art=new ImageView(this);art.setImageDrawable(artwork(entry));art.setScaleType(ImageView.ScaleType.FIT_CENTER);row.addView(art,new LinearLayout.LayoutParams(dp(62),dp(38)));TextView name=text(entry.label,15,INK);name.setPadding(dp(14),0,0,0);name.setMaxLines(2);row.addView(name,new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.bottomMargin=dp(8);row.setLayoutParams(params);return row;
    }
    private void showManageApps(){
        openPage("manage-apps","Manage favourites");paragraph("Choose apps for Favourites or Playtime, and change their order. All apps only keeps an app off the home screen without uninstalling it.");
        for(String section:new String[]{"favourites","playtime","hidden"}){
            heading(section.equals("favourites")?"Favourites":section.equals("playtime")?"Playtime":"All apps only");
            for(AppRepository.Entry entry:apps.section(store,section))page.addView(appRow(entry,()->showAppOptions(entry)));
        }
        focusPage();
    }
    private void showAppOptions(AppRepository.Entry entry){
        openPage("app-options",entry.label);pageBack=this::showManageApps;NookStore.AppChoice choice=store.choice(entry.packageName);
        for(String section:new String[]{"favourites","playtime","hidden"}){
            String label=section.equals("favourites")?"Show in Favourites":section.equals("playtime")?"Show in Playtime":"All apps only";
            page.addView(button(label+(choice.section.equals(section)?"  ✓":""),()->{choice.section=section;store.saveApps();renderApps();showAppOptions(entry);}));
        }
        if(!choice.section.equals("hidden")){
            heading("Order");List<AppRepository.Entry> peers=apps.section(store,choice.section);int index=peers.indexOf(entry);
            TextView left=button("Move left",()->{apps.move(store,entry.packageName,-1);renderApps();showAppOptions(entry);});left.setEnabled(index>0);left.setAlpha(index>0?1:.35f);page.addView(left);
            TextView right=button("Move right",()->{apps.move(store,entry.packageName,1);renderApps();showAppOptions(entry);});right.setEnabled(index<peers.size()-1);right.setAlpha(index<peers.size()-1?1:.35f);page.addView(right);
        }
        heading("App");page.addView(button("Open app",()->launch(entry)));page.addView(button("App information",()->{try{startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+entry.packageName)));}catch(ActivityNotFoundException|SecurityException e){openSystem(Settings.ACTION_SETTINGS);}}));focusPage();
    }
    private void showPreferences(){
        openPage("preferences","BAY65 preferences");
        heading("BAY65 Transit");paragraph("Add up to four stops. All stops stay visible side by side; Left and Right change their order.");
        if(store.stops.isEmpty())paragraph("No bus stops have been added.");
        for(NookStore.Stop stop:new ArrayList<>(store.stops)){
            TextView name=text(stop.name+" · "+stop.code,15,INK);name.setPadding(0,dp(6),0,dp(3));page.addView(name);paragraph("Services "+String.join(", ",stop.services));
            LinearLayout controls=horizontal();rowAction(controls,"Edit",()->showStopEditor(stop));int position=store.stops.indexOf(stop);
            TextView left=rowAction(controls,"← Left",()->{store.moveStop(stop.code,-1);renderBusCards();showPreferences();});left.setEnabled(position>0);left.setAlpha(position>0?1:.35f);
            TextView right=rowAction(controls,"Right →",()->{store.moveStop(stop.code,1);renderBusCards();showPreferences();});right.setEnabled(position<store.stops.size()-1);right.setAlpha(position<store.stops.size()-1?1:.35f);
            rowAction(controls,"Remove",()->new AlertDialog.Builder(this).setTitle("Remove this bus stop?").setMessage(stop.name).setNegativeButton("Cancel",null).setPositiveButton("Remove",(d,w)->{store.stops.remove(stop);store.saveStops();arrivalStates.remove(stop.code);renderBusCards();showPreferences();}).show());page.addView(controls);
        }
        if(store.stops.size()<4)page.addView(button("Add a bus stop",()->showStopEditor(null)));
        TextView ltaConnection=button(apiKey==null?"Connect LTA":"LTA connection · Connected",this::showLta);((LinearLayout.LayoutParams)ltaConnection.getLayoutParams()).topMargin=dp(16);page.addView(ltaConnection);
        heading("BAY65 Memories");paragraph("Import image files from the TV’s file picker or a USB drive. BAY65 keeps private copies on this TV; your originals remain unchanged.");
        page.addView(button("Import photos",this::choosePhotos));
        CheckBox enabled=new CheckBox(this);enabled.setText("Use photos as the background");enabled.setTextColor(INK);enabled.setTextSize(14);enabled.setChecked(store.photosEnabled());enabled.setPadding(0,dp(7),0,dp(7));enabled.setOnCheckedChangeListener((b,checked)->{store.preferences.edit().putBoolean("photos_enabled",checked).apply();restartPhotos();});page.addView(enabled);
        page.addView(button("Change photo every · "+intervalName(store.photoSeconds()),this::chooseInterval));
        CheckBox fit=new CheckBox(this);fit.setText("Show the whole photo, including portrait images");fit.setTextColor(INK);fit.setTextSize(14);fit.setChecked(store.wholePhoto());fit.setOnCheckedChangeListener((b,checked)->{store.preferences.edit().putBoolean("photo_whole",checked).apply();restartPhotos();});page.addView(fit);
        heading("Imported photos · "+store.photos.size());
        for(NookStore.Photo photo:new ArrayList<>(store.photos))page.addView(button(photo.name,()->new AlertDialog.Builder(this).setTitle(photo.name).setItems(new String[]{"Show this photo","Remove imported copy"},(dialog,which)->{if(which==0){photoIndex=store.photos.indexOf(photo);store.preferences.edit().putBoolean("photos_enabled",true).apply();restartPhotos();if(settingsDialog!=null)settingsDialog.dismiss();}else{new File(store.photoDirectory,photo.file).delete();store.photos.remove(photo);store.savePhotos();photoIndex=0;restartPhotos();showPreferences();}}).show()));
        if(!store.photos.isEmpty())page.addView(button("Remove all imported photos",()->new AlertDialog.Builder(this).setTitle("Remove imported photos?").setMessage("Only BAY65’s copies are removed. The original files are kept.").setNegativeButton("Cancel",null).setPositiveButton("Remove",(d,w)->{for(NookStore.Photo photo:store.photos)new File(store.photoDirectory,photo.file).delete();store.photos.clear();store.savePhotos();photoIndex=0;restartPhotos();showPreferences();}).show()));
        focusPage();
    }
    private TextView rowAction(LinearLayout row,String title,Runnable action){TextView view=button(title,action);view.setTextSize(12);view.setPadding(dp(10),dp(10),dp(10),dp(10));view.setGravity(Gravity.CENTER);LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);params.rightMargin=dp(6);row.addView(view,params);return view;}
    private String intervalName(int n){return n==30?"30 seconds":n==60?"1 minute":n==300?"5 minutes":"10 minutes";}
    private void chooseInterval(){int[] seconds={30,60,300,600};String[] labels={"30 seconds","1 minute","5 minutes","10 minutes"};int selected=0;for(int i=0;i<seconds.length;i++)if(seconds[i]==store.photoSeconds())selected=i;new AlertDialog.Builder(this).setTitle("Change photo every").setSingleChoiceItems(labels,selected,(d,which)->{store.preferences.edit().putInt("photo_seconds",seconds[which]).apply();restartPhotos();d.dismiss();showPreferences();}).setNegativeButton("Cancel",null).show();}
    private EditText field(String label,String value,int inputType){
        TextView caption=text(label,12,MUTED);caption.setPadding(0,dp(7),0,dp(5));page.addView(caption);EditText input=new EditText(this);input.setText(value);input.setTextColor(INK);input.setTextSize(15);input.setSingleLine(true);input.setInputType(inputType);input.setSelectAllOnFocus(true);input.setPadding(dp(10),dp(9),dp(10),dp(9));input.setBackground(surface(0x18ffffff,7));input.setContentDescription(label);LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.bottomMargin=dp(10);page.addView(input,params);return input;
    }
    private void showStopEditor(NookStore.Stop original){showStopEditor(original,original==null?"":original.name,original==null?"":original.code,original==null?"":String.join(", ",original.services));}
    private void showStopEditor(NookStore.Stop original,String draftName,String draftCode,String draftServices){
        openPage("stop-editor",original==null?"Add a bus stop":"Edit bus stop");pageBack=this::showPreferences;
        EditText name=field("Stop name",draftName,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        EditText code=field("Five-digit stop code",draftCode,InputType.TYPE_CLASS_NUMBER);
        EditText services=field("Bus services, separated by commas",draftServices,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        page.addView(button("Find a stop by name or code",()->showStopLookup(original,name.getText().toString(),code.getText().toString(),services.getText().toString())));
        page.addView(button("Choose services reporting arrivals",()->chooseServices(code,services)));
        TextView error=text("",13,0xffffb6a7);error.setPadding(0,dp(6),0,dp(9));page.addView(error);
        page.addView(button("Save stop",()->{
            try{
                String stopName=name.getText().toString().trim(),stopCode=code.getText().toString().trim();Rules.stop(stopName,stopCode);List<String> selected=Rules.services(services.getText().toString());
                NookStore.Stop duplicate=store.findStop(stopCode);if(duplicate!=null&&duplicate!=original)throw new IllegalArgumentException("This stop is already added. Edit it from BAY65 preferences.");
                if(original==null&&store.stops.size()>=4)throw new IllegalArgumentException("Remove a stop before adding another.");
                NookStore.Stop saved=new NookStore.Stop(stopName,stopCode,selected);
                if(original==null)store.stops.add(saved);else{int index=store.stops.indexOf(original);if(index>=0)store.stops.set(index,saved);else store.stops.add(saved);if(!original.code.equals(stopCode))arrivalStates.remove(original.code);}
                store.saveStops();renderBusCards();pollArrivals();showPreferences();message("Bus stop saved");
            }catch(IllegalArgumentException exception){error.setText(exception.getMessage());}
        }));focusPage();
    }
    private void chooseServices(EditText code,EditText services){
        String key=apiKey,requested=code.getText().toString().trim();
        if(key==null){message("Connect your LTA key in BAY65 preferences first.");return;}
        if(!requested.matches("[0-9]{5}")){message("Enter a five-digit stop code first.");return;}
        message("Checking services reporting arrivals…");
        io.execute(()->{try{
            JSONArray data=lta.arrivals(requested,key).optJSONArray("Services");List<String> numbers=new ArrayList<>();if(data!=null)for(int i=0;i<data.length();i++){JSONObject item=data.optJSONObject(i);if(item!=null)numbers.add(item.optString("ServiceNo"));}
            main.post(()->{if(!active||!services.isAttachedToWindow()||!code.getText().toString().trim().equals(requested))return;if(numbers.isEmpty()){message("No services are reporting arrivals now. Enter the service numbers manually.");return;}
                Set<String> selected=new HashSet<>(Arrays.asList(services.getText().toString().toUpperCase(Locale.ROOT).split("[,\\s]+")));boolean[] checked=new boolean[numbers.size()];for(int i=0;i<checked.length;i++)checked[i]=selected.contains(numbers.get(i));
                new AlertDialog.Builder(this).setTitle("Services reporting arrivals now").setMultiChoiceItems(numbers.toArray(new String[0]),checked,(d,which,on)->checked[which]=on).setNegativeButton("Cancel",null).setPositiveButton("Use services",(d,w)->{List<String> chosen=new ArrayList<>();for(int i=0;i<checked.length;i++)if(checked[i])chosen.add(numbers.get(i));if(chosen.size()>8)message("Choose up to eight services.");else services.setText(String.join(", ",chosen));}).show();
            });
        }catch(LtaClient.Failure failure){main.post(()->{if(active)message(failure.getMessage());});}});
    }

    private void showStopLookup(NookStore.Stop original,String draftName,String draftCode,String draftServices){
        if(apiKey==null){message("Connect your LTA key in BAY65 preferences to search. You can enter a stop manually.");return;}
        openPage("lookup","Find a bus stop");pageBack=()->showStopEditor(original,draftName,draftCode,draftServices);
        EditText query=field("Stop name, road, or five-digit code","",InputType.TYPE_CLASS_TEXT);
        TextView status=text("Enter a search term, then select Search.",12,MUTED);
        LinearLayout results=vertical();
        Runnable search=()->{
            String term=query.getText().toString().trim().toLowerCase(Locale.ROOT);results.removeAllViews();
            if(term.isEmpty()){status.setText("Enter a stop name, road, or code.");return;}
            if(stopDirectory==null){status.setText("Loading LTA’s public bus stop directory…");final String key=apiKey;
                io.execute(()->{try{
                    JSONArray directory=null;long accessed=0;File cache=new File(getCacheDir(),"public-stop-directory.json");
                    if(cache.isFile()&&System.currentTimeMillis()-cache.lastModified()<24*60*60*1000L){try{directory=new JSONArray(new String(Files.readAllBytes(cache.toPath()),StandardCharsets.UTF_8));accessed=cache.lastModified();}catch(Exception ignored){}}
                    if(directory==null){directory=lta.directory(key);accessed=System.currentTimeMillis();try{Files.write(cache.toPath(),directory.toString().getBytes(StandardCharsets.UTF_8));}catch(Exception ignored){}}
                    final JSONArray loaded=directory;final long retrieved=accessed;main.post(()->{stopDirectory=loaded;stopDirectoryAccessed=retrieved;if(active&&settingsPage.equals("lookup")&&query.isAttachedToWindow())renderStopMatches(original,draftName,draftCode,draftServices,query,status,results);});
                }catch(LtaClient.Failure failure){main.post(()->{if(status.isAttachedToWindow())status.setText(failure.getMessage());});}});
            }else renderStopMatches(original,draftName,draftCode,draftServices,query,status,results);
        };
        paragraph("Bus Stops data: LTA DataMall · Singapore Open Data Licence v1.0 (link in About BAY65). The directory may be cached on this device.");page.addView(button("Search",search));page.addView(status);page.addView(results);focusPage();
    }
    private void renderStopMatches(NookStore.Stop original,String draftName,String draftCode,String draftServices,EditText query,TextView status,LinearLayout results){
        results.removeAllViews();String term=query.getText().toString().trim().toLowerCase(Locale.ROOT);int count=0;
        if(term.isEmpty()){status.setText("Enter a stop name, road, or code.");return;}
        for(int i=0;i<stopDirectory.length();i++){
            JSONObject stop=stopDirectory.optJSONObject(i);if(stop==null)continue;
            String name=stop.optString("Description"),code=stop.optString("BusStopCode"),road=stop.optString("RoadName");
            if((name+" "+road+" "+code).toLowerCase(Locale.ROOT).contains(term)){
                results.addView(button(name+" · "+code+"\n"+road,()->showStopEditor(original,name,code,code.equals(draftCode)?draftServices:"")));count++;if(count>=30)break;
            }
        }
        status.setText((count==0?"No matching stops. Try another name or code.":count==30?"Showing up to 30 matches. Refine the search if needed.":count+" matching stop"+(count==1?"":"s"))+(stopDirectoryAccessed>0?"\nLTA DataMall · Accessed "+clock(stopDirectoryAccessed,"dd MMM yyyy HH:mm"):""));
    }
    private void showLta(){
        openPage("lta","LTA connection");pageBack=this::showPreferences;Window window=settingsDialog.getWindow();if(window!=null)window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        paragraph("Enter your DataMall API Access Key. BAY65 tests it with LTA and stores it encrypted using Android Keystore. The key is never bundled into the app or shown after saving. Use only your own credentials and follow LTA API terms (links in About BAY65).");
        EditText input=field(apiKey==null?"API access key":"New API access key (to replace the saved key)","",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setAutofillHints((String[])null);input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        TextView status=text(apiKey==null?"Not connected":"A key is saved on this TV.",13,MUTED);status.setPadding(0,dp(8),0,dp(12));page.addView(status);
        TextView connect=button("Connect and test",()->{});page.addView(connect);
        connect.setOnClickListener(v->{final String candidate=input.getText().toString().trim();input.setText("");if(candidate.length()<8||candidate.length()>512||!candidate.matches("[\\x21-\\x7e]+")){status.setText("Enter a valid DataMall API Access Key.");return;}connect.setEnabled(false);input.setEnabled(false);status.setText("Testing the connection to LTA…");
            io.execute(()->{try{lta.test(candidate);secrets.save(candidate);main.post(()->{apiKey=candidate;keyGeneration++;arrivalStates.clear();renderBusCards();pollArrivals();connect.setEnabled(true);input.setEnabled(true);status.setText("Connected to LTA. Your encrypted key is saved on this TV.");message("LTA connected");});}
            catch(LtaClient.Failure failure){main.post(()->{connect.setEnabled(true);input.setEnabled(true);status.setText(failure.getMessage());});}
            catch(Exception unavailable){main.post(()->{connect.setEnabled(true);input.setEnabled(true);status.setText("The key could not be securely saved. Please try again.");});}});
        });
        if(apiKey!=null)page.addView(button("Disconnect and remove saved key",()->{apiKey=null;keyGeneration++;secrets.clear();arrivalStates.clear();File cache=new File(getCacheDir(),"public-stop-directory.json");cache.delete();stopDirectory=null;stopDirectoryAccessed=0;renderBusCards();showLta();message("LTA key removed");}));focusPage();
    }
    private void choosePhotos(){
        for(String action:new String[]{Intent.ACTION_OPEN_DOCUMENT,Intent.ACTION_GET_CONTENT}){
            Intent picker=new Intent(action).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            for(ResolveInfo handler:getPackageManager().queryIntentActivities(picker,0)){
                if(handler.activityInfo==null||handler.activityInfo.packageName.contains("frameworkpackagestubs"))continue;
                Intent explicit=new Intent(picker).setClassName(handler.activityInfo.packageName,handler.activityInfo.name);
                try{startActivityForResult(explicit,PHOTO_REQUEST);return;}catch(ActivityNotFoundException|SecurityException ignored){}
            }
        }
        new AlertDialog.Builder(this).setTitle("A photo picker is needed")
                .setMessage("This TV does not currently have a compatible photo picker. Install a TV file manager that supports image selection, then return to Import photos. You can choose files from the TV or a USB drive.")
                .setNegativeButton("Close",null).setPositiveButton("Find a file manager",(d,w)->{
                    try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("market://search?q=file%20manager%20tv&c=apps")));}
                    catch(ActivityNotFoundException|SecurityException unavailable){message("Open the TV’s app store and search for a file manager with image-selection support.");}
                }).show();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(request!=PHOTO_REQUEST||result!=RESULT_OK||data==null)return;
        List<Uri> selected=new ArrayList<>();ClipData clips=data.getClipData();if(clips!=null){for(int i=0;i<clips.getItemCount();i++)selected.add(clips.getItemAt(i).getUri());}else if(data.getData()!=null)selected.add(data.getData());
        if(selected.isEmpty())return;final int existing=store.photos.size();message("Importing selected photos…");
        io.execute(()->{PhotoManager.ImportResult imported=photoManager.importFiles(selected,existing);main.post(()->{
            if(isDestroyed())return;store.photos.addAll(imported.added);store.savePhotos();if(!imported.added.isEmpty())store.preferences.edit().putBoolean("photos_enabled",true).apply();photoIndex=0;restartPhotos();
            message(imported.added.size()+" photo"+(imported.added.size()==1?"":"s")+" imported"+(imported.skipped>0?". Some files were skipped (unsupported image or storage limit).":"."));
            if(settingsDialog!=null&&settingsDialog.isShowing())showPreferences();
        });});
    }
    private void restartPhotos(){
        main.removeCallbacks(photoTick);photoGeneration++;showPhoto();if(active)main.postDelayed(photoTick,store.photoSeconds()*1000L);
    }
    private final Runnable photoTick=new Runnable(){@Override public void run(){if(!active)return;if(store.photosEnabled()&&store.photos.size()>1){photoIndex=(photoIndex+1)%store.photos.size();showPhoto();}main.postDelayed(this,store.photoSeconds()*1000L);}};
    private void showPhoto(){
        if(wallpaper==null)return;
        wallpaper.setScaleType(store.wholePhoto()?ImageView.ScaleType.FIT_CENTER:ImageView.ScaleType.CENTER_CROP);
        if(!store.photosEnabled()||store.photos.isEmpty()){wallpaper.setImageDrawable(null);return;}
        photoIndex=Math.max(0,Math.min(photoIndex,store.photos.size()-1));final NookStore.Photo photo=store.photos.get(photoIndex);final int generation=++photoGeneration;
        io.execute(()->{Bitmap bitmap=photoManager.decode(photo);main.post(()->{if(isDestroyed()||generation!=photoGeneration||!store.photosEnabled())return;wallpaper.setImageBitmap(bitmap);if(bitmap==null&&active)message("This photo could not be displayed. Try another image.");});});
    }
    private void openExternal(String url){
        try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(ActivityNotFoundException | SecurityException e){message("No web browser available. Open the address in About BAY65 on another device.");}
    }
    private void showAbout(){
        openPage("about","About BAY65");String version="0.2.0";try{version=getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception ignored){}
        heading("BAY65");paragraph("Your home. Your city. Your screen.");paragraph(version+" · Personalised Android TV Launcher");heading("This device");paragraph(Build.MANUFACTURER+" "+Build.MODEL+"\nAndroid "+Build.VERSION.RELEASE+" · API "+Build.VERSION.SDK_INT+"\n"+networkDescription);
        page.addView(button("Android device information",()->openSystem(Settings.ACTION_DEVICE_INFO_SETTINGS,Settings.ACTION_SETTINGS)));
        heading("This wallpaper");paragraph(store.photosEnabled()&&!store.photos.isEmpty()?"Imported photo · "+store.photos.get(Math.min(photoIndex,store.photos.size()-1)).name:"BAY65’s default background");
        heading("BAY65 Transit");paragraph("Seats · green | Standing · orange | Limited · red\nCapacity unknown · slate");paragraph(apiKey==null?"LTA is not connected.":"LTA DataMall · Updates every 20 seconds while BAY65 is visible. — means no estimate; ~ indicates a scheduled estimate.");
        heading("Data attribution");paragraph("Bus Arrival v3 and Bus Stops data: Land Transport Authority (LTA) DataMall, Singapore Open Data Licence v1.0. Arrival cards show the last successful access date and time. Data is provided as available; estimates are not guaranteed. BAY65 is independent and not endorsed by LTA.");
        paragraph("Licence: https://datamall.lta.gov.sg/content/datamall/en/SingaporeOpenDataLicence.html");
        page.addView(button("Singapore Open Data Licence",()->openExternal("https://datamall.lta.gov.sg/content/datamall/en/SingaporeOpenDataLicence.html")));
        page.addView(button("LTA API terms",()->openExternal("https://datamall.lta.gov.sg/content/datamall/en/api-terms-of-service.html")));
        heading("Privacy");paragraph("Stops, photos, and the encrypted key are stored privately on this TV. Your key and requested stop codes are sent directly to LTA over HTTPS. Photos are not uploaded by BAY65. No analytics, advertising, account sign-in, or cloud backup. Disconnect to remove the key, or uninstall BAY65 to remove its private data.");focusPage();
    }
}
