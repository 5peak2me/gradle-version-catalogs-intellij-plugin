package com.faendir.intellij.gradleVersionCatalogs.groovy.inlayhints

import com.faendir.intellij.gradleVersionCatalogs.VCElementType
import com.faendir.intellij.gradleVersionCatalogs.groovy.cache.BuildGradlePsiCache
import com.faendir.intellij.gradleVersionCatalogs.kotlin.findInVersionsTomlKeyValues
import com.faendir.intellij.gradleVersionCatalogs.toml.cache.VersionsTomlPsiCache
import com.faendir.intellij.gradleVersionCatalogs.toml.isVersionRef
import com.faendir.intellij.gradleVersionCatalogs.toml.unquote
import com.faendir.intellij.gradleVersionCatalogs.toml.vcElementType
import com.intellij.codeInsight.hints.*
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.childrenOfType
import org.jetbrains.plugins.gradle.config.isGradleFile
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.toml.lang.psi.*
import javax.swing.JPanel

@Suppress("UnstableApiUsage")
class VersionCatalogInlayHintsProvider : InlayHintsProvider<NoSettings> {

    private val prefixes = listOf("libs.", "huami.")

    override val key: SettingsKey<NoSettings> = SettingsKey("group.names.gradle")
    override val name: String = "Gradle Version Catalog references"
    override val group: InlayGroup = InlayGroup.OTHER_GROUP
    override val previewText: String? = null

    override fun createSettings(): NoSettings = NoSettings()

    override fun getCollectorFor(file: PsiFile, editor: Editor, settings: NoSettings, sink: InlayHintsSink): InlayHintsCollector? {
        if (file.isGradleFile() && file.name == GradleConstants.DEFAULT_SCRIPT_NAME) {
            return object : FactoryInlayHintsCollector(editor) {
                override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
                    if (prefixes.any(element.text::startsWith) && element is GrReferenceExpression) {
//                        println("${element::class.java} ---" + element.text)
                        val accessor = BuildGradlePsiCache.findAccessor(element)
                        if (accessor != null/* && BuildGradlePsiCache.findAccessor(element.parent) == null*/) {
                            val referencedElement = element.project.findInVersionsTomlKeyValues({ VersionsTomlPsiCache.getDefinitions(it, accessor.type) }, accessor.id)
                                    .firstOrNull()
                            if (referencedElement != null) {
                                val referencedValue = referencedElement.value
                                if (referencedValue != null) {
                                    val inlayText: String? = when (referencedElement.vcElementType) {
                                        VCElementType.LIBRARY -> resolvePotentiallyTabledDefinition(referencedValue, "module")
                                        VCElementType.VERSION -> referencedValue.text?.unquote()
                                        VCElementType.PLUGIN -> resolvePotentiallyTabledDefinition(referencedValue, "id")
                                        VCElementType.BUNDLE -> resolveBundlesDefinition(referencedValue)
                                        else -> null
                                    }
                                    if (inlayText != null) {
                                        sink.addInlineElement(
                                            element.textOffset + element.textLength,
                                            false,
                                            factory.roundWithBackgroundAndSmallInset(factory.smallText(inlayText)),
                                            false
                                        )
                                    }
                                }
                            }
                        }
                    }
                    return true
                }
            }
        }
        return null
    }

    /**
     * handle the definition of libs.bundles.xxx
     *
     * @since 1.3.2
     */
    private fun resolveBundlesDefinition(referencedValue: TomlValue): String? {
        return if (referencedValue is TomlArray) {
            referencedValue.elements.mapNotNull { value ->
                VersionsTomlPsiCache.getDefinitions(
                    referencedValue.containingFile as TomlFile,
                    VCElementType.LIBRARY
                ).find {
                    it.key.textMatches(value.text.unquote())
                }?.value?.let {
                    resolvePotentiallyTabledDefinition(it, "module")
                }
            }.joinToString(separator = "➕")
        } else null
    }

    private fun resolvePotentiallyTabledDefinition(referencedValue: TomlValue, moduleTableKey: String) = when (referencedValue) {
        is TomlTable, is TomlInlineTable -> {
            val keys = referencedValue.childrenOfType<TomlKeyValue>()
            keys.find { it.key.textMatches(moduleTableKey) }?.value?.text?.unquote()?.let { module ->
                (
                        keys.find { it.key.textMatches("version") }?.value?.text?.unquote()
                            ?: keys.find { it.isVersionRef() }?.value?.text?.unquote()?.let { search ->
                                VersionsTomlPsiCache.getDefinitions(
                                    referencedValue.containingFile as TomlFile,
                                    VCElementType.VERSION
                                ).find {
                                    it.key.textMatches(search)
                                }?.value?.text?.unquote()
                            }
                        )
                    ?.let { "$module:${it}" }
            }
        }

        is TomlLiteral -> referencedValue.text.unquote()
        else -> null
    }

    override fun createConfigurable(settings: NoSettings): ImmediateConfigurable {
        return object : ImmediateConfigurable {
            override fun createComponent(listener: ChangeListener) = JPanel()
        }
    }
}