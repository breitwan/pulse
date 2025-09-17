plugins {
    `java-library`
}

group = "pulse"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    api("org.jetbrains:annotations:26.0.2")
    implementation("it.unimi.dsi:fastutil:8.5.16")
    implementation("space.vectrix.flare:flare:2.0.1")
    implementation("space.vectrix.flare:flare-fastutil:2.0.1")
    implementation("org.jctools:jctools-core:4.0.5")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("--enable-preview")
}
