plugins { java; id("org.springframework.boot") version "4.1.1"; id("io.spring.dependency-management") version "1.1.7" }
group = "org.cvp"; version = "0.0.1-SNAPSHOT"
java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }
repositories { mavenCentral(); maven { url = uri("https://repo.spring.io/milestone") } }
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("org.springframework.ai:spring-ai-starter-mcp-server-webmvc:1.1.2")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
tasks.withType<Test> { useJUnitPlatform() }
