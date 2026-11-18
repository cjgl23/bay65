package app.nook.tv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/**
 * Restores the Everywhere orb after the TV restarts or BAY65 is updated, when the user allowed it.
 * When BAY65 is the home screen it starts anyway; this covers TVs where another launcher is home.
 * BOOT_COMPLETED may start a specialUse foreground service on every supported Android version.
 */
public final class EverywhereBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) return;
        SharedPreferences preferences = context.getSharedPreferences("nook", Context.MODE_PRIVATE);
        if (!preferences.getBoolean(Everywhere.ENABLED, false) || !preferences.getBoolean(Everywhere.RESTORE, true)) return;
        OrbOverlayService.sync(context);
    }
}
