import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

plugins {
    kotlin("jvm") version "1.3.61"
    id("application")
    id("com.github.ben-manes.versions") version "0.28.0"
}

group = "org.jraf"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

val versions = mapOf(
    "gradle" to "6.2.1",
    "ktor" to "1.3.1",
    "klibappstorerating" to "1.1.1",
    "logback" to "1.2.3",
    "kotlinxHtml" to "0.7.3"
)

tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }

    compileTestKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }

    wrapper {
        distributionType = Wrapper.DistributionType.ALL
        gradleVersion = versions["gradle"]
    }
}

tasks.withType<DependencyUpdatesTask> {
    rejectVersionIf {
        candidate.version.contains("alpha", true)
    }
}

tasks.register("stage") {
    dependsOn(":installDist")
}

application {
    mainClassName = "org.jraf.appstoreratingrss.main.MainKt"
}

dependencies {
    // Kotlin
    implementation(kotlin("stdlib-jdk8"))

    // Ktor
    implementation("io.ktor:ktor-server-core:${versions["ktor"]}")
    implementation("io.ktor:ktor-server-netty:${versions["ktor"]}")

    // App Store Rating
//    implementation("org.jraf:klibappstorerating:${versions["klibappstorerating"]}")
     implementation("com.github.BoD:klibappstorerating:${versions["klibappstorerating"]}")

    // Logback
    runtimeOnly("ch.qos.logback:logback-classic:${versions["logback"]}")

    // Kotlinx Html
    implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:${versions["kotlinxHtml"]}")
}
