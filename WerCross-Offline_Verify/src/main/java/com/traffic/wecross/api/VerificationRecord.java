package com.traffic.wecross.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class VerificationRecord {
    public String recordId;
    public String businessId;
    public String verifyType;
    public boolean passed;
    public String dataHash;
    public String resultHash;
    public String detailHash;
    public String algorithm;
    public long timestamp;
    public Map<String, Object> detail = new LinkedHashMap<>();
    public List<ChainWriteResult> chainResults = new ArrayList<>();
}
