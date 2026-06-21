# WeCross Offline Verification

WeCross WebApp extension and Spring Boot API for traffic-data verification.

## Features

- Merkle Root generation and Merkle Path verification.
- Experimental JPBC-based Groth16-style pairing proof.
- Experimental Shamir + ECDSA threshold-signature simulation.
- WeCross credential validation and resource selection.
- Optional verification-record submission through the WeCross Java SDK.
- Windows PowerShell startup scripts for local and hybrid deployments.

## Repository Layout

```text
WeCross-WebApp/              Vue 2 management frontend
WerCross-Offline_Verify/     Spring Boot verification API
start-local.ps1              Start development services
stop-local.ps1               Stop local services
WECROSS_FRONTEND_OFFLINE_VERIFICATION_GUIDE.md
```

The WeCross Router and Account Manager remain upstream dependencies. Clone them beside these directories when using `-Topology local`.

## Quick Start

Install JDK 8, Maven, Node.js 16 and npm. For a cloud Router with a local frontend and API:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\start-local.ps1 `
  -Mode mock `
  -Topology hybrid `
  -CloudRouter "<router-ip>:8250"
```

Open `http://localhost:9528`.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\stop-local.ps1
```

See [WECROSS_FRONTEND_OFFLINE_VERIFICATION_GUIDE.md](WECROSS_FRONTEND_OFFLINE_VERIFICATION_GUIDE.md) for architecture, API schemas, build and operation instructions, troubleshooting, and limitations.

## Security Notice

The current ZKP and threshold-signature modules are research simulations, not production cryptographic implementations. `mock` mode does not submit real blockchain transactions.