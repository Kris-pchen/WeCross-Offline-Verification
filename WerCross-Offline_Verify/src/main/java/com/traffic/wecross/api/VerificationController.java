package com.traffic.wecross.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/verification")
@CrossOrigin
public class VerificationController {
    private final VerificationService verificationService;

    public VerificationController(VerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "UP");
        result.put("service", "transportation-verification-service");
        return result;
    }

    @PostMapping("/merkle")
    public VerificationRecord verifyMerkle(@RequestBody VerificationRequests.MerkleRequest request) throws Exception {
        return verificationService.verifyMerkle(request);
    }

    @PostMapping("/groth16")
    public VerificationRecord verifyGroth16(@RequestBody VerificationRequests.Groth16Request request) throws Exception {
        return verificationService.verifyGroth16(request);
    }

    @PostMapping("/threshold-signature")
    public VerificationRecord verifyThresholdSignature(@RequestBody VerificationRequests.ThresholdSignatureRequest request) throws Exception {
        return verificationService.verifyThresholdSignature(request);
    }

    @PostMapping("/full")
    public Map<String, VerificationRecord> verifyAll(@RequestBody VerificationRequests.ThresholdSignatureRequest request) throws Exception {
        Map<String, VerificationRecord> result = new LinkedHashMap<>();

        VerificationRequests.MerkleRequest merkleRequest = copyBase(request, new VerificationRequests.MerkleRequest());
        VerificationRequests.Groth16Request groth16Request = copyBase(request, new VerificationRequests.Groth16Request());

        result.put("merkle", verificationService.verifyMerkle(merkleRequest));
        result.put("groth16", verificationService.verifyGroth16(groth16Request));
        result.put("thresholdSignature", verificationService.verifyThresholdSignature(request));
        return result;
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "FAILED");
        body.put("message", e.getMessage());
        return ResponseEntity.badRequest().body(body);
    }

    private <T extends VerificationRequests.BaseRequest> T copyBase(VerificationRequests.BaseRequest source, T target) {
        target.businessId = source.businessId;
        target.dataBlocks = source.dataBlocks;
        target.sampleIndex = source.sampleIndex;
        target.targetChains = source.targetChains;
        target.writeOnChain = source.writeOnChain;
        return target;
    }
}
