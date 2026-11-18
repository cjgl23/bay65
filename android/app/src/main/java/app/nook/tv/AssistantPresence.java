package app.nook.tv;

import java.util.ArrayList;
import java.util.List;

/**
 * Process-wide assistant state, touched only on the main thread. The launcher, the Everywhere panel and
 * the overlay orb all read it, so there is one conversation, one visible orb and one active session.
 */
final class AssistantPresence {
    interface Listener { void onPresenceChanged(); }

    private static final List<Listener> listeners = new ArrayList<>();
    /** The shared conversation (last six exchanges), kept in memory only. */
    static final List<Agent.Turn> history = new ArrayList<>();
    static boolean launcherVisible, panelVisible;
    static String state = "IDLE";
    static float level;
    static long lastActive = System.currentTimeMillis();

    private AssistantPresence() {}

    static void listen(Listener listener) { if (!listeners.contains(listener)) listeners.add(listener); }
    static void unlisten(Listener listener) { listeners.remove(listener); }

    static void setLauncherVisible(boolean visible) { launcherVisible = visible; changed(); }
    static void setPanelVisible(boolean visible) { panelVisible = visible; changed(); }

    static void setState(String next) {
        state = next;
        lastActive = System.currentTimeMillis();
        changed();
    }

    /** Levels change many times a second; listeners read them on their own frame clock instead. */
    static void setLevel(float next) { level = next; if (next > .05f) lastActive = System.currentTimeMillis(); }

    private static void changed() { for (Listener listener : new ArrayList<>(listeners)) listener.onPresenceChanged(); }
}
