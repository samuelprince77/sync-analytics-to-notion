package se.samuel.analytics.notion.sync

import org.gradle.testkit.runner.GradleRunner
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

@RunWith(Parameterized::class)
class TestAttributionsPlugin(
    private val androidGradlePluginVersion: String,
    private val gradleVersion: String,
) {

    @get:Rule
    val testProjectDir = TemporaryFolder()

    private lateinit var appBuildGradleFile: File
    private lateinit var gradleRunner: GradleRunner

    @Before
    fun setUp() {
        with(testProjectDir) {
            newFolder("app")
            newFile("settings.gradle.kts").apply {
                writeText(
                    """
                    include(":app")
                """.trimIndent()
                )
            }

            newFolder("buildSrc")
            newFile("buildSrc/build.gradle.kts").apply {
                writeText(
                    """
                    plugins {
                        `kotlin-dsl`
                    }
                    buildscript {
                        repositories {
                            mavenLocal()
                            gradlePluginPortal()
                            google()
                            mavenCentral()
                        }
                    }
                    repositories {
                        mavenLocal()
                        gradlePluginPortal()
                        google()
                        mavenCentral()
                    }
                    dependencies {
                        implementation("com.android.tools.build:gradle:$androidGradlePluginVersion")
                    }
                """.trimIndent()
                )
            }

            newFile("build.gradle.kts").apply {
                writeText(
                    """
                    buildscript {
                        repositories {
                            mavenLocal()
                            gradlePluginPortal()
                            google()
                            mavenCentral()
                        }
                        dependencies {
                            classpath("com.android.tools.build:gradle:$androidGradlePluginVersion")
                            classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.8.0")
                            classpath("se.samuel:sync-analytics-to-notion:1.0.0")
                        }
                    }
                    subprojects {
                        pluginManager.withPlugin("com.android.application") {
                            extensions.configure<com.android.build.api.dsl.ApplicationExtension>("android") {
                                compileSdk = 33
                                defaultConfig {
                                    applicationId = "com.example"
                                    versionCode = 1
                                    versionName = "0.1"
                                    minSdk = 21
                                    targetSdk = 31
                                }
                           }
                        }
                    }
                """.trimIndent()
                )
            }
        }

        appBuildGradleFile = testProjectDir.newFile("app/build.gradle.kts")
        gradleRunner = GradleRunner
            .create()
            .withProjectDir(testProjectDir.root)
            .withArguments("--stacktrace")
            .withGradleVersion(gradleVersion)
    }

    @Test
    fun `plugin applied to project`() {
        appBuildGradleFile.writeText(
            """
                plugins {
                    id("com.android.application")
                    id("sync-analytics-to-notion")
                }
                
                android {
                    namespace = "com.test.namespace"
                }
                
                syncAnalyticsToNotion {
                   setNotionAuthKey("tests")
                   setNotionDatabaseId("test1")
                   setNotionEventNameColumnName("TestColumn1")
                   setNotionParametersColumnName("TestColumn2")
                }
            """.trimIndent()
        )

        gradleRunner.build()
    }

    companion object {
        @Parameterized.Parameters(name = "AGP version: {0}, Gradle version: {1}")
        @JvmStatic
        fun agpAndGradleVersions(): Collection<Array<String>> = listOf(
            arrayOf("7.3.0", "7.4.2"),
            arrayOf("8.1.4", "8.4")
        )
    }

}