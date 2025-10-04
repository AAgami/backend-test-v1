package im.bigs.pg.external.pg

import im.bigs.pg.application.pg.port.out.PgApproveRequest
import im.bigs.pg.application.pg.port.out.PgApproveResult
import im.bigs.pg.application.pg.port.out.PgClientOutPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 폴백 PG 클라이언트
 * 실제 TestPG API 호출을 시도하고, 실패 시 FakePgClient로 폴백
 */
@Component
class FallbackPgClient(
    private val testPgClient: TestPgClient,
    private val fakePgClient: FakePgClient,
) : PgClientOutPort {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun supports(partnerId: Long): Boolean {
        // TestPgClient와 FakePgClient 모두 짝수 partnerId를 지원
        return partnerId % 2L == 0L
    }

    override fun approve(request: PgApproveRequest): PgApproveResult {
        log.info("폴백 PG 클라이언트 시작: partnerId={}, amount={}", request.partnerId, request.amount)

        return try {
            // 1. 실제 TestPG API 호출 시도
            log.debug("TestPG API 호출 시도 중...")
            val result = testPgClient.approve(request)
            log.info("TestPG API 호출 성공: approvalCode={}", result.approvalCode)
            result
        } catch (e: Exception) {
            // 2. TestPG API 실패 시 FakePgClient로 폴백
            log.warn("TestPG API 호출 실패, FakePgClient로 폴백: {}", e.message)

            try {
                val fallbackResult = fakePgClient.approve(request)
                log.info("FakePgClient 폴백 성공: approvalCode={}", fallbackResult.approvalCode)
                fallbackResult
            } catch (fallbackError: Exception) {
                log.error("FakePgClient 폴백도 실패: {}", fallbackError.message)
                throw IllegalStateException("모든 PG 클라이언트 호출 실패: TestPG=${e.message}, FakePG=${fallbackError.message}", e)
            }
        }
    }
}
