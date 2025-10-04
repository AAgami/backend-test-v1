package im.bigs.pg.application.payment.service

import im.bigs.pg.application.partner.port.out.FeePolicyOutPort
import im.bigs.pg.application.partner.port.out.PartnerOutPort
import im.bigs.pg.application.payment.port.`in`.PaymentCommand
import im.bigs.pg.application.payment.port.`in`.PaymentUseCase
import im.bigs.pg.application.payment.port.out.PaymentOutPort
import im.bigs.pg.application.pg.port.out.PgApproveRequest
import im.bigs.pg.application.pg.port.out.PgClientOutPort
import im.bigs.pg.domain.calculation.FeeCalculator
import im.bigs.pg.domain.payment.Payment
import im.bigs.pg.domain.payment.PaymentStatus
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * 결제 생성 유스케이스 구현체.
 * - 입력(REST 등) → 도메인/외부PG/영속성 포트를 순차적으로 호출하는 흐름을 담당합니다.
 * - 수수료 정책 조회 및 적용(계산)은 도메인 유틸리티를 통해 수행합니다.
 */
@Service
class PaymentService(
    private val partnerRepository: PartnerOutPort,
    private val feePolicyRepository: FeePolicyOutPort,
    private val paymentRepository: PaymentOutPort,
    private val pgClients: List<PgClientOutPort>,
) : PaymentUseCase {
    /**
     * 결제 승인/수수료 계산/저장을 순차적으로 수행합니다.
     * - 정책 기반 수수료 계산 구현
     * - 과제: 제휴사별 수수료 정책을 적용하도록 개선해 보세요.
     */
    override fun pay(command: PaymentCommand): Payment {
        val partner = partnerRepository.findById(command.partnerId)
            ?: throw IllegalArgumentException("Partner not found: ${command.partnerId}")
        require(partner.active) { "Partner is inactive: ${partner.id}" }

        val pgClient = pgClients.firstOrNull { it.supports(partner.id) }
            ?: throw IllegalStateException("No PG client for partner ${partner.id}")

        val approve = try {
            pgClient.approve(
                PgApproveRequest(
                    partnerId = partner.id,
                    amount = command.amount,
                    cardBin = command.cardBin,
                    cardLast4 = command.cardLast4,
                    productName = command.productName,
                ),
            )
        } catch (e: HttpClientErrorException) {
            // PG 클라이언트에서 발생한 에러를 적절한 예외로 변환
            when (e.statusCode) {
                HttpStatus.UNAUTHORIZED -> throw IllegalArgumentException("PG 인증 실패: ${e.responseBodyAsString}")
                HttpStatus.UNPROCESSABLE_ENTITY -> throw IllegalArgumentException("PG 결제 실패: ${e.responseBodyAsString}")
                else -> throw IllegalStateException("PG 시스템 오류: ${e.statusCode}")
            }
        }

        // 정책 기반 수수료 계산으로 변경
        val policy = feePolicyRepository.findEffectivePolicy(partner.id, LocalDateTime.now(ZoneOffset.UTC)) ?: throw IllegalArgumentException("Policy not found: ${partner.id}")

        val (fee, net) = FeeCalculator.calculateFee(command.amount, policy.percentage, policy.fixedFee)
        val payment = Payment(
            partnerId = partner.id,
            amount = command.amount,
            appliedFeeRate = policy.percentage,
            feeAmount = fee,
            netAmount = net,
            cardBin = command.cardBin,
            cardLast4 = command.cardLast4,
            approvalCode = approve.approvalCode,
            approvedAt = approve.approvedAt,
            status = PaymentStatus.APPROVED,
        )

        return paymentRepository.save(payment)
    }
}
