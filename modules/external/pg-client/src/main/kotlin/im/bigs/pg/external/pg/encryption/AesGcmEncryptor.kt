package im.bigs.pg.external.pg.encryption

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Test PG API AES-256-GCM 암호화
 * * 실제 API-KEY/IV 없이 Test PG API 문서 스펙 시뮬레이션
 * - AES-256-GCM 알고리즘
 * - SHA-256(API-KEY) 키 생성
 * - Base64URL 인코딩/디코딩
 * - 12바이트 IV 처리
 */
@Component
class AesGcmEncryptor {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val ALGORITHM = "AES"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12 // 96비트
        private const val GCM_TAG_LENGTH = 16 // 128비트
        private const val KEY_LENGTH = 32 // 256비트

        // Test PG 시뮬레이션용 고정 키
        private val SIMULATION_KEY = "test-pg-simulation-key-32bytes!!".toByteArray(StandardCharsets.UTF_8)

        // Test PG 시뮬레이션용 고정 IV
        private val SIMULATION_IV = "test-pg-iv-12".toByteArray(StandardCharsets.UTF_8)
    }

    /**
     * Test PG API 요청 데이터를 AES-256-GCM으로 암호화 시뮬레이션
     * * @param plainText 암호화할 평문 JSON
     * @param apiKey API-KEY
     * @param ivBase64Url IV Base64URL
     * @return Base64URL 인코딩된 암호문
     */
    fun encrypt(plainText: String, apiKey: String?, ivBase64Url: String?): String {
        log.debug("Test PG AES 암호화 시뮬레이션 시작: plainText 길이={}", plainText.length)

        try {
            // 1. 키 생성 시뮬레이션 (실제로는 SHA-256(API-KEY))
            val key = generateKey(apiKey)

            // 2. IV 생성 시뮬레이션 (실제로는 Base64URL 디코딩)
            val iv = generateIv(ivBase64Url)

            // 3. AES-256-GCM 암호화 시뮬레이션
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val keySpec = SecretKeySpec(key, ALGORITHM)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)

            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
            val cipherText = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))

            // 4. Base64URL 인코딩
            val encrypted = Base64.getUrlEncoder().withoutPadding().encodeToString(cipherText)

            log.debug("Test PG AES 암호화 시뮬레이션 완료: encrypted 길이={}", encrypted.length)
            return encrypted
        } catch (e: Exception) {
            log.error("Test PG AES 암호화 시뮬레이션 실패", e)
            throw RuntimeException("Test PG 암호화 시뮬레이션 실패", e)
        }
    }

    /**
     * Test PG API 응답 데이터를 AES-256-GCM으로 복호화 시뮬레이션
     * * @param encryptedText Base64URL 인코딩된 암호문
     * @param apiKey API-KEY (시뮬레이션용)
     * @param ivBase64Url IV Base64URL (시뮬레이션용)
     * @return 복호화된 평문
     */
    fun decrypt(encryptedText: String, apiKey: String?, ivBase64Url: String?): String {
        log.debug("Test PG AES 복호화 시뮬레이션 시작: encryptedText 길이={}", encryptedText.length)

        try {
            // 1. 키 생성 시뮬레이션
            val key = generateKey(apiKey)

            // 2. IV 생성 시뮬레이션
            val iv = generateIv(ivBase64Url)

            // 3. Base64URL 디코딩
            val cipherText = Base64.getUrlDecoder().decode(encryptedText)

            // 4. AES-256-GCM 복호화 시뮬레이션
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val keySpec = SecretKeySpec(key, ALGORITHM)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)

            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
            val plainText = cipher.doFinal(cipherText)

            val result = String(plainText, StandardCharsets.UTF_8)
            log.debug("Test PG AES 복호화 시뮬레이션 완료: plainText 길이={}", result.length)
            return result
        } catch (e: Exception) {
            log.error("Test PG AES 복호화 시뮬레이션 실패", e)
            throw RuntimeException("Test PG 복호화 시뮬레이션 실패", e)
        }
    }

    /**
     * API-KEY로부터 256비트 키 생성 시뮬레이션
     * 실제로는 SHA-256(API-KEY)를 사용하지만, 시뮬레이션에서는 고정 키 사용
     */
    private fun generateKey(apiKey: String?): ByteArray {
        return if (apiKey != null && apiKey.isNotEmpty()) {
            // API-KEY가 있으면 SHA-256 해시 시뮬레이션
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(apiKey.toByteArray(StandardCharsets.UTF_8))
            digest.digest()
        } else {
            // API-KEY가 없으면 시뮬레이션용 고정 키 사용
            SIMULATION_KEY
        }
    }

    /**
     * Base64URL IV를 12바이트 배열로 변환 시뮬레이션
     * 실제로는 Base64URL 디코딩하지만, 시뮬레이션에서는 고정 IV 사용
     */
    private fun generateIv(ivBase64Url: String?): ByteArray {
        return if (ivBase64Url != null && ivBase64Url.isNotEmpty()) {
            try {
                // Base64URL 디코딩 시뮬레이션
                Base64.getUrlDecoder().decode(ivBase64Url)
            } catch (e: Exception) {
                log.warn("IV Base64URL 디코딩 실패, 시뮬레이션용 고정 IV 사용: {}", ivBase64Url)
                SIMULATION_IV
            }
        } else {
            // IV가 없으면 시뮬레이션용 고정 IV 사용
            SIMULATION_IV
        }
    }

    /**
     * Test PG API 요청 스키마에 맞는 JSON 생성
     */
    fun createTestPgRequest(
        cardNumber: String,
        birthDate: String = "19900101",
        expiry: String = "1227",
        password: String = "12",
        amount: Int
    ): String {
        return """
        {
            "cardNumber": "$cardNumber",
            "birthDate": "$birthDate",
            "expiry": "$expiry",
            "password": "$password",
            "amount": $amount
        }
        """.trimIndent()
    }
}
