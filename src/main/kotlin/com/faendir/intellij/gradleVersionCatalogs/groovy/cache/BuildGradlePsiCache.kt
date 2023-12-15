package com.faendir.intellij.gradleVersionCatalogs.groovy.cache

import com.faendir.intellij.gradleVersionCatalogs.groovy.Accessor
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValuesManager.getProjectPsiDependentCache

/**
 *
 * Created by J!nl!n on 2023/7/6.
 *
 * Copyright © 2023 J!nl!n™ Inc. All rights reserved.
 *
 */
object BuildGradlePsiCache {

    fun findAccessor(element: PsiElement) = getProjectPsiDependentCache(element) { Accessor.find(it) }

}