package id.bi.detp.policy.domain;

import java.time.Instant;
import java.time.ZoneOffset;

public class PolicyFact {

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

    public int evalHour() {
        return timestamp.atZone(ZoneOffset.UTC).getHour();
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
