package app.nook.tv;

import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * The BAY65 data shared with the assistant: stop names and codes, live arrivals, favourite app and game
 * names and, when separately allowed, the media title another app reports as playing. One builder serves the launcher (cached arrivals) and the Everywhere panel (arrivals fetched
 * on demand). Pure Java plus org.json, so it runs in JVM tests.
 */
public final class AssistantContext {
    private AssistantContext() {}

    /** A configured stop and, when known, its latest LTA response and when it was fetched. */
    public static final class Stop {
        final String name, code;
        final List<String> services;
        final JSONObject arrivals;
        final long updated;
        public Stop(String name, String code, List<String> services, JSONObject arrivals, long updated) {
            this.name = name; this.code = code; this.services = services; this.arrivals = arrivals; this.updated = updated;
        }
    }

    /**
     * Media an app publishes through Android's MediaSession (never screen content). Values are untrusted app
     * text, so each is flattened to one line and shortened before it reaches the prompt.
     */
    public static final class Media {
        final String app, title, subtitle, artist, album;
        final boolean playing;
        public Media(String app, String title, String subtitle, String artist, String album, boolean playing) {
            this.app = clean(app); this.title = clean(title); this.subtitle = clean(subtitle);
            this.artist = clean(artist); this.album = clean(album); this.playing = playing;
        }
    }

    /** Longest media field passed on; enough for an episode title, short enough to limit injected text. */
    public static final int MEDIA_TEXT_MAX = 120;

    /** Arrivals older than this are treated as missing rather than read out as current. */
    public static final long FRESH_MS = 60_000L;

    public static String build(boolean share, boolean ltaConnected, List<Stop> stops, List<String> favourites, List<String> games, long now) {
        return build(share, ltaConnected, stops, favourites, games, null, now);
    }

    /** {@code media} has its own setting, so it is included even when the BAY65 data above is not shared. */
    public static String build(boolean share, boolean ltaConnected, List<Stop> stops, List<String> favourites, List<String> games, Media media, long now) {
        String playing = nowPlaying(media);
        if (!share) return playing;
        StringBuilder context = new StringBuilder();
        if (stops.isEmpty()) context.append("No bus stops are set up in BAY65.\n");
        else if (!ltaConnected) context.append("Live bus arrivals are off because LTA is not connected in BAY65 preferences.\n");
        for (Stop stop : stops) {
            boolean fresh = ltaConnected && stop.arrivals != null && now - stop.updated <= FRESH_MS && now >= stop.updated - 5_000L;
            context.append("Bus stop \"").append(stop.name).append("\" (").append(stop.code).append(")");
            if (!fresh) { context.append(": no current arrival data for services ").append(String.join(", ", stop.services)).append(".\n"); continue; }
            context.append(", LTA data from ").append(clock(stop.updated)).append(":\n");
            Map<String, JSONObject> services = new HashMap<>();
            JSONArray all = stop.arrivals.optJSONArray("Services");
            for (int i = 0; all != null && i < all.length(); i++) {
                JSONObject service = all.optJSONObject(i);
                if (service != null) services.put(service.optString("ServiceNo"), service);
            }
            for (String number : stop.services) {
                JSONObject service = services.get(number);
                List<String> buses = new ArrayList<>();
                for (String next : new String[] {"NextBus", "NextBus2", "NextBus3"}) {
                    JSONObject bus = service == null ? null : service.optJSONObject(next);
                    if (bus == null) continue;
                    String label = Rules.eta(bus.optString("EstimatedArrival"), bus.optInt("Monitored", 1), now);
                    if (label.equals("—")) continue;
                    String load = Rules.occupancy(bus.optString("Load")).label;
                    buses.add(label + (load.isEmpty() ? "" : " (" + load + ")"));
                }
                context.append("- Bus ").append(number).append(": ").append(buses.isEmpty() ? "no estimate" : String.join(", then ", buses)).append("\n");
            }
        }
        if (!favourites.isEmpty()) context.append("Favourite apps on this TV: ").append(String.join(", ", favourites)).append(".\n");
        if (!games.isEmpty()) context.append("Games in Playtime: ").append(String.join(", ", games)).append(".\n");
        if (!playing.isEmpty()) context.append(playing).append("\n");
        return context.toString().trim();
    }

    /** One sentence such as: The user is currently watching "Title" (Episode 2) in Netflix. */
    static String nowPlaying(Media media) {
        if (media == null || (media.title.isEmpty() && media.app.isEmpty())) return "";
        String app = media.app.isEmpty() ? "" : " in " + media.app;
        StringBuilder line = new StringBuilder("Now playing on this TV, as reported by the app's media controls: ");
        if (media.title.isEmpty()) return line.append("something is ").append(media.playing ? "playing" : "paused").append(app).append(", but the app did not share its title.").toString();
        boolean music = !media.artist.isEmpty() || !media.album.isEmpty();
        line.append(media.playing ? (music ? "the user is currently listening to \"" : "the user is currently watching \"") : "the user has paused \"").append(media.title).append("\"");
        if (!media.subtitle.isEmpty() && !media.subtitle.equals(media.artist) && !media.subtitle.equals(media.album)) line.append(" (").append(media.subtitle).append(")");
        if (!media.artist.isEmpty()) line.append(" by ").append(media.artist);
        if (!media.album.isEmpty() && !media.album.equals(media.title)) line.append(", from \"").append(media.album).append("\"");
        return line.append(app).append(".").toString();
    }

    static String clean(String value) {
        if (value == null) return "";
        String flat = value.replaceAll("[\\p{Cntrl}\\s]+", " ").replace('"', '\'').trim();
        return flat.length() <= MEDIA_TEXT_MAX ? flat : flat.substring(0, MEDIA_TEXT_MAX - 1).trim() + "…";
    }

    static String clock(long time) {
        SimpleDateFormat format = new SimpleDateFormat("h:mm:ss a", Locale.UK);
        format.setTimeZone(TimeZone.getTimeZone("Asia/Singapore"));
        return format.format(new Date(time));
    }
}
