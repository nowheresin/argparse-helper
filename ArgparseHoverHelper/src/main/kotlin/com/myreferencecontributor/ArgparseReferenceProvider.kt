package com.myreferencecontributor

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.ProcessingContext
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.resolve.PyResolveUtil
import com.intellij.psi.util.PsiTreeUtil
//import com.intellij.psi.search.PsiShortNamesCache

/**
 * ArgparseReferenceProvider 为所有带限定符的 PyReferenceExpression（如 args.xxx）提供引用支持。
 * 当用户 Ctrl+点击 `args.<name>` 时，IntelliJ 会调用本 Provider，并返回一个 PsiReference 用以解析跳转目标。
 */
class ArgparseReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        // 只处理 Python 的引用表达式，且需带限定符（args.xxx）
        if (element !is PyReferenceExpression) return PsiReference.EMPTY_ARRAY
        val qualifier = element.qualifier
        if (qualifier == null) return PsiReference.EMPTY_ARRAY

        // 仅参考属性名部分（属性名即 add_argument 中的 dest 名），设定引用范围
//        val nameElement = element.referenceNameElement ?: return PsiReference.EMPTY_ARRAY
//        val nameElement = (element as? PyReferenceExpression)?.nameElement ?: return PsiReference.EMPTY_ARRAY
        val refExpr = element as? PyReferenceExpression ?: return PsiReference.EMPTY_ARRAY
        val referencedName = refExpr.name ?: return PsiReference.EMPTY_ARRAY
        val start = refExpr.text.indexOf(referencedName)
        val range = TextRange(start, start + referencedName.length)

        // 返回自定义的引用实现
        return arrayOf(ArgparseReference(element, range))
    }
}

/**
 * ArgparseReference 实现了 PsiReferenceBase，用来在 resolve() 中完成跳转逻辑。
 * 它会尝试从 args 变量查找 parser.parse_args 调用，再定位对应的 add_argument 调用。
 */
