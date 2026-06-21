import java.math.BigInteger;
import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);

        // ==========================================
        // 0. 网络初始化
        // ==========================================
        List<TrafficNode> allNodes = Arrays.asList(
                new TrafficNode(1, "市交管局_A", "交管"), new TrafficNode(2, "市交管局_B", "交管"),
                new TrafficNode(3, "市交通委_A", "交通委"), new TrafficNode(4, "市交通委_B", "交通委"),
                new TrafficNode(5, "公交集团_A", "公交"), new TrafficNode(6, "公交集团_B", "公交"), new TrafficNode(7, "公交集团_C", "公交"),
                new TrafficNode(8, "科技公司_A", "科技"), new TrafficNode(9, "科技公司_B", "科技"), new TrafficNode(10, "科技公司_C", "科技")
        );
        int N = allNodes.size();
        System.out.print("请设定门限多签的阈值 T (建议 5-8): ");
        int T = scanner.nextInt();

        // 实例化三大核心协议组件
        ThresholdSignatureProtocol tssProtocol = new ThresholdSignatureProtocol(N, T);
        MerkleTreeProtocol merkleProtocol = new MerkleTreeProtocol();
        Groth16Protocol zkProtocol = new Groth16Protocol();

        // ==========================================
        // 1. 数据拥有者 (公交集团) 处理数据
        // ==========================================
        List<byte[]> localData = Arrays.asList(
                "Bus_101_Speed_40".getBytes(), "Bus_102_Speed_35".getBytes(),
                "Bus_103_Speed_42".getBytes(), "Bus_104_Speed_38".getBytes()
        );
        byte[] merkleRoot = merkleProtocol.buildTree(localData);

        int sampleIndex = 1;
        byte[] sampleData = localData.get(sampleIndex);
        MerkleTreeProtocol.MerklePath path = merkleProtocol.generateProof(sampleIndex, sampleData);
        Groth16Protocol.Proof proof = zkProtocol.generateProof(merkleRoot);

        // ==========================================
        // 2. 委员会交叉验证
        // ==========================================
        System.out.println("\n============== 阶段 3: 委员会验证 ==============");
        System.out.println("请输入参与验证的委员会节点 ID (例如 1 3 8 9 10): ");
        scanner.nextLine();
        String[] selectedIds = scanner.nextLine().trim().split("\\s+");

        Map<Integer, BigInteger> collectedShares = new HashMap<>();

        for (String idStr : selectedIds) {
            int nid = Integer.parseInt(idStr);
            TrafficNode validator = allNodes.get(nid - 1);
            System.out.println("\n[" + validator.name + "] 开始验证 >>>");

            boolean isPathValid = merkleProtocol.verifyProof(merkleRoot, sampleData, path);
            boolean isZkValid = zkProtocol.verify(proof, merkleRoot);

            if (isPathValid && isZkValid) {
                collectedShares.put(nid, tssProtocol.getShareForNode(nid));
                System.out.println("决议: [" + validator.name + "] 验证通过，交出私钥碎片。");
            }
        }

        // ==========================================
        // 3. 碎片聚合与上链验证
        // ==========================================
        byte[] finalSignature = tssProtocol.aggregateAndSign(collectedShares, merkleRoot);

        if (finalSignature != null) {
            tssProtocol.verifyFinalSignature(finalSignature, merkleRoot);
        }

        scanner.close();
    }
}