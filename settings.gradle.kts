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
        // 26.2 and 26.3: 26.1.2 is no longer supported (user, 2026-09-25). 1.21.1 and 1.20.1 live on their
        // own branches.
        branch("common")   { versions("26.2", "26.3") }
        branch("fabric")   { versions("26.2", "26.3") }
        branch("neoforge") { versions("26.2", "26.3") }
        vcsVersion = "26.2"
    }
}

rootProject.name = "riverfishing"
