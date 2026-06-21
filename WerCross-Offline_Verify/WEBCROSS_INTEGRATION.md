# WeCross integration guide

## 1. Business-backend relay mode

This mode is recommended for the first phase.

Current source layout:

```text
src/main/java/com/traffic/wecross/api       REST API, service layer and WeCross gateway
src/main/java/com/traffic/wecross/protocol  Merkle, Groth16 and threshold-signature simulation code
```

Flow:

```text
WeCross-WebApp custom page
  -> transportation-verification-service REST API
  -> Merkle / Groth16 / threshold signature verification
  -> WeCross Java SDK
  -> traffic.bcos30.VerificationStore / traffic.fabric20.VerificationStore / ...
```

The front end calls one of these APIs:

```text
POST /api/verification/merkle
POST /api/verification/groth16
POST /api/verification/threshold-signature
POST /api/verification/full
```

Example:

```json
{
  "businessId": "traffic-batch-001",
  "dataBlocks": [
    "Bus_101_Speed_40",
    "Bus_102_Speed_35",
    "Bus_103_Speed_42",
    "Bus_104_Speed_38"
  ],
  "sampleIndex": 1,
  "targetChains": [
    "traffic.bcos30.VerificationStore",
    "traffic.fabric20.VerificationStore"
  ],
  "writeOnChain": true
}
```

Threshold signature example:

```json
{
  "businessId": "traffic-batch-001",
  "dataBlocks": [
    "Bus_101_Speed_40",
    "Bus_102_Speed_35",
    "Bus_103_Speed_42",
    "Bus_104_Speed_38"
  ],
  "sampleIndex": 1,
  "totalNodes": 10,
  "threshold": 5,
  "participantIds": [1, 3, 5, 8, 10],
  "targetChains": [
    "traffic.bcos30.VerificationStore",
    "traffic.fabric20.VerificationStore"
  ],
  "writeOnChain": true
}
```

`WeCrossGateway` now contains the real WeCross Java SDK transaction call. The default remains mock mode so API and UI development do not accidentally submit transactions before contracts are deployed. Start the API with:

```bash
mvn spring-boot:run
```

The API listens on `8088`. `GET /api/verification/health` is public. All verification endpoints require the WeCross WebApp `Authorization` credential. The backend validates that credential against Router `listAccount` before running verification, then reuses the same credential when submitting the transaction. The frontend wrapper is `WeCross-WebApp/src/api/verification.js`.

After `VerificationStore` is deployed and registered on the target chains, start with real writes enabled:

```powershell
$env:WECROSS_MODE='sdk'
mvn spring-boot:run
```

The SDK connection is configured in `src/main/resources/application.toml` and defaults to `127.0.0.1:8250` with Router SSL disabled. The contract method defaults to `saveRecord` and can be changed with `WECROSS_CONTRACT_METHOD`. Default resource paths can be changed with `WECROSS_DEFAULT_TARGETS`.

The same resource method should exist on every chain:

```text
saveRecord(recordJson)
getRecord(recordId)
```

## 2. Interchain mode

Use this mode after the relay mode works.

Flow:

```text
Source-chain business contract / chaincode
  -> source-chain WeCrossHub.interchainInvoke(...)
  -> WeCross Router polls Hub
  -> target-chain VerificationStore.getRecord/saveRecord
  -> source-chain callback(...)
```

The target resource interface must match WeCross Interchain requirements:

```text
string[] func(string[] args)
```

The callback interface must match:

```text
string[] func(bool state, string[] result)
```

Recommended resource paths:

```text
traffic.fabric14.VerificationStore
traffic.fabric20.VerificationStore
traffic.bcos20.VerificationStore
traffic.bcos30.VerificationStore
```

For four chains, avoid starting with all 12 cross-chain directions. First verify:

```text
bcos30 -> fabric20
fabric20 -> bcos30
```

Then copy the same contract/chaincode interface to the other chains.

## 3. Threshold signature design

The current Java code is a simulation. It uses Shamir shares to recover a private key inside one Java process, then signs with a normal ECDSA private key.

For a real deployment, do not ask multiple users to log in to the same Web page and click sign. Use this pattern instead:

```text
User submits verification task
  -> backend creates signing task
  -> each validator node runs an independent signer service
  -> signer service verifies Merkle/ZKP result locally
  -> signer service returns a signature share or approval signature
  -> aggregator collects at least T valid responses
  -> aggregator creates final attestation
  -> backend writes attestation hash/result to chains through WeCross
```

The front end should show validator status only:

```text
pending / approved / rejected / timed out
```

It should not hold private key shares, and it should not be the place where threshold cryptography happens.
