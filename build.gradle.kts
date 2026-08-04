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
}

application {
    mainClass = "com.example.parceltracker.Main"
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
