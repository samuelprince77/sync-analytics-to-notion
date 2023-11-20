import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    kotlin("jvm") version libs.versions.kotlinVersion.get()
    `java-library`
    `java-gradle-plugin`
    `maven-publish`
    kotlin("plugin.serialization") version libs.versions.kotlinVersion.get()
}

group = "se.samuel"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withJavadocJar()
    withSourcesJar()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_17.toString()
}

dependencies {
    implementation(libs.kotlinGradlePlugin)
    implementation(libs.kotlinSerializationJson)

    implementation(libs.bundles.ktor)

    val compilerClasspath by configurations.creating {
        isCanBeConsumed = false
    }

    compileOnly(libs.kotlinCompilerEmbedable)
    compilerClasspath(kotlin("compiler", libs.versions.kotlinVersion.get()))

    testImplementation(libs.junit)
}

gradlePlugin {
    plugins {
        create("sync-analytics-to-notion") {
            id = name
            implementationClass = "se.samuel.analytics.notion.sync.SyncAnalyticsToNotionPlugin"
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "se.samuel"
            artifactId = "sync-analytics-to-notion"
            version = "1.0.0"

            from(components["java"])
        }
    }
}