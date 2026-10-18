plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
}

tasks.withType<Test> {
    // Host-only verification: fail loudly, report skipped tests explicitly.
    testLogging {
        events("passed", "failed", "skipped")
    }
}
