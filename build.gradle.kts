plugins {
	kotlin("jvm") version "1.9.25"
	kotlin("plugin.spring") version "1.9.25"
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
// TDD phase: minimal dependencies for Service-layer unit tests only.
//
// Intentionally EXCLUDED (re-enable later when those layers are introduced):
//   - spring-boot-starter-data-jpa  (no DB in TDD phase)
//   - spring-boot-starter-security  (no security in TDD phase)
//   - spring-boot-starter-web       (no controllers / web layer yet)
//   - org.postgresql:postgresql     (no DB in TDD phase)
//   - spring-security-test          (no security to test)
//   - kotlin("plugin.jpa") + allOpen(JPA annotations) — not needed without JPA
// ---------------------------------------------------------------------------
dependencies {
	// Core Spring Boot (DI, logging, config). Kept minimal so services can be
	// wired with @Service / constructor injection if desired, but unit tests
	// do NOT need to bootstrap a Spring context.
	implementation("org.springframework.boot:spring-boot-starter")
	implementation("org.springframework.boot:spring-boot-starter-validation")

	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation("org.jetbrains.kotlin:kotlin-reflect")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testImplementation("io.mockk:mockk:1.13.8")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}
