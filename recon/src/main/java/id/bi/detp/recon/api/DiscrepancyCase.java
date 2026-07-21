package id.bi.detp.recon.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DiscrepancyCase(
        UUID id,
        String status,
        long delta,
        long rtgsBalance,
        long platformBalance,
        long chainBalance,
        List<String> candidateUetrs,
        Instant slaDeadline,
        Instant createdAt
) {}
