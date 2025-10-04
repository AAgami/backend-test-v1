package im.bigs.pg.external.pg

import com.fasterxml.jackson.databind.ObjectMapper
import im.bigs.pg.application.pg.port.out.PgApproveRequest
import im.bigs.pg.application.pg.port.out.PgApproveResult
import im.bigs.pg.application.pg.port.out.PgClientOutPort
import im.bigs.pg.domain.payment.PaymentStatus
import im.bigs.pg.external.pg.encryption.AesGcmEncryptor
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
/**
 * Test PG API 시뮬레이션 어댑터
 * * API-KEY/IV 없이 Test PG API 문서 스펙을 임시 준수하는 시뮬레이션
 *
 * Test PG API 문서 기반 시나리오:
 * 1. 성공 케이스 (200)
 *    - 카드번호: 1111-1111-1111-1111 (모든 금액)
 *    - 카드번호: 2222-2222-2222-2222 (금액 ≤ 50,000)
 *    - 응답: {
 *        "approvalCode": "09304110",
 *        "approvedAt": "2025-09-30T07:39:26.675723",
 *        "maskedCardLast4": "1111",
 *        "amount": 10000,
 *        "status": "APPROVED"
 *      }
 *
 * !! 임시로 금액별 실패 케이스를 구현 (사유: API Key, IV X)
 * 2. 실패 케이스 (422)
 *    - 카드번호: 2222-2222-2222-2222 + 금액 > 50,000
 *    - 금액별 명시 매핑:
 *      - 51,000원: 1001 STOLEN_OR_LOST - 도난 또는 분실된 카드입니다.
 *      - 52,000원: 1003 EXPIRED_OR_BLOCKED - 정지되었거나 만료된 카드입니다.
 *      - 53,000원: 1004 TAMPERED_CARD - 위조 또는 변조된 카드입니다.
 *      - 54,000원: 1005 TAMPERED_CARD - 위조 또는 변조된 카드입니다. (허용되지 않은 카드)
 *      - 그 외 > 50,000원: 1002 INSUFFICIENT_LIMIT - 한도가 초과되었습니다.
 *    - 응답: {
 *        "code": 1002,
 *        "errorCode": "INSUFFICIENT_LIMIT", *        "message": "한도가 초과되었습니다.",
 *        "referenceId": "ff2ab0c6-b77c-4dfe-a294-4ec641f8b55b"
 *      }
 *
 * 3. 인증 실패 (401 Unauthorized)
 *    - API-KEY 헤더 누락 (partnerId = -1)
 *    - API-KEY 포맷 오류 (partnerId = -2) *    - 미등록 API-KEY (partnerId = -3)
 *
 * 4. 요청 유효성 검사 (400 Bad Request)
 *    - 금액이 정수가 아님 (소수점 포함)
 *    - 카드 BIN과 Last4 불일치
 *    - 지원하지 않는 파트너 ID
 */
