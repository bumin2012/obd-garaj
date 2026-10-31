plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}

// Yerel derlemelerde çıktıyı senkronize edilen klasörlerin dışına almak için (ör. OBD_BUILD_ROOT=%LOCALAPPDATA%\obd-garaj-build).
System.getenv("OBD_BUILD_ROOT")?.let { root ->
    allprojects {
        layout.buildDirectory.set(file("$root/${project.name}"))
    }
}
