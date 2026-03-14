import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.2.3"
    id("io.spring.dependency-management") version "1.1.4"
    kotlin("jvm") version "1.9.22"
    kotlin("plugin.spring") version "1.9.22"
    kotlin("plugin.jpa") version "1.9.22"
}

group = "com.decisionfabric"
version = "0.0.1-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

extra["awsSdkVersion"] = "2.23.17"
extra["resilience4jVersion"] = "2.2.0"
extra["droolsVersion"] = "9.44.0.Final"
extra["testcontainersVersion"] = "1.19.6"

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Kotlin
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // JWT (JWKS-based validation)
    implementation("org.springframework.security:spring-security-oauth2-resource-server")
    implementation("org.springframework.security:spring-security-oauth2-jose")

    // Database
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // DMN Engine — Drools
    implementation("org.kie:kie-api:${property("droolsVersion")}")
    implementation("org.drools:drools-core:${property("droolsVersion")}")
    implementation("org.kie:kie-dmn-core:${property("droolsVersion")}")
    implementation("org.kie:kie-dmn-feel:${property("droolsVersion")}")

    // Resilience4j
    implementation("io.github.resilience4j:resilience4j-spring-boot3:${property("resilience4jVersion")}")
    implementation("io.github.resilience4j:resilience4j-kotlin:${property("resilience4jVersion")}")

    // HTTP Client
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Observability
    implementation("io.micrometer:micrometer-registry-cloudwatch2")
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")

    // AWS SDK
    implementation(platform("software.amazon.awssdk:bom:${property("awsSdkVersion")}"))
    implementation("software.amazon.awssdk:ssm")
    implementation("software.amazon.awssdk:secretsmanager")

    // Configuration processor
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation(platform("org.testcontainers:testcontainers-bom:${property("testcontainersVersion")}"))
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("io.mockk:mockk:1.13.10")
    testImplementation("com.ninja-squad:springmockk:4.0.2")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict"
        jvmTarget = "21"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
