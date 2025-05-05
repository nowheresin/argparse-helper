package com.myreferencecontributor

import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceRegistrar
import com.jetbrains.python.psi.PyReferenceExpression

/**
 * ArgparseReferenceContributor 在 Python 语言环境下注册 ArgparseReferenceProvider，
 * 使得 Python 文件中的 PyReferenceExpression 都会使用上述引用逻辑。
 */
class ArgparseReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        // 对所有 PyReferenceExpression 应用 ArgparseReferenceProvider
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(PyReferenceExpression::class.java),
            ArgparseReferenceProvider()
        )
    }
}
