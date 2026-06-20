package net.mwtw.hippoStaff.grant;

import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DurationParser {
    private static final Pattern TOKEN = Pattern.compile("(\\d+)([smhdw])");

    private DurationParser() {
    }

    public static Duration parse(String input) {
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || "perm".equals(normalized) || "permanent".equals(normalized)) {
            return null;
        }

        Matcher matcher = TOKEN.matcher(normalized);
        int cursor = 0;
        Duration total = Duration.ZERO;
        while (matcher.find()) {
            if (matcher.start() != cursor) {
                throw new IllegalArgumentException("Invalid duration");
            }
            long value = Long.parseLong(matcher.group(1));
            String unit = matcher.group(2);
            total = total.plus(switch (unit) {
                case "s" -> Duration.ofSeconds(value);
                case "m" -> Duration.ofMinutes(value);
                case "h" -> Duration.ofHours(value);
                case "d" -> Duration.ofDays(value);
                case "w" -> Duration.ofDays(value * 7);
                default -> throw new IllegalArgumentException("Invalid duration unit");
            });
            cursor = matcher.end();
        }

        if (cursor != normalized.length() || total.isZero() || total.isNegative()) {
            throw new IllegalArgumentException("Invalid duration");
        }
        return total;
    }
}
