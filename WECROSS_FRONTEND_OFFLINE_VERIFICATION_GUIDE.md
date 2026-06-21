# WeCross 前端与链下验证 API 开发、接入及复现手册

## 1. 文档目的

本文记录本项目已经完成的 WeCross 前端扩展、链下验证 API 开发、WeCross Router 鉴权接入、链上写入适配、测试数据和启动停止方式。按本文可恢复当前目录、重新构建并运行系统、验证三个功能及排查常见错误。

> 当前能力边界：Merkle Tree 是实际 SHA-256 计算与 Merkle Path 校验；Groth16 ZKP 和门限签名是教学/实验模拟，不是生产级 ZKP 电路或分布式门限签名；`mock` 模式的链上交易哈希也是模拟值。

## 2. 项目结构与来源

统一目录：

```text
C:\Users\lenovo\Desktop\交通大模型项目\WeCross
├─ WeCross/                    # Router 源码和 dist
├─ WeCross-Account-Manager/    # 账户管理服务
├─ WeCross-Console/            # 官方控制台
├─ WeCross-Java-SDK-src/       # SDK 源码参考
├─ WeCross-WebApp/             # Vue 2 前端
├─ WerCross-Offline_Verify/    # Spring Boot 链下验证 API
├─ start-local.ps1             # 统一启动
└─ stop-local.ps1              # 统一停止
```

注意实际目录名是 `WerCross-Offline_Verify`，脚本也使用此拼写。

官方仓库：

```powershell
git clone https://github.com/WeBankBlockchain/WeCross.git
git clone https://github.com/WeBankBlockchain/WeCross-WebApp.git
git clone https://github.com/WeBankBlockchain/WeCross-Account-Manager.git
```

`WerCross-Offline_Verify` 是在原链下验证 Java 代码基础上新增的 API 项目，不属于官方仓库。

## 3. 架构

### 3.1 全本地

```text
浏览器 -> WebApp :9528
              ├─ /api/verification/* -> Verification API :8088
              └─ 其他 API -> Router :8250 -> Account Manager :8340 -> 各区块链
```

### 3.2 当前推荐的混合部署

```text
本地 WebApp :9528
├─ 链下验证请求 -> 本地 Verification API 127.0.0.1:8088
└─ WeCross 请求 -> 云端 Router <云服务器IP>:8250
                         ├─ 云端账户服务
                         └─ BCOS/Fabric/ChainMaker 等资源

本地 Verification API -> 同一个云端 Router（credential 校验和 SDK 写链）
```

关键原则：

- `/api/verification` 代理到本地 8088。
- 其他前端请求代理到当前云端 Router。
- Verification API 必须连接与 WebApp 登录相同的 Router。
- 本地 Router 和云端 Router 的 credential 不能混用。

## 4. 前端完成的修改

### 4.1 导航与路由

修改 `WeCross-WebApp/src/router/index.js`，新增 `/verification/index`，菜单名“链下验证”，管理员和普通用户均可访问。

### 4.2 API 封装

新增 `WeCross-WebApp/src/api/verification.js`：

| 方法 | 接口 | 作用 |
|---|---|---|
| `getVerificationHealth` | `GET /api/verification/health` | 健康检查 |
| `verifyMerkle` | `POST /api/verification/merkle` | Merkle 生成和验证 |
| `verifyGroth16` | `POST /api/verification/groth16` | 模拟 ZKP |
| `verifyThresholdSignature` | `POST /api/verification/threshold-signature` | 模拟门限签名 |
| `verifyAll` | `POST /api/verification/full` | 组合接口，页面暂未使用 |

现有 Axios 拦截器会给非健康请求添加 WeCross credential：

```http
Authorization: <wecross-token>
```

### 4.3 链下验证页面

新增 `WeCross-WebApp/src/views/verification/index.vue`，包含：

