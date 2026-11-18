package app.nook.tv;

/**
 * Settings and decisions for BAY65 Assistant Everywhere Mode: the orb shown over other apps.
 * Pure Java (no Android classes) so the rules run in JVM tests; Android maps them onto windows.
 */
public final class Everywhere {
    private Everywhere() {}

    // Preference keys in the existing "nook" SharedPreferences. All default to the documented values.
    public static final String ENABLED = "everywhere_enabled", CORNER = "everywhere_corner", SIZE = "everywhere_size",
            OPACITY = "everywhere_opacity", AUTO_DIM = "everywhere_auto_dim", RESTORE = "everywhere_restore";

    public enum Corner {
        BOTTOM_RIGHT("Bottom right", true, true), BOTTOM_LEFT("Bottom left", true, false),
        TOP_RIGHT("Top right", false, true), TOP_LEFT("Top left", false, false);
        public final String label;
        public final boolean bottom, right;
        Corner(String label, boolean bottom, boolean right) { this.label = label; this.bottom = bottom; this.right = right; }
    }

    public enum Size {
        SMALL("Small", 48), MEDIUM("Medium", 64), LARGE("Large", 80);
        public final String label;
        public final int dp;
        Size(String label, int dp) { this.label = label; this.dp = dp; }
    }

    /** Idle opacity choices, in percent. */
    public static final int[] OPACITIES = {15, 30, 50, 100};
    public static final int DEFAULT_OPACITY = 30;

    public static Corner corner(String name) {
        for (Corner c : Corner.values()) if (c.name().equals(name)) return c;
        return Corner.BOTTOM_RIGHT;
    }

    public static Size size(String name) {
        for (Size s : Size.values()) if (s.name().equals(name)) return s;
        return Size.SMALL;
    }

    public static int opacity(int percent) {
        for (int o : OPACITIES) if (o == percent) return o;
        return DEFAULT_OPACITY;
    }

    /**
     * Whether the overlay orb should be on screen. The launcher and the assistant panel show their own
     * orb, so the overlay hides while either is visible: there is never more than one orb.
     */
    public static boolean showOverlay(boolean enabled, boolean permitted, boolean screenOn, boolean launcherVisible, boolean panelVisible) {
        return enabled && permitted && screenOn && !launcherVisible && !panelVisible;
    }

    /** After this long without assistant activity, an auto-dimming orb fades to half its idle opacity. */
    public static final long DIM_AFTER_MS = 30_000L;

    /** The alpha (0..1) for the overlay orb. */
    public static float alpha(int idlePercent, boolean autoDim, long idleForMs) {
        float alpha = opacity(idlePercent) / 100f;
        if (autoDim && idleForMs >= DIM_AFTER_MS) alpha = alpha / 2f;
        return Math.max(.05f, alpha);
    }

    /** OLED care: the orb drifts a few dp around its corner, moving one step every this many ms. */
    public static final long SHIFT_EVERY_MS = 10 * 60_000L;
    private static final int[][] SHIFTS = {{0, 0}, {4, 0}, {4, 4}, {0, 4}, {-4, 4}, {-4, 0}, {-4, -4}, {0, -4}, {4, -4}};

    /** The drift offset in dp (x towards the screen centre, y towards the screen centre) for a time. */
    public static int[] shift(long now) {
        int step = (int) ((now / SHIFT_EVERY_MS) % SHIFTS.length);
        return new int[] {Math.abs(SHIFTS[step][0]), Math.abs(SHIFTS[step][1])};
    }

    /** Frame delay for the overlay orb: a gentle 5 fps while idle over other apps, every frame when active. */
    public static long overlayFrameDelay(boolean quiet, float level) {
        return quiet && level <= .05f ? 200 : 0;
    }

    /** Margin from the screen edge, in dp, matching the launcher's orb placement. */
    public static final int EDGE_DP = 24;
}
