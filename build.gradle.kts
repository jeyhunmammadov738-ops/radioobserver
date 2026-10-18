// Root build file. Phase 1: :app (minimal Compose entry) + :core:radio (pure JVM logic).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}
