plugins {
    // Provides `run` and `installDist`/`distZip` — the Gradle-idiomatic replacement for
    // the Maven copy-dependencies + manifest-classpath packaging.
    application
}

group = "com.example"
version = "1.0.0"

// Bump to 25/26 as your JDK/toolchain allows; virtual threads only need 21+.
val helidonVersion = "4.1.4"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Helidon BOM aligns all io.helidon.* versions (and manages Jackson).
    implementation(platform("io.helidon:helidon-bom:$helidonVersion"))

    // Web server (virtual-thread based in Helidon 4)
    implementation("io.helidon.webserver:helidon-webserver")
    implementation("io.helidon.http.media:helidon-http-media-jackson")
    implementation("io.helidon.openapi:helidon-openapi")
    implementation("io.helidon.config:helidon-config-yaml")
    implementation("io.helidon.logging:helidon-logging-jul")

    // Storage
    implementation("org.postgresql:postgresql:42.7.4")
    implementation("com.zaxxer:HikariCP:5.1.0")

    // JSON (versions managed by the Helidon BOM above)
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // --- Testing ---
    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.26.3")
    // WireMock as the local "sandbox" HTTP server for courier/geocoding adapter tests.
    // The standalone (shaded) jar keeps its Jetty/Jackson off the app classpath.
    testImplementation("org.wiremock:wiremock-standalone:3.9.2")
    // Testcontainers: a real Postgres for persistence-adapter tests (skipped without Docker).
    testImplementation("org.testcontainers:postgresql:1.20.4")
    testImplementation("org.testcontainers:junit-jupiter:1.20.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass = "com.example.parceltracker.Main"
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
    // Surface a one-line summary (incl. skipped Testcontainers ITs when Docker is absent).
    addTestListener(object : TestListener {
        override fun beforeSuite(suite: TestDescriptor) {}
        override fun beforeTest(test: TestDescriptor) {}
        override fun afterTest(test: TestDescriptor, result: TestResult) {}
        override fun afterSuite(suite: TestDescriptor, result: TestResult) {
            if (suite.parent == null) {
                println("Tests: ${result.testCount} — ${result.successfulTestCount} passed, " +
                        "${result.failedTestCount} failed, ${result.skippedTestCount} skipped")
            }
        }
    })
}
