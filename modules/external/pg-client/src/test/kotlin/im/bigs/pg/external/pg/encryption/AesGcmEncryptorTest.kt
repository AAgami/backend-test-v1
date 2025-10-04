package im.bigs.pg.external.pg.encryption

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@DisplayName("AesGcmEncryptor 테스트")
class AesGcmEncryptorTest {
    private val aesGcmEncryptor = AesGcmEncryptor()

    @Test
    @DisplayName("Test PG API 요청 암호화/복호화가 정상 동작해야 한다")
    fun `Test PG API 요청 암호화 복호화가 정상 동작해야 한다`() {
        // given
        val plainText = """
        {
            "cardNumber": "1111-1111-1111-1111",
            "birthDate": "19900101",
            "expiry": "1227",
            "password": "12",
            "amount": 10000
        }
        """.trimIndent()

        val apiKey = "11111111-1111-4111-8111-111111111111"
        val iv = "dGVzdC1wZy1pdi0xMg"

        // when
        val encrypted = aesGcmEncryptor.encrypt(plainText, apiKey, iv)
        val decrypted = aesGcmEncryptor.decrypt(encrypted, apiKey, iv)

        // then
        assertNotNull(encrypted)
        assertTrue(encrypted.isNotEmpty())
        assertEquals(plainText, decrypted)
    }

    @Test
    @DisplayName("Test PG API 요청 생성이 정상 동작해야 한다")
    fun `Test PG API 요청 생성이 정상 동작해야 한다`() {
        // when
        val request = aesGcmEncryptor.createTestPgRequest(
            cardNumber = "2222-2222-2222-2222",
            amount = 51000
        )

        // then
        assertTrue(request.contains("2222-2222-2222-2222"))
        assertTrue(request.contains("51000"))
        assertTrue(request.contains("cardNumber"))
        assertTrue(request.contains("amount"))
        assertTrue(request.contains("birthDate"))
        assertTrue(request.contains("expiry"))
        assertTrue(request.contains("password"))
    }

    @Test
    @DisplayName("API-KEY가 없을 때도 암호화가 동작해야 한다")
    fun `API-KEY가 없을 때도 암호화가 동작해야 한다`() {
        // given
        val plainText = "test data"
        val apiKey: String? = null
        val iv: String? = null

        // when
        val encrypted = aesGcmEncryptor.encrypt(plainText, apiKey, iv)
        val decrypted = aesGcmEncryptor.decrypt(encrypted, apiKey, iv)

        // then
        assertNotNull(encrypted)
        assertTrue(encrypted.isNotEmpty())
        assertEquals(plainText, decrypted)
    }

    @Test
    @DisplayName("잘못된 IV로도 암호화가 동작해야 한다")
    fun `잘못된 IV로도 암호화가 동작해야 한다`() {
        // given
        val plainText = "test data"
        val apiKey = "test-key"
        val invalidIv = "invalid-iv-format"

        // when
        val encrypted = aesGcmEncryptor.encrypt(plainText, apiKey, invalidIv)
        val decrypted = aesGcmEncryptor.decrypt(encrypted, apiKey, invalidIv)

        // then
        assertNotNull(encrypted)
        assertTrue(encrypted.isNotEmpty())
        assertEquals(plainText, decrypted)
    }

    @Test
    @DisplayName("Base64URL 형식의 암호문이 생성되어야 한다")
    fun `Base64URL 형식의 암호문이 생성되어야 한다`() {
        // given
        val plainText = "test data"
        val apiKey = "test-key"
        val iv = "test-iv"

        // when
        val encrypted = aesGcmEncryptor.encrypt(plainText, apiKey, iv)

        // then
        // Base64URL 형식 확인 (패딩 없음, URL-safe 문자만 사용)
        assertTrue(encrypted.matches(Regex("[A-Za-z0-9_-]+")))
        assertTrue(!encrypted.endsWith("=")) // 패딩 없음
    }
}
