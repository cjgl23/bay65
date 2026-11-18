package app.nook.tv;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class AgentTest {
    @Test public void everyFreePresetUsesHttpsChatCompletions(){
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent",Agent.endpoint(Agent.Provider.GEMINI,null,"gemini-3.5-flash-lite"));
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent",Agent.endpoint(Agent.Provider.GEMINI,null,"models/gemini-3.5-flash-lite"));
        assertEquals("gemini-3.5-flash-lite",Agent.Provider.GEMINI.defaultModel);
        assertEquals("https://api.groq.com/openai/v1/chat/completions",Agent.endpoint(Agent.Provider.GROQ,"ignored","m"));
        assertEquals("https://openrouter.ai/api/v1/chat/completions",Agent.endpoint(Agent.Provider.OPENROUTER,"","m"));
        for(String bad:new String[]{"a/b","x:generateContent","../x"}){try{Agent.endpoint(Agent.Provider.GEMINI,null,bad);fail(bad);}catch(IllegalArgumentException expected){}}
        assertTrue(Agent.canSearch(Agent.Provider.GEMINI));
        assertFalse(Agent.canSearch(Agent.Provider.GROQ));
        assertTrue(Agent.Provider.GEMINI.keyRequired);
        assertFalse(Agent.Provider.CUSTOM.keyRequired);
        assertEquals(Agent.Provider.GEMINI,Agent.provider("unknown"));
        assertEquals(Agent.Provider.GROQ,Agent.provider("groq"));
    }
    @Test public void customServersMustBeHttpsWithoutCredentialsInTheUrl(){
        assertEquals("https://ollama.home.ts.net/v1/chat/completions",Agent.endpoint(Agent.Provider.CUSTOM," https://ollama.home.ts.net/v1/ ","llama3.2"));
        for(String bad:new String[]{"","http://192.168.1.5:11434/v1","ftp://x/v1","https://user:pw@host/v1","https://host/v1?x=1","https:///v1","not a url"}){
            try{Agent.endpoint(Agent.Provider.CUSTOM,bad,"llama3.2");fail(bad);}catch(IllegalArgumentException expected){}
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
    @Test public void geminiRequestsUseNativeRolesAndOptionalGoogleSearch() throws Exception{
        Agent.Call call=Agent.call(Agent.Provider.GEMINI,null,"gemini-3.5-flash-lite","AIzaKey12345","SYSTEM",
                Arrays.asList(new Agent.Turn("user","hi"),new Agent.Turn("assistant","hello")),"Trending OPM songs?",true);
        assertTrue(call.gemini);
        assertFalse(call.endpoint.contains("AIzaKey12345"));
        JSONObject body=new JSONObject(call.body);
        assertEquals("SYSTEM",body.getJSONObject("systemInstruction").getJSONArray("parts").getJSONObject(0).getString("text"));
        JSONArray contents=body.getJSONArray("contents");
        assertEquals(3,contents.length());
        assertEquals("model",contents.getJSONObject(1).getString("role"));
        assertEquals("Trending OPM songs?",contents.getJSONObject(2).getJSONArray("parts").getJSONObject(0).getString("text"));
        assertTrue(body.getJSONArray("tools").getJSONObject(0).has("google_search"));
        assertFalse(new JSONObject(Agent.geminiBody("S",Arrays.<Agent.Turn>asList(),"q",false)).has("tools"));
        Agent.Call groq=Agent.call(Agent.Provider.GROQ,null,"llama-3.3-70b-versatile","gsk_key1234","S",Arrays.<Agent.Turn>asList(),"q",true);
        assertFalse(groq.gemini);
        assertEquals("llama-3.3-70b-versatile",new JSONObject(groq.body).getString("model"));
    }
    @Test public void groundedGeminiAnswersCarryUpToThreeSourcesAndSkipThoughts(){
        String body="{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":\"planning\",\"thought\":true},{\"text\":\"Top OPM tracks this week include \"},{\"text\":\"songs by SB19.\"}]},"
                +"\"finishReason\":\"STOP\",\"groundingMetadata\":{\"webSearchQueries\":[\"trending OPM songs\"],\"groundingChunks\":["
                +"{\"web\":{\"uri\":\"https://a\",\"title\":\"spotify.com\"}},{\"web\":{\"uri\":\"https://b\",\"title\":\"billboard.com\"}},"
                +"{\"web\":{\"uri\":\"https://c\",\"title\":\"spotify.com\"}},{\"web\":{\"uri\":\"https://d\",\"title\":\"rappler.com\"}},{\"web\":{\"uri\":\"https://e\",\"title\":\"x.com\"}}]}}]}";
        Agent.Reply reply=Agent.reply(body,true);
        assertEquals("Top OPM tracks this week include songs by SB19.",reply.text);
        assertEquals(Arrays.asList("spotify.com","billboard.com","rappler.com"),reply.sources);
        Agent.Reply plain=Agent.reply("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Hi\"}]}}]}",true);
        assertEquals("Hi",plain.text);assertTrue(plain.sources.isEmpty());
        for(String bad:new String[]{"{}","{\"candidates\":[{\"finishReason\":\"SAFETY\"}]}","nope"}){try{Agent.reply(bad,true);fail(bad);}catch(IllegalArgumentException expected){}}
        try{Agent.reply("{\"candidates\":[{\"finishReason\":\"SAFETY\"}]}",true);}catch(IllegalArgumentException e){assertTrue(e.getMessage().contains("declined"));}
        assertTrue(Agent.reply("{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}",false).sources.isEmpty());
    }
    @Test public void promptsSendFavouriteAppQuestionsToSearchOnlyWhenAvailable(){
        String searching=Agent.systemPrompt("Monday","Favourite apps on this TV: Spotify.",true);
        assertTrue(searching.contains("Use Google Search"));
        assertTrue(searching.contains("favourite apps"));
        assertTrue(searching.contains("OPM"));
        String offline=Agent.systemPrompt("Monday","",false);
        assertTrue(offline.contains("cannot search the web"));
        assertFalse(offline.contains("Use Google Search"));
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
    @Test public void geminiBadKeyIsReportedAsAKeyProblemNotAModelProblem(){
        // Captured from generativelanguage.googleapis.com: an invalid key returns HTTP 400 in an array.
        String gemini="[{\n  \"error\": {\n    \"code\": 400,\n    \"message\": \"Please pass a valid API key\",\n    \"status\": \"INVALID_ARGUMENT\"\n  }\n}\n]";
        String message=Agent.failure(400,gemini);
        assertTrue(message,message.contains("API key"));
        assertFalse(message,message.contains("model"));
        assertTrue(message.endsWith("(Please pass a valid API key)"));
    }
    @Test public void providerReasonsDecideModelAndOtherErrors(){
        assertTrue(Agent.failure(400,"{\"error\":{\"message\":\"models/gemini-x is not found\"}}").contains("model name"));
        assertTrue(Agent.failure(404,null).contains("model name"));
        String other=Agent.failure(400,"{\"error\":{\"message\":\"Bad thing\\nhappened\"}}");
        assertEquals("The provider rejected the request. (Bad thing happened)",other);
        assertEquals("The provider rejected the request.",Agent.failure(400,"<html>oops</html>"));
        assertTrue(Agent.failure(500,"{\"error\":{\"message\":\"secret detail\"}}").contains("temporarily"));
        StringBuilder longReason=new StringBuilder();for(int i=0;i<50;i++)longReason.append("word ");
        assertTrue(Agent.failure(429,"{\"error\":{\"message\":\""+longReason+"\"}}").length()<240);
    }
    @Test public void serverErrorsNeverBlameTheModelOrKey(){
        // Gemini's real overload reply: a 503 whose message mentions "model".
        String overloaded="{\"error\":{\"code\":503,\"message\":\"The model is overloaded. Please try again later.\",\"status\":\"UNAVAILABLE\"}}";
        assertEquals("The assistant is temporarily unavailable. Try again shortly.",Agent.failure(503,overloaded));
        assertEquals("The assistant is temporarily unavailable. Try again shortly.",Agent.failure(500,"{\"error\":{\"message\":\"invalid api key cache\"}}"));
        assertEquals("The assistant is temporarily unavailable. Try again shortly.",Agent.failure(0,null));
        // Client errors still use the provider's reason.
        assertTrue(Agent.failure(400,"{\"error\":{\"message\":\"models/x is not found\"}}").contains("model name"));
        assertTrue(Agent.failure(403,null).contains("API key"));
        assertFalse(Agent.failure(429,"{\"error\":{\"message\":\"model quota\"}}").contains("model name"));
    }
    @Test public void dailyQuotaIsDistinguishedFromPerMinuteLimits(){
        assertTrue(Agent.failure(429,"{\"error\":{\"message\":\"Quota exceeded for metric: requests per day\"}}").contains("Try again tomorrow"));
        assertTrue(Agent.failure(429,"{\"error\":{\"message\":\"Rate limit: requests per minute\"}}").contains("Wait a minute"));
    }
    @Test public void searchedQuestionsRetryWithoutSearchOnlyForLimitsAndGroundingErrors(){
        assertTrue(Agent.retryWithoutSearch(429,null));
        assertTrue(Agent.retryWithoutSearch(400,"{\"error\":{\"message\":\"Search Grounding is not supported.\"}}"));
        assertTrue(Agent.retryWithoutSearch(400,"[{\"error\":{\"message\":\"google_search grounding unavailable\"}}]"));
        assertFalse(Agent.retryWithoutSearch(400,"{\"error\":{\"message\":\"API key not valid\"}}"));
        assertFalse(Agent.retryWithoutSearch(503,null));
        assertFalse(Agent.retryWithoutSearch(401,null));
        Agent.Call searched=Agent.call(Agent.Provider.GEMINI,null,"gemini-3.5-flash-lite","AIzaKey12345","S",Arrays.<Agent.Turn>asList(),"q",true);
        assertTrue(searched.searched);
        assertFalse(Agent.call(Agent.Provider.GEMINI,null,"gemini-3.5-flash-lite","AIzaKey12345","S",Arrays.<Agent.Turn>asList(),"q",false).searched);
        assertFalse("Only Gemini can search",Agent.call(Agent.Provider.GROQ,null,"m","gsk_key1234","S",Arrays.<Agent.Turn>asList(),"q",true).searched);
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
