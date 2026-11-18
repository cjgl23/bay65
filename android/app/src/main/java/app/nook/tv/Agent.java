package app.nook.tv;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;

/**
 * Provider presets, validation and request/response handling for the BAY65 Assistant.
 * Gemini uses its native API so answers can be grounded with Google Search; every other
 * provider uses the OpenAI-compatible chat format. Pure Java (plus org.json) for JVM tests.
 */
public final class Agent {
    private Agent() {}

    /** Free-to-use providers. Model names are editable because free catalogues change often. */
    public enum Provider {
        GEMINI("gemini", "Google Gemini · free tier", "https://generativelanguage.googleapis.com/v1beta",
                "gemini-3.5-flash-lite", true, "aistudio.google.com/apikey"),
        GROQ("groq", "Groq · free tier", "https://api.groq.com/openai/v1",
                "llama-3.3-70b-versatile", true, "console.groq.com/keys"),
        OPENROUTER("openrouter", "OpenRouter · free models", "https://openrouter.ai/api/v1",
                "meta-llama/llama-3.3-70b-instruct:free", true, "openrouter.ai/keys"),
        CUSTOM("custom", "Ollama or another OpenAI-compatible server", null,
                "llama3.2", false, null);

        public final String id, label, baseUrl, defaultModel, keyPage;
        public final boolean keyRequired;

        Provider(String id, String label, String baseUrl, String defaultModel, boolean keyRequired, String keyPage) {
            this.id = id; this.label = label; this.baseUrl = baseUrl; this.defaultModel = defaultModel;
            this.keyRequired = keyRequired; this.keyPage = keyPage;
        }
    }

    public static Provider provider(String id) {
        for (Provider p : Provider.values()) if (p.id.equals(id)) return p;
        return Provider.GEMINI;
    }

    /** Only Gemini can search the web for current answers (Grounding with Google Search). */
    public static boolean canSearch(Provider provider) { return provider == Provider.GEMINI; }

