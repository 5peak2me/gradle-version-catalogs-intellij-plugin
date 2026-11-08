package com.faendir.intellij.gradleVersionCatalogs.groovy.inlayhints

import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.utils.inlays.InlayHintsProviderTestCase
import org.jetbrains.plugins.groovy.GroovyFileType

class VersionCatalogInlayHintsProviderTest : InlayHintsProviderTestCase() {

    override fun setUp() {
        super.setUp()
        ApplicationManager.getApplication().runWriteAction {
            FileTypeManager.getInstance().associatePattern(GroovyFileType.GROOVY_FILE_TYPE, "build.gradle")
        }
    }

    override fun tearDown() {
        try {
            ApplicationManager.getApplication().runWriteAction {
                FileTypeManager.getInstance().removeAssociation(
                    GroovyFileType.GROOVY_FILE_TYPE,
                    FileTypeManager.parseFromString("build.gradle")
                )
            }
        } finally {
            super.tearDown()
        }
    }

    fun testLibraryAccessorHintInBuildGradle() {
        myFixture.addFileToProject(
            "gradle/libs.versions.toml",
            """
            [libraries]
            junit = "junit:junit:4.13.2"
            """.trimIndent()
        )

        val source = """
            dependencies {
                implementation libs.junit
            }
            """.trimIndent()

        myFixture.configureByText("build.gradle", source)

        val textWithInlays = dumpInlayHints(source, VersionCatalogInlayHintsProvider())

        assertTrue(textWithInlays, textWithInlays.contains("junit:junit:4.13.2"))
        assertEquals(1, Regex("junit:junit:4\\.13\\.2").findAll(textWithInlays).count())
    }

    fun testNonCatalogReferencesDoNotShowHintsInBuildGradle() {
        myFixture.addFileToProject(
            "gradle/libs.versions.toml",
            """
            [libraries]
            implementation = "example:implementation:1.0"
            """.trimIndent()
        )

        val source = """
            dependencies {
                implementation 'example:direct:1.0'
            }
            """.trimIndent()

        myFixture.configureByText("build.gradle", source)

        val textWithInlays = dumpInlayHints(source, VersionCatalogInlayHintsProvider())

        assertFalse(textWithInlays, textWithInlays.contains("example:implementation:1.0"))
    }

    fun testNestedLibraryAccessorHintOnlyShowsOnOutermostAccessorInBuildGradle() {
        myFixture.addFileToProject(
            "gradle/libs.versions.toml",
            """
            [libraries]
            compose-ui = "androidx.compose.ui:ui:1.11.3"
            compose-ui-tooling = "androidx.compose.ui:ui-tooling:1.11.3"
            compose-ui-tooling-preview = "androidx.compose.ui:ui-tooling-preview:1.11.3"
            """.trimIndent()
        )

        val source = """
            dependencies {
                implementation libs.compose.ui.tooling.preview
            }
            """.trimIndent()

        myFixture.configureByText("build.gradle", source)

        val textWithInlays = dumpInlayHints(source, VersionCatalogInlayHintsProvider())

        assertTrue(textWithInlays, textWithInlays.contains("androidx.compose.ui:ui-tooling-preview:1.11.3"))
        assertEquals(1, Regex("androidx\\.compose\\.ui:ui-tooling-preview:1\\.11\\.3").findAll(textWithInlays).count())
        assertFalse(textWithInlays, textWithInlays.contains("androidx.compose.ui:ui:1.11.3"))
        assertFalse(textWithInlays, textWithInlays.contains("androidx.compose.ui:ui-tooling:1.11.3"))
    }
}
