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

val xjc by configurations.creating

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.0.3"))
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.glassfish.jaxb:jaxb-runtime:4.0.9")
    implementation("org.docx4j:docx4j-JAXB-ReferenceImpl:17.3.2")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")
    xjc("org.glassfish.jaxb:jaxb-xjc:4.0.9")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform { excludeTags("integration") }
    inputs.files("../examples/docx/word-edited-application.docx", "../examples/docx/word-filled-template.docx")
}
val integrationTest by tasks.registering(Test::class) {
    description = "Run tests against the local BaseX container"
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("integration") }
    shouldRunAfter(tasks.test)
}


val generatedXml = layout.buildDirectory.dir("generated/sources/xjc")
val generateXmlModels by tasks.registering(JavaExec::class) {
    classpath = xjc
    mainClass.set("com.sun.tools.xjc.XJCFacade")
    inputs.dir("src/main/resources/schemas")
    outputs.dir(generatedXml)
    args = listOf("-d", generatedXml.get().asFile.absolutePath, "-p", "pl.cloudmigration.xml.model") +
        listOf("competition", "application", "evaluation", "decision").map {
            file("src/main/resources/schemas/$it.xsd").absolutePath
        }
    doFirst { outputs.files.singleFile.mkdirs() }
}
sourceSets.main { java.srcDir(generatedXml) }
tasks.compileJava { dependsOn(generateXmlModels) }

// Build the frontend explicitly before packaging; Gradle never installs pnpm packages.
tasks.processResources {
    from("../frontend/build") { into("static") }
}
tasks.bootJar {
    val frontendIndex = layout.projectDirectory.file("../frontend/build/200.html")
    doFirst {
        check(frontendIndex.asFile.exists()) {
            "Build the frontend first: cd ../frontend && pnpm build"
        }
    }
}

tasks.register<JavaExec>("generateExamples") {
    group = "documentation"
    description = "Generate XML and DOCX demonstration fixtures"
    dependsOn(tasks.classes)
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("pl.cloudmigration.demo.GenerateExamples")
    args("../examples")
}
