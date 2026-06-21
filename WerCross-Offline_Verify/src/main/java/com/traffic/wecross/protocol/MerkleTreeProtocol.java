package com.traffic.wecross.protocol;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

public class MerkleTreeProtocol {
    private List<List<byte[]>> treeLayers;
    private MessageDigest digest;

    public MerkleTreeProtocol() throws Exception {
        digest = MessageDigest.getInstance("SHA-256");
    }

    public static class MerklePath {
        public List<byte[]> siblings = new ArrayList<>();
        public List<Boolean> isLeftNodeList = new ArrayList<>();
    }

    public byte[] buildTree(List<byte[]> dataBlocks) {
        System.out.println(">> [Merkle 模块] 开始构建底层数据哈希树...");
        treeLayers = new ArrayList<>();
        List<byte[]> currentLayer = new ArrayList<>();

        for (byte[] block : dataBlocks) {
            currentLayer.add(digest.digest(block));
        }
        treeLayers.add(currentLayer);

        while (currentLayer.size() > 1) {
            List<byte[]> nextLayer = new ArrayList<>();
            for (int i = 0; i < currentLayer.size(); i += 2) {
                byte[] left = currentLayer.get(i);
                byte[] right = (i + 1 < currentLayer.size()) ? currentLayer.get(i + 1) : left;
                nextLayer.add(hashConcat(left, right));
            }
            treeLayers.add(nextLayer);
            currentLayer = nextLayer;
        }
        byte[] root = currentLayer.get(0);
        System.out.println(">> [Merkle 模块] 构建完成！Root: 0x" + bytesToHex(root));
        return root;
    }

    public MerklePath generateProof(int dataIndex, byte[] sampleData) {
        System.out.println("\n>> [Merkle 模块] 正在为样本 [" + new String(sampleData) + "] 提取验证路径 (Proof)...");
        MerklePath path = new MerklePath();
        int currentIndex = dataIndex;

        for (int i = 0; i < treeLayers.size() - 1; i++) {
            List<byte[]> layer = treeLayers.get(i);
            boolean isLeftNode = (currentIndex % 2 == 0);
            int siblingIndex = isLeftNode ? (currentIndex + 1) : (currentIndex - 1);
            if (siblingIndex >= layer.size()) siblingIndex = currentIndex;

            path.siblings.add(layer.get(siblingIndex));
            path.isLeftNodeList.add(isLeftNode);

            String position = isLeftNode ? "左侧" : "右侧";
            System.out.println("   ├─ 路径层级 " + i + ": 提取兄弟节点 " + bytesToHex(path.siblings.get(i)).substring(0, 16) + "... (" + position + ")");
            currentIndex /= 2;
        }
        return path;
    }

    public boolean verifyProof(byte[] root, byte[] data, MerklePath path) {
        System.out.println(">> [Merkle 模块] 验证方开始依靠部分路径快速追溯 Root...");
        byte[] currentHash = digest.digest(data);

        for (int i = 0; i < path.siblings.size(); i++) {
            byte[] sibling = path.siblings.get(i);
            if (path.isLeftNodeList.get(i)) {
                currentHash = hashConcat(currentHash, sibling);
            } else {
                currentHash = hashConcat(sibling, currentHash);
            }
        }
        boolean isValid = MessageDigest.isEqual(currentHash, root);
        System.out.println("   └─ 追溯结果: " + (isValid ? "✅ 路径匹配，数据真实存在" : "❌ 路径断裂，数据被篡改"));
        return isValid;
    }

    private byte[] hashConcat(byte[] left, byte[] right) {
        byte[] combined = new byte[left.length + right.length];
        System.arraycopy(left, 0, combined, 0, left.length);
        System.arraycopy(right, 0, combined, left.length, right.length);
        return digest.digest(combined);
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
