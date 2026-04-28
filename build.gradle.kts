plugins {
	java
	id("org.springframework.boot") version "4.0.2"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "ru.paperless"
version = "0.0.1-SNAPSHOT"
description = "Отчеты"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(17)
	}
}

repositories {
	mavenCentral()
}

extra["springCloudVersion"] = "2025.1.0"

configurations.all {
	// Убираем Spring Data REST (он тебе не нужен, но может подтягиваться транзитивно)
	exclude(group = "org.springframework.boot", module = "spring-boot-starter-data-rest")
	exclude(group = "org.springframework.data", module = "spring-data-rest-webmvc")
	exclude(group = "org.springframework.data", module = "spring-data-rest-core")
}

dependencies {
	// REST контроллеры
	implementation("org.springframework.boot:spring-boot-starter-webmvc")

	// Валидация (@Valid, @NotNull, etc.) — у тебя сейчас её не хватает
	implementation("org.springframework.boot:spring-boot-starter-validation")

	// JPA + Postgres
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	runtimeOnly("org.postgresql:postgresql")

	// Liquibase
	implementation("org.springframework.boot:spring-boot-starter-liquibase")

	// OpenFeign (если используешь)
	implementation("org.springframework.cloud:spring-cloud-starter-openfeign")

	// Swagger / OpenAPI UI
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0")
	implementation("org.springframework.boot:spring-boot-starter-validation")

	// CSV
	implementation("org.apache.commons:commons-csv:1.10.0")
	// Excel (XLSX)
	implementation("org.apache.poi:poi-ooxml:5.2.5")

	// Lombok
	compileOnly("org.projectlombok:lombok")
	annotationProcessor("org.projectlombok:lombok")

	// Dev
	developmentOnly("org.springframework.boot:spring-boot-docker-compose")

	// Tests
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}