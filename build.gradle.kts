plugins {
    id("com.diffplug.spotless") version "6.25.0"
}

allprojects {
    group = "com.example"
    version = "0.0.1-SNAPSHOT"
}

subprojects {
    apply(plugin = "java")

    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    dependencies {
        "compileOnly"("org.projectlombok:lombok:1.18.30")
        "annotationProcessor"("org.projectlombok:lombok:1.18.30")
        "testCompileOnly"("org.projectlombok:lombok:1.18.30")
        "testAnnotationProcessor"("org.projectlombok:lombok:1.18.30")
        "testImplementation"("org.springframework.boot:spring-boot-starter-test")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("passed", "failed", "skipped")
        }
    }
}

spotless {
    java {
        target("**/*.java")
        targetExclude("**/build/**")
        googleJavaFormat("1.19.2")
        importOrder("java", "javax", "org", "com", "")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        ktlint()
    }
}
