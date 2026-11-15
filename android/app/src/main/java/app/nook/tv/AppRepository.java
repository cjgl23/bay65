package app.nook.tv;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class AppRepository {
    static final class Entry {
        final String packageName,label;
        final Intent intent;
        final Drawable artwork;
        final boolean game;
        Entry(String pkg,String label,Intent intent,Drawable artwork,boolean game) {
            this.packageName=pkg;this.label=label;this.intent=intent;this.artwork=artwork;this.game=game;
        }
    }
    private final Context context;
    final List<Entry> entries=new ArrayList<>();
    AppRepository(Context context){this.context=context;}
    void refresh(NookStore store) {
        PackageManager pm=context.getPackageManager();Map<String,Entry> found=new LinkedHashMap<>();
        for(String category:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER}){
            Intent query=new Intent(Intent.ACTION_MAIN).addCategory(category);
            for(ResolveInfo info:pm.queryIntentActivities(query,0)){
                String pkg=info.activityInfo.packageName;
                if(pkg.equals(context.getPackageName())||found.containsKey(pkg)||!info.activityInfo.enabled)continue;
                Intent launch=pm.getLeanbackLaunchIntentForPackage(pkg);if(launch==null)launch=pm.getLaunchIntentForPackage(pkg);if(launch==null)continue;
                Drawable art=info.activityInfo.loadBanner(pm);if(art==null)art=info.activityInfo.applicationInfo.loadBanner(pm);if(art==null)art=info.loadIcon(pm);
                String label=info.loadLabel(pm).toString();
                ApplicationInfo application=info.activityInfo.applicationInfo;
                boolean game=application.category==ApplicationInfo.CATEGORY_GAME || (application.flags&ApplicationInfo.FLAG_IS_GAME)!=0;
                found.put(pkg,new Entry(pkg,label,launch,art,game));
            }
        }
        entries.clear();entries.addAll(found.values());entries.sort(Comparator.comparing(e->e.label.toLowerCase(Locale.ROOT)));
        boolean initial=!store.preferences.getBoolean("apps_initialised",false);
        String[] favourites={"com.netflix.ninja","com.google.android.youtube.tv","com.disney.disneyplus","com.amazon.amazonvideo.livingroom","com.apple.atve.androidtv.appletv","com.spotify.tv.android"};
        if(initial){
            for(String pkg:favourites){Entry entry=find(pkg);if(entry!=null&&store.choice(pkg)==null)store.apps.add(new NookStore.AppChoice(pkg,"favourites"));}
        }
        for(Entry e:entries)if(store.choice(e.packageName)==null){
            String name=e.label.toLowerCase(Locale.ROOT);
            boolean preferredGame=name.contains("red ball")||name.contains("redball")||name.contains("astronaut stickman");
            store.apps.add(new NookStore.AppChoice(e.packageName,initial&&preferredGame?"playtime":"hidden"));
        }
        store.preferences.edit().putBoolean("apps_initialised",true).apply();store.saveApps();
    }
    Entry find(String pkg){for(Entry e:entries)if(e.packageName.equals(pkg))return e;return null;}
    List<Entry> section(NookStore store,String section){
        List<Entry> result=new ArrayList<>();
        for(NookStore.AppChoice choice:store.apps){Entry e=find(choice.packageName);if(e!=null&&section.equals(choice.section))result.add(e);}
        return result;
    }
    boolean move(NookStore store,String pkg,int offset){
        NookStore.AppChoice selected=store.choice(pkg);if(selected==null||selected.section.equals("hidden"))return false;
        List<NookStore.AppChoice> peers=new ArrayList<>();for(NookStore.AppChoice c:store.apps)if(c.section.equals(selected.section)&&find(c.packageName)!=null)peers.add(c);
        int index=peers.indexOf(selected),target=index+offset;if(target<0||target>=peers.size())return false;
        Collections.swap(store.apps,store.apps.indexOf(selected),store.apps.indexOf(peers.get(target)));store.saveApps();return true;
    }
}