- Merkle Root 生成、零知识证明 ZKP、门限阈值签名三个页签。
- 业务标识、运行按钮、重置、验证服务状态。
- Merkle 手动输入和文件上传两种来源。
- 抽样索引范围校验。
- 从历史记录选择或粘贴 64 位十六进制 Merkle Root。
- 总节点数、签名阈值、参与节点编号校验。
- “写入区块链”开关。
- 调用 `/sys/listResources` 获取 WeCross 资源并多选，不再手输资源路径。
- 展示记录 ID、算法、结果、各类哈希和完整 JSON。
- 最近记录存入 `localStorage` 的 `wecross-verification-history`。

### 4.4 文件分块规则与测试数据

- `txt/csv/json/xml/log/md`：每个非空行是一个 Merkle 叶子。
- CSV 表头也属于一个叶子。
- 其他文件：按 64 KiB 分块，每块 Base64 编码后发送。
- 浏览器端文件上限 10 MiB。

测试文件：`WeCross-WebApp/test-data/traffic-data-1000.csv`。

它包含 1 行表头和 1000 条交通记录，共产生 1001 个叶子。

### 4.5 开发代理

`WeCross-WebApp/vue.config.js` 当前混合配置：

```js
proxy: {
  '/api/verification': {
    target: 'http://127.0.0.1:8088',
    changeOrigin: true
  },
  '/': {
    target: 'http://<云服务器IP>:8250',
    changeOrigin: true
  }
}
```

更换云服务器时修改第二个地址。第一个地址保持本地，除非 API 也部署在云端。

`devServer.proxy` 只在 `npm run dev` 生效；生产 `dist` 部署到 Nginx 后必须配置等价反向代理。

## 5. 链下验证 API 完成的修改

核心目录：

```text
WerCross-Offline_Verify/src/main/java/com/traffic/wecross/api/
├─ VerificationApiApplication.java
├─ VerificationController.java
├─ VerificationRequests.java
├─ VerificationService.java
├─ VerificationRecord.java
├─ ChainWriteResult.java
├─ HashUtils.java
├─ WeCrossGateway.java
├─ WeCrossCredentialFilter.java
└─ ApiSecurityConfig.java
```

算法层位于：

```text
WerCross-Offline_Verify/src/main/java/com/traffic/wecross/protocol/
```

API 层负责 HTTP、参数校验、结果结构化、鉴权和写链；算法层不直接依赖 Vue。

### 5.1 WeCross credential 鉴权

除健康检查和 `OPTIONS` 外都必须携带 token：

```text
浏览器 Authorization
 -> WeCrossCredentialFilter
 -> POST {Router}/auth/listAccount
 -> errorCode=0 时放行
```

这里使用通用 JSON 读取 Router 响应，而不是 SDK `AccountResponse`。原因是云端账号包含 `CHAIN_MAKER` 时，`wecross-java-sdk 1.4.0` 无法识别该子类型，会产生 `InvalidTypeIdException` 和 503。

状态含义：

- `401 Missing...`：没有 token。
- `401 Invalid or expired...`：token 无效/过期或来自另一个 Router。
- `503 Unable to verify...`：API 无法访问 Router 或 Router 异常。

### 5.2 Merkle Root

示例请求：

```json
{
  "businessId": "traffic-test-001",
  "dataBlocks": ["speed=40", "speed=35", "speed=42"],
  "sampleIndex": 1,
  "writeOnChain": false,
  "targetChains": []
}
```

规则：每块 UTF-8 数据先 SHA-256；父节点为相邻哈希拼接后 SHA-256；奇数节点复制最后一个；为抽样叶子生成兄弟节点和左右方向；根据 Merkle Path 重算根。

详情字段：`merkleRoot`、`sampleIndex`、`sampleData`、`siblings`、`isLeftNodeList`、`leafCount`。

### 5.3 模拟 Groth16 ZKP

示例请求：

```json
{
  "businessId": "traffic-test-001",
  "merkleRoot": "64位十六进制Merkle Root",
  "writeOnChain": false,
  "targetChains": []
}
```

后端把 Root 映射为有限域元素 `x`，用 JPBC Type A pairing 验证：

```text
e(piA, piB) = e(alpha, beta) * e(G1^x, gamma) * e(piC, delta)
```

公开：Merkle Root、可推导的 `x`、群参数/生成元、`alpha/beta/gamma/delta`、`piA/piB/piC`。

