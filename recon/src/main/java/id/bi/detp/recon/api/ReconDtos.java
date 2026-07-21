package id.bi.detp.recon.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReconResult(
        UUID runId,
        boolean balanced,
        long rtgsOmnibus,
        long platformLedger,
        long chainSupply,
        long pipelineIn,
        long pipelineOut,
        long delta,
        String lsn,
        long blockHeight
) {}

public record ReconStatus(
        String status,
        Instant lastRunAt,
        int openCases
) {}

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

public record ResolveRequest(
        String resolution,
        String evidence
) {}
