package app.nook.tv;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class NookStore {
    static final class Stop {
        final String name, code;
        final List<String> services;
        Stop(String name, String code, List<String> services) {
            this.name = name; this.code = code; this.services = new ArrayList<>(services);
        }
    }
    static final class AppChoice {
        final String packageName;
        String section;
        AppChoice(String packageName, String section) { this.packageName = packageName; this.section = section; }
    }
    static final class Photo {
        final String file, name;
        Photo(String file, String name) { this.file = file; this.name = name; }
    }
    final SharedPreferences preferences;
    final File photoDirectory;
    final List<Stop> stops = new ArrayList<>();
    final List<AppChoice> apps = new ArrayList<>();
    final List<Photo> photos = new ArrayList<>();
    NookStore(Context context) {
        preferences = context.getSharedPreferences("nook", Context.MODE_PRIVATE);
        photoDirectory = new File(context.getFilesDir(), "photos");
        if (!photoDirectory.exists()) photoDirectory.mkdirs();
        JSONArray saved = read("stops");
        for (int i=0; i<saved.length() && stops.size()<4; i++) {
            JSONObject s=saved.optJSONObject(i); if(s==null) continue;
            try {
                String name=s.getString("name"),code=s.getString("code"); Rules.stop(name,code);
                List<String> services=Rules.services(s.getString("services"));
                if(findStop(code)==null) stops.add(new Stop(name,code,services));
            } catch(JSONException | IllegalArgumentException ignored) {}
        }
        saved=read("apps");
        for(int i=0;i<saved.length();i++) {
            JSONObject a=saved.optJSONObject(i); if(a==null)continue;
            String pkg=a.optString("package"),section=a.optString("section");
            if(!pkg.isEmpty() && (section.equals("favourites")||section.equals("playtime")||section.equals("hidden")) && choice(pkg)==null)
                apps.add(new AppChoice(pkg,section));
        }
        saved=read("photos");
        for(int i=0;i<saved.length();i++) {
            JSONObject p=saved.optJSONObject(i);if(p==null)continue;
            String file=p.optString("file");
            if(file.matches("[a-f0-9-]+\\.img") && new File(photoDirectory,file).isFile()) photos.add(new Photo(file,p.optString("name","Photo")));
        }
    }
    private JSONArray read(String key) { try { return new JSONArray(preferences.getString(key,"[]")); } catch(JSONException e) { return new JSONArray(); } }
    Stop findStop(String code) { for(Stop s:stops)if(s.code.equals(code))return s;return null; }
    AppChoice choice(String pkg) { for(AppChoice a:apps)if(a.packageName.equals(pkg))return a;return null; }
    void saveStops() {
        JSONArray array=new JSONArray();
        try { for(Stop s:stops) array.put(new JSONObject().put("name",s.name).put("code",s.code).put("services",String.join(", ",s.services))); }
        catch(JSONException impossible) { throw new IllegalStateException(impossible); }
        preferences.edit().putString("stops",array.toString()).apply();
    }
    void saveApps() {
        JSONArray array=new JSONArray();
        try {for(AppChoice a:apps)array.put(new JSONObject().put("package",a.packageName).put("section",a.section));}
        catch(JSONException impossible) {throw new IllegalStateException(impossible);}
        preferences.edit().putString("apps",array.toString()).apply();
    }
    void savePhotos() {
        JSONArray array=new JSONArray();
        try {for(Photo p:photos)array.put(new JSONObject().put("file",p.file).put("name",p.name));}
        catch(JSONException impossible) {throw new IllegalStateException(impossible);}
        preferences.edit().putString("photos",array.toString()).apply();
    }
    void moveStop(String code,int by) { Stop s=findStop(code);if(s==null)return;int i=stops.indexOf(s),j=i+by;if(j>=0&&j<stops.size()){Collections.swap(stops,i,j);saveStops();} }
    boolean photosEnabled() { return preferences.getBoolean("photos_enabled",true); }
    boolean wholePhoto() { return preferences.getBoolean("photo_whole",false); }
    int photoSeconds() { int n=preferences.getInt("photo_seconds",60);return n==30||n==60||n==300||n==600?n:60; }
}
