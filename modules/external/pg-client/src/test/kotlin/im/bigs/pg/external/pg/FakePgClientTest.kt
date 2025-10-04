package im.bigs.pg.external.pg

import com.fasterxml.jackson.databind.ObjectMapper
import im.bigs.pg.application.pg.port.out.PgApproveRequest
import im.bigs.pg.domain.payment.PaymentStatus
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@DisplayName("FakePgClient 테스트")
class FakePgClientTest {
    private val objectMapper = ObjectMapper()
    private val aesGcmEncryptor = im.bigs.pg.external.pg.encryption.AesGcmEncryptor()
    private val fakePgClient = FakePgClient(objectMapper, aesGcmEncryptor)

    @Test
    @DisplayName("짝수 partnerId만 지원해야 한다")
    fun `짝수 partnerId만 지원해야 한다`() {
        assertTrue(fakePgClient.supports(2L))
        assertFalse(fakePgClient.supports(1L))
        assertTrue(fakePgClient.supports(4L))
        assertFalse(fakePgClient.supports(3L))
    }

    @Test
    @DisplayName("성공 카드(1111)로 5만원 이하 결제시 성공해야 한다")
    fun `성공 카드로 5만원 이하 결제시 성공해야 한다`() {
        // 준비
        val request = PgApproveRequest(
            partnerId = 2L,
            amount = BigDecimal.valueOf(10000),
            cardBin = "1111",
            cardLast4 = "1111",
            productName = "테스트 상품"
        )

        // 실행
        val result = fakePgClient.approve(request)

        // 결과
        assertEquals(PaymentStatus.APPROVED, result.status)
        assertTrue(result.approvalCode.length == 8)
    }

