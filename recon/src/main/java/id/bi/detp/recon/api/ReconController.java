package id.bi.detp.recon.api;

import id.bi.detp.recon.repo.DiscrepancyCaseRepository;
import id.bi.detp.recon.service.ReconService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ReconController {

    private final ReconService reconService;
    private final DiscrepancyCaseRepository caseRepository;

    public ReconController(ReconService reconService, DiscrepancyCaseRepository caseRepository) {
        this.reconService = reconService;
        this.caseRepository = caseRepository;
    }

    @PostMapping("/recon/run")
    public ReconResult runRecon() {
        return reconService.runRecon();
    }

    @GetMapping("/recon/status")
    public ReconStatus getStatus() {
        return reconService.getStatus();
    }

    @GetMapping("/cases")
    public List<DiscrepancyCase> listCases(@RequestParam(required = false) String status) {
        return caseRepository.findCases(Optional.ofNullable(status));
    }

    @PostMapping("/cases/{id}/resolve")
    public ResponseEntity<DiscrepancyCase> resolveCase(@PathVariable UUID id, @RequestBody ResolveRequest request) {
        if (request.resolution() == null || request.resolution().isBlank()
                || request.evidence() == null || request.evidence().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "resolution and evidence required");
        }
        if (!caseRepository.resolve(id, request.resolution(), request.evidence())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "case not found or already resolved");
        }
        return caseRepository.findCases(Optional.empty()).stream()
                .filter(c -> c.id().equals(id))
                .findFirst()
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "case not found"));
    }
}
