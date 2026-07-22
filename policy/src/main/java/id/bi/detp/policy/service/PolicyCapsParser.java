package id.bi.detp.policy.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class PolicyCapsParser {

    private static final Pattern PER_ISSUANCE = Pattern.compile("amount\\s*>\\s*(\\d+)");
    private static final Pattern DAILY_CUMULATIVE =
            Pattern.compile("dailyCumulative\\s*\\+\\s*amount\\s*>=\\s*(\\d+)L?");

    private PolicyCapsParser() {
    }

    static long perIssuanceCap(String drlContent) {
        return extract(drlContent, PER_ISSUANCE, 500_000_000L);
    }

    static long dailyCumulativeCap(String drlContent) {
        return extract(drlContent, DAILY_CUMULATIVE, 5_000_000_000L);
    }

    private static long extract(String drlContent, Pattern pattern, long fallback) {
        if (drlContent == null) {
            return fallback;
        }
        Matcher matcher = pattern.matcher(drlContent);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : fallback;
    }
}
