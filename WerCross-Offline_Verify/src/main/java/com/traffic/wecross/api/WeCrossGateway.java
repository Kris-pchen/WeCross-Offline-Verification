package com.traffic.wecross.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webank.wecrosssdk.common.StatusCode;
import com.webank.wecrosssdk.rpc.WeCrossRPC;
import com.webank.wecrosssdk.rpc.WeCrossRPCFactory;
import com.webank.wecrosssdk.rpc.common.Receipt;
import com.webank.wecrosssdk.rpc.methods.response.TransactionResponse;
import com.webank.wecrosssdk.rpc.service.WeCrossRPCService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class WeCrossGateway {
    private final ObjectMapper objectMapper;
    private final List<String> defaultTargets;
    private final String mode;
    private final String contractMethod;

    public WeCrossGateway(
            ObjectMapper objectMapper,
            @Value("${wecross.default-targets}") String defaultTargets,
            @Value("${wecross.mode:mock}") String mode,
            @Value("${wecross.contract-method:saveRecord}") String contractMethod) {
        this.objectMapper = objectMapper;
        this.defaultTargets = Arrays.asList(defaultTargets.split("\\s*,\\s*"));
        this.mode = mode;
        this.contractMethod = contractMethod;
    }

    public List<ChainWriteResult> writeRecord(
            VerificationRecord record, List<String> requestedTargets) {
        List<String> targets =
                requestedTargets == null || requestedTargets.isEmpty()
                        ? defaultTargets
                        : requestedTargets;

        if (!"sdk".equalsIgnoreCase(mode)) {
            return writeMockRecord(record, targets);
        }

        try {
            WeCrossRPC weCrossRPC = WeCrossRPCFactory.build(new WeCrossRPCService());
            String recordJson = objectMapper.writeValueAsString(record);
            List<ChainWriteResult> results = new ArrayList<>();
            for (String resourcePath : targets) {
                results.add(writeByWeCrossSdk(weCrossRPC, resourcePath, recordJson));
            }
            return results;
        } catch (Exception e) {
            return failedResults(targets, "WeCross SDK initialization failed: " + messageOf(e));
        }
    }

    private ChainWriteResult writeByWeCrossSdk(
            WeCrossRPC weCrossRPC, String resourcePath, String recordJson) {
        try {
            TransactionResponse response =
                    weCrossRPC
                            .sendTransaction(resourcePath, contractMethod, recordJson)
                            .send();
            if (response == null || response.getErrorCode() != StatusCode.SUCCESS) {
                return ChainWriteResult.failed(
                        resourcePath,
                        response == null ? "empty transaction response" : response.getMessage());
            }

            Receipt receipt = response.getReceipt();
            if (receipt == null || receipt.getErrorCode() != StatusCode.SUCCESS) {
                return ChainWriteResult.failed(
                        resourcePath,
                        receipt == null ? "empty transaction receipt" : receipt.getMessage());
            }
            return ChainWriteResult.success(resourcePath, receipt.getHash());
        } catch (Exception e) {
            return ChainWriteResult.failed(resourcePath, messageOf(e));
        }
    }

    private List<ChainWriteResult> writeMockRecord(
            VerificationRecord record, List<String> targets) {
        List<ChainWriteResult> results = new ArrayList<>();
        for (String resourcePath : targets) {
            String txHash =
                    "0x"
                            + HashUtils.sha256Hex(
                                    resourcePath + ":" + record.recordId + ":" + record.resultHash);
            results.add(ChainWriteResult.success(resourcePath, txHash));
        }
        return results;
    }

    private List<ChainWriteResult> failedResults(List<String> targets, String message) {
        List<ChainWriteResult> results = new ArrayList<>();
        for (String target : targets) {
            results.add(ChainWriteResult.failed(target, message));
        }
        return results;
    }

    private String messageOf(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}