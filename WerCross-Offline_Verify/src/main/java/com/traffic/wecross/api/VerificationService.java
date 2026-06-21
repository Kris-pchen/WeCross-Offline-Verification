package com.traffic.wecross.api;

import com.traffic.wecross.protocol.Groth16Protocol;
import com.traffic.wecross.protocol.MerkleTreeProtocol;
import com.traffic.wecross.protocol.ThresholdSignatureProtocol;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class VerificationService {
    private final ObjectMapper objectMapper;
    private final WeCrossGateway weCrossGateway;

    public VerificationService(ObjectMapper objectMapper, WeCrossGateway weCrossGateway) {
        this.objectMapper = objectMapper;
        this.weCrossGateway = weCrossGateway;
    }

    public VerificationRecord verifyMerkle(VerificationRequests.MerkleRequest request) throws Exception {
        PreparedMerkle prepared = prepareMerkle(request.dataBlocks, request.sampleIndex);

        boolean passed = prepared.protocol.verifyProof(prepared.root, prepared.sampleData, prepared.path);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("merkleRoot", HashUtils.hex(prepared.root));
        detail.put("sampleIndex", prepared.sampleIndex);
        detail.put("sampleData", new String(prepared.sampleData, StandardCharsets.UTF_8));
        detail.put("siblings", HashUtils.hexList(prepared.path.siblings));
        detail.put("isLeftNodeList", prepared.path.isLeftNodeList);
        detail.put("leafCount", prepared.blocks.size());

        VerificationRecord record = buildRecord(request.businessId, "MERKLE_ROOT", passed,
                HashUtils.hex(prepared.root), "SHA-256", detail);
        writeIfRequested(record, request.writeOnChain, request.targetChains);
        return record;
    }

    public VerificationRecord verifyGroth16(VerificationRequests.Groth16Request request) throws Exception {
        byte[] merkleRoot = resolveMerkleRoot(request.merkleRoot, request.dataBlocks, request.sampleIndex);
        Groth16Protocol zkProtocol = new Groth16Protocol();
        Groth16Protocol.Proof proof = zkProtocol.generateProof(merkleRoot);
        boolean passed = zkProtocol.verify(proof, merkleRoot);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("merkleRoot", HashUtils.hex(merkleRoot));
        detail.put("proofHash", proofHash(proof));
        detail.put("piA", proof.pi_A.toString());
        detail.put("piB", proof.pi_B.toString());
        detail.put("piC", proof.pi_C.toString());

        VerificationRecord record = buildRecord(request.businessId, "GROTH16_ZKP", passed,
                HashUtils.hex(merkleRoot), "JPBC-SIMULATED-GROTH16", detail);
        writeIfRequested(record, request.writeOnChain, request.targetChains);
        return record;
    }

    public VerificationRecord verifyThresholdSignature(VerificationRequests.ThresholdSignatureRequest request) throws Exception {
        byte[] merkleRoot = resolveMerkleRoot(request.merkleRoot, request.dataBlocks, request.sampleIndex);
        int totalNodes = request.totalNodes == null ? 10 : request.totalNodes;
        int threshold = request.threshold == null ? 5 : request.threshold;

        if (threshold <= 0 || threshold > totalNodes) {
            throw new IllegalArgumentException("threshold must be greater than 0 and less than or equal to totalNodes");
        }

        List<Integer> participants = request.participantIds == null || request.participantIds.isEmpty()
                ? defaultParticipants(threshold)
                : request.participantIds;

        ThresholdSignatureProtocol thresholdProtocol = new ThresholdSignatureProtocol(totalNodes, threshold);
        Map<Integer, BigInteger> collectedShares = new HashMap<>();
        for (Integer participantId : participants) {
            if (participantId == null || participantId < 1 || participantId > totalNodes) {
                throw new IllegalArgumentException("participantId out of range: " + participantId);
            }
            collectedShares.put(participantId, thresholdProtocol.getShareForNode(participantId));
        }

        byte[] signature = thresholdProtocol.aggregateAndSign(collectedShares, merkleRoot);
        boolean passed = signature != null && thresholdProtocol.verifyFinalSignature(signature, merkleRoot);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("merkleRoot", HashUtils.hex(merkleRoot));
        detail.put("totalNodes", totalNodes);
        detail.put("threshold", threshold);
        detail.put("participants", participants);
        detail.put("signatureHash", signature == null ? null : HashUtils.hex(HashUtils.sha256(signature)));
        detail.put("signature", signature == null ? null : HashUtils.hex(signature));

        VerificationRecord record = buildRecord(request.businessId, "THRESHOLD_SIGNATURE", passed,
                HashUtils.hex(merkleRoot), "SHAMIR-PLUS-ECDSA-SIMULATION", detail);
        writeIfRequested(record, request.writeOnChain, request.targetChains);
        return record;
    }

    private PreparedMerkle prepareMerkle(List<String> dataBlocks, Integer sampleIndex) throws Exception {
        List<String> normalized = dataBlocks == null || dataBlocks.isEmpty()
                ? Arrays.asList("Bus_101_Speed_40", "Bus_102_Speed_35", "Bus_103_Speed_42", "Bus_104_Speed_38")
                : dataBlocks;

        int index = sampleIndex == null ? 0 : sampleIndex;
        if (index < 0 || index >= normalized.size()) {
            throw new IllegalArgumentException("sampleIndex must be between 0 and " + (normalized.size() - 1));
        }

        List<byte[]> blocks = new ArrayList<>();
        for (String item : normalized) {
            blocks.add(item.getBytes(StandardCharsets.UTF_8));
        }

        MerkleTreeProtocol protocol = new MerkleTreeProtocol();
        byte[] root = protocol.buildTree(blocks);
        byte[] sampleData = blocks.get(index);
        MerkleTreeProtocol.MerklePath path = protocol.generateProof(index, sampleData);
        PreparedMerkle prepared = new PreparedMerkle();
        prepared.protocol = protocol;
        prepared.blocks = blocks;
        prepared.root = root;
        prepared.sampleData = sampleData;
        prepared.sampleIndex = index;
        prepared.path = path;
        return prepared;
    }

    private byte[] resolveMerkleRoot(String requestedRoot, List<String> dataBlocks, Integer sampleIndex) throws Exception {
        if (requestedRoot != null && !requestedRoot.trim().isEmpty()) {
            return HashUtils.fromHex(requestedRoot.trim());
        }
        return prepareMerkle(dataBlocks, sampleIndex).root;
    }
    private VerificationRecord buildRecord(String businessId, String verifyType, boolean passed,
                                           String dataHash, String algorithm, Map<String, Object> detail) throws Exception {
        VerificationRecord record = new VerificationRecord();
        record.recordId = UUID.randomUUID().toString();
        record.businessId = businessId == null || businessId.trim().isEmpty() ? record.recordId : businessId;
        record.verifyType = verifyType;
        record.passed = passed;
        record.dataHash = dataHash;
        record.algorithm = algorithm;
        record.timestamp = System.currentTimeMillis();
        record.detail = detail;

        String detailJson = objectMapper.writeValueAsString(detail);
        record.detailHash = HashUtils.sha256Hex(detailJson);
        record.resultHash = HashUtils.sha256Hex(record.businessId + ":" + verifyType + ":" + passed + ":" + dataHash + ":" + record.detailHash);
        return record;
    }

    private void writeIfRequested(VerificationRecord record, Boolean writeOnChain, List<String> targetChains) {
        if (writeOnChain == null || writeOnChain) {
            record.chainResults = weCrossGateway.writeRecord(record, targetChains);
        }
    }

    private String proofHash(Groth16Protocol.Proof proof) {
        String material = proof.pi_A.toString() + "|" + proof.pi_B.toString() + "|" + proof.pi_C.toString();
        return HashUtils.sha256Hex(material);
    }

    private List<Integer> defaultParticipants(int threshold) {
        List<Integer> participants = new ArrayList<>();
        for (int i = 1; i <= threshold; i++) {
            participants.add(i);
        }
        return participants;
    }

    private static class PreparedMerkle {
        private MerkleTreeProtocol protocol;
        private List<byte[]> blocks;
        private byte[] root;
        private byte[] sampleData;
        private int sampleIndex;
        private MerkleTreeProtocol.MerklePath path;
    }
}


