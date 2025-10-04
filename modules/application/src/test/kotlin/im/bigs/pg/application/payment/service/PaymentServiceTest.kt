package im.bigs.pg.application.payment.service

import im.bigs.pg.application.partner.port.out.FeePolicyOutPort
import im.bigs.pg.application.partner.port.out.PartnerOutPort
import im.bigs.pg.application.payment.port.`in`.PaymentCommand
import im.bigs.pg.application.payment.port.out.PaymentOutPort
import im.bigs.pg.application.pg.port.out.PgApproveResult
import im.bigs.pg.application.pg.port.out.PgClientOutPort
import im.bigs.pg.domain.partner.FeePolicy
import im.bigs.pg.domain.partner.Partner
import im.bigs.pg.domain.payment.Payment
import im.bigs.pg.domain.payment.PaymentStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class PaymentServiceTest {
    private val partnerRepo = mockk<PartnerOutPort>()
    private val feeRepo = mockk<FeePolicyOutPort>()
    private val paymentRepo = mockk<PaymentOutPort>()
    private val pgClient = mockk<PgClientOutPort>()

    // PaymentService 인스턴스 생성
    private val paymentService = PaymentService(
        partnerRepository = partnerRepo,
        feePolicyRepository = feeRepo,
        paymentRepository = paymentRepo,
        pgClients = listOf(pgClient)
    )

    @Test
    fun `결제 생성 성공 테스트 - 정책 기반 수수료 계산`() {
        // 준비: 테스트에 필요한 데이터와 상황을 설정
        val partnerId = 1L
        val amount = BigDecimal("10000")
        val command = PaymentCommand(
            partnerId = partnerId,
            amount = amount,
            cardBin = "123456",
            cardLast4 = "4242",
            productName = "테스트 상품"
        )

        val partner = Partner(
            id = partnerId,
            code = "TEST1",
            name = "테스트 파트너",
            active = true
        )
        every { partnerRepo.findById(partnerId) } returns partner

        // pg client mock 응답
        every { pgClient.supports(partnerId) } returns true
        val pgResult = PgApproveResult(
            approvalCode = "12345678",
            approvedAt = LocalDateTime.now(),
            status = PaymentStatus.APPROVED,
        )

        every { pgClient.approve(any()) } returns pgResult

        // 수수료 정책 mock 응답
        val feePolicy = FeePolicy(
            id = 1L,
            partnerId = partnerId,
            effectiveFrom = LocalDateTime.now().minusDays(1),
            percentage = BigDecimal("0.0235"),
            fixedFee = BigDecimal("100")
        )
        every { feeRepo.findEffectivePolicy(partnerId, any()) } returns feePolicy

        // 저장소 mock 응답 설정
        every { paymentRepo.save(any()) } answers { firstArg() }

        // 실행: 테스트할 메소드 실행
        val result = paymentService.pay(command)

        // 검증: 결과 확인
        // 기본 정보
        assertEquals(partnerId, result.partnerId)
        assertEquals(amount, result.amount)
        assertEquals("12345678", result.approvalCode)
        assertEquals(PaymentStatus.APPROVED, result.status)

        // 수수료 계산
        assertEquals(BigDecimal("0.0235"), result.appliedFeeRate)
        assertEquals(BigDecimal("335"), result.feeAmount)
        assertEquals(BigDecimal("9665"), result.netAmount)

        // 카드 정보 마스킹
        assertEquals("123456", result.cardBin)
        assertEquals("4242", result.cardLast4)
    }

    @Test
    fun `존재하지 않는 파트너로 결제 시도하면 실패`() {
        // 준비
        val partnerId = 999L
        val command = PaymentCommand(
            partnerId = partnerId,
            amount = BigDecimal("10000"),
            cardBin = "123456",
            cardLast4 = "4242",
        )

        // 실행
        every { partnerRepo.findById(partnerId) } returns null

        // 검증 & 결과
        val exception = assertThrows<IllegalArgumentException> {
            paymentService.pay(command)
        }
        assertEquals("Partner not found: $partnerId", exception.message)
    }

    @Test
    fun `비활성 파트너로 결제 시도하면 실패`() {
        // 준비
        val partnerId = 1L
        val command = PaymentCommand(
            partnerId = partnerId,
            amount = BigDecimal("10000"),
            cardBin = "123456",
            cardLast4 = "4242"
        )

        val inactivePartner = Partner(
            id = partnerId,
            code = "TEST1",
            name = "비활성 파트너",
            active = false // 비활성 상태
        )
        every { partnerRepo.findById(partnerId) } returns inactivePartner

        // 실행 & 결과
        val exception = assertThrows<IllegalArgumentException> {
            paymentService.pay(command)
        }
        assertEquals("Partner is inactive: $partnerId", exception.message)
    }

    @Test
    fun `수수료 정책이 없는 경우 결제 실패`() {
        val partnerId = 1L
        val command = PaymentCommand(
            partnerId = partnerId,
            amount = BigDecimal("10000"),
            cardBin = "123456",
            cardLast4 = "4242"
        )

        val partner = Partner(
            id = partnerId,
            code = "TEST1",
            name = "테스트 파트너",
            active = true
        )
        every { partnerRepo.findById(partnerId) } returns partner
        every { pgClient.supports(partnerId) } returns true

        val pgResult = PgApproveResult(
            approvalCode = "12345678",
            approvedAt = LocalDateTime.now(),
            status = PaymentStatus.APPROVED
        )
        every { pgClient.approve(any()) } returns pgResult

        every { feeRepo.findEffectivePolicy(partnerId, any()) } returns null

        val exception = assertThrows<IllegalArgumentException> {
            paymentService.pay(command)
        }
        assertEquals("Policy not found: $partnerId", exception.message)
    }

    @Test
    @DisplayName("결제 시 수수료 정책을 적용하고 저장해야 한다")
    fun `결제 시 수수료 정책을 적용하고 저장해야 한다`() {
        // 준비
        every { partnerRepo.findById(1L) } returns Partner(1L, "TEST", "Test", true)
        every { pgClient.supports(1L) } returns true
        every { pgClient.approve(any()) } returns PgApproveResult(
            approvalCode = "12345678",
            approvedAt = LocalDateTime.now(),
            status = PaymentStatus.APPROVED,
        )

        every { feeRepo.findEffectivePolicy(1L, any()) } returns FeePolicy(
            id = 10L, partnerId = 1L,
            effectiveFrom = LocalDateTime.ofInstant(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC),
            percentage = BigDecimal("0.0300"),
            fixedFee = BigDecimal("100")
        )
        val savedSlot = slot<Payment>()
        every { paymentRepo.save(capture(savedSlot)) } answers { savedSlot.captured.copy(id = 99L) }

        // 실행
        val cmd = PaymentCommand(partnerId = 1L, amount = BigDecimal("10000"), cardLast4 = "4242")
        val res = paymentService.pay(cmd)

        // 결과
        assertEquals(99L, res.id)
        assertEquals(BigDecimal("400"), res.feeAmount)
        assertEquals(BigDecimal("9600"), res.netAmount)
        assertEquals(PaymentStatus.APPROVED, res.status)
    }
}
