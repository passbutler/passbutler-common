plugins {
    id("org.jetbrains.kotlin.jvm")

    id("org.gradle.java-library")

    id("com.squareup.sqldelight") version "1.4.4"
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

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    // Kotlin
    val kotlinVersion = "1.4.10"
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")

    // Kotlin Coroutines core
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.1")

    // TinyLog logger
    val tinylogVersion = "2.2.0"
    implementation("org.tinylog:tinylog-api-kotlin:$tinylogVersion")
    implementation("org.tinylog:tinylog-impl:$tinylogVersion")

    // JSON library
    implementation("org.json:json:20200518")

    // SQLDelight
    implementation("com.squareup.sqldelight:coroutines-extensions:1.4.4")

    // Retrofit with OkHttp
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.9.0")

    // JUnit 5
    val junitVersion = "5.7.0"
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")

    // Mockk.io
    testImplementation("io.mockk:mockk:1.10.2")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

sqldelight {
    database("PassButlerDatabase") {
        packageName = "de.passbutler.common.database"
    }
}