plugins {
    kotlin("jvm") version "1.5.30"
    id("com.github.johnrengelman.shadow") version "7.0.0"
}

group = "spolks"
version = "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.3")
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "spolks.tcpserver.MainKt"
    }

}

tasks{
    shadowJar {
        manifest {
            attributes(Pair("Main-Class", "spolks.tcpserver.MainKt"))
        }
        archiveName = "server.jar"
    }
}