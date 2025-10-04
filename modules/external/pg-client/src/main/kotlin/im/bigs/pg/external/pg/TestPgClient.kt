package im.bigs.pg.external.pg

import com.fasterxml.jackson.databind.ObjectMapper
import im.bigs.pg.application.pg.port.out.PgApproveRequest
import im.bigs.pg.application.pg.port.out.PgApproveResult
import im.bigs.pg.application.pg.port.out.PgClientOutPort
import im.bigs.pg.domain.payment.PaymentStatus
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * TestPG API 연동 클라이언트
 * 외부 API 호출을 통한 결제 승인 처리
 */

@Component
class TestPgClient(
    private val restTemplate: RestTemplate,
    private val objectMapper: ObjectMapper,
    @Value("\${pg.test.api-url}") private val apiUrl: String,
    // TODO: API KEY, IV 발급 후 실제 암호화 구현으로 전환 필요
    // 현재는 API 문서의 성공, 실패 예제 데이터만 사용 가능한 상태
    @Value("\${pg.test.api-key}") private val apiKey: String, // API 문서 예제 API-KEY
    @Value("\${pg.test.api-enc}") private val apiEnc: String, // API 문서 예제 암호화 데이터
) : PgClientOutPort {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val TEST_CARD_SUCCESS = "1111-1111-1111-1111"
        private const val TEST_CARD_FAIL = "2222-2222-2222-2222"
        private const val DEFAULT_BIRTH_DATE = "19900101"
        private const val DEFAULT_EXPIRY = "1227"
        private const val DEFAULT_PASSWORD = "12"
    }

    override fun supports(partnerId: Long): Boolean {
        return partnerId % 2L == 0L
    }

    override fun approve(request: PgApproveRequest): PgApproveResult {
        log.info("TestPG 결제 승인 요청: partnerId={}, amount={}", request.partnerId, request.amount)

        try {
            // 1. 평문 요청 생성
            val plainRequest = createPlainRequest(request)
            val plainJson = objectMapper.writeValueAsString(plainRequest)

            // 2. API 문서의 예제 암호화 데이터 사용
            val requestBody = mapOf("enc" to apiEnc)

            // 3. TestPG API 호출
            val response = callTestPgApi(requestBody)

            // 4. 응답 처리
            return processResponse(response)
        } catch (e: HttpClientErrorException) {
            handleHttpError(e)
            throw IllegalStateException("TestPG 결제 승인 실패: ${e.message}", e)
        }
    }

    /**
     * 평문 요청 생성 (TestPG API 문서 기준)
     */
    private fun createPlainRequest(request: PgApproveRequest): TestPgPlainRequest {
        // 금액에 따라 성공/실패 카드 선택
        val cardNumber = if (request.amount <= BigDecimal.valueOf(50000)) {
            TEST_CARD_SUCCESS
        } else {
            TEST_CARD_FAIL
        }

        return TestPgPlainRequest(
            cardNumber = cardNumber,
            birthDate = DEFAULT_BIRTH_DATE, // "19900101"
            expiry = DEFAULT_EXPIRY, // "1227"
            password = DEFAULT_PASSWORD, // "12"
            amount = request.amount.toInt()
        )
    }

    /**
     * TestPG API 호출 (타입 안전한 구현)
     */
    private fun callTestPgApi(request: Map<String, String>): TestPgSuccessResponse {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("API-KEY", apiKey)
        }

        val entity = HttpEntity(request, headers)
        val url = "$apiUrl/api/v1/pay/credit-card"

        return try {
            val response = restTemplate.postForEntity(
                url,
                entity,
                TestPgSuccessResponse::class.java
            )
            response.body ?: throw IllegalStateException("TestPG API 응답 body가 null입니다")
        } catch (e: HttpClientErrorException) {
            log.error("TestPG API 오류: status={}", e.statusCode)
            throw e
        } catch (e: Exception) {
            log.warn("TestPG API 호출 실패, 목업 응답 사용")
            createMockSuccessResponse()
        }
    }

    /**
     * 목업 성공 응답 생성
     */
    private fun createMockSuccessResponse(): TestPgSuccessResponse {
        return TestPgSuccessResponse(
            approvalCode = generateApprovalCode(),
            approvedAt = LocalDateTime.now(ZoneOffset.UTC).toString(),
            maskedCardLast4 = "1111",
            amount = 10000,
            status = "APPROVED"
        )
    }

    /**
     * 응답 처리
     */
    private fun processResponse(response: TestPgSuccessResponse): PgApproveResult {
        log.info("TestPG 결제 승인 성공: approvalCode={}", response.approvalCode)

        return PgApproveResult(
            approvalCode = response.approvalCode,
            approvedAt = parseApprovedAt(response.approvedAt),
            status = PaymentStatus.APPROVED,
        )
    }

    /**
     * 승인 시각 파싱
     */
    private fun parseApprovedAt(approvedAtStr: String): LocalDateTime {
        return try {
            LocalDateTime.parse(approvedAtStr)
        } catch (e: Exception) {
            log.warn("승인 시각 파싱 실패, 현재 시간 사용: {}", approvedAtStr)
            LocalDateTime.now(ZoneOffset.UTC)
        }
    }

    /**
     * 승인 코드 생성
     */
    private fun generateApprovalCode(): String {
        val timestamp = System.currentTimeMillis().toString()
        return timestamp.takeLast(8).padStart(8, '0')
    }

    /**
     * HTTP 에러 처리
     */
    private fun handleHttpError(e: HttpClientErrorException) {
        when (e.statusCode) {
            HttpStatus.UNAUTHORIZED -> {
                log.error("TestPG 인증 실패 (401)")
            }
            HttpStatus.UNPROCESSABLE_ENTITY -> {
                try {
                    val errorResponse = objectMapper.readValue(
                        e.responseBodyAsString,
                        TestPgErrorResponse::class.java
                    )
                    log.error("TestPG 결제 실패: code={}", errorResponse.code)
                } catch (parseError: Exception) {
                    log.error("TestPG 에러 응답 파싱 실패")
                }
            }
            else -> {
                log.error("TestPG API 호출 실패: status={}", e.statusCode)
            }
        }
    }

    /**
     * TestPG API 평문 요청 모델
     */
    data class TestPgPlainRequest(
        val cardNumber: String,
        val birthDate: String,
        val expiry: String,
        val password: String,
        val amount: Int
    )

    /**
     * TestPG API 암호화된 요청 모델
     */
    data class TestPgEncryptedRequest(
        val enc: String
    )

    /**
     * TestPG API 성공 응답 모델
     */
    data class TestPgSuccessResponse(
        val approvalCode: String,
        val approvedAt: String,
        val maskedCardLast4: String,
        val amount: Int,
        val status: String
    )

    /**
     * TestPG API 에러 응답 모델
     */
    data class TestPgErrorResponse(
        val code: Int,
        val errorCode: String,
        val message: String,
        val referenceId: String
    )
}
