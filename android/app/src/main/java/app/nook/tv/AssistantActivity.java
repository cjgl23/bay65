package app.nook.tv;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

/**
 * The Everywhere Mode panel: a translucent BAY65 Assistant window that opens over the current app when the
 * user asks for it (the "Ask BAY65" notification, the "Ask BAY65" app entry, or "Hey Google, open Ask BAY65").
 *
 * Being a normal foreground activity, it gets D-pad focus, Back and the microphone legitimately and only
 * while it is open. Closing it returns focus to the app underneath. It reuses the launcher's Assistant
 * controller, provider settings, encrypted key and conversation, and fetches bus arrivals on demand only.
 */
public final class AssistantActivity extends Activity implements AssistantPresence.Listener {
    private static final int INK = 0xffffffff, MUTED = 0xffa1b8c7, PANEL = 0xf20c1825, ACCENT = 0xff53decf;
    /** How long the panel stays after an answer finishes before handing the screen back. */
    private static final long CLOSE_AFTER_MS = 12_000L;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final LtaClient lta = new LtaClient();
    private final Map<String, JSONObject> arrivals = new HashMap<>();
    private final Map<String, Long> fetched = new HashMap<>();
    private AgentSettings settings;
    private NookStore store;
    private Assistant assistant;
    private List<String> favourites = new ArrayList<>(), games = new ArrayList<>();
    private boolean ltaConnected;

    private final Runnable closeIfIdle = () -> {
        if (("IDLE".equals(AssistantPresence.state) || "ERROR".equals(AssistantPresence.state)) && !isFinishing()) finish();
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        settings = new AgentSettings(this);
        store = new NookStore(this);
        build();
        prefetch();
        if (state == null) main.post(() -> { if (settings.ready()) assistant.activate(); });
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private TextView text(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        return view;
    }

    private void build() {
        FrameLayout root = new FrameLayout(this);
        Everywhere.Corner corner = Everywhere.corner(getSharedPreferences("nook", MODE_PRIVATE).getString(Everywhere.CORNER, null));
        LinearLayout dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(corner.bottom ? Gravity.BOTTOM : Gravity.TOP);

        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setPadding(dp(14), dp(11), dp(14), dp(12));
        GradientDrawable surface = new GradientDrawable();
        surface.setColor(PANEL); surface.setCornerRadius(dp(14));
        bubble.setBackground(surface);
        bubble.setVisibility(View.GONE);
        TextView status = text("", 10, ACCENT), question = text("", 12, MUTED), answer = text("", 14, INK);
        question.setPadding(0, dp(6), 0, dp(4)); question.setMaxLines(2); question.setEllipsize(TextUtils.TruncateAt.END);
        answer.setLineSpacing(dp(2), 1); answer.setMaxLines(8); answer.setEllipsize(TextUtils.TruncateAt.END);
        answer.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        bubble.addView(status); bubble.addView(question); bubble.addView(answer);

        OrbView orb = new OrbView(this);
        orb.setFocusable(true); orb.setClickable(true); orb.setLongClickable(true);
        int size = dp(80);
        LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(dp(380), -2);
        if (corner.right) { bubbleParams.rightMargin = dp(10); dock.addView(bubble, bubbleParams); dock.addView(orb, new LinearLayout.LayoutParams(size, size)); }
        else { bubbleParams.leftMargin = dp(10); dock.addView(orb, new LinearLayout.LayoutParams(size, size)); dock.addView(bubble, bubbleParams); }

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-2, -2,
                (corner.bottom ? Gravity.BOTTOM : Gravity.TOP) | (corner.right ? Gravity.END : Gravity.START));
        params.setMargins(dp(Everywhere.EDGE_DP), dp(Everywhere.EDGE_DP), dp(Everywhere.EDGE_DP), dp(Everywhere.EDGE_DP));
        root.addView(dock, params);
        setContentView(root);

        assistant = new Assistant(this, new Assistant.Host() {
            @Override public boolean configured() { return settings.ready(); }
            @Override public Agent.Call call(List<Agent.Turn> history, String question, boolean allowSearch) {
                return settings.call(history, question, allowSearch, context(), settings.key());
            }
            @Override public void openSettings() {
                answer.setText("Set up the BAY65 Assistant in BAY65 Settings first. Press Back to return.");
                bubble.setVisibility(View.VISIBLE);
            }
        }, orb, bubble, status, question, answer);
        orb.requestFocus();
        if (!settings.ready()) { answer.setText("Set up the BAY65 Assistant in BAY65 Settings first. Press Back to return."); bubble.setVisibility(View.VISIBLE); }
    }

