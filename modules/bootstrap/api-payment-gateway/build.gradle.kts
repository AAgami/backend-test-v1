tasks.jar {
    enabled = false
}

tasks.bootJar {
    enabled = true
}

dependencies {
    implementation(projects.modules.domain)
    implementation(projects.modules.application)
    implementation(projects.modules.infrastructure.persistence)
    implementation(projects.modules.external.pgClient)
    implementation(libs.spring.boot.starter.jpa)
    implementation(libs.bundles.bootstrap)
    // API 문서 & 테스트용 Swagger UI 사용을 위한 추가 (springdoc)
    implementation(libs.springdoc.openapi.starter.webmvc.ui)
    // 개발 단계에서 이용하기 위해 testImplementation -> implementation로 변경
    implementation(libs.database.h2)
    testImplementation(libs.bundles.test)
    testImplementation(libs.spring.boot.starter.test) {
        exclude(module = "mockito-core")
    }
    testImplementation(libs.spring.mockk)
}
