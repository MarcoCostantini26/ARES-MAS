plugins {
    kotlin("jvm") version "1.9.22"
    application
}

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
    maven { url = uri("https://raw.githubusercontent.com/jacamo-lang/mvn-repo/master") }
}

dependencies {
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("io.github.jason-lang:jason-interpreter:3.3.1")
    implementation("org.antlr:antlr4:4.12.0")
}

application {
    mainClass.set("jason.infra.local.RunLocalMAS")
}