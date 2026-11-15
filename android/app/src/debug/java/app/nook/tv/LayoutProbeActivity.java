package app.nook.tv;

import android.content.SharedPreferences;
import java.io.File;

/** Development-only layout checks never touch the user's preferences or photos. */
public final class LayoutProbeActivity extends MainActivity {
    @Override public SharedPreferences getSharedPreferences(String name,int mode){return super.getSharedPreferences(name+"_layout_test",mode);}
    @Override public File getFilesDir(){File folder=new File(super.getFilesDir(),"layout-test");folder.mkdirs();return folder;}
    @Override public File getCacheDir(){File folder=new File(super.getCacheDir(),"layout-test");folder.mkdirs();return folder;}
}