    /**
     * On demand only: when the panel opens, fetch arrivals for the saved stops (if sharing is on and LTA is
     * connected) so a bus question has fresh data. Nothing is polled while the panel is closed.
     */
    private void prefetch() {
        final boolean share = settings.share();
        final SecretStore ltaSecrets = new SecretStore(this);
        final List<NookStore.Stop> stops = new ArrayList<>(store.stops);
        io.execute(() -> {
            List<String> favs = AppRepository.labels(this, store, "favourites"), play = AppRepository.labels(this, store, "playtime");
            String key = share ? ltaSecrets.read() : null;
            Map<String, JSONObject> found = new HashMap<>();
            Map<String, Long> when = new HashMap<>();
            for (NookStore.Stop stop : stops) {
                if (key == null) break;
                try { found.put(stop.code, lta.arrivals(stop.code, key)); when.put(stop.code, System.currentTimeMillis()); }
                catch (LtaClient.Failure unavailable) { /* reported to the assistant as "no current arrival data" */ }
            }
            final boolean connected = key != null;
            main.post(() -> {
                if (isDestroyed()) return;
                favourites = favs; games = play; ltaConnected = connected;
                arrivals.putAll(found); fetched.putAll(when);
            });
        });
    }

    private String context() {
        List<AssistantContext.Stop> stops = new ArrayList<>();
        for (NookStore.Stop stop : store.stops) {
            Long when = fetched.get(stop.code);
            stops.add(new AssistantContext.Stop(stop.name, stop.code, stop.services, arrivals.get(stop.code), when == null ? 0 : when));
        }
        return AssistantContext.build(settings.share(), ltaConnected, stops, favourites, games,
                NowPlaying.current(this, settings.shareNowPlaying()), System.currentTimeMillis());
    }

    /** Opening "Ask BAY65" again while the panel exists starts a new question in the same panel. */
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (settings.ready()) assistant.activate();
    }

    @Override protected void onStart() {
        super.onStart();
        AssistantPresence.setPanelVisible(this, true);
        AssistantPresence.listen(this);
    }

    /** Close a while after the conversation goes quiet, so the panel never lingers over a film. */
    @Override public void onPresenceChanged() { scheduleClose(); }

    @Override public void onUserInteraction() {
        super.onUserInteraction();
        scheduleClose();
    }

    private void scheduleClose() {
        main.removeCallbacks(closeIfIdle);
        if ("IDLE".equals(AssistantPresence.state) || "ERROR".equals(AssistantPresence.state)) main.postDelayed(closeIfIdle, CLOSE_AFTER_MS);
    }

    @Override public void onBackPressed() { finish(); }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == Assistant.PERMISSION_REQUEST) assistant.onPermission(results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == Assistant.VOICE_REQUEST) assistant.onVoiceResult(result, data);
    }

    /**
     * Leaving the panel (Back, Home, another app) stops listening, thinking and speaking at once. The
     * system voice screen also stops the panel briefly; its result still arrives and starts a new question.
     */
    @Override protected void onStop() {
        AssistantPresence.unlisten(this);
        main.removeCallbacks(closeIfIdle);
        AssistantPresence.setPanelVisible(this, false);
        assistant.stop();
        super.onStop();
    }

    @Override protected void onDestroy() {
        AssistantPresence.setPanelVisible(this, false);
        main.removeCallbacksAndMessages(null);
        assistant.release();
        io.shutdownNow();
        super.onDestroy();
    }
}
