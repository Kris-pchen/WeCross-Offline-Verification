package com.traffic.wecross.protocol;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.*;

public class ShamirSecretSharing {
    // 使用一个标准的 256 位大素数构建有限域 F_p
    private static final BigInteger PRIME = new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", 16);
    private final int n;
    private final int t;
    private final SecureRandom random = new SecureRandom();

    public ShamirSecretSharing(int n, int t) {
        this.n = n;
        this.t = t;
    }

    public Map<Integer, BigInteger> generateShares(BigInteger secretKey) {
        BigInteger[] coefficients = new BigInteger[t];
        coefficients[0] = secretKey;
        for (int i = 1; i < t; i++) {
            coefficients[i] = new BigInteger(256, random).mod(PRIME);
        }

        Map<Integer, BigInteger> shares = new HashMap<>();
        for (int i = 1; i <= n; i++) {
            BigInteger x = BigInteger.valueOf(i);
            BigInteger y = BigInteger.ZERO;
            for (int j = 0; j < t; j++) {
                BigInteger term = coefficients[j].multiply(x.pow(j)).mod(PRIME);
                y = y.add(term).mod(PRIME);
            }
            shares.put(i, y);
        }
        return shares;
    }

    public BigInteger recoverSecret(Map<Integer, BigInteger> providedShares) {
        if (providedShares.size() < t) throw new IllegalArgumentException("签名数量未达到门限阈值！");

        BigInteger secret = BigInteger.ZERO;
        List<Integer> xValues = new ArrayList<>(providedShares.keySet());

        for (int i = 0; i < t; i++) {
            int x_i = xValues.get(i);
            BigInteger y_i = providedShares.get(x_i);
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
        return secret.compareTo(BigInteger.ZERO) < 0 ? secret.add(PRIME) : secret;
    }
}
