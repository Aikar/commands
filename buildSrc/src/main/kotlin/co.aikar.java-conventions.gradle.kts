/*
 * This file was generated by the Gradle 'init' task.
 */

plugins {
    `java-library`
    `maven-publish`
}

repositories {
    mavenLocal()
    maven {
        url = uri("https://repo.aikar.co/content/groups/aikar/")
    }

    maven {
        url = uri("https://repo.maven.apache.org/maven2/")
    }

    maven {
        url = uri("https://libraries.minecraft.net/")
    }

    maven {
        url = uri("https://oss.sonatype.org/content/repositories/snapshots")
    }

    maven {
        url = uri("https://repo.spongepowered.org/maven/")
    }

    maven {
        url = uri("https://jcenter.bintray.com")
    }

    maven {
        url = uri("https://repo.velocitypowered.com/snapshots/")
    }
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.4.0")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.4.0")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.4.0")
    testImplementation("org.mockito:mockito-core:2.25.1")
    testImplementation("org.mockito:mockito-junit-jupiter:2.25.1")
    compileOnly("com.google.guava:guava:15.0")
    compileOnly("org.jetbrains:annotations:15.0")
}
// TODO: Add shading

group = "co.aikar"
version = "0.5.0-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_1_8

java {
    withSourcesJar()
}

publishing {
    publications.create<MavenPublication>("maven") {
        from(components["java"])
    }
}

tasks.withType<JavaCompile>() {
    options.encoding = "UTF-8"
}
