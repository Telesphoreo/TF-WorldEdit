import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.3.41"
}

repositories {
    maven { url = uri("https://hub.spigotmc.org/nexus/content/groups/public") }
}

applyCommonConfiguration()

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

dependencies {
    "compile"("org.bukkit:bukkit:1.13.2-R0.1-SNAPSHOT") {
        exclude("junit", "junit")
    }
    "implementation"(project(":worldedit-libs:core:ap"))
    "implementation"(project(":worldedit-core"))
    "implementation"(kotlin("stdlib-jdk8"))
    "implementation"(kotlin("reflect"))
}
