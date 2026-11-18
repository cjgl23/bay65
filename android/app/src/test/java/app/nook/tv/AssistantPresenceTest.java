package app.nook.tv;

import org.junit.Test;
import static org.junit.Assert.*;

public class AssistantPresenceTest {
    @Test public void oldTaskStoppingCannotExposeOverlayOverTheHomeTask() {
        Object ordinary = new Object(), home = new Object();
        try {
            AssistantPresence.setLauncherVisible(ordinary, true);
            AssistantPresence.setLauncherVisible(home, true);
            AssistantPresence.setLauncherVisible(ordinary, false);
            assertTrue(AssistantPresence.launcherVisible);
            assertFalse(Everywhere.showOverlay(true, true, true,
                    AssistantPresence.launcherVisible, AssistantPresence.panelVisible));
            // Repeated stop/destroy callbacks from an old task are harmless.
            AssistantPresence.setLauncherVisible(ordinary, false);
            assertTrue(AssistantPresence.launcherVisible);
            AssistantPresence.setLauncherVisible(home, false);
            assertTrue(Everywhere.showOverlay(true, true, true,
                    AssistantPresence.launcherVisible, AssistantPresence.panelVisible));
        } finally {
            AssistantPresence.setLauncherVisible(ordinary, false);
            AssistantPresence.setLauncherVisible(home, false);
        }
    }

    @Test public void repeatedStartsAndOverlappingPanelsKeepOnlyOneOrb() {
        Object launcher = new Object(), oldPanel = new Object(), newPanel = new Object();
        try {
            AssistantPresence.setLauncherVisible(launcher, true);
            AssistantPresence.setLauncherVisible(launcher, true);
            AssistantPresence.setPanelVisible(oldPanel, true);
            AssistantPresence.setPanelVisible(newPanel, true);
            AssistantPresence.setPanelVisible(oldPanel, false);
            assertTrue(AssistantPresence.panelVisible);
            AssistantPresence.setPanelVisible(newPanel, false);
            assertTrue(AssistantPresence.launcherVisible);
            assertFalse(Everywhere.showOverlay(true, true, true,
                    AssistantPresence.launcherVisible, AssistantPresence.panelVisible));
            AssistantPresence.setLauncherVisible(launcher, false);
            assertFalse(AssistantPresence.launcherVisible);
            assertFalse(AssistantPresence.panelVisible);
        } finally {
            AssistantPresence.setLauncherVisible(launcher, false);
            AssistantPresence.setPanelVisible(oldPanel, false);
            AssistantPresence.setPanelVisible(newPanel, false);
        }
    }
}