内部秘密或临时值：setup 标量 `a1/b1/c1/d1` 和证明阶段标量 `a/b/c`。

返回 `merkleRoot`、`piA/piB/piC`、`proofHash = SHA-256(piA|piB|piC)`。

局限：没有 R1CS/QAP/业务电路，没有把原始数据和 Merkle Path 作为私密 witness；Prover/Verifier 同进程且 setup 每次生成。因此它只演示 pairing 等式，不证明“知道属于该 Root 的私密叶子”。

### 5.4 模拟门限阈值签名

示例请求：

```json
{
  "businessId": "traffic-test-001",
  "merkleRoot": "64位十六进制Merkle Root",
  "totalNodes": 5,
  "threshold": 3,
  "participantIds": [1, 2, 3],
  "writeOnChain": false,
  "targetChains": []
}
```

流程：后端中心化生成 EC 密钥；Shamir 为模拟节点生成份额；收集指定份额；达到阈值后拉格朗日插值并检查；使用全局 EC 私钥执行 `SHA256withECDSA`；公钥验证结果。

返回 `totalNodes`、`threshold`、`participants`、`signature`、`signatureHash`。

局限：份额都在同一后端，没有 DKG、独立节点和部分签名；最终签名仍由全局私钥产生，不是真正阈值 ECDSA。真实系统不需要多个用户同时登录前端，而应由独立签名节点服务持有份额并返回 partial signature。

### 5.5 通用结果字段

| 字段 | 含义 |
|---|---|
| `recordId` | 本次验证 UUID |
| `businessId` | 业务标识 |
| `verifyType` | 三种验证类型之一 |
| `passed` | 是否通过 |
| `dataHash` | 当前实现为 Merkle Root |
| `detailHash` | `SHA-256(detail JSON)` |
| `resultHash` | 对业务标识、类型、状态、数据哈希、详情哈希组合求 SHA-256 |
| `algorithm` | 算法描述 |
| `timestamp` | Unix 毫秒时间戳 |
| `detail` | 算法详情 |
| `chainResults` | 每个目标资源的写入结果 |

`resultHash` 材料：

```text
businessId + ":" + verifyType + ":" + passed + ":" + dataHash + ":" + detailHash
```

## 6. WeCross 资源和写链

资源路径不是裸合约地址，通常是：

```text
zone.chain.resource
traffic.bcos30.VerificationStore
traffic.fabric20.VerificationStore
```

流程：先在链上部署合约/chaincode，再由 WeCross Stub 注册或识别为资源，最后应用用统一资源路径调用。

配置文件 `application.properties`：

```properties
server.port=8088
wecross.mode=${WECROSS_MODE:mock}
wecross.contract-method=${WECROSS_CONTRACT_METHOD:saveRecord}
wecross.default-targets=${WECROSS_DEFAULT_TARGETS:traffic.bcos30.VerificationStore,traffic.fabric20.VerificationStore}
wecross.router-url=${WECROSS_ROUTER_URL:http://127.0.0.1:8250}
```

- `mock`：不发送交易，只生成模拟 txHash。
- `sdk`：调用 WeCross Java SDK 执行资源的 `saveRecord(recordJson)`。

使用 `sdk` 前必须：

1. 在 BCOS/Fabric 部署 `VerificationStore` 合约/chaincode。
2. 实现匹配的 `saveRecord` 方法。
3. 注册为 WeCross 资源并能被 `/sys/listResources` 查到。
4. 为用户配置链账户和权限。
5. 设置真实资源路径。

当前仓库还没有完成生产合约/chaincode 部署，不能把 mock txHash 当成链上交易证明。

## 7. 环境要求

- Windows 10/11、PowerShell 5.1+。
- JDK 8。
- Maven 3.6+。
- Node.js 16.20.2 和 npm。
- 可用的 WeCross Router/Account Manager。

检查：

```powershell
java -version
mvn -version
node -v
npm -v
```

## 8. 从源码构建

### 8.1 Account Manager

