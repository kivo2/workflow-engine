plugins {
    id("org.springframework.boot") version "3.3.8"
    id("io.spring.dependency-management") version "1.1.7"
    `java-library`
}

dependencies {
    implementation(project(":common"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.kafka:spring-kafka")
    runtimeOnly("org.postgresql:postgresql")
    testRuntimeOnly("com.h2database:h2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.springframework.kafka:spring-kafka-test")
}

val integrationTest: SourceSet =
    sourceSets.create("integrationTest") {
        java.srcDir("src/integrationTest/java")
        resources.srcDir("src/integrationTest/resources")
        compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
        runtimeClasspath += sourceSets.main.get().output + sourceSets.test.get().output
    }

configurations["integrationTestImplementation"].extendsFrom(configurations["testImplementation"])
configurations["integrationTestRuntimeOnly"].extendsFrom(configurations["testRuntimeOnly"])

dependencies {
    "integrationTestImplementation"("org.springframework.boot:spring-boot-testcontainers")
    "integrationTestImplementation"("org.testcontainers:junit-jupiter")
    "integrationTestImplementation"("org.testcontainers:postgresql")
    "integrationTestRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

val integrationTestTask =
    tasks.register<Test>("integrationTest") {
        description = "Runs integration tests against a full Spring context."
        group = "verification"
        testClassesDirs = integrationTest.output.classesDirs
        classpath = integrationTest.runtimeClasspath
        useJUnitPlatform()
        outputs.upToDateWhen { false }
        testLogging { events("passed", "failed", "skipped") }
        System.getenv("DOCKER_HOST")?.let { environment("DOCKER_HOST", it) }
        System.getenv("DOCKER_API_VERSION")?.let { systemProperty("api.version", it) }
    }

tasks.named<ProcessResources>("processIntegrationTestResources") {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

tasks.named("check") { dependsOn(integrationTestTask) }
