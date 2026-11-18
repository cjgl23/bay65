package app.nook.tv;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Voice loop for the floating orb: listen with the TV's speech recogniser, ask the chosen
 * free provider, then answer on screen and aloud with the TV's built-in text-to-speech.
 * Speech recognition and synthesis use Android's own services, so they need no API key.
 */
final class Assistant {
    static final int PERMISSION_REQUEST = 43, VOICE_REQUEST = 44;

    /** What the activity supplies: setup state, and each question's request with BAY65 context. */
    interface Host {
        boolean configured();
        Agent.Call call(List<Agent.Turn> history, String question, boolean allowSearch);
        void openSettings();
    }

    private final Activity activity;
    private final Host host;
    /** Its own thread, so a slow or abandoned answer never delays bus arrivals or photos. */
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    /** Remembers when this TV's in-app recogniser could not hear, so later presses go straight to the system voice screen. */
    private static final String SYSTEM_VOICE = "agent_system_voice";
    private final SharedPreferences preferences;
    private boolean heardSound;
    private int silentStreak;
    private final OrbView orb;
    private final LinearLayout bubble;
    private final TextView status, question, answer;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AgentClient client = new AgentClient();
    private final List<Agent.Turn> history = new ArrayList<>();
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private boolean ttsReady;
    private String state = "IDLE";
    private int generation;

    Assistant(Activity activity, Host host, OrbView orb, LinearLayout bubble, TextView status, TextView question, TextView answer) {
        this.activity = activity; this.host = host; this.orb = orb;
        this.preferences = activity.getSharedPreferences("nook", Context.MODE_PRIVATE);
        this.bubble = bubble; this.status = status; this.question = question; this.answer = answer;
        orb.setOnClickListener(v -> activate());
        orb.setOnLongClickListener(v -> { typeQuestion(); return true; });
        render();
    }

    boolean bubbleShown() { return bubble.getVisibility() == View.VISIBLE; }

    /** OK on the orb, or the remote's search key: talk, or interrupt what is in progress. */
    void activate() {
        if (!host.configured()) { host.openSettings(); return; }
        if (!state.equals("IDLE") && !state.equals("ERROR")) { stop(); return; }
        listen();
    }

    void dismiss() { stop(); bubble.setVisibility(View.GONE); }

    /** Forget the conversation, so earlier questions never go to a new provider or after Turn off. */
    void reset() { dismiss(); history.clear(); preferences.edit().remove(SYSTEM_VOICE).apply(); }

    void stop() {
        generation++;
        client.cancel();
        if (recognizer != null) recognizer.cancel();
        if (tts != null) tts.stop();
        orb.setLevel(0);
        setState("IDLE");
    }

    void release() {
        stop();
        io.shutdownNow();
        if (recognizer != null) { recognizer.destroy(); recognizer = null; }
        if (tts != null) { tts.shutdown(); tts = null; }
    }

    private void listen() {
        if (preferences.getBoolean(SYSTEM_VOICE, false)) { listenWithSystem(); return; }
        if (SpeechRecognizer.isRecognitionAvailable(activity)) {
            if (activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { listenInApp(); return; }
            activity.requestPermissions(new String[] {Manifest.permission.RECORD_AUDIO}, PERMISSION_REQUEST);
            return;
        }
        listenWithSystem();
    }

    void onPermission(boolean granted) {
        if (granted) listenInApp(); else listenWithSystem();
    }

    /** The system voice screen needs no microphone permission for BAY65; typing is the last resort. */
    private void listenWithSystem() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask BAY65");
        try { activity.startActivityForResult(intent, VOICE_REQUEST); }
        catch (ActivityNotFoundException | SecurityException unavailable) { preferences.edit().remove(SYSTEM_VOICE).apply(); typeQuestion(); }
    }

