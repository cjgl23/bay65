package app.nook.tv;

import android.service.notification.NotificationListenerService;

/**
 * Exists only so the user can grant BAY65 Android's notification-listener access, which Android requires
 * before {@link android.media.session.MediaSessionManager#getActiveSessions} returns other apps' media
 * sessions (see {@link NowPlaying}). It overrides none of the notification callbacks, so notifications are
 * never read, stored or forwarded, and it asks Android to unbind as soon as it connects. Unbinding keeps
 * the grant, which is all that media-session access checks.
 */
public final class NowPlayingService extends NotificationListenerService {
    @Override public void onListenerConnected() {
        super.onListenerConnected();
        try { requestUnbind(); } catch (RuntimeException ignored) { /* still bound; callbacks remain no-ops */ }
    }
}
