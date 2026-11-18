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
 * The BAY65 data shared with the assistant: stop names and codes, live arrivals, and favourite app and
 * game names. One builder serves the launcher (cached arrivals) and the Everywhere panel (arrivals fetched
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

    /** Arrivals older than this are treated as missing rather than read out as current. */
    public static final long FRESH_MS = 60_000L;

    public static String build(boolean share, boolean ltaConnected, List<Stop> stops, List<String> favourites, List<String> games, long now) {
        if (!share) return "";
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
        return context.toString().trim();
    }

    static String clock(long time) {
        SimpleDateFormat format = new SimpleDateFormat("h:mm:ss a", Locale.UK);
        format.setTimeZone(TimeZone.getTimeZone("Asia/Singapore"));
        return format.format(new Date(time));
    }
}
