package com.traffic.wecross.api;

public class ChainWriteResult {
    public String resourcePath;
    public String txHash;
    public String status;
    public String message;

    public static ChainWriteResult success(String resourcePath, String txHash) {
        ChainWriteResult result = new ChainWriteResult();
        result.resourcePath = resourcePath;
        result.txHash = txHash;
        result.status = "SUCCESS";
        result.message = "record has been accepted by WeCross gateway";
        return result;
    }

    public static ChainWriteResult failed(String resourcePath, String message) {
        ChainWriteResult result = new ChainWriteResult();
        result.resourcePath = resourcePath;
        result.status = "FAILED";
        result.message = message;
        return result;
    }
}
