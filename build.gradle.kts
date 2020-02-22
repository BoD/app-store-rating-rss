plugins {
    kotlin("jvm") version "1.3.61"
    id("application")
    id("com.github.ben-manes.versions") version "0.28.0"
}

group = "org.jraf"
version = "1.0.0"

repositories {
    mavenLocal()
    mavenCentral()
}

tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }

    compileTestKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }

    wrapper {
        distributionType = Wrapper.DistributionType.ALL
        gradleVersion = "6.1.1"
    }
}

tasks.register("stage") {
    dependsOn(":installDist")
}

application {
    mainClassName = "org.jraf.appstoreratingrss.main.MainKt"
}

val versions = mapOf("ktor" to "1.3.0")

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("io.ktor:ktor-server-core:${versions["ktor"]}")
    implementation("io.ktor:ktor-server-netty:${versions["ktor"]}")
}