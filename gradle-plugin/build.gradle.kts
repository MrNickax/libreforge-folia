import org.gradle.api.publish.maven.tasks.PublishToMavenRepository

plugins {
    id("java-gradle-plugin")
    id("maven-publish")
}

dependencies {
    implementation(gradleApi())
    implementation(localGroovy())
    compileOnly("com.gradleup.shadow:shadow-gradle-plugin:9.3.1")
}

group = "com.willfp"
version = "2.0.1-folia"

tasks {
    build {
        dependsOn(publishToMavenLocal)
    }
}

gradlePlugin {
    plugins {
        create("libreforgeGradlePlugin") {
            id = "com.willfp.libreforge-gradle-plugin"
            implementationClass = "com.willfp.libreforge.gradle.LibreforgeGradlePlugin"
        }
    }
}


publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = group.toString()
            artifactId = "libreforge-gradle-plugin"
            version = version.toString()

            from(components["java"])
        }
    }
}

// This module is already published to GitHub Packages at its current version and
// release coordinates there are immutable, so re-running `publish` on every master
// push returns HTTP 409 (Conflict) and aborts the whole publish task before the
// core libreforge artifact is uploaded. The gradle-plugin is only needed at
// Maven Local for local builds, so skip remote (GitHub Packages) publishing here.
tasks.withType<PublishToMavenRepository>().configureEach {
    enabled = false
}