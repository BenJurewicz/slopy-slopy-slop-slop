plugins {
    java
    id("org.springframework.boot") version "4.0.3"
}

group = "pl.cloudmigration"
version = "0.0.1-SNAPSHOT"

repositories { mavenCentral() }

java {
    toolchain { languageVersion = JavaLanguageVersion.of(23) }
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.0.3"))
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test { useJUnitPlatform() }
