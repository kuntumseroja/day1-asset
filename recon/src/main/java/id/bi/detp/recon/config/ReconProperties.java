package id.bi.detp.recon.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "recon")
public record ReconProperties(
        String rtgsUrl,
        String fireflyUrl,
        long tolerance,
        int slaHours,
        boolean enforceRtgs
) {}
