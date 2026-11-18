package app.nook.tv;

import org.junit.Test;
import static org.junit.Assert.*;
import static app.nook.tv.VoiceRules.*;

public class VoiceRulesTest {
    @Test public void constantsMatchAndroidSpeechRecognizer(){
        // Values from android.speech.SpeechRecognizer, which are public API and never renumbered.
        assertEquals(1,ERROR_NETWORK_TIMEOUT);assertEquals(2,ERROR_NETWORK);assertEquals(3,ERROR_AUDIO);assertEquals(4,ERROR_SERVER);
        assertEquals(5,ERROR_CLIENT);assertEquals(6,ERROR_SPEECH_TIMEOUT);assertEquals(7,ERROR_NO_MATCH);assertEquals(8,ERROR_RECOGNIZER_BUSY);
        assertEquals(9,ERROR_INSUFFICIENT_PERMISSIONS);assertEquals(11,ERROR_SERVER_DISCONNECTED);
    }
    @Test public void recogniserAndAudioFaultsHandOverToTheSystemVoiceScreen(){
        for(int error:new int[]{ERROR_AUDIO,ERROR_CLIENT,ERROR_SERVER,ERROR_RECOGNIZER_BUSY,ERROR_SERVER_DISCONNECTED,ERROR_INSUFFICIENT_PERMISSIONS})
            assertEquals(Next.SYSTEM_VOICE,afterError(error,true));
        assertEquals(Next.UNAVAILABLE,afterError(ERROR_NETWORK,false));
        assertEquals(Next.UNAVAILABLE,afterError(ERROR_NETWORK_TIMEOUT,true));
    }
    @Test public void silenceNeverOpensTheSystemScreenUnasked(){
        // Nobody spoke, or the mic picked nothing up: say so and let the user press OK again.
        assertEquals(Next.RETRY_MESSAGE,afterError(ERROR_SPEECH_TIMEOUT,false));
        assertEquals(Next.RETRY_MESSAGE,afterError(ERROR_NO_MATCH,false));
        assertEquals(Next.RETRY_MESSAGE,afterError(ERROR_SPEECH_TIMEOUT,true));
        assertEquals(Next.RETRY_MESSAGE,afterError(ERROR_NO_MATCH,true));
    }
    @Test public void faultedRecognisersAreRebuilt(){
        assertTrue(recreateRecognizer(ERROR_CLIENT));assertTrue(recreateRecognizer(ERROR_SERVER_DISCONNECTED));assertTrue(recreateRecognizer(ERROR_RECOGNIZER_BUSY));
        assertFalse(recreateRecognizer(ERROR_NO_MATCH));assertFalse(recreateRecognizer(ERROR_AUDIO));
    }
    @Test public void idleOrbDrawsAtAReducedFrameRate(){
        assertEquals(66,orbFrameDelay(true,false,0f));
        assertEquals(33,orbFrameDelay(true,true,0f));
        assertEquals(0,orbFrameDelay(false,false,0f));
        assertEquals(0,orbFrameDelay(true,false,.5f));
    }
}
