import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.3.41"
}

applyCommonConfiguration()

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

repositories {
    maven { url = uri("https://hub.spigotmc.org/nexus/content/groups/public") }
    maven { url = uri("https://repo.codemc.org/repository/maven-public") }
    maven { url = uri("https://papermc.io/repo/repository/maven-public/") }
}


dependencies {
    "compile"("org.bukkit:bukkit:1.13.2-R0.1-SNAPSHOT") {
        exclude("junit", "junit")
    }
    "compileOnly"("com.destroystokyo.paper:paper-api:1.13.2-R0.1-SNAPSHOT")
    "implementation"(project(":worldedit-libs:core:ap"))
    "implementation"(project(":worldedit-core"))
    "implementation"(kotlin("stdlib-jdk8"))
    implementation(kotlin("stdlib-jdk8"))
}
repositories {
    mavenCentral()
}
val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "1.8"
}
val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "1.8"
}