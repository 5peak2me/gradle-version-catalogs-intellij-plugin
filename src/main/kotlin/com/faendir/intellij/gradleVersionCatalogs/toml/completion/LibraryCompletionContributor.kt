package com.faendir.intellij.gradleVersionCatalogs.toml.completion

import com.faendir.intellij.gradleVersionCatalogs.VCElementType
import com.faendir.intellij.gradleVersionCatalogs.toml.inVersionsToml
import com.faendir.intellij.gradleVersionCatalogs.toml.isLibraryModuleDef
import com.faendir.intellij.gradleVersionCatalogs.toml.vcElementType
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlLiteral
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED

class LibraryCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().inVersionsToml(),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
                    val literal = parameters.position.parent as? TomlLiteral ?: return
                    val keyValue = literal.parent as? TomlKeyValue ?: return
                    val withVersion = when {
                        keyValue.vcElementType == VCElementType.LIBRARY -> true
                        keyValue.isLibraryModuleDef() -> false
                        else -> return
                    }
                    result.stopHere()
                    val completionPrefix = CompletionUtil.findReferenceOrAlphanumericPrefix(parameters)
                    val cld = ConcurrentLinkedDeque<RepositoryArtifactInfo>()
                    val split = completionPrefix.split(":")
                    val groupId = split[0]
                    val artifactId = split.getOrNull(1)
                    val searchPromise = searchStringDependency(groupId, artifactId, withVersion, literal.project, createSearchParameters(parameters), cld)
                    result.restartCompletionOnAnyPrefixChange()
                    waitAndAdd(searchPromise, cld) { info ->
                        result.addElement(
                            LookupElementBuilder.create("${info.groupId}:${info.artifactId}${if (withVersion && info.version != null) ":${info.version}" else ""}")
                        )
                    }
                }
            }
        )
    }

    private fun searchStringDependency(
        groupId: String,
        artifactId: String?,
        withVersion: Boolean,
        project: Project,
        searchParameters: SearchParameters,
        results: ConcurrentLinkedDeque<RepositoryArtifactInfo>
    ): Promise<Int> {
        return searchWithMavenDependencySearch(groupId, artifactId, withVersion, project, searchParameters, results)
            ?: searchWithRepositorySearch(groupId, artifactId, project, searchParameters, results)
            ?: completedPromise(0)
    }

    private fun waitAndAdd(
        searchPromise: Promise<Int>,
        cld: ConcurrentLinkedDeque<RepositoryArtifactInfo>,
        handler: (RepositoryArtifactInfo) -> Unit
    ) {
        while (searchPromise.state == Promise.State.PENDING || cld.isNotEmpty()) {
            ProgressManager.checkCanceled()
            val item = cld.poll()
            if (item != null) {
                handler.invoke(item)
            }
        }
    }

    private fun createSearchParameters(params: CompletionParameters): SearchParameters {
        return SearchParameters(params.invocationCount < 2, ApplicationManager.getApplication().isUnitTestMode)
    }

    private data class SearchParameters(val useCache: Boolean, val useLocalOnly: Boolean)

    private data class RepositoryArtifactInfo(val groupId: String, val artifactId: String, val version: String?)

    private fun searchWithMavenDependencySearch(
        groupId: String,
        artifactId: String?,
        withVersion: Boolean,
        project: Project,
        searchParameters: SearchParameters,
        results: ConcurrentLinkedDeque<RepositoryArtifactInfo>
    ): Promise<Int>? {
        try {
            val contributorClass = findClass("org.jetbrains.idea.maven.completion.MavenDependencySearchContributor") ?: return null
            val extensionPoint = contributorClass.getField("EP_NAME").get(null)
            val contributors = extensionPoint.javaClass.getMethod("getExtensionList").invoke(extensionPoint) as? List<*> ?: return null
            if (contributors.isEmpty()) return null

            if (artifactId == null) {
                val fullTextSearch = contributorClass.getMethod(
                    "fulltextSearch",
                    Project::class.java,
                    String::class.java,
                    Boolean::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType,
                    Consumer::class.java,
                    Continuation::class.java
                )
                contributors.forEach { contributor ->
                    fullTextSearch.invokeSuspendBlocking(
                        contributor,
                        project,
                        groupId,
                        searchParameters.useCache,
                        searchParameters.useLocalOnly,
                        Consumer<Any> { artifact -> toRepositoryArtifactInfo(artifact)?.let(results::add) }
                    )
                }
            } else {
                val getArtifactIds = contributorClass.getMethod(
                    "getArtifactIds",
                    Project::class.java,
                    String::class.java,
                    Continuation::class.java
                )
                val getVersions = contributorClass.getMethod(
                    "getVersions",
                    Project::class.java,
                    String::class.java,
                    String::class.java,
                    Continuation::class.java
                )
                val artifactIds = mutableSetOf<String>()
                contributors.forEach { contributor ->
                    artifactIds += getArtifactIds.invokeSuspendBlocking(contributor, project, groupId).asStringSet()
                }
                artifactIds
                    .filter { it.startsWith(artifactId) }
                    .forEach { artifact ->
                        val versions = mutableSetOf<String?>()
                        if (withVersion) {
                            contributors.forEach { contributor ->
                                versions += getVersions.invokeSuspendBlocking(contributor, project, groupId, artifact).asStringSet()
                            }
                        }
                        if (versions.isEmpty()) versions += null
                        versions.forEach { version ->
                            results.add(RepositoryArtifactInfo(groupId, artifact, version))
                        }
                    }
            }
            return completedPromise(results.size)
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: InvocationTargetException) {
            if (e.targetException is ProcessCanceledException) throw e.targetException
            return null
        } catch (_: ReflectiveOperationException) {
            return null
        } catch (_: LinkageError) {
            return null
        }
    }

    private fun searchWithRepositorySearch(
        groupId: String,
        artifactId: String?,
        project: Project,
        searchParameters: SearchParameters,
        results: ConcurrentLinkedDeque<RepositoryArtifactInfo>
    ): Promise<Int>? {
        try {
            val serviceClass = findClass("org.jetbrains.idea.reposearch.DependencySearchService") ?: return null
            val searchParametersClass = findClass("org.jetbrains.idea.reposearch.SearchParameters") ?: return null
            val searchParametersInstance = searchParametersClass
                .getConstructor(Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
                .newInstance(searchParameters.useCache, searchParameters.useLocalOnly)
            val service = serviceClass.getMethod("getInstance", Project::class.java).invoke(null, project) ?: return null
            val consumer = Consumer<Any> { artifact -> toRepositoryArtifactInfo(artifact)?.let(results::add) }
            return if (artifactId == null) {
                serviceClass.getMethod("fulltextSearch", String::class.java, searchParametersClass, Consumer::class.java)
                    .invoke(service, groupId, searchParametersInstance, consumer).asIntPromise()
            } else {
                serviceClass.getMethod("suggestPrefix", String::class.java, String::class.java, searchParametersClass, Consumer::class.java)
                    .invoke(service, groupId, artifactId, searchParametersInstance, consumer).asIntPromise()
            }
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: InvocationTargetException) {
            if (e.targetException is ProcessCanceledException) throw e.targetException
            return null
        } catch (_: ReflectiveOperationException) {
            return null
        } catch (_: LinkageError) {
            return null
        }
    }

    private fun Method.invokeSuspendBlocking(target: Any?, vararg args: Any?): Any? {
        val latch = CountDownLatch(1)
        var resumedValue: Any? = null
        var resumedError: Throwable? = null
        val continuation = object : Continuation<Any?> {
            override val context: CoroutineContext = EmptyCoroutineContext

            override fun resumeWith(result: Result<Any?>) {
                result
                    .onSuccess { resumedValue = it }
                    .onFailure { resumedError = it }
                latch.countDown()
            }
        }

        val arguments = arrayOfNulls<Any?>(args.size + 1)
        args.copyInto(arguments)
        arguments[args.size] = continuation
        val result = invoke(target, *arguments)
        if (result !== COROUTINE_SUSPENDED) return result

        while (!latch.await(10, TimeUnit.MILLISECONDS)) {
            ProgressManager.checkCanceled()
        }
        resumedError?.let { throw it }
        return resumedValue
    }

    private fun toRepositoryArtifactInfo(artifact: Any): RepositoryArtifactInfo? {
        val groupId = artifact.callStringGetter("getGroupId") ?: return null
        val artifactId = artifact.callStringGetter("getArtifactId") ?: return null
        val version = artifact.callStringGetter("getVersion")
        return RepositoryArtifactInfo(groupId, artifactId, version)
    }

    private fun Any.callStringGetter(name: String): String? {
        return runCatching { javaClass.getMethod(name).invoke(this) as? String }.getOrNull()
    }

    private fun Any?.asStringSet(): Set<String> {
        return (this as? Iterable<*>)?.filterIsInstance<String>()?.toSet() ?: emptySet()
    }

    @Suppress("UNCHECKED_CAST")
    private fun Any?.asIntPromise(): Promise<Int>? {
        return this as? Promise<Int>
    }

    private fun completedPromise(value: Int): Promise<Int> {
        val promise = AsyncPromise<Int>()
        promise.setResult(value)
        return promise
    }

    private fun findClass(name: String): Class<*>? {
        return runCatching { Class.forName(name, false, javaClass.classLoader) }.getOrNull()
    }
}