    void onVoiceResult(int result, Intent data) {
        ArrayList<String> heard = result == Activity.RESULT_OK && data != null ? data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS) : null;
        if (heard != null && !heard.isEmpty() && !heard.get(0).trim().isEmpty()) ask(heard.get(0).trim());
    }

    private void listenInApp() {
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(activity);
            recognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) {}
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float db) { if (db > VoiceRules.HEARD_DB) heardSound = true; orb.setLevel((db + 2f) / 12f); }
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onEndOfSpeech() { orb.setLevel(0); }
                @Override public void onError(int error) {
                    if (!state.equals("LISTENING")) return;
                    // A recogniser that reported a client or server fault is rebuilt for the next press.
                    if (VoiceRules.recreateRecognizer(error)) main.post(Assistant.this::dropRecognizer);
                    switch (VoiceRules.afterError(error, heardSound)) {
                        case SYSTEM_VOICE:
                            silentStreak = heardSound ? 0 : silentStreak + 1;
                            if (VoiceRules.rememberSystemVoice(error, heardSound, silentStreak)) preferences.edit().putBoolean(SYSTEM_VOICE, true).apply();
                            setState("IDLE"); listenWithSystem(); break;
                        case RETRY_MESSAGE: silentStreak = 0; setState("IDLE"); show("", "I didn't catch that. Press OK on the orb to try again."); break;
                        default: fail("Voice input is unavailable. Hold OK on the orb to type instead.");
                    }
                }
                @Override public void onResults(Bundle results) {
                    silentStreak = 0;
                    ArrayList<String> heard = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (heard != null && !heard.isEmpty() && !heard.get(0).trim().isEmpty()) ask(heard.get(0).trim());
                    else { setState("IDLE"); show("", "I didn't catch that. Press OK on the orb to try again."); }
                }
                @Override public void onPartialResults(Bundle partial) {
                    ArrayList<String> heard = partial.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (heard != null && !heard.isEmpty()) question.setText(heard.get(0));
                }
                @Override public void onEvent(int type, Bundle params) {}
            });
        }
        generation++;
        heardSound = false;
        if (tts != null) tts.stop();
        show("", "Listening… ask about your buses, or anything else.");
        setState("LISTENING");
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        try { recognizer.startListening(intent); }
        catch (RuntimeException unavailable) { setState("IDLE"); dropRecognizer(); listenWithSystem(); }
    }

    private void dropRecognizer() {
        if (recognizer == null) return;
        try { recognizer.destroy(); } catch (RuntimeException ignored) {}
        recognizer = null;
    }

    void typeQuestion() {
        if (!host.configured()) { host.openSettings(); return; }
        stop();
        EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setHint("When is my next bus?");
        input.setSingleLine(true);
        new AlertDialog.Builder(activity).setTitle("Ask BAY65").setView(input).setNegativeButton("Cancel", null)
                .setPositiveButton("Ask", (dialog, which) -> {
                    String text = input.getText().toString().trim();
                    if (!text.isEmpty()) ask(text.length() > 500 ? text.substring(0, 500) : text);
                }).show();
    }

    private void ask(String text) {
        // A late voice result can arrive after the assistant was turned off; ignore it.
        if (!host.configured()) { stop(); return; }
        final int ask = ++generation;
        final Agent.Call call, plain;
        try {
            List<Agent.Turn> past = new ArrayList<>(history);
            call = host.call(past, text, true);
            plain = call.searched ? host.call(past, text, false) : null;
        } catch (RuntimeException invalid) {
            fail(invalid instanceof IllegalArgumentException ? invalid.getMessage() : "The assistant settings could not be read. Open Assistant settings and save again.");
            return;
        }
        show(text, "…");
        setState("THINKING");
        io.execute(() -> {
            Agent.Reply reply = null;
            String error = null;
            boolean unsearched = false;
            try { reply = client.ask(call); }
            catch (AgentClient.Failure failure) {
                error = failure.getMessage();
                if (plain != null && Agent.retryWithoutSearch(failure.status, failure.body)) {
                    try { reply = client.ask(plain); unsearched = true; }
                    catch (AgentClient.Failure again) { error = again.getMessage(); }
                    catch (RuntimeException unexpected) { error = "The assistant hit an unexpected problem. Try again."; }
                }
            }
            catch (RuntimeException unexpected) { error = "The assistant hit an unexpected problem. Try again."; }
            final Agent.Reply result = reply;
            final String problem = error;
            final boolean withoutSearch = unsearched;
            main.post(() -> {
                if (ask != generation || activity.isDestroyed()) return;
                if (result == null) { fail(problem); return; }
                history.add(new Agent.Turn("user", text));
                history.add(new Agent.Turn("assistant", result.text));
                while (history.size() > 12) history.remove(0);
                // Sources and notes are shown on screen only; the voice reads just the answer.
                String note = withoutSearch ? "\n\nWeb search was unavailable, so this answer may be out of date."
                        : result.sources.isEmpty() ? "" : "\n\nSources: " + String.join(" · ", result.sources);
                show(text, result.text + note);
                speak(result.text, ask);
            });
        });
    }

    private void speak(String text, int ask) {
        if (tts == null) {
            tts = new TextToSpeech(activity, code -> main.post(() -> {
                ttsReady = code == TextToSpeech.SUCCESS && tts != null;
                // A failed start is retried on the next answer instead of staying silent until restart.
                if (!ttsReady && tts != null) { tts.shutdown(); tts = null; setState("IDLE"); return; }
                if (ttsReady) {
                    Locale singapore = Locale.forLanguageTag("en-SG");
                    if (tts.isLanguageAvailable(singapore) >= TextToSpeech.LANG_AVAILABLE) tts.setLanguage(singapore);
                    else tts.setLanguage(Locale.UK);
                    tts.setOnUtteranceProgressListener(progress);
                }
                if (ask == generation) speak(text, ask);
            }));
            return;
        }
        if (!ttsReady) { setState("IDLE"); return; }
        setState("SPEAKING");
        if (tts.speak(Agent.spokenForm(text), TextToSpeech.QUEUE_FLUSH, null, "bay65-" + ask) != TextToSpeech.SUCCESS) setState("IDLE");
    }

    private final UtteranceProgressListener progress = new UtteranceProgressListener() {
        @Override public void onStart(String id) {}
        @Override public void onDone(String id) { main.post(() -> finished(id)); }
        @Override public void onError(String id) { main.post(() -> finished(id)); }
        @Override public void onStop(String id, boolean interrupted) { main.post(() -> finished(id)); }
        /** Each spoken word pulses the orb, standing in for Rupert's playback level meter. */
        @Override public void onRangeStart(String id, int start, int end, int frame) {
            main.post(() -> { if (state.equals("SPEAKING")) { orb.setLevel(.55f + Math.min(.4f, (end - start) * .04f)); main.postDelayed(() -> orb.setLevel(.15f), 140); } });
        }
    };

    private void finished(String id) {
        if (!("bay65-" + generation).equals(id) || !state.equals("SPEAKING")) return;
        orb.setLevel(0);
        setState("IDLE");
    }

    private void fail(String message) {
        orb.setLevel(0);
        answer.setText(message);
        bubble.setVisibility(View.VISIBLE);
        setState("ERROR");
    }

    private void show(String asked, String reply) {
        question.setText(asked);
        question.setVisibility(asked.isEmpty() ? View.GONE : View.VISIBLE);
        answer.setText(reply);
        bubble.setVisibility(View.VISIBLE);
    }

    private void setState(String next) {
        state = next;
        render();
    }

    private void render() {
        switch (state) {
            case "LISTENING": orb.setMode(OrbView.Mode.MIC); break;
            case "THINKING": orb.setMode(OrbView.Mode.SHIMMER); break;
            case "SPEAKING": orb.setMode(OrbView.Mode.SPEECH); break;
            default: orb.setMode(OrbView.Mode.QUIET);
        }
        status.setText(Agent.stateLabel(state) + (state.equals("IDLE") || state.equals("ERROR") ? " · OK to talk · Hold OK to type" : " · OK to stop"));
        orb.setContentDescription(host.configured()
                ? "BAY65 Assistant. " + Agent.stateLabel(state) + ". Press OK to " + (state.equals("IDLE") || state.equals("ERROR") ? "talk, or hold OK to type." : "stop.")
                : "BAY65 Assistant. Not set up. Press OK to set it up.");
    }

    void refresh() { render(); }
}
