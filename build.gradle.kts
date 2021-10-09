import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.kotlin.jvm")

    id("org.gradle.java-library")

    id("com.squareup.sqldelight") version "1.5.1"
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

val javaVersion = JavaVersion.VERSION_1_8

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    // Kotlin
    val kotlinVersion = "1.5.30"
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")

    // Kotlin Coroutines core
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2")

    // TinyLog logger
    val tinylogVersion = "2.3.2"
    implementation("org.tinylog:tinylog-api-kotlin:$tinylogVersion")
    implementation("org.tinylog:tinylog-impl:$tinylogVersion")

    // JSON library
    implementation("org.json:json:20210307")

    // SQLDelight
    implementation("com.squareup.sqldelight:coroutines-extensions:1.5.1")

    // Retrofit with OkHttp
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.9.1")

    // JUnit 5
    val junitVersion = "5.8.1"
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")

    // Mockk.io
    testImplementation("io.mockk:mockk:1.12.0")
}

java {
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        // Required minimum Java 8 for Mockk
        jvmTarget = "1.8"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

sqldelight {
    database("PassButlerDatabase") {
        packageName = "de.passbutler.common.database"

        // Used to store generated test databases to verify migrations
        schemaOutputDirectory = file("src/main/sqldelight/databases")

        // Ensure errors in migration files will result in failed compilation
        verifyMigrations = true
    }
}
