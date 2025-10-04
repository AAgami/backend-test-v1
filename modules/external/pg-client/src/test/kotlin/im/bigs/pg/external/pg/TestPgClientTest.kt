package im.bigs.pg.external.pg

import com.fasterxml.jackson.databind.ObjectMapper
import im.bigs.pg.application.pg.port.out.PgApproveRequest
import im.bigs.pg.domain.payment.PaymentStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestPgClientTest {
    private val restTemplate = mockk<RestTemplate>(relaxed = true)
    private val objectMapper = mockk<ObjectMapper>(relaxed = true)

    private val testPgClient = TestPgClient(
        restTemplate = restTemplate,
        objectMapper = objectMapper,
        apiUrl = "https://api-test-pg.bigs.im",
        apiKey = "test-api-key",
        apiEnc = "test-enc-data"
    )

    @Test
    @DisplayName("짝수 partnerId만 지원해야 한다")
    fun `짝수 partnerId만 지원해야 한다`() {
        assertTrue(testPgClient.supports(2L))
        assertFalse(testPgClient.supports(1L))
        assertTrue(testPgClient.supports(4L))
        assertFalse(testPgClient.supports(3L))
    }

    @Test
    @DisplayName("5만원 이하 결제시 성공해야 한다")
    fun `5만원 이하 결제시 성공해야 한다`() {
        // 준비
        val request = PgApproveRequest(
            partnerId = 2L,
            amount = BigDecimal.valueOf(10000),
            cardBin = "111111",
            cardLast4 = "1111",
            productName = "테스트 상품"
        )

        val successResponse = TestPgClient.TestPgSuccessResponse(
            approvalCode = "12345678",
            approvedAt = "2025-10-05T00:00:00.000000",
            maskedCardLast4 = "1111",
            amount = 10000,
            status = "APPROVED"
        )

        every {
            restTemplate.postForEntity(
                any<String>(),
                any(),
                TestPgClient.TestPgSuccessResponse::class.java,
                *anyVararg()
            )
        } returns ResponseEntity.ok(successResponse)

        // 실행
        val result = testPgClient.approve(request)

        // 검증
        assertEquals(PaymentStatus.APPROVED, result.status)
        assertEquals("12345678", result.approvalCode)
    }

    @Test
    @DisplayName("5만원 초과 결제시 한도초과 오류를 반환해야 한다")
    fun `5만원 초과 결제시 한도초과 오류를 반환해야 한다`() {
        // 준비
        val request = PgApproveRequest(
            partnerId = 2L,
            amount = BigDecimal.valueOf(60000),
            cardBin = "222222",
            cardLast4 = "2222",
            productName = "테스트 상품"
        )

        val errorResponse = """
            {
                "code": 1002,
                "errorCode": "INSUFFICIENT_LIMIT",
                "message": "한도가 초과되었습니다.",
                "referenceId": "ff2ab0c6-b77c-4dfe-a294-4ec641f8b55b"
            }
        """.trimIndent()

        every {
            restTemplate.postForEntity(
                any<String>(),
                any(),
                TestPgClient.TestPgSuccessResponse::class.java,
                *anyVararg()
            )
        } throws HttpClientErrorException(HttpStatus.UNPROCESSABLE_ENTITY, "422", errorResponse.toByteArray(), null)

        // 실행 & 검증
        val exception = assertFailsWith<IllegalStateException> {
            testPgClient.approve(request)
        }
        assertTrue(exception.message?.contains("TestPG 결제 승인 실패") == true)
    }

    @Test
    @DisplayName("API 요청시 올바른 헤더가 설정되어야 한다")
    fun `API 요청시 올바른 헤더가 설정되어야 한다`() {
        // 준비
        val request = PgApproveRequest(
            partnerId = 2L,
            amount = BigDecimal.valueOf(10000),
            cardBin = "111111",
            cardLast4 = "1111",
            productName = "테스트 상품"
        )

        val successResponse = TestPgClient.TestPgSuccessResponse(
            approvalCode = "12345678",
            approvedAt = "2025-10-05T00:00:00.000000",
            maskedCardLast4 = "1111",
            amount = 10000,
            status = "APPROVED"
        )

        val headerSlot = slot<HttpEntity<*>>()

        every {
            restTemplate.postForEntity(
                any<String>(),
                capture(headerSlot),
                TestPgClient.TestPgSuccessResponse::class.java,
                *anyVararg()
            )
        } returns ResponseEntity.ok(successResponse)

        // 실행
        testPgClient.approve(request)

        // 검증
        with(headerSlot.captured.headers) {
            assertEquals(MediaType.APPLICATION_JSON, contentType)
            assertEquals("test-api-key", get("API-KEY")?.first())
        }
    }

    @Test
    @DisplayName("API-KEY 인증 실패시 적절한 예외를 반환해야 한다")
    fun `API-KEY 인증 실패시 적절한 예외를 반환해야 한다`() {
        // 준비
        val request = PgApproveRequest(
            partnerId = 2L,
            amount = BigDecimal.valueOf(10000),
            cardBin = "111111",
            cardLast4 = "1111",
            productName = "테스트 상품"
        )

        every {
            restTemplate.postForEntity(
                any<String>(),
                any(),
                TestPgClient.TestPgSuccessResponse::class.java,
                *anyVararg()
            )
        } throws HttpClientErrorException(HttpStatus.UNAUTHORIZED, "401")

        // 실행 & 검증
        val exception = assertFailsWith<IllegalStateException> {
            testPgClient.approve(request)
        }
        assertTrue(exception.message?.contains("TestPG 결제 승인 실패") == true)
    }
    @Test
    @DisplayName("API 타임아웃시 목업 응답을 반환해야 한다")
    fun `API 타임아웃시 목업 응답을 반환해야 한다`() {
        // 준비
        val request = PgApproveRequest(
            partnerId = 2L,
            amount = BigDecimal.valueOf(10000),
            cardBin = "111111",
            cardLast4 = "1111",
            productName = "테스트 상품"
        )

        every {
            restTemplate.postForEntity(
                any<String>(),
                any(),
                TestPgClient.TestPgSuccessResponse::class.java,
                *anyVararg()
            )
        } throws RuntimeException("Read timed out")

        // 실행
        val result = testPgClient.approve(request)

        // 검증
        assertEquals(PaymentStatus.APPROVED, result.status)
        assertTrue(result.approvalCode.length == 8) // 8자리 승인번호
    }

    @Test
    @DisplayName("복호화 실패시 적절한 예러를 반환해야 한다")
    fun `복호화 실패시 적절한 에러를 반환해야 한다`() {
        // 준비
        val request = PgApproveRequest(
            partnerId = 2L,
            amount = BigDecimal.valueOf(10000),
            cardBin = "111111",
            cardLast4 = "1111",
            productName = "테스트 상품"
        )

        every {
            restTemplate.postForEntity(
                any<String>(),
                any(),
                TestPgClient.TestPgSuccessResponse::class.java,
                *anyVararg()
            )
        } throws HttpClientErrorException(
            HttpStatus.BAD_REQUEST,
            "400",
            """{"message":"decrypt/parse failed"}""".toByteArray(),
            null
        )

        // 실행 & 검증
        val exception = assertFailsWith<IllegalStateException> {
            testPgClient.approve(request)
        }
        assertTrue(exception.message?.contains("TestPG 결제 승인 실패") == true)
    }
}
