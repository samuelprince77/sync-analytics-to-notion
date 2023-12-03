import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    kotlin("jvm") version libs.versions.kotlinVersion.get()
    `java-library`
    `java-gradle-plugin`
    `maven-publish`
    signing
    kotlin("plugin.serialization") version libs.versions.kotlinVersion.get()
}

val groupName = "io.github.samuelprince77"
val versionName = "1.0.3"

group = groupName
version = versionName

repositories {
    mavenCentral()
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
    implementation(libs.coroutines)
    implementation(libs.kotlinGradlePlugin)
    implementation(libs.kotlinSerializationJson)
    implementation(platform(libs.okhttpBom))
    implementation(libs.okhttp)

    val compilerClasspath by configurations.creating {
        isCanBeConsumed = false
    }

    compileOnly(libs.kotlinCompilerEmbedable)
    testCompileOnly(libs.kotlinCompilerEmbedable)
    compilerClasspath(kotlin("compiler", libs.versions.kotlinVersion.get()))

    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
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
    repositories.maven {
        name = "mavenCentral"
        setUrl("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")

        credentials.apply {
            username = providers.gradleProperty("sonatype.username").get()
            password = providers.gradleProperty("sonatype.password").get()
        }
    }

    repositories.maven {
        name = "sonatypeSnapshots"
        setUrl("https://s01.oss.sonatype.org/content/repositories/snapshots/")
        with(credentials) {
            username = providers.gradleProperty("sonatype.username").get()
            password = providers.gradleProperty("sonatype.password").get()
        }
    }

    publications {
        create<MavenPublication>("analyticsPlugin") {
            groupId = groupName
            artifactId = "sync-analytics-to-notion"
            version = versionName
            from(components["java"])

            pom {
                name.set("Sync analytics to notion")
                description.set("A kotlin gradle plugin that parses your analytics and syncs event names and parameter to notion")
                url.set("https://github.com/samuelprince77/sync-analytics-to-notion")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://github.com/samuelprince77/sync-analytics-to-notion/blob/main/Licence.txt")
                    }
                }
                developers {
                    developer {
                        id.set("samuelprince77")
                        name.set("Samuel Prince")
                        email.set("samuel.prince77@gmail.com")
                    }
                }
                scm {
                    connection.set("scm:git:git://example.com/my-library.git")
                    developerConnection.set("scm:git:ssh://git@github.com/samuelprince77/sync-analytics-to-notion.git")
                    url.set("https://github.com/samuelprince77/sync-analytics-to-notion")
                }
            }
        }
    }
}

signing {
    sign(publishing.publications["analyticsPlugin"])
}