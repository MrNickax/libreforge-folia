import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    repositories {
        mavenCentral()
    }

    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.21")
    }
}

plugins {
    java
    id("java-library")
    id("com.gradleup.shadow") version "9.3.1"
    id("maven-publish")
}

allprojects {
    apply(plugin = "kotlin")
    apply(plugin = "java")
    apply(plugin = "maven-publish")
    apply(plugin = "com.gradleup.shadow")

    repositories {
        mavenLocal() // TODO: REMOVE
        mavenCentral()

        // Folia fork of eco (com.willfp:eco:*-folia), published to GitHub Packages.
        // Pin only the forked module here; every other com.willfp artifact (e.g.
        // ModelEngineBridge) still resolves from auxilor below.
        maven("https://maven.pkg.github.com/MrNickax/eco-folia") {
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: (findProperty("gpr.user") as String?)
                password = System.getenv("GITHUB_TOKEN") ?: (findProperty("gpr.key") as String?)
            }
            content { includeModule("com.willfp", "eco") }
        }

        maven("https://jitpack.io/")
        maven("https://repo.auxilor.io/repository/maven-public/")
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.purpurmc.org/snapshots")
        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
        maven("https://repo.codemc.io/repository/maven-public/")
        maven("https://maven.enginehub.org/repo/")
        maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
        maven("https://maven.citizensnpcs.co/repo")
        maven("https://repo.william278.net/releases")
        maven("https://repo.momirealms.net/releases/")
        maven("https://repo.artillex-studios.com/releases/")
        maven("https://mvn.lumine.io/repository/maven-public/")
        maven("https://repo.nexomc.com/releases")
    }

    dependencies {
        compileOnly("org.jetbrains:annotations:26.0.2")
        compileOnly(kotlin("stdlib", version = "2.3.0"))

        compileOnly(fileTree("lib") { include("*.jar") })
    }

    publishing {
        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/MrNickax/libreforge-folia")
                credentials {
                    username = System.getenv("GITHUB_ACTOR") ?: (findProperty("gpr.user") as String?)
                    password = System.getenv("GITHUB_TOKEN") ?: (findProperty("gpr.key") as String?)
                }
            }
        }
    }

    tasks {
        processResources {
            filesMatching(listOf("**plugin.yml")) {
                expand("projectVersion" to rootProject.version)
            }
        }

        withType<JavaCompile> {
            options.encoding = "UTF-8"
            options.release = 21
        }

        withType<KotlinCompile> {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_21)
            }
        }

        compileJava {
            dependsOn(clean)
        }

        build {
            dependsOn(shadowJar)
            dependsOn(publishToMavenLocal)
        }
    }

    java {
        withSourcesJar()
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }
}

group = "com.willfp"
version = findProperty("version")!!