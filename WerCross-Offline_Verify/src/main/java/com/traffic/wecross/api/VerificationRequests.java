package com.traffic.wecross.api;

import java.util.ArrayList;
import java.util.List;

public class VerificationRequests {
    public static class BaseRequest {
        public String businessId;
        public List<String> dataBlocks = new ArrayList<>();
        public Integer sampleIndex = 0;
        public List<String> targetChains = new ArrayList<>();
        public Boolean writeOnChain = Boolean.TRUE;
    }

    public static class MerkleRequest extends BaseRequest {
    }

    public static class Groth16Request extends BaseRequest {
        public String merkleRoot;
    }

    public static class ThresholdSignatureRequest extends BaseRequest {
        public String merkleRoot;
        public Integer totalNodes = 10;
        public Integer threshold = 5;
        public List<Integer> participantIds = new ArrayList<>();
    }
}


