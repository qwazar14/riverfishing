// §26.x-unobf: loom + architectury-plugin live on the ROOT buildscript classpath so every version node
// can `apply plugin:` the RIGHT loom variant by id — `dev.architectury.loom` (obfuscated, remapping)
// for <26.1, `dev.architectury.loom-no-remap` (unobfuscated) for 26.1+. Both ids ship in the same jar.
buildscript {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.architectury.dev/")
        maven("https://maven.neoforged.net/releases/")
    }
    dependencies {
        classpath("dev.architectury:architectury-loom:1.17.491")
        classpath("architectury-plugin:architectury-plugin.gradle.plugin:3.5.169")
    }
}

plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "1.21.1" /* [SC] DO NOT EDIT */
