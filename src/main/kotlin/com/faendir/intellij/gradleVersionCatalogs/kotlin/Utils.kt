package com.faendir.intellij.gradleVersionCatalogs.kotlin

import com.google.common.base.CaseFormat
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.search.FilenameIndex
import com.intellij.util.applyIf
import com.intellij.util.io.exists
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlKeyValue
import java.nio.file.Path
import kotlin.text.substringBefore

fun Project.findInVersionsTomlKeyValues(getKeyValues: (TomlFile) -> List<TomlKeyValue>, search: String): Map<String, List<TomlKeyValue>> {
    return FilenameIndex.getAllFilesByExt(this, "toml")
        .applyIf(basePath.isNullOrEmpty().not()) {
            apply {
                val path = Path.of(basePath!!, ".gradle", "libs.versions.toml")
                if (path.exists()) {
                    add(VfsUtil.findFile(path, true))
                }
            }
        }
        .filter { it.name.endsWith("versions.toml") }
        .map { it.toPsiFile(this) }
        .filterIsInstance<TomlFile>()
//        .flatMap { file -> getKeyValues(file).filter { it.key.textMatches(search) } }
        .flatMap { file -> getKeyValues(file).filter { it.key.text.replace('_', '-') == search } }
        .groupBy { it.containingFile.name.namespace }
}

internal inline val String.namespace: String
    get() = substringBefore('.')