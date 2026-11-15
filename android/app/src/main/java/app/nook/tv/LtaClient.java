package app.nook.tv;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;

final class LtaClient {
    private static final String BASE="https://datamall2.mytransport.sg/ltaodataservice/";
    static final class Failure extends Exception {
        final boolean authentication;
        Failure(String message,boolean authentication) {super(message);this.authentication=authentication;}
    }
    private JSONObject get(String path,String key) throws Failure {
        if(key==null||key.length()<8||key.length()>512||!key.matches("[\\x21-\\x7e]+"))throw new Failure("Enter your DataMall API Access Key.",true);
        HttpsURLConnection connection=null;
        try {
            connection=(HttpsURLConnection)new URL(BASE+path).openConnection();
            connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(10000);connection.setReadTimeout(12000);
            connection.setRequestProperty("AccountKey",key);connection.setRequestProperty("Accept","application/json");connection.setUseCaches(false);
            int code=connection.getResponseCode();
            if(code==401||code==403)throw new Failure("LTA rejected the key. Check your DataMall API Access Key and its activation.",true);
            if(code==429)throw new Failure("LTA’s request limit was reached. Please wait a minute.",false);
            if(code!=200)throw new Failure("LTA is temporarily unavailable. Try again shortly.",false);
            try(InputStream input=connection.getInputStream();ByteArrayOutputStream output=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[8192];int count,total=0;
                while((count=input.read(buffer))!=-1){total+=count;if(total>6_000_000)throw new IOException("Response too large");output.write(buffer,0,count);}
                return new JSONObject(output.toString("UTF-8"));
            }
        } catch(IOException|JSONException exception) {throw new Failure("Cannot retrieve LTA data. Check the internet connection and try again.",false);}
        finally {if(connection!=null)connection.disconnect();}
    }
    void test(String key) throws Failure {
        if(get("BusStops?$skip=0",key).optJSONArray("value")==null)throw new Failure("LTA returned an unexpected response.",false);
    }
    JSONObject arrivals(String code,String key) throws Failure {
        if(!code.matches("[0-9]{5}"))throw new Failure("Enter a five-digit bus stop code.",false);
        JSONObject result=get("v3/BusArrival?BusStopCode="+code,key);
        if(result.has("BusStopCode")&&!code.equals(result.optString("BusStopCode")))throw new Failure("LTA returned a different bus stop.",false);
        return result;
    }
    JSONArray directory(String key) throws Failure {
        JSONArray all=new JSONArray();
        for(int skip=0;skip<15000;skip+=500){
            JSONArray page=get("BusStops?$skip="+skip,key).optJSONArray("value");
            if(page==null)throw new Failure("LTA’s bus stop directory is unavailable.",false);
            for(int i=0;i<page.length();i++)all.put(page.opt(i));
            if(page.length()<500)return all;
        }
        throw new Failure("The bus stop directory is larger than expected. Enter the stop manually.",false);
    }
}
