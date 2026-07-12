pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.architectury.dev/")
        maven("https://maven.neoforged.net/releases/")
        maven("https://maven.kikugie.dev/releases")
        maven("https://maven.kikugie.dev/snapshots")
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.6"
}

// §multi-version (MC 26.x): Stonecutter "branched setup" over the Architectury modules — each of
// common/fabric/neoforge is a branch that builds its shared src/ per game version.
stonecutter {
    kotlinController = true
    centralScript = "build.gradle"
    create(rootProject) {
        branch("common")   { versions("1.21.1", "26.1.2") }
        branch("fabric")   { versions("1.21.1", "26.1.2") }
        branch("neoforge") { versions("1.21.1", "26.1.2") }
        vcsVersion = "1.21.1"
    }
}

rootProject.name = "riverfishing"
