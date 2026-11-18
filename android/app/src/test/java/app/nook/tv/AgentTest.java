package app.nook.tv;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class AgentTest {
    @Test public void everyFreePresetUsesHttpsChatCompletions(){
        assertEquals("https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",Agent.endpoint(Agent.Provider.GEMINI,null));
        assertEquals("https://api.groq.com/openai/v1/chat/completions",Agent.endpoint(Agent.Provider.GROQ,"ignored"));
        assertEquals("https://openrouter.ai/api/v1/chat/completions",Agent.endpoint(Agent.Provider.OPENROUTER,""));
        assertTrue(Agent.Provider.GEMINI.keyRequired);
        assertFalse(Agent.Provider.CUSTOM.keyRequired);
        assertEquals(Agent.Provider.GEMINI,Agent.provider("unknown"));
        assertEquals(Agent.Provider.GROQ,Agent.provider("groq"));
    }
    @Test public void customServersMustBeHttpsWithoutCredentialsInTheUrl(){
        assertEquals("https://ollama.home.ts.net/v1/chat/completions",Agent.endpoint(Agent.Provider.CUSTOM," https://ollama.home.ts.net/v1/ "));
        for(String bad:new String[]{"","http://192.168.1.5:11434/v1","ftp://x/v1","https://user:pw@host/v1","https://host/v1?x=1","https:///v1","not a url"}){
            try{Agent.endpoint(Agent.Provider.CUSTOM,bad);fail(bad);}catch(IllegalArgumentException expected){}
        }
    }
    @Test public void modelsAndKeysAreValidated(){
        assertEquals("meta-llama/llama-3.3-70b-instruct:free",Agent.model(" meta-llama/llama-3.3-70b-instruct:free "));
        for(String bad:new String[]{"","has space","x\"y"}){try{Agent.model(bad);fail(bad);}catch(IllegalArgumentException expected){}}
        assertTrue(Agent.validKey("AIzaSyExample123",true));
        assertFalse(Agent.validKey("",true));
        assertTrue(Agent.validKey("",false));
        assertFalse(Agent.validKey("short",false));
        assertFalse(Agent.validKey("has spaces in it",true));
    }
    @Test public void requestCarriesSystemHistoryAndQuestion() throws Exception{
        JSONObject body=new JSONObject(Agent.requestBody("gemini-flash-latest","SYSTEM",
                Arrays.asList(new Agent.Turn("user","hi"),new Agent.Turn("assistant","hello")),"When is bus 10?"));
        assertEquals("gemini-flash-latest",body.getString("model"));
        assertFalse(body.getBoolean("stream"));
        JSONArray messages=body.getJSONArray("messages");
        assertEquals(4,messages.length());
        assertEquals("system",messages.getJSONObject(0).getString("role"));
        assertEquals("assistant",messages.getJSONObject(2).getString("role"));
        assertEquals("When is bus 10?",messages.getJSONObject(3).getString("content"));
    }
    @Test public void answersAreReadFromTheFirstChoice(){
        assertEquals("Bus 10 arrives in 4 min.",Agent.answer("{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"  Bus 10 arrives in 4 min. \"}}]}"));
        for(String bad:new String[]{"{}","not json","{\"choices\":[]}","{\"choices\":[{\"message\":{\"content\":null}}]}","{\"choices\":[{\"message\":{\"content\":\"  \"}}]}"}){
            try{Agent.answer(bad);fail(bad);}catch(IllegalArgumentException expected){}
        }
    }
    @Test public void providerErrorsBecomeActionableMessages(){
        assertTrue(Agent.failure(401).contains("API key"));
        assertTrue(Agent.failure(429).contains("free-tier limit"));
        assertTrue(Agent.failure(404).contains("model name"));
        assertTrue(Agent.failure(503).contains("temporarily"));
    }
    @Test public void spokenFormDropsMarkdownAndLinks(){
        assertEquals("Bus 10 is in 3 min. See LTA.",Agent.spokenForm("**Bus 10** is in 3 min. See [LTA](https://lta.gov.sg). ```code```"));
    }
    @Test public void systemPromptForbidsInventedArrivalsAndNotesMissingData(){
        assertTrue(Agent.systemPrompt("Monday","").contains("No BAY65 data is shared"));
        String prompt=Agent.systemPrompt("Monday 1 January 2026, 8:00 am","Bus stop \"Home\" (12345)");
        assertTrue(prompt.contains("never invent arrival times"));
        assertTrue(prompt.endsWith("Bus stop \"Home\" (12345)"));
        assertEquals("Listening",Agent.stateLabel("LISTENING"));
        assertEquals("Ready",Agent.stateLabel(null));
    }
}