```powershell
cd "C:\Users\lenovo\Desktop\交通大模型项目\WeCross\WeCross-Account-Manager"
.\gradlew.bat assemble
```

成功后应有 `dist/apps`、`dist/lib`、`dist/conf`。

### 8.2 Router

按官方流程生成 Router。统一脚本当前预期目录：

```text
WeCross/dist/routers/127.0.0.1-8250-25500
```

不同则修改 `start-local.ps1` 的 `$routerDir`。本地配置通常为 RPC `127.0.0.1:8250`，Account Manager `127.0.0.1:8340`。云端远程访问通常监听 `0.0.0.0`，并在安全组开放 8250；生产应增加 TLS、来源限制和反向代理。

### 8.3 Verification API

```powershell
cd "C:\Users\lenovo\Desktop\交通大模型项目\WeCross\WerCross-Offline_Verify"
mvn clean package
```

预期 `BUILD SUCCESS`，输出 `target/transportation_model-1.0-SNAPSHOT.jar`。JPBC 使用项目 `lib` 的 `systemPath` 会产生 Maven 警告，但当前可编译。

### 8.4 WebApp

```powershell
cd "C:\Users\lenovo\Desktop\交通大模型项目\WeCross\WeCross-WebApp"
npm install
npm run lint
npm run build:prod
```

生产输出在 `WeCross-WebApp/dist`。

## 9. 启动步骤

### 9.1 云端 Router + 本地 WebApp/API（推荐）

确认 `vue.config.js` 中验证 API 为本地，Router 为云端，然后运行：

```powershell
cd "C:\Users\lenovo\Desktop\交通大模型项目\WeCross"

powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\start-local.ps1 `
  -Mode mock `
  -Topology hybrid `
  -CloudRouter "<云服务器IP>:8250"
```

真实 SDK 写链改为：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\start-local.ps1 `
  -Mode sdk `
  -Topology hybrid `
  -CloudRouter "<云服务器IP>:8250"
```

脚本会跳过本地 Router/Account Manager，设置 SDK Router 和 `WECROSS_ROUTER_URL`，启动 API 8088 和 WebApp 9528。

访问：`http://localhost:9528`。

### 9.2 全部本地

先把 `vue.config.js` 的 Router target 改为 `http://127.0.0.1:8250`，然后：

```powershell
cd "C:\Users\lenovo\Desktop\交通大模型项目\WeCross"
powershell -NoProfile -ExecutionPolicy Bypass -File .\start-local.ps1 -Mode mock -Topology local
```

端口：Account Manager 8340、Router 8250、API 8088、WebApp 9528。

### 9.3 手动启动 API 和前端

终端 1：

```powershell
cd "C:\Users\lenovo\Desktop\交通大模型项目\WeCross\WerCross-Offline_Verify"
$env:WECROSS_MODE = "mock"
$env:WECROSS_ROUTER_URL = "http://<云服务器IP>:8250"
mvn spring-boot:run
```

终端 2：

```powershell
cd "C:\Users\lenovo\Desktop\交通大模型项目\WeCross\WeCross-WebApp"
npm run dev -- --no-open
```

## 10. 停止步骤

```powershell
cd "C:\Users\lenovo\Desktop\交通大模型项目\WeCross"
powershell -NoProfile -ExecutionPolicy Bypass -File .\stop-local.ps1
```

停止脚本按端口结束本机 9528、8088、8250、8340。混合模式不会停止云端 Router，但会停止本机恰好占用 8250/8340 的进程。

检查：

```powershell
Get-NetTCPConnection -State Listen |
  Where-Object { $_.LocalPort -in 9528,8088,8250,8340 } |
  Select-Object LocalAddress,LocalPort,OwningProcess
```

## 11. 启动后验证

健康检查（绕过系统代理）：

```powershell
curl.exe --noproxy "*" http://127.0.0.1:8088/api/verification/health
```

预期：

```json
{"status":"UP","service":"transportation-verification-service"}
```

前端检查：

```powershell
curl.exe --noproxy "*" -I http://127.0.0.1:9528
```

