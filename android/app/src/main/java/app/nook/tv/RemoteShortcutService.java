package app.nook.tv;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Intent;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/** Optional key filter. No window content, accessibility events, or key history is collected. */
public final class RemoteShortcutService extends AccessibilityService {
    public static final String KEY = "everywhere_shortcut_key";
    public static final int[] KEYS = {0, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_PROG_RED,
            KeyEvent.KEYCODE_PROG_BLUE, KeyEvent.KEYCODE_SEARCH};
    public static final String[] LABELS = {"Off", "Menu", "Red", "Blue", "Search"};
    private int consumedKey;

    public static boolean enabled(android.content.Context context) {
        String services = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        String name = new ComponentName(context, RemoteShortcutService.class).flattenToString();
        if (services != null) for (String service : services.split(":"))
            if (name.equalsIgnoreCase(service)) return true;
        return false;
    }

    @Override protected boolean onKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (consumedKey == code) {
            if (event.getAction() == KeyEvent.ACTION_UP) consumedKey = 0;
            return true;
        }
        android.content.SharedPreferences preferences = getSharedPreferences("nook", MODE_PRIVATE);
        int selected = preferences.getInt(KEY, 0);
        if (selected == 0 || code != selected || event.getAction() != KeyEvent.ACTION_DOWN
                || event.getRepeatCount() != 0 || event.isCanceled()
                || !preferences.getBoolean(Everywhere.ENABLED, false)
                || !Settings.canDrawOverlays(this) || !new AgentSettings(this).ready()) return false;
        try {
            startActivity(new Intent(this, AssistantActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            consumedKey = code;
            return true;
        } catch (RuntimeException unavailable) { return false; }
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { consumedKey = 0; }
    @Override public void onDestroy() { consumedKey = 0; super.onDestroy(); }
}
