package id.bi.detp.recon.service;

import id.bi.detp.recon.api.ReconResult;
import id.bi.detp.recon.api.ReconStatus;
import id.bi.detp.recon.client.FireflyClient;
import id.bi.detp.recon.client.RtgsClient;
import id.bi.detp.recon.config.ReconProperties;
import id.bi.detp.recon.repo.DiscrepancyCaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ReconService {

    private static final Logger log = LoggerFactory.getLogger(ReconService.class);

    private final RtgsClient rtgsClient;
    private final FireflyClient fireflyClient;
    private final PlatformLedgerReader ledgerReader;
    private final DiscrepancyCaseRepository caseRepository;
    private final ReconProperties properties;
    private final KafkaTemplate<String, Map<String, Object>> kafkaTemplate;

    private final AtomicReference<Instant> lastRunAt = new AtomicReference<>();
    private final AtomicReference<String> lastStatus = new AtomicReference<>("GREEN");

    public ReconService(
            RtgsClient rtgsClient,
            FireflyClient fireflyClient,
            PlatformLedgerReader ledgerReader,
            DiscrepancyCaseRepository caseRepository,
            ReconProperties properties,
            KafkaTemplate<String, Map<String, Object>> kafkaTemplate) {
        this.rtgsClient = rtgsClient;
        this.fireflyClient = fireflyClient;
        this.ledgerReader = ledgerReader;
        this.caseRepository = caseRepository;
        this.properties = properties;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Three-way invariant: omnibus = platform + pipelineIn = chain + pipelineOut (tolerance 0).
     * PipelineIn covers sagas funded/minting not yet on platform; pipelineOut covers RTGS-funded
     * amounts not yet reflected on-chain.
     */
    public ReconResult runRecon() {
        UUID runId = UUID.randomUUID();

        long rtgsOmnibus = rtgsClient.fetchClosingBalance();
        PlatformLedgerReader.PlatformSnapshot platform = ledgerReader.snapshot();
        FireflyClient.ChainSupply chain = fireflyClient.fetchSupply();

        long platformLedger = platform.platformLedger();
        long pipelineIn = platform.pipelineIn();
        long pipelineOut = platform.pipelineOut();
        long chainSupply = chain.totalSupply();

        long adjustedPlatform = platformLedger + pipelineIn;
        long adjustedChain = chainSupply + pipelineOut;

        long deltaPlatformChain = adjustedChain - adjustedPlatform;
        long deltaRtgsPlatform = rtgsOmnibus - adjustedPlatform;
        long delta = properties.enforceRtgs()
                ? maxAbs(deltaPlatformChain, deltaRtgsPlatform)
                : deltaPlatformChain;

        boolean balanced = Math.abs(deltaPlatformChain) <= properties.tolerance()
                && (!properties.enforceRtgs() || Math.abs(deltaRtgsPlatform) <= properties.tolerance());

        lastRunAt.set(Instant.now());
        lastStatus.set(balanced ? "GREEN" : "RED");

        if (!balanced) {
            List<String> candidates = new ArrayList<>(platform.candidateUetrs());
            UUID caseId = caseRepository.insertCase(delta, rtgsOmnibus, platformLedger, chainSupply, candidates);
            publishBreak(runId, caseId, delta, rtgsOmnibus, platformLedger, chainSupply, candidates);
            log.warn("Recon break runId={} caseId={} delta={} candidates={}", runId, caseId, delta, candidates);
        } else {
            log.info("Recon balanced runId={} rtgs={} platform={} chain={}", runId, rtgsOmnibus, adjustedPlatform, adjustedChain);
        }

        return new ReconResult(
                runId,
                balanced,
                rtgsOmnibus,
                platformLedger,
                chainSupply,
                pipelineIn,
                pipelineOut,
                delta,
                platform.lsn(),
                chain.blockHeight()
        );
    }

    private long maxAbs(long a, long b) {
        return Math.max(Math.abs(a), Math.abs(b));
    }

    private void publishBreak(UUID runId, UUID caseId, long delta, long rtgs, long platform, long chain, List<String> uetrs) {
        try {
            kafkaTemplate.send("recon.break", caseId.toString(), Map.of(
                    "runId", runId.toString(),
                    "caseId", caseId.toString(),
                    "delta", delta,
                    "rtgsBalance", rtgs,
                    "platformBalance", platform,
                    "chainBalance", chain,
                    "candidateUetrs", uetrs
            ));
        } catch (Exception e) {
            log.debug("Kafka publish skipped: {}", e.getMessage());
        }
    }

    public ReconStatus getStatus() {
        return new ReconStatus(
                lastStatus.get(),
                lastRunAt.get(),
                caseRepository.countOpenCases()
        );
    }
}
