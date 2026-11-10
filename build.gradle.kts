// Raiz do projeto Android (DowTube).
// O Chaquopy só pode ser aplicado em UM módulo por app (ver app/build.gradle.kts).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.chaquopy) apply false
}
