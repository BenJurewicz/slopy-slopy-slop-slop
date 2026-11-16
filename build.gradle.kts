plugins {
    id("com.diffplug.spotless") version "8.11.0"
}

repositories { mavenCentral() }

spotless {
    java {
        target("backend/src/*/java/**/*.java", "infra/scripts/*.java")
        eclipse("4.39").configFile("backend/eclipse-formatter.xml")
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts", "backend/*.gradle.kts")
        ktlint("1.8.0")
    }
}
