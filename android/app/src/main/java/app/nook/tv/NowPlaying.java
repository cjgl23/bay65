package app.nook.tv;

import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.provider.Settings;
import java.util.List;

/**
 * What is playing, from the metadata apps publish through Android's MediaSession (the same data shown in
 * the system's media controls): app, title, subtitle, artist, album and playing or paused. Android only
 * shows other apps' sessions to an enabled notification listener, so this needs the user to allow
 * {@link NowPlayingService} in Notification access. Screen content and notifications are never read.
 */
final class NowPlaying {
    /** Preference: share the current media with the assistant. Off by default. */
    static final String SHARE = "agent_share_now_playing";

    private NowPlaying() {}

    static ComponentName listener(Context context) { return new ComponentName(context, NowPlayingService.class); }

    static boolean granted(Context context) {
        ComponentName mine = listener(context);
        if (Build.VERSION.SDK_INT >= 27) {
            NotificationManager notifications = context.getSystemService(NotificationManager.class);
            return notifications != null && notifications.isNotificationListenerAccessGranted(mine);
        }
        String enabled = Settings.Secure.getString(context.getContentResolver(), "enabled_notification_listeners");
        if (enabled == null) return false;
        for (String name : enabled.split(":")) if (mine.equals(ComponentName.unflattenFromString(name))) return true;
        return false;
    }

    /** The playing (or else paused) media, or null when sharing is off, access is not granted or nothing plays. */
    static AssistantContext.Media current(Context context, boolean share) {
        if (!share || !granted(context)) return null;
        try {
            MediaSessionManager sessions = context.getSystemService(MediaSessionManager.class);
            if (sessions == null) return null;
            // Ordered by Android's media priority: the session most recently playing comes first.
            List<MediaController> controllers = sessions.getActiveSessions(listener(context));
            MediaController paused = null;
            for (MediaController controller : controllers) {
                if (context.getPackageName().equals(controller.getPackageName()) || controller.getMetadata() == null) continue;
                PlaybackState playback = controller.getPlaybackState();
                int state = playback == null ? PlaybackState.STATE_NONE : playback.getState();
                if (playing(state)) return media(context, controller, true);
                if (state == PlaybackState.STATE_PAUSED && paused == null) paused = controller;
            }
            return paused == null ? null : media(context, paused, false);
        } catch (RuntimeException unavailable) {
            // Access revoked between the check and the call, or the media service is unavailable.
            return null;
        }
    }

    private static boolean playing(int state) {
        switch (state) {
            case PlaybackState.STATE_PLAYING: case PlaybackState.STATE_BUFFERING: case PlaybackState.STATE_CONNECTING:
            case PlaybackState.STATE_FAST_FORWARDING: case PlaybackState.STATE_REWINDING:
            case PlaybackState.STATE_SKIPPING_TO_NEXT: case PlaybackState.STATE_SKIPPING_TO_PREVIOUS:
            case PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM:
                return true;
            default:
                return false;
        }
    }

    private static AssistantContext.Media media(Context context, MediaController controller, boolean playing) {
        MediaMetadata metadata = controller.getMetadata();
        return new AssistantContext.Media(label(context, controller.getPackageName()),
                text(metadata, MediaMetadata.METADATA_KEY_DISPLAY_TITLE, MediaMetadata.METADATA_KEY_TITLE),
                text(metadata, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),
                text(metadata, MediaMetadata.METADATA_KEY_ARTIST, MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
                text(metadata, MediaMetadata.METADATA_KEY_ALBUM), playing);
    }

    private static String text(MediaMetadata metadata, String... keys) {
        for (String key : keys) {
            CharSequence value = metadata.getText(key);
            if (value != null && value.toString().trim().length() > 0) return value.toString();
        }
        return "";
    }

    private static String label(Context context, String packageName) {
        try {
            PackageManager packages = context.getPackageManager();
            return packages.getApplicationLabel(packages.getApplicationInfo(packageName, 0)).toString();
        } catch (PackageManager.NameNotFoundException hidden) {
            return packageName;
        }
    }
}
