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
// Phase 3: Database Persistence Layer
//
// Added dependencies:
//   - spring-boot-starter-data-jpa  (JPA support)
//   - org.postgresql:postgresql     (PostgreSQL driver)
//   - com.h2database:h2             (In-memory DB for tests)
//   - spring-boot-starter-web       (Web/REST support)
//
// Still EXCLUDED (will be added in later phases):
//   - spring-boot-starter-security  (no security yet - Phase 4)
//   - spring-security-test          (no security to test yet - Phase 4)
// ---------------------------------------------------------------------------
dependencies {
	// Core Spring Boot (DI, logging, config)
	implementation("org.springframework.boot:spring-boot-starter")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-web")

	// 🆕 Phase 3: JPA and Database support
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	runtimeOnly("org.postgresql:postgresql")

	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation("org.jetbrains.kotlin:kotlin-reflect")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testImplementation("io.mockk:mockk:1.13.8")
	testImplementation("com.h2database:h2")  // 🆕 In-memory DB for integration tests
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
