plugins {
	kotlin("jvm") version "1.9.25"
	kotlin("plugin.spring") version "1.9.25"
	kotlin("plugin.jpa") version "1.9.25"  // 🆕 ADD THIS
	id("org.springframework.boot") version "3.5.14-SNAPSHOT"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
	maven { url = uri("https://repo.spring.io/snapshot") }
}

// ---------------------------------------------------------------------------
// Phase 4: REST API + Security Layer
//
// Added dependencies:
//   - spring-boot-starter-security   (Spring Security framework)
//   - jjwt-*                         (JWT token generation/validation)
//   - springdoc-openapi              (Swagger/OpenAPI documentation)
//   - spring-security-test           (Security testing utilities)
// ---------------------------------------------------------------------------
dependencies {
	// Core Spring Boot (DI, logging, config)
	implementation("org.springframework.boot:spring-boot-starter")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-web")

	// Phase 3: JPA and Database support
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	runtimeOnly("org.postgresql:postgresql")

	// Phase 4: Security
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("io.jsonwebtoken:jjwt-api:0.12.3")
	runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.3")
	runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.3")

	// Phase 4: API Documentation
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.0")

	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation("org.jetbrains.kotlin:kotlin-reflect")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testImplementation("io.mockk:mockk:1.13.8")
	testImplementation("com.h2database:h2")  // In-memory DB for integration tests
	testImplementation("org.springframework.security:spring-security-test")  // Phase 4: Security testing
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict")
	}
}

allOpen {
	annotation("jakarta.persistence.Entity")
	annotation("jakarta.persistence.MappedSuperclass")
	annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
	useJUnitPlatform()
}
