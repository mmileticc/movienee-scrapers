plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
    id("org.jetbrains.dokka") version "2.1.0"
    application
}

group = "com.milinko"
version = "1.0.0"

kotlin {
    jvmToolchain(21)
}

application {
    mainClass = "com.milinko.scraping.MainKt"
}

// API documentation generated from the KDoc comments: ./gradlew dokkaGenerate -> build/dokka/html
dokka {
    moduleName.set("movienee-scrapers")
    dokkaSourceSets.main {
        includes.from("dokka/module.md")
        sourceLink {
            localDirectory.set(file("src/main/kotlin"))
            remoteUrl("https://github.com/mmileticc/movienee-scrapers/tree/main/src/main/kotlin")
            remoteLineSuffix.set("#L")
        }
    }
}

dependencies {
    implementation("org.jsoup:jsoup:1.17.2")
    implementation("com.microsoft.playwright:playwright:1.63.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("ch.qos.logback:logback-classic:1.4.14")
}

// Playwright CLI (same version as the library), e.g. to install Chromium:
// ./gradlew playwrightCli --args="install chromium"
tasks.register<JavaExec>("playwrightCli") {
    group = "application"
    description = "Runs the Playwright CLI (e.g. browser installation)."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.microsoft.playwright.CLI")
}
