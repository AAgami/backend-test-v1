package im.bigs.pg.external.pg.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.web.client.RestTemplate

/**
 * TestPG API 연동을 위한 RestTemplate Bean 제공
 */
@Configuration
class RestTemplateConfig {
    /**
     * RestTemplate Bean 등록
     * HTTP 클라이언트 기본 설정
     * 타임아웃 및 에러 처리 설정
     */
    @Bean
    fun restTemplate(): RestTemplate {
        return RestTemplate().apply {
            requestFactory = HttpComponentsClientHttpRequestFactory().apply {
                // 초기설정 60초
                setConnectTimeout(60000)
                setReadTimeout(60000)
            }
        }
    }
}