    @Test
    @DisplayName("실패 카드(2222)로 5만원 초과 결제시 한도초과 오류를 반환해야 한다")
    fun `실패 카드로 5만원 초과 결제시 한도초과 오류를 반환해야 한다`() {
        // 준비
        val request = PgApproveRequest(
            partnerId = 2L,
            amount = BigDecimal.valueOf(60000),
            cardBin = "2222",
            cardLast4 = "2222",
            productName = "테스트 상품"
        )

        // 실행 & 결과
        val exception = assertFailsWith<HttpClientErrorException> {
            fakePgClient.approve(request)
        }

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, exception.statusCode)
        assertTrue(exception.responseBodyAsString.contains("INSUFFICIENT_LIMIT"))
        assertTrue(exception.responseBodyAsString.contains("한도가 초과되었습니다"))
    }

    @Test
    @DisplayName("실패 카드(2222)로 51000원 결제시 STOLEN_OR_LOST 오류를 반환해야 한다")
    fun `실패 카드로 51000원 결제시 도난분실 오류를 반환해야 한다`() {
        // 준비
        val request = PgApproveRequest(
            partnerId = 2L,
            amount = BigDecimal.valueOf(51000),
            cardBin = "2222",
            cardLast4 = "2222",
            productName = "테스트 상품"
        )

        // 실행 & 결과
        val exception = assertFailsWith<HttpClientErrorException> {
            fakePgClient.approve(request)
        }

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, exception.statusCode)
        assertTrue(exception.responseBodyAsString.contains("STOLEN_OR_LOST"))
        assertTrue(exception.responseBodyAsString.contains("도난 또는 분실된 카드입니다"))
    }

    @Test
    @DisplayName("실패 카드(2222)로 52000원 결제시 EXPIRED_OR_BLOCKED 오류를 반환해야 한다")
    fun `실패 카드로 52000원 결제시 정지만료 오류를 반환해야 한다`() {
        // 준비
        val request = PgApproveRequest(
            partnerId = 2L,
            amount = BigDecimal.valueOf(52000),
            cardBin = "2222",
            cardLast4 = "2222",
            productName = "테스트 상품"
        )

        // 실행 & 결과
        val exception = assertFailsWith<HttpClientErrorException> {
            fakePgClient.approve(request)
        }

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, exception.statusCode)
        assertTrue(exception.responseBodyAsString.contains("EXPIRED_OR_BLOCKED"))
        assertTrue(exception.responseBodyAsString.contains("정지되었거나 만료된 카드입니다"))
    }

    @Test
    @DisplayName("실패 카드(2222)로 53000원 결제시 TAMPERED_CARD 오류를 반환해야 한다")
    fun `실패 카드로 53000원 결제시 위조변조 오류를 반환해야 한다`() {
        // 준비
        val request = PgApproveRequest(
            partnerId = 2L,
            amount = BigDecimal.valueOf(53000),
            cardBin = "2222",
            cardLast4 = "2222",
            productName = "테스트 상품"
        )

        // 실행 & 결과
        val exception = assertFailsWith<HttpClientErrorException> {
            fakePgClient.approve(request)
        }

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, exception.statusCode)
        assertTrue(exception.responseBodyAsString.contains("TAMPERED_CARD"))
        assertTrue(exception.responseBodyAsString.contains("위조 또는 변조된 카드입니다"))
    }

    @Test
    @DisplayName("실패 카드(2222)로 54000원 결제시 TAMPERED_CARD 오류를 반환해야 한다")
    fun `실패 카드로 54000원 결제시 허용되지 않은 카드 오류를 반환해야 한다`() {
        // 준비
        val request = PgApproveRequest(
            partnerId = 2L,
            amount = BigDecimal.valueOf(54000),
            cardBin = "2222",
            cardLast4 = "2222",
            productName = "테스트 상품"
        )

        // 실행 & 결과
        val exception = assertFailsWith<HttpClientErrorException> {
            fakePgClient.approve(request)
        }

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, exception.statusCode)
        assertTrue(exception.responseBodyAsString.contains("TAMPERED_CARD"))
        assertTrue(exception.responseBodyAsString.contains("허용되지 않은 카드"))
    }

    @Test
    @DisplayName("API-KEY 헤더 누락시 401 오류를 반환해야 한다")
    fun `API-KEY 헤더 누락시 401 오류를 반환해야 한다`() {
        // 준비
        val request = PgApproveRequest(
            partnerId = -1L, // API-KEY 헤더 누락 시나리오
            amount = BigDecimal.valueOf(10000),
            cardBin = "1111",
            cardLast4 = "1111",
            productName = "테스트 상품"
        )

        // 실행 & 결과
        val exception = assertFailsWith<HttpClientErrorException> {
            fakePgClient.approve(request)
        }

        assertEquals(HttpStatus.UNAUTHORIZED, exception.statusCode)
        assertTrue(exception.responseBodyAsString.isNullOrEmpty()) // 401은 응답 본문 없음
    }

    @Test
    @DisplayName("API-KEY 포맷 오류시 401 오류를 반환해야 한다")
    fun `API-KEY 포맷 오류시 401 오류를 반환해야 한다`() {
        // 준비
        val request = PgApproveRequest(
            partnerId = -2L, // API-KEY 포맷 오류 시나리오
            amount = BigDecimal.valueOf(10000),
            cardBin = "1111",
            cardLast4 = "1111",
            productName = "테스트 상품"
        )

        // 실행 & 결과
        val exception = assertFailsWith<HttpClientErrorException> {
            fakePgClient.approve(request)
        }

        assertEquals(HttpStatus.UNAUTHORIZED, exception.statusCode)
        assertTrue(exception.responseBodyAsString.isNullOrEmpty()) // 401은 응답 본문 없음
    }

    @Test
    @DisplayName("미등록 API-KEY로 요청시 401 오류를 반환해야 한다")
    fun `미등록 API-KEY로 요청시 401 오류를 반환해야 한다`() {
        // 준비
        val request = PgApproveRequest(
            partnerId = -3L, // 미등록 API-KEY 시나리오
            amount = BigDecimal.valueOf(10000),
            cardBin = "1111",
            cardLast4 = "1111",
            productName = "테스트 상품"
        )

        // 실행 & 결과
        val exception = assertFailsWith<HttpClientErrorException> {
            fakePgClient.approve(request)
        }

        assertEquals(HttpStatus.UNAUTHORIZED, exception.statusCode)
        assertTrue(exception.responseBodyAsString.isNullOrEmpty()) // 401은 응답 본문 없음
    }
}