class ArgparseReference(element: PyReferenceExpression, range: TextRange)
    : PsiReferenceBase<PyReferenceExpression>(element, range) {

    override fun resolve(): PsiElement? {
        val attrName = element.name ?: return null
        val qualifier = element.qualifier as? PyReferenceExpression ?: return null

        // 1. 找到生成 args 的 parse_args 调用
        val parseCall = findParseCall(qualifier) ?: return null

        // 2. 确定 parser 来源（变量或函数调用）
        val parserSource = findParserSource(parseCall) ?: return null

        // 3. 在 parser 的上下文中查找 add_argument 调用并匹配属性名
        return findAddArgumentCall(parserSource, attrName)
    }

    override fun getVariants(): Array<Any> = emptyArray()  // 不提供自动补全

    // 查找 qualifier（如 args）对应的 parse_args 调用：args = parser.parse_args()
    private fun findParseCall(argsRef: PyReferenceExpression): PyCallExpression? {
        val resolved = argsRef.reference?.resolve()
        if (resolved is PyTargetExpression) {
            val parent = resolved.parent
            if (parent is PyAssignmentStatement) {
                val value = parent.assignedValue
                if (value is PyCallExpression) {
                    val callee = value.callee as? PyReferenceExpression
                    val name = callee?.name
                    // 支持 parse_args 或 parse_known_args
                    if (name == "parse_args" || name == "parse_known_args") {
                        return value
                    }
                }
            }
        }
        return null
    }

    // 从 parse_args 调用中提取 parser 源：parser.parse_args() 或 function().parse_args()
    private fun findParserSource(parseCall: PyCallExpression): PsiElement? {
        val callee = parseCall.callee as? PyQualifiedExpression ?: return null
        val qualifier = callee.qualifier
        return qualifier
    }

    // 在 parserSource 上查找对应的 add_argument 调用，匹配属性名 attrName
    private fun findAddArgumentCall(parserSource: PsiElement, attrName: String): PsiElement? {
        // 情况 A: parserSource 是变量引用（PyReferenceExpression）
        if (parserSource is PyReferenceExpression) {
            val resolved = parserSource.reference?.resolve()
            if (resolved is PyTargetExpression) {
                // 先在当前作用域或文件中寻找 parser.add_argument
                val addCall = findAddArgumentInScope(resolved, attrName)
                if (addCall != null) return addCall

                // 如果 parser = someFunction()，则函数内部可能有 add_argument
                val assignment = resolved.parent as? PyAssignmentStatement
                val call = assignment?.assignedValue as? PyCallExpression
                val funcName = (call?.callee as? PyReferenceExpression)?.name
                if (funcName != null) {
                    // 在所有同名函数体内查找 add_argument
//                    val functions = PsiShortNamesCache.getInstance(parserSource.project)
//                        .getFunctionsByName(funcName, GlobalSearchScope.projectScope(parserSource.project))
                    val functions = findPythonFunctionsByName(parserSource.project, funcName)
                    for (func in functions.filterIsInstance<PyFunction>()) {
                        val target = findAddArgumentInFunction(func, attrName)
                        if (target != null) return target
                    }
                }
            }
        }
        // 情况 B: parserSource 是函数调用（PyCallExpression），如 create_parser().parse_args()
        else if (parserSource is PyCallExpression) {
            val funcName = (parserSource.callee as? PyReferenceExpression)?.name
            if (funcName != null) {
                // 在所有同名函数体内查找 add_argument
//                val functions = PsiShortNamesCache.getInstance(parserSource.project)
//                    .getFunctionsByName(funcName, GlobalSearchScope.projectScope(parserSource.project))
                val functions = findPythonFunctionsByName(parserSource.project, funcName)
                for (func in functions.filterIsInstance<PyFunction>()) {
                    val target = findAddArgumentInFunction(func, attrName)
                    if (target != null) return target
                }
            }
        }
        return null
    }

    // 在变量 parserTarget 所在文件中查找 parser.add_argument(...)
    private fun findAddArgumentInScope(parserTarget: PyTargetExpression, attrName: String): PsiElement? {
        val psiFile = parserTarget.containingFile as? PyFile ?: return null
        // 收集所有调用表达式
        val calls = PsiTreeUtil.collectElementsOfType(psiFile, PyCallExpression::class.java)
        for (call in calls) {
            val callee = call.callee
            if (callee is PyQualifiedExpression) {
                val qualifier = callee.qualifier as? PyReferenceExpression
                val name = callee.referencedName
                if (name == "add_argument" && qualifier != null) {
                    // 判断 add_argument 的调用对象是否是我们的 parserTarget
                    val resolvedQual = qualifier.reference?.resolve()
                    if (resolvedQual == parserTarget) {
                        if (matchesArgument(call, attrName)) {
                            return call
                        }
                    }
                }
            }
        }
        return null
    }

    // 在给定函数 func 内查找 add_argument 调用（函数内部可能定义 parser 并添加参数）
    private fun findAddArgumentInFunction(func: PyFunction, attrName: String): PsiElement? {
        // 遍历函数体中的所有调用
        val calls = PsiTreeUtil.collectElementsOfType(func, PyCallExpression::class.java)
        for (call in calls) {
            val callee = call.callee
            if (callee is PyQualifiedExpression) {
                val name = callee.referencedName
                if (name == "add_argument") {
                    if (matchesArgument(call, attrName)) {
                        return call
                    }
                }
            }
        }
        return null
    }

    // 检查一个 add_argument 调用是否匹配属性名 attrName（考虑 dest 或选项名）
    private fun matchesArgument(call: PyCallExpression, attrName: String): Boolean {
        // 检查关键字参数 dest
        call.argumentList?.arguments?.forEach { arg ->
            if (arg is PyKeywordArgument && arg.keyword == "dest") {
                val destValue = (arg.valueExpression as? PyStringLiteralExpression)?.stringValue
                if (destValue == attrName) {
                    return true
                }
            }
        }
        // 如果没有 dest，则使用位置参数推断 dest（去掉前导 '-' 并将 '-' 转为 '_'）
        val args = call.argumentList?.arguments?.filterIsInstance<PyStringLiteralExpression>()
        if (!args.isNullOrEmpty()) {
            // 支持多个选项名时以最后一个为准
            val last = args.last().stringValue
            val nameInCall = last.trimStart('-').replace('-', '_')
            if (nameInCall == attrName) {
                return true
            }
        }
        return false
    }


    ////////
    fun findPythonFunctionsByName(project: Project, funcName: String): List<PyFunction> {
        val result = mutableListOf<PyFunction>()

        // 1. 查找所有 .py 文件
        val pyFiles = FilenameIndex.getAllFilesByExt(project, "py", GlobalSearchScope.projectScope(project))

        for (virtualFile in pyFiles) {
            val psiFile = PsiManager.getInstance(project).findFile(virtualFile) as? PyFile ?: continue

            // 2. 查找所有函数定义
            val functions = PsiTreeUtil.findChildrenOfType(psiFile, PyFunction::class.java)
            for (func in functions) {
                if (func.name == funcName) {
                    result.add(func)
                }
            }
        }

        return result
    }
}
