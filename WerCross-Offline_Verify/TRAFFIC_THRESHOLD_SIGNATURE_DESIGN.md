# 交通大模型门限签名实现设计

## 1. 验证节点是什么

这里的“验证节点”不是 WeCross 前端用户，也不一定是 Fabric/BCOS 共识节点。

在交通大模型场景中，验证节点应理解为一个独立机构部署的后端服务实例，例如：

```text
市交管局A signer 服务
市交管局B signer 服务
区交通局A signer 服务
区交通局B signer 服务
第三方审计机构 signer 服务
```

每个 signer 服务运行在对应机构自己的服务器或内网中，持有该机构自己的签名私钥或门限签名私钥碎片。前端页面只展示任务状态，不保存私钥，也不执行签名。

## 2. 推荐第一阶段：阈值多签，不直接做真 TSS

你当前 `ThresholdSignatureProtocol` 是教学模拟：它用 Shamir 恢复完整私钥，然后用普通 ECDSA 签名。这个流程不能直接用于生产，因为聚合者一旦恢复完整私钥，就破坏了“私钥永不重构”的安全目标。

第一阶段建议实现“阈值多签”：

```text
N 个授权验证机构中，至少 T 个机构分别签名
Fabric2 链码或后端验证至少 T 个合法签名
```

这种方式不是单一聚合签名 `sigma`，而是：

```text
sigma_set = [sig_交通局1, sig_交通局3, sig_交通局5, ...]
```

它更容易落地，也更适合 Fabric chaincode 验证。

## 3. 完整业务流程

```text
1. 公交集团在链下接收交通数据
2. 公交集团构建 MHT，得到 Merkle Root
3. 公交集团基于 Root 生成零知识证明 pi
4. 业务后端通过 WeCross 写入 BCOS3：
   - root
   - proofHash
   - verifyingKeyHash
   - datasetId
   - modelId
   - bcos3TxHash

5. 市交通委或调度后端创建验证任务
6. 多个交通局 signer 服务通过 API 拉取任务或接收推送
7. 每个 signer 服务通过 WeCross 读取 BCOS3 上的 root/proofHash
8. 每个 signer 服务在本地验证零知识证明 pi
9. 验证通过后，signer 服务用自己的机构私钥签名 messageHash
10. 聚合服务收集至少 T 个合法机构签名
11. 聚合服务通过 WeCross 写入 Fabric2
12. Fabric2 链码验证至少 T 个签名来自授权机构
13. 若验证通过，Fabric2 记录该数据/模型训练证明有效
```

## 4. 建议签名消息

所有 signer 必须签同一个确定性消息，建议不要只签 Root，而是签完整上下文哈希：

```text
messageHash = SHA256(
  taskId
  + sourceChain = traffic.bcos30.VerificationStore
  + targetChain = traffic.fabric20.VerificationStore
  + bcos3TxHash
  + datasetId
  + modelId
  + merkleRoot
  + proofHash
  + verifyingKeyHash
  + expireAt
)
```

这样可以避免签名被拿到其他数据集、模型或链上记录中重放。

## 5. 需要新增的服务

### 5.1 验证任务服务

由你的业务后端提供：

```text
POST /api/verification-tasks
GET  /api/verification-tasks/{taskId}
POST /api/verification-tasks/{taskId}/signatures
```

它负责：

```text
创建任务
记录 BCOS3 上的 Root/pi 信息
收集各 signer 的签名
检查签名机构是否授权
判断是否满足阈值 T
通过 WeCross 写入 Fabric2
```

### 5.2 机构 signer 服务

每个交通局单独部署：

```text
GET  /signer/tasks/pending
POST /signer/tasks/{taskId}/approve
POST /signer/tasks/{taskId}/reject
```

signer 服务负责：

```text
读取任务
通过 WeCross 或后端读取 BCOS3 存证
本地验证 Groth16 proof
签名 messageHash
把签名返回给任务服务
```

## 6. Fabric2 链码验证逻辑

Fabric2 链码保存授权验证机构公钥列表：

```json
{
  "validatorId": "traffic-bureau-a",
  "publicKey": "...",
  "enabled": true
}
```

链码接收：

```json
{
  "taskId": "...",
  "messageHash": "...",
  "threshold": 5,
  "signatures": [
    {
      "validatorId": "traffic-bureau-a",
      "signature": "..."
    }
  ]
}
```

链码验证：

```text
1. 签名数量是否 >= threshold
2. validatorId 是否在授权名单中
3. 是否有重复 validatorId
4. 每个签名是否能用对应公钥验证 messageHash
5. bcos3TxHash / merkleRoot / proofHash 是否已记录
```

验证通过后写入：

```text
taskId -> VALID
```

## 7. 第二阶段：真正门限签名 TSS

如果必须得到单个聚合签名 `sigma`，需要引入成熟 TSS 协议，而不是恢复完整私钥。

可选方向：

```text
BLS threshold signature
FROST/Schnorr threshold signature
门限 ECDSA 协议
```

真实 TSS 流程是：

```text
各 signer 服务只生成 signature share
聚合器只合成最终 sigma
聚合器永远不能恢复完整私钥
Fabric2 只验证 sigma 和 group public key
```

这个阶段会涉及 DKG、密钥轮换、share 备份、恶意节点识别和链码验证能力，建议在第一阶段阈值多签跑通后再做。

