package app.nook.tv;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Collections;
import java.util.IdentityHashMap;

/**
 * Process-wide assistant state, touched only on the main thread. The launcher, the Everywhere panel and
 * the overlay orb all read it, so there is one conversation, one visible orb and one active session.
 */
final class AssistantPresence {
    interface Listener { void onPresenceChanged(); }

    private static final List<Listener> listeners = new ArrayList<>();
    /** The shared conversation (last six exchanges), kept in memory only. */
    static final List<Agent.Turn> history = new ArrayList<>();
    private static final Set<Object> launchers = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<Object> panels = Collections.newSetFromMap(new IdentityHashMap<>());
    static boolean launcherVisible, panelVisible;
    static String state = "IDLE";
    static float level;
    static long lastActive = System.currentTimeMillis();

    private AssistantPresence() {}

    static void listen(Listener listener) { if (!listeners.contains(listener)) listeners.add(listener); }
    static void unlisten(Listener listener) { listeners.remove(listener); }

    // Android TV can keep separate HOME and ordinary launcher instances in different tasks.
    // A late stop from one instance must not hide the other instance's presence.
    static void setLauncherVisible(Object owner, boolean visible) {
        if (visible) launchers.add(owner); else launchers.remove(owner);
        launcherVisible = !launchers.isEmpty();
        changed();
    }
    static void setPanelVisible(Object owner, boolean visible) {
        if (visible) panels.add(owner); else panels.remove(owner);
        panelVisible = !panels.isEmpty();
        changed();
    }

    static void setState(String next) {
        state = next;
        lastActive = System.currentTimeMillis();
        changed();
    }

    /** Levels change many times a second; listeners read them on their own frame clock instead. */
    static void setLevel(float next) { level = next; if (next > .05f) lastActive = System.currentTimeMillis(); }

    private static void changed() { for (Listener listener : new ArrayList<>(listeners)) listener.onPresenceChanged(); }
}
