package app.nook.tv;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

/**
 * BAY65 Assistant Everywhere Mode: keeps the orb visible above other apps.
 *
 * The orb window is display-only (not focusable, not touchable), so it never takes D-pad focus or remote
 * input from Netflix, YouTube, games or playback controls. Questions are asked in {@link AssistantActivity},
 * which opens on demand. This service never listens to the microphone, never polls any API and never reads
 * other apps' content; it only draws the orb and keeps the "Ask BAY65" notification available.
 */
public final class OrbOverlayService extends Service implements AssistantPresence.Listener {
    private static final String CHANNEL = "bay65_everywhere";
    private static final int NOTIFICATION_ID = 65;
    private static final long TICK_MS = 15_000L;

    private final Handler main = new Handler(Looper.getMainLooper());
    private WindowManager windows;
    private OrbView orb;
    private WindowManager.LayoutParams params;
    private SharedPreferences preferences;
    private boolean attached, screenOn = true;

    /** Starts the service while BAY65 is visible (allowed from the foreground on every Android version). */
    static void sync(Context context) {
        SharedPreferences preferences = context.getSharedPreferences("nook", Context.MODE_PRIVATE);
        Intent intent = new Intent(context, OrbOverlayService.class);
        if (preferences.getBoolean(Everywhere.ENABLED, false) && Settings.canDrawOverlays(context) && new AgentSettings(context).ready()) {
            try { context.startForegroundService(intent); } catch (RuntimeException notAllowedNow) { /* retried on next launcher resume */ }
        } else {
            context.stopService(intent);
        }
    }

    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            screenOn = Intent.ACTION_SCREEN_ON.equals(intent.getAction());
            update();
        }
    };

    private final Runnable tick = new Runnable() {
        @Override public void run() { update(); main.postDelayed(this, TICK_MS); }
    };

    @Override public void onCreate() {
        super.onCreate();
        windows = getSystemService(WindowManager.class);
        preferences = getSharedPreferences("nook", MODE_PRIVATE);
        PowerManager power = getSystemService(PowerManager.class);
        screenOn = power == null || power.isInteractive();
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screen, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screen, filter);
        AssistantPresence.listen(this);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = notification();
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            else startForeground(NOTIFICATION_ID, notification);
        } catch (RuntimeException notAllowed) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!preferences.getBoolean(Everywhere.ENABLED, false) || !Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY; }
        main.removeCallbacks(tick);
        main.post(tick);
        return START_STICKY;
    }

    @Override public void onPresenceChanged() { update(); }

    /** Adds, updates or removes the orb window to match settings, permission, screen and visibility. */
    private void update() {
        boolean permitted = Settings.canDrawOverlays(this);
        boolean enabled = preferences.getBoolean(Everywhere.ENABLED, false);
        if (!enabled || !permitted) {
            // Permission revoked or mode turned off: stop quietly; the launcher explains when settings open.
            if (!permitted) preferences.edit().putBoolean(Everywhere.ENABLED, false).apply();
            detach();
            stopSelf();
            return;
        }
        boolean show = Everywhere.showOverlay(true, true, screenOn, AssistantPresence.launcherVisible, AssistantPresence.panelVisible);
        if (!show) { detach(); return; }
        attach();
        if (!attached) return;
        orb.setMode(OrbView.forState(AssistantPresence.state));
        long idle = System.currentTimeMillis() - AssistantPresence.lastActive;
        orb.setAlpha(Everywhere.alpha(preferences.getInt(Everywhere.OPACITY, Everywhere.DEFAULT_OPACITY),
                preferences.getBoolean(Everywhere.AUTO_DIM, true), idle));
        place();
    }

    private void attach() {
        if (attached) return;
        if (orb == null) {
            orb = new OrbView(this);
            orb.setAmbient(true);
            orb.setFocusable(false);
            orb.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        int size = dp(Everywhere.size(preferences.getString(Everywhere.SIZE, null)).dp);
        params = new WindowManager.LayoutParams(size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.setTitle("BAY65 Assistant");
        place();
        try { windows.addView(orb, params); attached = true; }
        catch (RuntimeException rejected) { attached = false; }
    }

    private void place() {
        if (params == null) return;
        Everywhere.Corner corner = Everywhere.corner(preferences.getString(Everywhere.CORNER, null));
        int size = dp(Everywhere.size(preferences.getString(Everywhere.SIZE, null)).dp);
        int[] shift = Everywhere.shift(System.currentTimeMillis());
        boolean changed = params.width != size;
        params.width = size; params.height = size;
        int gravity = (corner.bottom ? Gravity.BOTTOM : Gravity.TOP) | (corner.right ? Gravity.END : Gravity.START);
        int x = dp(Everywhere.EDGE_DP + shift[0]), y = dp(Everywhere.EDGE_DP + shift[1]);
        changed |= params.gravity != gravity || params.x != x || params.y != y;
        params.gravity = gravity; params.x = x; params.y = y;
        if (attached && changed) {
            try { windows.updateViewLayout(orb, params); } catch (RuntimeException gone) { attached = false; }
        }
    }

    private void detach() {
        if (!attached) return;
        try { windows.removeView(orb); } catch (RuntimeException alreadyGone) {}
        attached = false;
    }

    private Notification notification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null && manager.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "BAY65 Assistant Everywhere", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Keeps the BAY65 Assistant orb available over other apps. Select it to ask a question.");
            channel.setShowBadge(false);
            manager.createNotificationChannel(channel);
        }
        PendingIntent ask = PendingIntent.getActivity(this, 0,
                new Intent(this, AssistantActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_bay65_notification)
                .setContentTitle("BAY65 Assistant")
                .setContentText("Select to ask a question")
                .setContentIntent(ask)
                .setOngoing(true)
                .setShowWhen(false)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override public void onDestroy() {
        main.removeCallbacksAndMessages(null);
        AssistantPresence.unlisten(this);
        try { unregisterReceiver(screen); } catch (RuntimeException ignored) {}
        detach();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
