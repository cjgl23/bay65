package app.nook.tv;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** Validation and arrival presentation shared by the native UI and JVM checks. */
public final class Rules {
    private Rules() {}
    public enum Occupancy {
        SEATS("Seats", 0xff4ade80), STANDING("Standing", 0xfffb923c), LIMITED("Limited", 0xfff87171),
        UNKNOWN("", 0xffa1b8c7);
        public final String label;
        public final int color;
        Occupancy(String label, int color) { this.label = label; this.color = color; }
    }
    public static Occupancy occupancy(String load) {
        if ("SEA".equals(load)) return Occupancy.SEATS;
        if ("SDA".equals(load)) return Occupancy.STANDING;
        if ("LSD".equals(load)) return Occupancy.LIMITED;
        return Occupancy.UNKNOWN;
    }
    /** Borrow only the free spacer above favourites; never move the app shelf down. */
    public static int busCardHeight(int minimum,int available,int content,int gap) {
        return Math.min(Math.max(minimum,available-gap),Math.max(minimum,content));
    }
    /** Some TVs advertise settings handlers that only show an unavailable-app message. */
    public static boolean settingsPlaceholder(String packageName,String activityName){
        return packageName!=null&&packageName.contains("frameworkpackagestubs")
                ||activityName!=null&&(activityName.endsWith(".EmptyStubActivity")||activityName.contains(".Stubs$"));
    }
    public static List<String> services(String text) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String part : text.toUpperCase(Locale.ROOT).split("[,\\s]+")) {
            if (part.isEmpty()) continue;
            if (!part.matches("[A-Z0-9]{1,6}") || !part.matches(".*[0-9].*"))
                throw new IllegalArgumentException("Enter service numbers separated by commas.");
            result.add(part);
        }
        if (result.isEmpty() || result.size() > 8)
            throw new IllegalArgumentException("Choose between one and eight services.");
        return new ArrayList<>(result);
    }
    public static void stop(String name, String code) {
        if (name.trim().isEmpty() || name.length() > 80)
            throw new IllegalArgumentException("Enter a stop name, up to 80 characters.");
        if (!code.matches("[0-9]{5}"))
            throw new IllegalArgumentException("Enter a five-digit stop code.");
    }
    public static String eta(String iso, int monitored, long now) {
        if (iso == null || iso.isEmpty()) return "—";
        try {
            String value = iso.replace("Z", "+0000").replaceAll("([+-][0-9]{2}):([0-9]{2})$", "$1$2");
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US);
            format.setLenient(false);
            long remaining = format.parse(value).getTime() - now;
            if (remaining < -60_000L) return "—";
            long minutes = Math.max(0, remaining / 60_000L);
            return (monitored == 0 ? "~" : "") + (minutes == 0 ? "Arriving" : minutes + " min");
        } catch (ParseException | NullPointerException exception) { return "—"; }
    }
}
