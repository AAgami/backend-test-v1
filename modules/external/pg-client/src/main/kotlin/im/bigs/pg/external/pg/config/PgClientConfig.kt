package im.bigs.pg.external.pg.config

import im.bigs.pg.application.pg.port.out.PgClientOutPort
import im.bigs.pg.external.pg.FakePgClient
import im.bigs.pg.external.pg.FallbackPgClient
import im.bigs.pg.external.pg.MockPgClient
import im.bigs.pg.external.pg.TestPgClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

/**
 * PG 클라이언트 설정
 * 폴백 전략에 따라 적절한 클라이언트를 선택
 */
@Configuration
class PgClientConfig {

    @Value("\${pg.test.fallback-enabled}")
    private var fallbackEnabled: Boolean = true

    /**
     * 폴백이 활성화된 경우 FallbackPgClient를 사용
     * 비활성화된 경우 TestPgClient를 직접 사용
     */
    @Bean
    @Primary
    fun pgClient(
        testPgClient: TestPgClient,
        fakePgClient: FakePgClient,
        mockPgClient: MockPgClient
    ): PgClientOutPort {
        return if (fallbackEnabled) {
            // 폴백 전략: TestPgClient → FakePgClient
            FallbackPgClient(testPgClient, fakePgClient)
        } else {
            // 직접 TestPgClient 사용 (폴백 없음)
            testPgClient
        }
    }
}
