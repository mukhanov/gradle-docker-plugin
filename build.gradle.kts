plugins {
    id("org.jetbrains.kotlin.jvm").version("1.3.31")
    id("com.gradle.plugin-publish").version("0.10.1")
    `java-gradle-plugin`
    `maven-publish`
}

group = "com.github.mukhanov"
version = "1.2"

repositories {
    jcenter()
}

publishing {
    publications.create<MavenPublication>("maven") {
        from(components.findByName("java"))
    }
}

gradlePlugin {
    plugins {
        create("gradleDockerPlugin") {
            id = "com.github.mukhanov.gradle-docker-plugin"
            implementationClass = "com.github.mukhanov.gradle.docker.plugin.DockerPlugin"
        }
    }
}

pluginBundle {
    website = "https://github.com/mukhanov/gradle-docker-plugin"
    vcsUrl = "https://github.com/mukhanov/gradle-docker-plugin"
    description = "Gradle plugin for building compact images"
    (plugins) {
        "gradleDockerPlugin" {
            displayName = "Gradle Greeting plugin"
            tags = listOf("individual", "tags", "per", "plugin")
            version = "1.3"
        }
    }
}

dependencies {
    api("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation(gradleApi())
}

