package id.bi.detp.recon.api;

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
