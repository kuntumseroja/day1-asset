package id.bi.detp.recon.api;

import java.time.Instant;

public record ReconStatus(
        String status,
        Instant lastRunAt,
        int openCases
) {}
