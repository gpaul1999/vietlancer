plugins {
    // Tự động tải JDK 25 nếu máy chưa có (toolchain auto-provisioning)
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "vietlancer-backend"
