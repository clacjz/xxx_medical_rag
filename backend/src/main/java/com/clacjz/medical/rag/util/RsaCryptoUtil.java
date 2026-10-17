package com.clacjz.medical.rag.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

@Slf4j
@Component
public class RsaCryptoUtil {

    private static final int KEY_SIZE = 2048;
    private static final String ALGORITHM = "RSA";
    private static final String TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";

    private KeyPair keyPair;

    @Value("${app.rsa.private-key:}")
    private String configuredPrivateKey;

    @Value("${app.rsa.public-key:}")
    private String configuredPublicKey;

    @PostConstruct
    public void init() {
        if (configuredPrivateKey != null && !configuredPrivateKey.isEmpty()
                && configuredPublicKey != null && !configuredPublicKey.isEmpty()) {
            loadKeyPair();
        } else {
            log.warn("RSA keys not configured, generating new key pair. This should NOT happen in production!");
            generateKeyPair();
        }
        log.info("RSA key pair initialized successfully with {} bits", KEY_SIZE);
    }

    private void loadKeyPair() {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM);

            // 加载私钥
            byte[] privateKeyBytes = Base64.getDecoder().decode(configuredPrivateKey);
            PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
            PrivateKey privateKey = keyFactory.generatePrivate(privateKeySpec);

            // 加载公钥
            byte[] publicKeyBytes = Base64.getDecoder().decode(configuredPublicKey);
            X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKeyBytes);
            PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);

            this.keyPair = new KeyPair(publicKey, privateKey);
            log.info("RSA key pair loaded from configuration");
        } catch (Exception e) {
            log.error("Failed to load RSA key pair from configuration", e);
            throw new IllegalStateException("Failed to load RSA key pair", e);
        }
    }

    public void generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(ALGORITHM);
            generator.initialize(KEY_SIZE, new SecureRandom());
            this.keyPair = generator.generateKeyPair();
            log.info("New RSA key pair generated");
        } catch (Exception e) {
            log.error("Failed to generate RSA key pair", e);
            throw new IllegalStateException("Failed to generate RSA key pair", e);
        }
    }

    public String getPublicKeyBase64() {
        if (keyPair == null || keyPair.getPublic() == null) {
            throw new IllegalStateException("Public key not initialized");
        }
        byte[] encodedKey = keyPair.getPublic().getEncoded();
        log.debug("Public key encoded length: {} bytes", encodedKey.length);
        return Base64.getEncoder().encodeToString(encodedKey);
    }

    public String generateKeyId() {
        return java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    public String encrypt(String data) {
        if (data == null || data.isEmpty()) {
            return data;
        }

        try {
            OAEPParameterSpec oaepParams = new OAEPParameterSpec(
                    "SHA-256", "MGF1", new MGF1ParameterSpec("SHA-256"), PSource.PSpecified.DEFAULT);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keyPair.getPublic(), oaepParams);

            byte[] dataBytes = data.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] encryptedBytes = cipher.doFinal(dataBytes);

            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            log.error("Encryption failed", e);
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public String decrypt(String encryptedData) {
        if (encryptedData == null || encryptedData.isEmpty()) {
            return encryptedData;
        }

        if (keyPair == null || keyPair.getPrivate() == null) {
            throw new IllegalStateException("Private key not initialized");
        }

        try {
            OAEPParameterSpec oaepParams = new OAEPParameterSpec(
                    "SHA-256", "MGF1", new MGF1ParameterSpec("SHA-256"), PSource.PSpecified.DEFAULT);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keyPair.getPrivate(), oaepParams);

            byte[] encryptedBytes = Base64.getDecoder().decode(encryptedData);
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);

            return new String(decryptedBytes, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Decryption failed: {}", e.getMessage(), e);
            throw new RuntimeException("Decryption failed", e);
        }
    }

    public boolean isEncrypted(String data) {
        if (data == null || data.isEmpty()) {
            return false;
        }

        try {
            Base64.getDecoder().decode(data);
            return data.length() > 100;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
