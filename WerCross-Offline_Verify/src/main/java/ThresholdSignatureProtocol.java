import java.math.BigInteger;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.util.*;

public class ThresholdSignatureProtocol {
    private static final BigInteger PRIME = new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", 16);
    private final int t;
    private final SecureRandom random = new SecureRandom();

    private KeyPair globalKeyPair;
    private BigInteger realPrivateKey;
    private Map<Integer, BigInteger> distributedShares;

    public ThresholdSignatureProtocol(int n, int t) throws Exception {
        this.t = t;
        System.out.println("\n>> [门限模块] 执行系统初始化与 DKG...");
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
        keyGen.initialize(new ECGenParameterSpec("secp256r1"));
        globalKeyPair = keyGen.generateKeyPair();

        // 生成并截取落在有限域内的真实私钥标量
        realPrivateKey = new BigInteger(1, globalKeyPair.getPrivate().getEncoded()).mod(PRIME);

        // === 新增：输出原始全局私钥 ===
        System.out.println("   ├─ [对比点 1 - 生成] 原始全局私钥标量值 (Hex): \n   │  " + realPrivateKey.toString(16));

        generateShares(n);
        System.out.println("   └─ [门限模块] 全局私钥已切分为 " + n + " 份，分发给联邦节点。");
    }

    private void generateShares(int n) {
        BigInteger[] coefficients = new BigInteger[t];
        coefficients[0] = realPrivateKey; // 常数项就是真实私钥
        for (int i = 1; i < t; i++) {
            coefficients[i] = new BigInteger(256, random).mod(PRIME);
        }

        distributedShares = new HashMap<>();
        for (int i = 1; i <= n; i++) {
            BigInteger x = BigInteger.valueOf(i);
            BigInteger y = BigInteger.ZERO;
            for (int j = 0; j < t; j++) {
                BigInteger term = coefficients[j].multiply(x.pow(j)).mod(PRIME);
                y = y.add(term).mod(PRIME);
            }
            distributedShares.put(i, y);
        }
    }

    public BigInteger getShareForNode(int nodeId) {
        return distributedShares.get(nodeId);
    }

    public byte[] aggregateAndSign(Map<Integer, BigInteger> collectedShares, byte[] dataToSign) throws Exception {
        System.out.println("\n>> [门限模块] 开始执行碎片聚合与 ECDSA 签名...");
        if (collectedShares.size() < t) {
            System.out.println("❌ 碎片数量不足，无法聚合！");
            return null;
        }

        BigInteger secret = BigInteger.ZERO;
        List<Integer> xValues = new ArrayList<>(collectedShares.keySet());

        // 拉格朗日插值法核心计算
        for (int i = 0; i < t; i++) {
            int x_i = xValues.get(i);
            BigInteger y_i = collectedShares.get(x_i);
            BigInteger numerator = BigInteger.ONE;
            BigInteger denominator = BigInteger.ONE;

            for (int j = 0; j < t; j++) {
                if (i != j) {
                    int x_j = xValues.get(j);
                    numerator = numerator.multiply(BigInteger.valueOf(-x_j)).mod(PRIME);
                    denominator = denominator.multiply(BigInteger.valueOf(x_i - x_j)).mod(PRIME);
                }
            }
            BigInteger lagrangeBasis = numerator.multiply(denominator.modInverse(PRIME)).mod(PRIME);
            secret = secret.add(y_i.multiply(lagrangeBasis)).mod(PRIME);
        }

        // 调整负数模运算结果
        BigInteger recovered = secret.compareTo(BigInteger.ZERO) < 0 ? secret.add(PRIME) : secret;

        // === 新增：输出恢复出的私钥 ===
        System.out.println("   ├─ [对比点 1 - 恢复] 拉格朗日插值恢复出的私钥标量 (Hex): \n   │  " + recovered.toString(16));

        if (!recovered.equals(realPrivateKey)) {
            System.out.println("   └─ ❌ 密钥恢复比对失败！");
            return null;
        } else {
            System.out.println("   ├─ ✅ 密钥恢复比对成功！数学原理生效。");
        }

        // 使用底层公私钥对目标数据（通常是 Merkle Root）进行真正签名
        Signature ecdsa = Signature.getInstance("SHA256withECDSA");
        ecdsa.initSign(globalKeyPair.getPrivate());
        ecdsa.update(dataToSign);
        byte[] signature = ecdsa.sign();

        // === 新增：输出生成的签名 ===
        System.out.println("   └─ [对比点 2 - 生成] 生成的聚合 ECDSA 签名值 (Hex): \n      " + MerkleTreeProtocol.bytesToHex(signature));

        return signature;
    }

    public boolean verifyFinalSignature(byte[] signature, byte[] data) throws Exception {
        System.out.println("\n>> [智能合约层] 接收跨链请求，验证最终聚合签名...");

        // === 新增：输出传入合约待验证的签名 ===
        System.out.println("   ├─ [对比点 2 - 验证] 智能合约接收到的待验证签名值 (Hex): \n   │  " + MerkleTreeProtocol.bytesToHex(signature));

        Signature verifier = Signature.getInstance("SHA256withECDSA");
        verifier.initVerify(globalKeyPair.getPublic());
        verifier.update(data); // 这里的 data 必须和签名时的 data (Merkle Root) 一致
        boolean isValid = verifier.verify(signature);

        System.out.println("   └─ 链上验证结果: " + (isValid ? "✅ 签名合法，允许数据/模型上链" : "❌ 签名伪造，交易回滚"));
        return isValid;
    }
}