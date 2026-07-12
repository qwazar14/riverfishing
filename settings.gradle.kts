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
        // 26.x family only (all unobfuscated). 1.21.1 was the port base; it stays frozen/released on the
        // mc-1.21.1 branch. 26.2 gets added once 26.1.2 compiles + runs.
        branch("common")   { versions("26.1.2") }
        branch("fabric")   { versions("26.1.2") }
        branch("neoforge") { versions("26.1.2") }
        vcsVersion = "26.1.2"
    }
}

rootProject.name = "riverfishing"
