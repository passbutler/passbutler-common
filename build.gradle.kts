plugins {
    id("org.jetbrains.kotlin.jvm")

    id("org.gradle.java-library")

    id("com.squareup.sqldelight") version "1.3.0"
}

buildscript {
    repositories {
        mavenLocal()
        mavenCentral()
        google()
    }
}

version = "1.0.0"
group = "de.passbutler.common"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    // Kotlin
    val kotlinVersion = "1.3.72"
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:$kotlinVersion")

    // Kotlin Coroutines core
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.7")

    // TinyLog logger
    val tinylogVersion = "2.1.2"
    implementation("org.tinylog:tinylog-api-kotlin:$tinylogVersion")
    implementation("org.tinylog:tinylog-impl:$tinylogVersion")

    // JSON library
    implementation("org.json:json:20200518")

    // SQLDelight
    implementation("com.squareup.sqldelight:coroutines-extensions:1.4.0")

    // Retrofit with OkHttp
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.7.2")

    // JUnit 5
    val junitVersion = "5.6.2"
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")

    // Mockk.io
    testImplementation("io.mockk:mockk:1.10.0")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

sqldelight {
    database("PassButlerDatabase") {
        packageName = "de.passbutler.common.database"
    }
}