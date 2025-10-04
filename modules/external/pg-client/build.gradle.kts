tasks.jar {
    enabled = true
}

tasks.bootJar {
    enabled = false
}

dependencies {
    implementation(projects.modules.application)
    implementation(projects.modules.domain)
    implementation(libs.spring.boot.starter.web)
    implementation("org.apache.httpcomponents.client5:httpclient5:5.2.1")

    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("org.junit.jupiter:junit-jupiter:5.8.1")
    testImplementation("org.springframework:spring-test")
}
