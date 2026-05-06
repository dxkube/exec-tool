package com.dataprocessor.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

public class StringToUUID {

    /**
     * 将输入字符串转换为UUID格式（8-4-4-4-12）
     * @param input 输入字符串
     * @return 符合UUID格式的字符串
     */
    public static String generateUUID(String input) {
        try {
            // 使用MD5哈希算法处理输入字符串
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(input.getBytes());

            // 将字节数组转换为32位十六进制字符串
            StringBuilder hexBuilder = new StringBuilder();
            for (byte b : hashBytes) {
                // 转换为两位十六进制，不足两位补0
                hexBuilder.append(String.format("%02x", b));
            }
            String hexStr = hexBuilder.toString();

            // 按照UUID格式分割（8-4-4-4-12）
            return new StringBuilder()
                    .append(hexStr.substring(0, 8))
                    .append("-")
                    .append(hexStr.substring(8, 12))
                    .append("-")
                    .append(hexStr.substring(12, 16))
                    .append("-")
                    .append(hexStr.substring(16, 20))
                    .append("-")
                    .append(hexStr.substring(20))
                    .toString();

        } catch (NoSuchAlgorithmException e) {
            // 理论上不会抛出此异常，因为MD5是Java标准算法
            throw new RuntimeException("MD5 algorithm not found", e);
        }
    }

    public static void main(String[] args) {
        String str1 = "H32011800019_20250928";
        System.out.println("输入: " + str1 + " → 生成UUID: " + generateUUID(str1));
    }
}
