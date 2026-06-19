package com.faendir.intellij.gradleVersionCatalogs.kotlin.inlayhints

import com.intellij.testFramework.utils.inlays.InlayHintsProviderTestCase

/**
 *
 * Created by J!nl!n on 2023/7/6.
 *
 * Copyright © 2023 J!nl!n™ Inc. All rights reserved.
 * https://github.com/JetBrains/intellij-community/blob/master/plugins/kotlin/idea/tests/test/org/jetbrains/kotlin/idea/codeInsight/hints/AbstractKotlinReferenceTypeHintsProviderTest.kt
 * https://plugins.jetbrains.com/docs/intellij/inlay-hints.html#advanced-inlay-hints
 */
class VersionCatalogInlayHintsProviderTest : InlayHintsProviderTestCase() {

    fun testLibraryAccessorHint() {
        addGradleApiStubs()
        myFixture.addFileToProject(
            "src/main/java/Libs.java",
            """
            import org.gradle.api.artifacts.MinimalExternalModuleDependency;
            import org.gradle.api.provider.Provider;

            public class Libs {
                public Provider<MinimalExternalModuleDependency> getJunit() {
                    return null;
                }
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "gradle/libs.versions.toml",
            """
            [libraries]
            junit = "junit:junit:4.13.2"
            """.trimIndent()
        )

        val source = """
            import org.gradle.api.artifacts.MinimalExternalModuleDependency
            import org.gradle.api.provider.Provider

            val libs = Libs()

            dependencies {
                implementation(libs.junit)
            }

            fun dependencies(block: Dependencies.() -> Unit) {}

            class Dependencies {
                fun implementation(dependency: Any?) {}
            }
            """.trimIndent()

        myFixture.configureByText("build.gradle.kts", source)

        val textWithInlays = dumpInlayHints(source, VersionCatalogInlayHintsProvider())

        assertTrue(textWithInlays, textWithInlays.contains("junit:junit:4.13.2"))
        assertEquals(1, Regex("junit:junit:4\\.13\\.2").findAll(textWithInlays).count())
    }

    private fun addGradleApiStubs() {
        myFixture.addFileToProject(
            "src/main/kotlin/org/gradle/api/provider/Provider.kt",
            """
            package org.gradle.api.provider

            interface Provider<T>
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "src/main/kotlin/org/gradle/api/artifacts/MinimalExternalModuleDependency.kt",
            """
            package org.gradle.api.artifacts

            interface MinimalExternalModuleDependency
            """.trimIndent()
        )
    }
}
