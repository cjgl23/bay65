package app.nook.tv;

/**
 * Decisions for the assistant's voice loop, kept free of Android classes so they run in JVM tests.
 * Error codes are android.speech.SpeechRecognizer's values (stable public constants).
 */
public final class VoiceRules {
    private VoiceRules() {}

    public static final int ERROR_NETWORK_TIMEOUT = 1, ERROR_NETWORK = 2, ERROR_AUDIO = 3, ERROR_SERVER = 4,
            ERROR_CLIENT = 5, ERROR_SPEECH_TIMEOUT = 6, ERROR_NO_MATCH = 7, ERROR_RECOGNIZER_BUSY = 8,
            ERROR_INSUFFICIENT_PERMISSIONS = 9, ERROR_SERVER_DISCONNECTED = 11;

    /** Above this onRmsChanged level (dB) the microphone was clearly receiving sound. */
    public static final float HEARD_DB = 2f;

    public enum Next { SYSTEM_VOICE, RETRY_MESSAGE, UNAVAILABLE }

    /**
     * What to do after an in-app recognition error. Errors that mean the recogniser or its audio never
     * worked (common when a TV's only microphone is in the remote) hand over to the system voice screen,
     * which can open the remote's microphone itself. A timeout with no sound at all means the same.
     */
    public static Next afterError(int error, boolean heardSound) {
        switch (error) {
            case ERROR_AUDIO: case ERROR_CLIENT: case ERROR_SERVER: case ERROR_RECOGNIZER_BUSY:
            case ERROR_SERVER_DISCONNECTED: case ERROR_INSUFFICIENT_PERMISSIONS:
                return Next.SYSTEM_VOICE;
            case ERROR_SPEECH_TIMEOUT: case ERROR_NO_MATCH:
                return heardSound ? Next.RETRY_MESSAGE : Next.SYSTEM_VOICE;
            default:
                return Next.UNAVAILABLE;
        }
    }

    /**
     * Whether to go straight to the system voice screen on later presses: only when the microphone
     * itself seems unusable in-app, not for transient faults such as a busy recogniser. A silent press
     * may just mean nobody spoke, so it takes two silent presses in a row (silentStreak counts this one).
     */
    public static boolean rememberSystemVoice(int error, boolean heardSound, int silentStreak) {
        if (error == ERROR_AUDIO || error == ERROR_INSUFFICIENT_PERMISSIONS) return true;
        return (error == ERROR_SPEECH_TIMEOUT || error == ERROR_NO_MATCH) && !heardSound && silentStreak >= 2;
    }

    /** Errors after which a cached SpeechRecognizer should be destroyed and created afresh next time. */
    public static boolean recreateRecognizer(int error) {
        return error == ERROR_CLIENT || error == ERROR_SERVER || error == ERROR_RECOGNIZER_BUSY || error == ERROR_SERVER_DISCONNECTED;
    }

    /**
     * Milliseconds until the orb's next frame: every frame while it is active or focused-and-moving,
     * but a calm 15 frames a second while it idles in the corner, to spare low-end TV GPUs.
     */
    public static long orbFrameDelay(boolean quiet, boolean focused, float level) {
        if (!quiet || level > .05f) return 0;
        return focused ? 33 : 66;
    }
}
