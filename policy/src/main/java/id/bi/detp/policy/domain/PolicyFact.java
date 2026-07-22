package id.bi.detp.policy.domain;

import java.time.Instant;
import java.time.ZoneId;

public class PolicyFact {

    private static final ZoneId POLICY_ZONE = ZoneId.of("Asia/Jakarta");

    private String participantId;
    private String tier;
    private long amount;
    private long dailyCumulative;
    private Instant timestamp;

    public PolicyFact() {
    }

    public PolicyFact(String participantId, String tier, long amount, long dailyCumulative, Instant timestamp) {
        this.participantId = participantId;
        this.tier = tier;
        this.amount = amount;
        this.dailyCumulative = dailyCumulative;
        this.timestamp = timestamp;
    }

    /** Business-hour checks use WIB (UTC+7), matching D-ETP operating timezone. */
    public int evalHour() {
        return timestamp.atZone(POLICY_ZONE).getHour();
    }

    public String getParticipantId() {
        return participantId;
    }

    public void setParticipantId(String participantId) {
        this.participantId = participantId;
    }

    public String getTier() {
        return tier;
    }

    public void setTier(String tier) {
        this.tier = tier;
    }

    public long getAmount() {
        return amount;
    }

    public void setAmount(long amount) {
        this.amount = amount;
    }

    public long getDailyCumulative() {
        return dailyCumulative;
    }

    public void setDailyCumulative(long dailyCumulative) {
        this.dailyCumulative = dailyCumulative;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