    /** The request URL. Gemini's model is part of the path; custom servers must use HTTPS. */
    public static String endpoint(Provider provider, String customBase, String model) {
        if (provider == Provider.GEMINI) {
            String id = model(model).replaceFirst("^models/", "");
            if (!id.matches("[A-Za-z0-9._-]{1,80}")) throw new IllegalArgumentException("Enter a Gemini model name, for example gemini-3.5-flash-lite.");
            return provider.baseUrl + "/models/" + id + ":generateContent";
        }
        String base = provider.baseUrl != null ? provider.baseUrl : customBase == null ? "" : customBase.trim();
        URI uri;
        try { uri = new URI(base); } catch (URISyntaxException e) { throw new IllegalArgumentException("Enter a valid server address."); }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getHost().isEmpty())
            throw new IllegalArgumentException("Enter an https:// server address, for example https://ollama.example.ts.net/v1");
        if (uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null)
            throw new IllegalArgumentException("Remove any user name, query or fragment from the server address.");
        if (base.length() > 300) throw new IllegalArgumentException("The server address is too long.");
        return base.replaceAll("/+$", "") + "/chat/completions";
    }

    public static String model(String value) {
        String model = value == null ? "" : value.trim();
        if (!model.matches("[A-Za-z0-9._:/@+-]{1,120}"))
            throw new IllegalArgumentException("Enter a model name, for example gemini-3.5-flash-lite.");
        return model;
    }

    /** Same printable-ASCII rule as the LTA key. Blank is allowed only when the provider needs no key. */
    public static boolean validKey(String key, boolean required) {
        if (key == null || key.isEmpty()) return !required;
        return key.length() >= 8 && key.length() <= 512 && key.matches("[\\x21-\\x7e]+");
    }

    public static final class Turn {
        public final String role, text;
        public Turn(String role, String text) { this.role = role; this.text = text; }
    }

    /** A finished request: where it goes, how the key is sent, and the JSON body. */
    public static final class Call {
        public final String endpoint, key, body;
        public final boolean gemini;
        Call(String endpoint, String key, String body, boolean gemini) { this.endpoint = endpoint; this.key = key; this.body = body; this.gemini = gemini; }
    }

    /** An answer plus, for searched answers, the web sources shown under it (never spoken). */
    public static final class Reply {
        public final String text;
        public final List<String> sources;
        Reply(String text, List<String> sources) { this.text = text; this.sources = sources; }
    }

    public static Call call(Provider provider, String customBase, String model, String key, String system, List<Turn> history, String question, boolean search) {
        String name = model(model);
        String body = provider == Provider.GEMINI ? geminiBody(system, history, question, search) : requestBody(name, system, history, question);
        return new Call(endpoint(provider, customBase, name), key, body, provider == Provider.GEMINI);
    }

    public static String systemPrompt(String now, String context) { return systemPrompt(now, context, false); }

    public static String systemPrompt(String now, String context, boolean search) {
        return "You are the BAY65 Assistant, a friendly voice assistant on a Singapore family's Android TV home screen. "
                + "Answer in at most three short sentences that sound natural when read aloud. "
                + "Use plain text only: no markdown, lists, emoji or links. "
                + "The family's favourite apps are listed below when shared. Questions about what to watch, play or listen to, "
                + "new releases, charts, trending titles, artists, genres or recommendations relate to those apps: name the app it is on when you can. "
                + (search
                    ? "Use Google Search for anything current, such as new releases, top 10 lists, trending songs or shows, news, sports, weather and prices. "
                        + "Assume Singapore unless the user names another country, genre or language (for example OPM means Original Pilipino Music). "
                        + "Say briefly when you could not confirm something. "
                    : "You cannot search the web, so say when an answer may be out of date rather than guessing about current charts or releases. ")
                + "For bus questions, use only the live arrival data below and say when it is missing or outdated; never invent arrival times. "
                + "Capacity: Seats means seats available, Standing means standing room, Limited means limited standing. "
                + "A ~ before a time means a scheduled rather than live estimate. "
                + "You cannot open apps or change settings; tell the user where to do it instead.\n"
                + "Current time in Singapore: " + now + "\n"
                + (context == null || context.isEmpty() ? "No BAY65 data is shared with you." : context);
    }

    public static String requestBody(String model, String system, List<Turn> history, String question) {
        try {
            JSONArray messages = new JSONArray().put(new JSONObject().put("role", "system").put("content", system));
            for (Turn turn : history) messages.put(new JSONObject().put("role", turn.role).put("content", turn.text));
            messages.put(new JSONObject().put("role", "user").put("content", question));
            return new JSONObject().put("model", model).put("messages", messages)
                    .put("max_tokens", 400).put("temperature", 0.4).put("stream", false).toString();
        } catch (JSONException impossible) { throw new IllegalStateException(impossible); }
    }

    /** Gemini's native request. History roles map to user and model; search adds the google_search tool. */
    public static String geminiBody(String system, List<Turn> history, String question, boolean search) {
        try {
            JSONArray contents = new JSONArray();
            for (Turn turn : history) contents.put(geminiTurn(turn.role.equals("assistant") ? "model" : "user", turn.text));
            contents.put(geminiTurn("user", question));
            JSONObject body = new JSONObject()
                    .put("systemInstruction", new JSONObject().put("parts", new JSONArray().put(new JSONObject().put("text", system))))
                    .put("contents", contents)
                    .put("generationConfig", new JSONObject().put("maxOutputTokens", 400).put("temperature", 0.4));
            if (search) body.put("tools", new JSONArray().put(new JSONObject().put("google_search", new JSONObject())));
            return body.toString();
        } catch (JSONException impossible) { throw new IllegalStateException(impossible); }
    }

    private static JSONObject geminiTurn(String role, String text) throws JSONException {
        return new JSONObject().put("role", role).put("parts", new JSONArray().put(new JSONObject().put("text", text)));
    }

    /** Text parts of the first candidate (skipping thoughts) and up to three distinct web source titles. */
    public static Reply geminiAnswer(String body) {
        try {
            JSONObject candidate = new JSONObject(body).getJSONArray("candidates").getJSONObject(0);
            JSONObject content = candidate.optJSONObject("content");
            JSONArray parts = content == null ? null : content.optJSONArray("parts");
            StringBuilder text = new StringBuilder();
            for (int i = 0; parts != null && i < parts.length(); i++) {
                JSONObject part = parts.optJSONObject(i);
                if (part != null && !part.optBoolean("thought")) text.append(part.optString("text", ""));
            }
            String answer = text.toString().trim();
            if (answer.isEmpty()) {
                String reason = candidate.optString("finishReason", "");
                throw new IllegalArgumentException(reason.equals("SAFETY") || reason.equals("PROHIBITED_CONTENT")
                        ? "Gemini declined to answer that question." : "The assistant returned an empty answer. Try again.");
            }
            java.util.LinkedHashSet<String> sources = new java.util.LinkedHashSet<>();
            JSONObject grounding = candidate.optJSONObject("groundingMetadata");
            JSONArray chunks = grounding == null ? null : grounding.optJSONArray("groundingChunks");
            for (int i = 0; chunks != null && i < chunks.length() && sources.size() < 3; i++) {
                JSONObject web = chunks.optJSONObject(i) == null ? null : chunks.optJSONObject(i).optJSONObject("web");
                String title = web == null ? "" : web.optString("title", "").replaceAll("[\\p{Cntrl}]+", " ").trim();
                if (!title.isEmpty()) sources.add(title.length() > 60 ? title.substring(0, 60) : title);
            }
            return new Reply(answer.length() > 4000 ? answer.substring(0, 4000) : answer, new java.util.ArrayList<>(sources));
        } catch (JSONException e) {
            throw new IllegalArgumentException("The assistant returned an unexpected response. Try again shortly.");
        }
    }

    public static Reply reply(String body, boolean gemini) {
        return gemini ? geminiAnswer(body) : new Reply(answer(body), java.util.Collections.<String>emptyList());
    }

    /** The first choice's text, or an IllegalArgumentException when the reply has none. */
    public static String answer(String body) {
        try {
            JSONObject choice = new JSONObject(body).getJSONArray("choices").getJSONObject(0);
            Object content = choice.getJSONObject("message").opt("content");
            String text = content instanceof String ? ((String) content).trim() : "";
            if (text.isEmpty()) throw new IllegalArgumentException("The assistant returned an empty answer. Try again.");
            return text.length() > 4000 ? text.substring(0, 4000) : text;
        } catch (JSONException e) {
            throw new IllegalArgumentException("The assistant returned an unexpected response. Try again shortly.");
        }
    }

    public static String failure(int status) { return failure(status, null); }

    /**
     * Maps a provider error to an actionable message. Gemini reports a bad key as HTTP 400,
     * so the provider's own message decides between key and model problems before the status does.
     */
    public static String failure(int status, String body) {
        String reason = providerMessage(body), lower = reason.toLowerCase(Locale.ROOT);
        if (status == 401 || status == 403 || lower.contains("api key") || lower.contains("api_key"))
            return "The provider rejected the API key. Check it in Assistant settings." + detail(reason);
        if (status == 429) return "The free-tier limit was reached. Wait a minute and try again." + detail(reason);
        if (status == 404 || lower.contains("model"))
            return "The provider rejected the model. Check the model name in Assistant settings." + detail(reason);
        if (status == 400) return "The provider rejected the request." + detail(reason);
        return "The assistant is temporarily unavailable. Try again shortly.";
    }

    /** error.message from an OpenAI-style body, including Gemini's array-wrapped form; "" if absent. */
    static String providerMessage(String body) {
        if (body == null || body.trim().isEmpty()) return "";
        try {
            String text = body.trim();
            JSONObject root = text.startsWith("[") ? new JSONArray(text).getJSONObject(0) : new JSONObject(text);
            Object error = root.opt("error");
            String message = error instanceof JSONObject ? ((JSONObject) error).optString("message", "") : error instanceof String ? (String) error : "";
            message = message.replaceAll("[\\p{Cntrl}]+", " ").replaceAll("\\s+", " ").trim();
            return message.length() > 160 ? message.substring(0, 157) + "…" : message;
        } catch (JSONException e) { return ""; }
    }

    private static String detail(String reason) { return reason.isEmpty() ? "" : " (" + reason + ")"; }

    /** Strips formatting a TV voice should not read out, as Rupert does before speech. */
    public static String spokenForm(String text) {
        String spoken = text
                .replaceAll("```[\\s\\S]*?```", "")
                .replaceAll("\\[([^\\]]+)\\]\\([^)]*\\)", "$1")
                .replaceAll("https?://\\S+", "")
                .replaceAll("`[^`]*`", "")
                .replaceAll("[#*_>|]", "")
                .replaceAll("\\s+", " ")
                .trim();
        return spoken.length() > 1800 ? spoken.substring(0, 1800) : spoken;
    }

    /** Short status words shown beside the orb, mirroring Rupert's voice labels. */
    public static String stateLabel(String state) {
        switch (state == null ? "" : state.toUpperCase(Locale.ROOT)) {
            case "LISTENING": return "Listening";
            case "THINKING": return "Thinking";
            case "SPEAKING": return "Speaking";
            case "ERROR": return "Needs attention";
            default: return "Ready";
        }
    }
}