页面操作：登录当前 Router 的账号；进入“链下验证”；先生成 Merkle Root；再在 ZKP 和门限签名页选择该 Root；测试阶段关闭“写入区块链”，确认三个结果通过后再配置真实资源。

切换 Router 后清理旧 token：

```javascript
localStorage.removeItem('wecross-token')
localStorage.removeItem('wecross-username')
location.href = '/#/login'
```

## 12. 已完成的端到端测试

使用 `traffic-data-1000.csv`：

```text
叶子数：1001
抽样索引：500
Merkle Root：cdb034ea92f479ac02d2074089f31be9e129b833f35db0f52c765adadfcc4632
Merkle passed：true
ZKP passed：true
Threshold passed：true
三个功能 Root 相同：true
```

文件内容、换行、编码、分块规则或是否包含表头变化都会改变 Root。

## 13. 常见错误

### 13.1 curl 明明访问 8088，却显示连接 33210/7890

这是 `HTTP_PROXY/HTTPS_PROXY`。检查：

```powershell
Get-ChildItem Env: | Where-Object Name -Match 'proxy'
```

测试 localhost 时使用 `curl.exe --noproxy "*" ...`。

### 13.2 无法连接 127.0.0.1:8088

API 没启动：

```powershell
Get-NetTCPConnection -LocalPort 8088 -State Listen
Get-Content .\WerCross-Offline_Verify\verification-api.log -Tail 100
```

### 13.3 401 Invalid or expired credential

token 过期、来自另一个 Router，或切换拓扑后未重新登录。清理 localStorage 并重新登录。

### 13.4 503 Unable to verify credential with Router

检查 `WECROSS_ROUTER_URL`、云端 8250 安全组、网络和 API 日志。下面即使返回 404 也表示端口可达：

```powershell
curl.exe --noproxy "*" http://<云服务器IP>:8250/
```

项目已修复旧 SDK 解析 `CHAIN_MAKER` 账户导致的 503。

### 13.5 资源下拉为空

检查链是否接入 Router、合约/chaincode 是否注册、当前账号是否绑定链账户和拥有权限，以及 Network 中 `/sys/listResources` 的响应。

### 13.6 验证成功但链上没有交易

`-Mode mock` 不写链；`sdk` 还要求真实 `VerificationStore.saveRecord`、注册资源、链账户和权限。

### 13.7 修改 vue.config.js 不生效

开发代理仅在启动时读取，停止后重新启动前端。

## 14. 日志

```text
WeCross-WebApp/wecross-webapp-dev.log
WerCross-Offline_Verify/verification-api.log
WeCross-Account-Manager/dist/start.out
WeCross-Account-Manager/dist/start.err
WeCross/dist/routers/127.0.0.1-8250-25500/start.out
WeCross/dist/routers/127.0.0.1-8250-25500/start.err
```

实时查看：

```powershell
Get-Content .\WerCross-Offline_Verify\verification-api.log -Wait -Tail 100
```

## 15. 生产化待办

1. 使用成熟框架建立真实 ZKP 电路，证明私密叶子/路径与公开 Root 的关系。
2. 分离 proving key、verification key 和 setup 生命周期。
3. 以独立节点实现 DKG、份额存储、partial signature 和聚合。
4. 为 BCOS/Fabric 实现并部署 `VerificationStore` 合约/chaincode。
5. 定义链上记录、事件、查询、幂等、重试和多链部分成功补偿。
6. 用 Nginx/TLS 部署前端及反向代理。
7. 升级/扩展 SDK 原生支持 Router 的全部 Stub/账户类型。
8. 为 API 增加数据库、审计、限流、自动化测试和密钥管理。

## 16. 最短日常操作

启动：

```powershell
cd "C:\Users\lenovo\Desktop\交通大模型项目\WeCross"
powershell -NoProfile -ExecutionPolicy Bypass -File .\start-local.ps1 -Mode mock -Topology hybrid -CloudRouter "<云服务器IP>:8250"
```

访问：`http://localhost:9528`。

检查：

```powershell
curl.exe --noproxy "*" http://127.0.0.1:8088/api/verification/health
```

停止：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\stop-local.ps1
```