package com.dataprocessor.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * 加密工具类，提供字段加密功能
 */
public class EncryptionUtil {
    private static final Logger logger = LoggerFactory.getLogger(EncryptionUtil.class);
    
    /**
     * 使用AES加密字符串
     * 
     * @param value 要加密的字符串
     * @param key 加密密钥
     * @return 加密后的字符串
     */
    public static String encrypt(String value, String key) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        
        try {
            // 生成16字节的密钥
            byte[] keyBytes = getKeyBytes(key);
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");
            
            // 设置加密参数
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            
            // 加密并返回Base64编码的结果
            byte[] encryptedBytes = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encryptedBytes);
            
        } catch (Exception e) {
            logger.error("加密过程中出错", e);
            // 加密失败时返回原值，避免数据丢失
            return value;
        }
    }
    
    /**
     * 解密已加密的字符串
     * 
     * @param encryptedValue 加密后的字符串
     * @param key 解密密钥
     * @return 解密后的原始字符串
     */
    public static String decrypt(String encryptedValue, String key) {
        if (encryptedValue == null || encryptedValue.isEmpty()) {
            return encryptedValue;
        }
        
        try {
            // 生成16字节的密钥
            byte[] keyBytes = getKeyBytes(key);
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");
            
            // 设置解密参数
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            
            // 解密
            byte[] decodedBytes = Base64.getDecoder().decode(encryptedValue);
            byte[] decryptedBytes = cipher.doFinal(decodedBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            logger.error("解密过程中出错", e);
            // 解密失败时返回原值
            return encryptedValue;
        }
    }
    
    /**
     * 从密钥字符串生成固定长度的密钥字节数组
     */
    private static byte[] getKeyBytes(String key) throws Exception {
        // 使用SHA-256哈希函数生成固定长度的字节数组
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(key.getBytes(StandardCharsets.UTF_8));
        byte[] keyBytes = new byte[16];
        System.arraycopy(digest.digest(), 0, keyBytes, 0, keyBytes.length);
        return keyBytes;
    }
} 