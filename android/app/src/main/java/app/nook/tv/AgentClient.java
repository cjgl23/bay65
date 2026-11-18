package app.nook.tv;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;

/** One HTTPS request per question to the user's chosen provider. The key is never logged. */
final class AgentClient {
    static final class Failure extends Exception {
        Failure(String message) { super(message); }
    }

    /** At most 64 KB of the error body, read only to show the provider's reason. */
    private static String errorBody(HttpsURLConnection connection) {
        try (InputStream input = connection.getErrorStream()) {
            if (input == null) return null;
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count, total = 0;
            while ((count = input.read(buffer)) != -1 && total < 65536) { total += count; output.write(buffer, 0, count); }
            return output.toString("UTF-8");
        } catch (IOException unavailable) { return null; }
    }

    String ask(String endpoint, String key, String body) throws Failure {
        HttpsURLConnection connection = null;
        try {
            connection = (HttpsURLConnection) new URL(endpoint).openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(45000);
            connection.setUseCaches(false);
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            if (key != null && !key.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + key);
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(payload.length);
            try (OutputStream output = connection.getOutputStream()) { output.write(payload); }
            int code = connection.getResponseCode();
            if (code != 200) throw new Failure(Agent.failure(code, errorBody(connection)));
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count, total = 0;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > 1_000_000) throw new IOException("Response too large");
                    output.write(buffer, 0, count);
                }
                return Agent.answer(output.toString("UTF-8"));
            }
        } catch (IllegalArgumentException e) {
            throw new Failure(e.getMessage());
        } catch (IOException | ClassCastException e) {
            throw new Failure("Cannot reach the assistant. Check the internet connection and try again.");
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
