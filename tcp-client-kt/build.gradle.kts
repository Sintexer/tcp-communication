plugins {
    kotlin("jvm") version "1.5.30"
}

group = "spolks"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.3")
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "spolks.tcpclient.MainKt"
    }
}