@Component
class FakePgClient(
    private val objectMapper: ObjectMapper,
    private val aesGcmEncryptor: AesGcmEncryptor,
) : PgClientOutPort {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        // API 문서 테스트 시나리오용 상수
        private const val TEST_CARD_SUCCESS = "1111-1111-1111-1111"
        private const val TEST_CARD_FAIL = "2222-2222-2222-2222"
        private const val DEFAULT_BIRTH_DATE = "19900101"
        private const val DEFAULT_EXPIRY = "1227"
        private const val DEFAULT_PASSWORD = "12"

        /**
         * Test PG API 문서 스펙에 따른 결정적 금액별 에러 매핑
         * 카드 2222-2222-2222-2222일 때만 적용
         */
        private val ERROR_BY_AMOUNT = mapOf(
            51_000L to PgError(1001, "STOLEN_OR_LOST", "도난 또는 분실된 카드입니다."),
            52_000L to PgError(1003, "EXPIRED_OR_BLOCKED", "정지되었거나 만료된 카드입니다."),
            53_000L to PgError(1004, "TAMPERED_CARD", "위조 또는 변조된 카드입니다."),
            54_000L to PgError(1005, "TAMPERED_CARD", "위조 또는 변조된 카드입니다. (허용되지 않은 카드)")
        )

        private const val SUCCESS_BOUNDARY_AMOUNT = 50_000L
    }

    override fun supports(partnerId: Long): Boolean {
        return partnerId % 2L == 0L
    }

    override fun approve(request: PgApproveRequest): PgApproveResult {
        log.info("Test PG API 시뮬레이션: partnerId={}, amount={}", request.partnerId, request.amount)

        try {
            // 1. Test PG API 요청 암호화
            val apiKey = generateApiKey(request.partnerId)
            val iv = generateIv(request.partnerId)

            // Test PG API 요청 스키마로 변환
            val cardNumber = "${request.cardBin}-${request.cardLast4}-${request.cardBin}-${request.cardLast4}"
            val requestJson = aesGcmEncryptor.createTestPgRequest(
                cardNumber = cardNumber,
                amount = request.amount.toInt()
            )

            // AES-256-GCM 암호화
            val encryptedRequest = aesGcmEncryptor.encrypt(requestJson, apiKey, iv)
            log.debug("Test PG API 요청 암호화 시뮬레이션 완료: encrypted 길이={}", encryptedRequest.length)

            // 2. Test PG API 인증 실패 시나리오
            when (request.partnerId) {
                -1L -> {
                    log.warn("Test PG API 인증 실패: API-KEY 헤더 누락")
                    throw HttpClientErrorException(HttpStatus.UNAUTHORIZED)
                }
                -2L -> {
                    log.warn("Test PG API 인증 실패: API-KEY 포맷 오류")
                    throw HttpClientErrorException(HttpStatus.UNAUTHORIZED)
                }
                -3L -> {
                    log.warn("Test PG API 인증 실패: 미등록 API-KEY")
                    throw HttpClientErrorException(HttpStatus.UNAUTHORIZED)
                }
            }

            // 3. Test PG API 카드번호 검증
            val cardBin = request.cardBin
            val cardLast4 = request.cardLast4

            // Test PG API 문서의 테스트 카드 규칙 적용
            // 성공: 1111-1111-1111-1111, 실패: 2222-2222-2222-2222
            if (cardBin == null || cardLast4 == null || (cardBin != "1111" && cardBin != "2222") || (cardBin == "1111" && cardLast4 != "1111") ||
                (cardBin == "2222" && cardLast4 != "2222")
            ) {
                log.warn("Test PG API 카드번호 검증 실패: cardBin={}, cardLast4={}", cardBin, cardLast4)
                throw IllegalArgumentException("Invalid card number.")
            }

            // 4. Test PG API 실패 카드(2222) 에러 케이스 시뮬레이션
            if (cardBin == "2222") {
                val amount = request.amount.longValueExact()
                val error = mapErrorFor2222(amount)

                error?.let {
                    log.warn("Test PG API 결제 실패: cardBin=2222, amount={}, errorCode={}", amount, it.errorCode)
                    val errorResponse = TestPgErrorResponse(
                        code = it.code,
                        errorCode = it.errorCode,
                        message = it.message,
                        referenceId = UUID.randomUUID().toString()
                    )
                    val responseBody = objectMapper.writeValueAsString(errorResponse)
                    throw HttpClientErrorException.create(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "TestPG 결제 실패",
                        org.springframework.http.HttpHeaders(),
                        responseBody.toByteArray(),
                        java.nio.charset.StandardCharsets.UTF_8
                    )
                }
            }

            // 5. Test PG API 성공 응답 시뮬레이션
            // API 문서 스펙과 동일한 형식으로 응답 생성
            val approvalCode = generateApprovalCode()
            val approvedAt = LocalDateTime.now(ZoneOffset.UTC)

            // Test PG API 응답 암호화 시뮬레이션
            val responseJson = """
            {
                "approvalCode": "$approvalCode",
                "approvedAt": "$approvedAt",
                "maskedCardLast4": "${request.cardLast4 ?: "1111"}",
                "amount": ${request.amount},
                "status": "APPROVED"
            }
            """.trimIndent()

            val encryptedResponse = aesGcmEncryptor.encrypt(responseJson, apiKey, iv)
            log.debug("Test PG API 응답 암호화 시뮬레이션 완료: encrypted 길이={}", encryptedResponse.length)

            log.info("Test PG API 결제 성공: approvalCode={}, amount={}", approvalCode, request.amount)

            return PgApproveResult(
                approvalCode = approvalCode,
                approvedAt = approvedAt,
                status = PaymentStatus.APPROVED
            )
        } catch (e: HttpClientErrorException) {
            // Test PG API 에러 응답 시뮬레이션
            when (e.statusCode) {
                HttpStatus.UNAUTHORIZED -> log.error("Test PG API 인증 실패 (401): {}", e.message)
                HttpStatus.UNPROCESSABLE_ENTITY -> log.error("Test PG API 결제 실패 (422): {}", e.responseBodyAsString)
                else -> log.error("Test PG API 호출 실패: status={}", e.statusCode)
            }
            throw e
        }
    }

    /**
     * Test PG API 문서 스펙에 따른 2222 카드의 금액별 에러 매핑
     */
    private fun mapErrorFor2222(amount: Long): PgError? {
        // 50,000원 이하면 성공
        if (amount <= SUCCESS_BOUNDARY_AMOUNT) return null

        // 명시적으로 매핑된 금액이 있으면 해당 에러
        ERROR_BY_AMOUNT[amount]?.let { return it }

        // 그 외 50,000원 초과는 기본 한도초과 에러
        return PgError(1002, "INSUFFICIENT_LIMIT", "한도가 초과되었습니다.")
    }

    /**
     * Test PG API 승인 코드 생성 시뮬레이션
     * 실제 API와 유사한 8자리 승인 코드 생성
     */
    private fun generateApprovalCode(): String {
        val timestamp = System.currentTimeMillis().toString()
        return timestamp.takeLast(8).padStart(8, '0')
    }

    /**
     * Test PG API-KEY 생성 시뮬레이션
     * partnerId 기반으로 시뮬레이션용 API-KEY 생성
     */
    private fun generateApiKey(partnerId: Long): String {
        return when (partnerId) {
            -1L -> "" // API-KEY 헤더 누락 시뮬레이션
            -2L -> "INVALID-FORMAT" // API-KEY 포맷 오류 시뮬레이션
            -3L -> "00000000-0000-4000-8000-000000000000" // 미등록 API-KEY 시뮬레이션
            else -> "11111111-1111-4111-8111-111111111111" // 정상 API-KEY 시뮬레이션
        }
    }

    /**
     * Test PG IV 생성 시뮬레이션
     * partnerId 기반으로 시뮬레이션용 IV 생성
     */
    private fun generateIv(partnerId: Long): String {
        return when (partnerId) {
            -1L, -2L, -3L -> "" // 인증 실패 시나리오에서는 IV 없음
            else -> "dGVzdC1wZy1pdi0xMg" // Base64URL 인코딩된 시뮬레이션 IV
        }
    }

    private data class PgError(
        val code: Int,
        val errorCode: String,
        val message: String
    )

    private data class TestPgErrorResponse(
        val code: Int,
        val errorCode: String,
        val message: String,
        val referenceId: String
    )
}
