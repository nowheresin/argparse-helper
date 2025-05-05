import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.*

// 新建文件 ArgparseResolver.kt
internal object ArgparseResolver {

    internal data class ArgParam(  ///  internal
        val defaultValue: String?,
        val actionValue: String?
    )


    internal fun resolveParserFromReference(ref: PyReferenceExpression): Pair<PyTargetExpression, String?>? {
        return when (val resolved = ref.reference?.resolve()) {
            is PyTargetExpression -> {
                Pair(resolved, resolved.name)
            }
            is PyFunction -> resolveParserFromFunction(resolved)
            else -> null
        }
    }
    internal fun resolveParserFromFunctionCall(call: PyCallExpression): Pair<PyTargetExpression, String?>? {
        return when (val resolved = call.callee?.reference?.resolve()) {
            is PyFunction -> resolveParserFromFunction(resolved)
            else -> null
        }
    }
    private fun resolveParserFromFunction(func: PyFunction): Pair<PyTargetExpression, String?>? {
        func.statementList.statements.forEach { stmt ->
            if (stmt is PyReturnStatement) {
                when (val retVal = stmt.expression) {
                    is PyReferenceExpression -> {
                        val assignment = func.statementList.statements
                            .filterIsInstance<PyAssignmentStatement>()
                            .find { (it.leftHandSideExpression as? PyTargetExpression)?.name == retVal.name }
                        val targetExpr = assignment?.leftHandSideExpression as? PyTargetExpression
                        return targetExpr?.let { Pair(it, it.name) }
                    }
                    is PyCallExpression -> {
                        if ((retVal.callee as? PyReferenceExpression)?.name == "ArgumentParser") {
                            // 匿名 parser，不好做后续匹配，返回 null
                            return null
                        }
                    }
                }
            }
        }
        return null
    }
    internal fun extractCallExprFromTarget(target: PyTargetExpression?): PyCallExpression? {
        return target?.findAssignedValue() as? PyCallExpression
    }

    internal fun collectParserDefaults(context: PsiElement, targetParserName: String?, parserTarget: PyTargetExpression): Map<String, ArgParam> {
        val result = mutableMapOf<String, ArgParam>()
        context.accept(object : PyRecursiveElementVisitor() {
            override fun visitPyCallExpression(node: PyCallExpression) {
                collectAddArgumentCalls(node, targetParserName, parserTarget, result)
                super.visitPyCallExpression(node)
            }
        })
        return result
    }

    private fun collectAddArgumentCalls(
        call: PyCallExpression,
        targetParserName: String?,
        parserTarget: PyTargetExpression,
        result: MutableMap<String, ArgParam>
    ) {
        val allNames = mutableSetOf<String>()
        collectAllParamNames(call, targetParserName, parserTarget, allNames)
        // 解析参数属性
        val defaultArg = call.getKeywordArgument("default")?.let { processValue(it) }
        val actionArg = call.getKeywordArgument("action")?.let { processValue(it) }
        val paramInfo = ArgParam(defaultArg, actionArg)

        // 为所有名称建立映射
        allNames.forEach { name ->
            result[name] = paramInfo
        }

    }

    private fun collectAllParamNames(  // private
        call: PyCallExpression,
        targetParserName: String?,
        parserTarget: PyTargetExpression,
        allNames: MutableSet<String>
    ) {
        val qualified = call.callee as? PyQualifiedExpression ?: return
        val currentParserName = qualified.qualifier?.name ?: return

        val qualifierExpr = qualified.qualifier as? PyReferenceExpression ?: return
        val resolvedQualifier = qualifierExpr.reference?.resolve()
        if (resolvedQualifier != parserTarget) return

        // 关键过滤：匹配parser名称或匿名创建
        if (targetParserName != null && currentParserName != targetParserName) return
        if (targetParserName == null && qualified.qualifier !is PyCallExpression) return

        if (qualified.referencedName != "add_argument") return

        // 解析所有flags
        val flags = call.arguments
            .filterIsInstance<PyStringLiteralExpression>()
            .takeIf { it.isNotEmpty() }
            ?.flatMap { it.stringValue.split(',').map { f -> f.trim() } }
            ?: return

        // 解析dest参数（优先使用）
        val destArg = call.getKeywordArgument("dest")?.let { expr ->
            when (expr) {
                is PyStringLiteralExpression -> expr.stringValue
                else -> expr.text?.removeSurrounding("\"")?.removeSurrounding("'")
            }
        }

        // 生成所有可能的参数名称
        allNames.apply {
            // 添加dest值
            destArg?.let { add(it) }

            // 添加所有flag解析结果
            flags.forEach { flag ->
                parseFlag(flag)?.let { add(it) }
            }
        }
    }

    // 修改后的flag解析方法
    private fun parseFlag(flag: String): String? {
        return flag.split(',')
            .map {
                it.trim()
                    .removePrefix("--")
                    .removePrefix("-")
                    .replace("-", "_")  // 统一转为snake_case
            }
            .firstOrNull { it.isNotEmpty() }
    }

    private fun processValue(expr: PyExpression): String {
        return when (expr) {
            is PyStringLiteralExpression -> "\"${expr.stringValue}\""
            is PyNumericLiteralExpression -> expr.text
            is PyBoolLiteralExpression -> expr.text
            else -> expr.text
        }
    }

    /////////////////////////////////////////////////////

//    private fun PyCallExpression.getKeywordArgument(name: String): PyExpression? {
//        return arguments.find {
//            (it as? PyKeywordArgument)?.keyword == name
//        }?.let { (it as PyKeywordArgument).valueExpression }
//    }
//
//    private fun PyCallExpression.getReceiver(): PyExpression? {
//        return when (val parent = this.parent) {
//            is PyQualifiedExpression -> parent.qualifier
//            else -> (this.callee?.children?.firstOrNull()) as PyExpression?
//        }
//    }

    ////////////////////////////////////////////////////////

}