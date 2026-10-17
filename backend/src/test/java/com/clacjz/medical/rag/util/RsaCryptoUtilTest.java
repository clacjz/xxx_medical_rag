package com.clacjz.medical.rag.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RsaCryptoUtil 单元测试")
class RsaCryptoUtilTest {

    private RsaCryptoUtil rsaCryptoUtil;

    @BeforeEach
    void setUp() {
        rsaCryptoUtil = new RsaCryptoUtil();
        rsaCryptoUtil.generateKeyPair();
    }

    @Nested
    @DisplayName("加密测试")
    class EncryptTests {

        @Test
        @DisplayName("加密成功 - 普通文本")
        void encrypt_Success_NormalText() {
            String original = "Hello, World!";
            String encrypted = rsaCryptoUtil.encrypt(original);

            assertNotNull(encrypted);
            assertNotEquals(original, encrypted);
        }

        @Test
        @DisplayName("加密成功 - 空字符串")
        void encrypt_Success_EmptyString() {
            String original = "";
            String encrypted = rsaCryptoUtil.encrypt(original);

            assertEquals("", encrypted);
        }

        @Test
        @DisplayName("加密成功 - null 输入")
        void encrypt_Success_NullInput() {
            String encrypted = rsaCryptoUtil.encrypt(null);

            assertNull(encrypted);
        }

        @Test
        @DisplayName("加密成功 - 中文字符")
        void encrypt_Success_ChineseCharacters() {
            String original = "这是一段中文测试文本！";
            String encrypted = rsaCryptoUtil.encrypt(original);

            assertNotNull(encrypted);
            assertNotEquals(original, encrypted);
        }

        @Test
        @DisplayName("加密成功 - 特殊字符")
        void encrypt_Success_SpecialCharacters() {
            String original = "!@#$%^&*()_+-=[]{}|;':\",./<>?`~";
            String encrypted = rsaCryptoUtil.encrypt(original);

            assertNotNull(encrypted);
            assertNotEquals(original, encrypted);
        }
    }

    @Nested
    @DisplayName("解密测试")
    class DecryptTests {

        @Test
        @DisplayName("解密成功 - 正常加密解密")
        void decrypt_Success_NormalEncryption() {
            String original = "Hello, World!";
            String encrypted = rsaCryptoUtil.encrypt(original);
            String decrypted = rsaCryptoUtil.decrypt(encrypted);

            assertEquals(original, decrypted);
        }

        @Test
        @DisplayName("解密成功 - 空字符串")
        void decrypt_Success_EmptyString() {
            String original = "";
            String encrypted = rsaCryptoUtil.encrypt(original);
            String decrypted = rsaCryptoUtil.decrypt(encrypted);

            assertEquals(original, decrypted);
        }

        @Test
        @DisplayName("解密成功 - null 输入")
        void decrypt_Success_NullInput() {
            String decrypted = rsaCryptoUtil.decrypt(null);

            assertNull(decrypted);
        }

        @Test
        @DisplayName("解密成功 - 中文字符")
        void decrypt_Success_ChineseCharacters() {
            String original = "这是一段中文测试文本！";
            String encrypted = rsaCryptoUtil.encrypt(original);
            String decrypted = rsaCryptoUtil.decrypt(encrypted);

            assertEquals(original, decrypted);
        }
    }

    @Nested
    @DisplayName("公钥获取测试")
    class GetPublicKeyBase64Tests {

        @Test
        @DisplayName("获取公钥成功")
        void getPublicKeyBase64_Success() {
            String publicKey = rsaCryptoUtil.getPublicKeyBase64();

            assertNotNull(publicKey);
            assertFalse(publicKey.isEmpty());
        }

        @Test
        @DisplayName("获取公钥格式正确 - Base64 编码")
        void getPublicKeyBase64_ValidBase64() {
            String publicKey = rsaCryptoUtil.getPublicKeyBase64();

            assertDoesNotThrow(() -> java.util.Base64.getDecoder().decode(publicKey));
        }
    }

    @Nested
    @DisplayName("密钥 ID 生成测试")
    class GenerateKeyIdTests {

        @Test
        @DisplayName("生成密钥 ID 成功")
        void generateKeyId_Success() {
            String keyId = rsaCryptoUtil.generateKeyId();

            assertNotNull(keyId);
            assertEquals(16, keyId.length());
        }

        @Test
        @DisplayName("生成的密钥 ID 是唯一的")
        void generateKeyId_Unique() {
            String keyId1 = rsaCryptoUtil.generateKeyId();
            String keyId2 = rsaCryptoUtil.generateKeyId();

            assertNotEquals(keyId1, keyId2);
        }

        @Test
        @DisplayName("生成的密钥 ID 不包含连字符")
        void generateKeyId_NoHyphens() {
            String keyId = rsaCryptoUtil.generateKeyId();

            assertFalse(keyId.contains("-"));
        }

        @Test
        @DisplayName("生成的密钥 ID 是十六进制字符")
        void generateKeyId_HexCharacters() {
            String keyId = rsaCryptoUtil.generateKeyId();

            assertTrue(keyId.matches("[0-9a-f]+"));
        }
    }

    @Nested
    @DisplayName("加密判断测试")
    class IsEncryptedTests {

        @Test
        @DisplayName("判断加密数据 - 是")
        void isEncrypted_True() {
            String encrypted = rsaCryptoUtil.encrypt("test data");
            assertTrue(rsaCryptoUtil.isEncrypted(encrypted));
        }

        @Test
        @DisplayName("判断普通文本 - 否")
        void isEncrypted_False_PlainText() {
            assertFalse(rsaCryptoUtil.isEncrypted("Hello, World!"));
        }

        @Test
        @DisplayName("判断 null - 否")
        void isEncrypted_False_Null() {
            assertFalse(rsaCryptoUtil.isEncrypted(null));
        }

        @Test
        @DisplayName("判断空字符串 - 否")
        void isEncrypted_False_EmptyString() {
            assertFalse(rsaCryptoUtil.isEncrypted(""));
        }

        @Test
        @DisplayName("判断无效 Base64 - 否")
        void isEncrypted_False_InvalidBase64() {
            assertFalse(rsaCryptoUtil.isEncrypted("not-valid-base64!!!"));
        }
    }
}
