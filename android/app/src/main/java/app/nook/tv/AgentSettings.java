package app.nook.tv;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * The saved assistant configuration (provider, model, server, web search, sharing) and its encrypted key,
 * read the same way by the launcher and the Everywhere panel so there is one source of truth.
 */
final class AgentSettings {
    private final SharedPreferences preferences;
    private final SecretStore secrets;

    AgentSettings(Context context) {
        preferences = context.getSharedPreferences("nook", Context.MODE_PRIVATE);
        secrets = SecretStore.assistant(context);
    }

    Agent.Provider provider() {
        String id = preferences.getString("agent_provider", null);
        return id == null ? null : Agent.provider(id);
    }

    /** Decrypts the key on each call; nothing is cached outside Android Keystore-protected storage. */
    String key() { return secrets.read(); }

    boolean ready() {
        Agent.Provider provider = provider();
        return provider != null && (!provider.keyRequired || key() != null);
    }

    boolean search(Agent.Provider provider) {
        return Agent.canSearch(provider) && preferences.getBoolean("agent_search", true);
    }

    boolean share() { return preferences.getBoolean("agent_share", true); }

    boolean shareNowPlaying() { return preferences.getBoolean(NowPlaying.SHARE, false); }

    /** The request for one question. The context string is built by the caller from what it can see. */
    Agent.Call call(List<Agent.Turn> history, String question, boolean allowSearch, String context, String key) {
        Agent.Provider provider = provider();
        if (provider == null) throw new IllegalArgumentException("Set up the BAY65 Assistant in Settings first.");
        boolean search = allowSearch && search(provider);
        String system = Agent.systemPrompt(now(), context, search);
        return Agent.call(provider, preferences.getString("agent_base", ""),
                preferences.getString("agent_model", provider.defaultModel), key, system, history, question, search);
    }

    static String now() {
        SimpleDateFormat format = new SimpleDateFormat("EEEE d MMMM yyyy, h:mm a", Locale.UK);
        format.setTimeZone(TimeZone.getTimeZone("Asia/Singapore"));
        return format.format(new Date());
    }
}
