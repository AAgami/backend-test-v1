package im.bigs.pg.external.pg

import im.bigs.pg.application.pg.port.out.PgApproveRequest
import im.bigs.pg.application.pg.port.out.PgApproveResult
import im.bigs.pg.domain.payment.PaymentStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.web.client.HttpClientErrorException
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@DisplayName("FallbackPgClient 테스트")
class FallbackPgClientTest {
    private val testPgClient = mockk<TestPgClient>()
    private val fakePgClient = mockk<FakePgClient>()
    private val fallbackPgClient = FallbackPgClient(testPgClient, fakePgClient)

    @Test
    @DisplayName("TestPgClient 성공 시 FakePgClient를 호출하지 않아야 한다")
    fun `TestPgClient 성공 시 FakePgClient를 호출하지 않아야 한다`() {
        // given
        val request = PgApproveRequest(
            partnerId = 2L,
            amount = BigDecimal.valueOf(10000),
            cardBin = "1111",
            cardLast4 = "1111",
            productName = "테스트 상품"
        )

        val expectedResult = PgApproveResult(
            approvalCode = "12345678",
            approvedAt = java.time.LocalDateTime.now(),
            status = PaymentStatus.APPROVED
        )

        every { testPgClient.supports(2L) } returns true
        every { testPgClient.approve(request) } returns expectedResult

        // when
        val result = fallbackPgClient.approve(request)

        // then
        assertEquals(expectedResult, result)
        verify(exactly = 1) { testPgClient.approve(request) }
        verify(exactly = 0) { fakePgClient.approve(any()) }
    }

    @Test
    @DisplayName("TestPgClient 실패 시 FakePgClient로 폴백해야 한다")
    fun `TestPgClient 실패 시 FakePgClient로 폴백해야 한다`() {
        // given
        val request = PgApproveRequest(
            partnerId = 2L,
            amount = BigDecimal.valueOf(10000),
            cardBin = "1111",
            cardLast4 = "1111",
            productName = "테스트 상품"
        )

        val fallbackResult = PgApproveResult(
            approvalCode = "87654321",
            approvedAt = java.time.LocalDateTime.now(),
            status = PaymentStatus.APPROVED
        )

        every { testPgClient.supports(2L) } returns true
        every { testPgClient.approve(request) } throws HttpClientErrorException(
            org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
            "Service Unavailable"
        )
        every { fakePgClient.supports(2L) } returns true
        every { fakePgClient.approve(request) } returns fallbackResult

        // when
        val result = fallbackPgClient.approve(request)

        // then
        assertEquals(fallbackResult, result)
        verify(exactly = 1) { testPgClient.approve(request) }
        verify(exactly = 1) { fakePgClient.approve(request) }
    }

    @Test
    @DisplayName("TestPgClient와 FakePgClient 모두 실패 시 예외를 던져야 한다")
    fun `TestPgClient와 FakePgClient 모두 실패 시 예외를 던져야 한다`() {
        // given
        val request = PgApproveRequest(
            partnerId = 2L,
            amount = BigDecimal.valueOf(10000),
            cardBin = "1111",
            cardLast4 = "1111",
            productName = "테스트 상품"
        )

        every { testPgClient.supports(2L) } returns true
        every { testPgClient.approve(request) } throws RuntimeException("TestPG API 실패")
        every { fakePgClient.supports(2L) } returns true
        every { fakePgClient.approve(request) } throws RuntimeException("FakePG 실패")

        // when & then
        val exception = assertFailsWith<IllegalStateException> {
            fallbackPgClient.approve(request)
        }

        assertTrue(exception.message?.contains("모든 PG 클라이언트 호출 실패") == true)
        verify(exactly = 1) { testPgClient.approve(request) }
        verify(exactly = 1) { fakePgClient.approve(request) }
    }

    @Test
    @DisplayName("짝수 partnerId만 지원해야 한다")
    fun `짝수 partnerId만 지원해야 한다`() {
        // given
        every { testPgClient.supports(2L) } returns true
        every { testPgClient.supports(1L) } returns false
        every { fakePgClient.supports(2L) } returns true
        every { fakePgClient.supports(1L) } returns false

        // when & then
        assertTrue(fallbackPgClient.supports(2L))
        assertTrue(!fallbackPgClient.supports(1L))
    }
}
