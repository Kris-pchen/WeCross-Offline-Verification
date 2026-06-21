package com.traffic.wecross.protocol;

import it.unisa.dia.gas.jpbc.Element;
import it.unisa.dia.gas.jpbc.Field;
import it.unisa.dia.gas.jpbc.Pairing;
import it.unisa.dia.gas.plaf.jpbc.pairing.PairingFactory;
import it.unisa.dia.gas.plaf.jpbc.pairing.a.TypeACurveGenerator;

public class Groth16Protocol {
    private Pairing pairing;
    private Field Zr, G1, G2;

    // 验证密钥 (公开给所有交通节点的参数)
    private Element alpha, beta, gamma, delta;
    private Element G1_gen, G2_gen;

    // 可信设置阶段产生的底层标量 (这在真实世界中就是需要被销毁的"剧毒废料")
    private Element a1, b1, c1, d1;

    public static class Proof {
        public Element pi_A, pi_B, pi_C;
    }

    public Groth16Protocol() {
        // 1. 初始化椭圆曲线和群
        TypeACurveGenerator pg = new TypeACurveGenerator(160, 512);
        pairing = PairingFactory.getPairing(pg.generate());
        Zr = pairing.getZr(); G1 = pairing.getG1(); G2 = pairing.getG2();
        G1_gen = G1.newRandomElement().getImmutable();
        G2_gen = G2.newRandomElement().getImmutable();

        // 2. 模拟 Trusted Setup (可信设置)
        // 生成底层真实标量
        a1 = Zr.newRandomElement().getImmutable();
        b1 = Zr.newRandomElement().getImmutable();
        c1 = Zr.newRandomElement().getImmutable();
        d1 = Zr.newRandomElement().getImmutable();

        // 基于底层标量生成公开的验证参数
        alpha = G1_gen.powZn(a1).getImmutable();
        beta = G2_gen.powZn(b1).getImmutable();
        gamma = G2_gen.powZn(c1).getImmutable();
        delta = G2_gen.powZn(d1).getImmutable();
    }

    public Proof generateProof(byte[] merkleRoot) {
        System.out.println("\n>> [ZK 模块] 机密计算环境开始生成 Groth16 证明...");

        // 将 Merkle Root 映射为有限域 Zr 上的标量 x
        Element x = Zr.newElement().setFromHash(merkleRoot, 0, merkleRoot.length).getImmutable();

        // 引入证明阶段的随机化扰动因子 (保证零知识性，不泄露真实数据)
        Element c = Zr.newRandomElement().getImmutable();
        Element b = Zr.newRandomElement().getImmutable();

        // 核心数学求解: 必须使用与验证密钥同源的 a1, b1, c1, d1
        // 方程: a*b = a1*b1 + x*c1 + c*d1
        Element sum = a1.mul(b1).add(x.mul(c1)).add(c.mul(d1));
        Element a = sum.div(b).getImmutable();

        // 将求解出的 a, b, c 标量映射到椭圆曲线上，生成证明点
        Proof proof = new Proof();
        proof.pi_A = G1_gen.powZn(a).getImmutable();
        proof.pi_B = G2_gen.powZn(b).getImmutable();
        proof.pi_C = G1_gen.powZn(c).getImmutable();

        System.out.println("   ├─ 生成 pi_A (G1): " + proof.pi_A.toString().substring(0, 30) + "...");
        System.out.println("   ├─ 生成 pi_B (G2): " + proof.pi_B.toString().substring(0, 30) + "...");
        System.out.println("   └─ 生成 pi_C (G1): " + proof.pi_C.toString().substring(0, 30) + "...");
        return proof;
    }

    public boolean verify(Proof proof, byte[] merkleRoot) {
        System.out.println(">> [ZK 模块] 验证方开始计算椭圆曲线双线性配对...");

        // 验证者将公开的 Merkle Root 也映射为群上的点
        Element x = Zr.newElement().setFromHash(merkleRoot, 0, merkleRoot.length).getImmutable();
        Element publicInputX_G1 = G1_gen.powZn(x).getImmutable();

        // 双线性配对计算
        Element leftSide = pairing.pairing(proof.pi_A, proof.pi_B);
        Element rightSide = pairing.pairing(alpha, beta)
                .mul(pairing.pairing(publicInputX_G1, gamma))
                .mul(pairing.pairing(proof.pi_C, delta));

        System.out.println("   ├─ 方程左侧 e(A,B) 结果: " + leftSide.toString().substring(0, 30) + "...");
        System.out.println("   ├─ 方程右侧 乘积项结果: " + rightSide.toString().substring(0, 30) + "...");

        boolean isValid = leftSide.isEqual(rightSide);
        System.out.println("   └─ ZK配对结果: " + (isValid ? "✅ 完美相等，计算合法" : "❌ 不相等，证明伪造"));
        return isValid;
    }
}
