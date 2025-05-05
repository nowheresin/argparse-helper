package com.argparsedocumentprovider

import ArgparseResolver
import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.*

class ArgparseDocumentationProvider : AbstractDocumentationProvider() {

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        if (element == null || element.text.isEmpty()) return null
        val fullText = element.text.trim()
        if (!fullText.contains(".")) return null
        val parts = fullText.split('.', limit = 2)
        if (parts.size < 2) return null
        val variableName = parts[0]
        val paramName = parts[1]

        val mainFile = element.containingFile as? PyFile ?: return null

        // 1. 查找变量赋值
        val argsAssignment = mainFile.statements
            .filterIsInstance<PyAssignmentStatement>()
            .find { (it.leftHandSideExpression as? PyTargetExpression)?.name == variableName }
            ?: return null

        // 2. 解析parse_args调用链
        val parseArgsCall = argsAssignment.assignedValue as? PyCallExpression ?: return null
        if ((parseArgsCall.callee as? PyReferenceExpression)?.name != "parse_args") return null

        // 3. 获取调用parse_args的parser实例
        val (parserTarget, parserVarName) = when (val receiver = parseArgsCall.getReceiver()) {
            is PyReferenceExpression -> resolveParserFromReference(receiver)
            is PyCallExpression -> resolveParserFromFunctionCall(receiver)
            else -> null
        } ?: return """<i><b>$fullText</b></i><br/><b><font color="red">Undefined parser.</font></b>"""
        val parserDef = extractCallExprFromTarget(parserTarget) ?: return null

        // 4. 收集特定parser的参数
        val context = when (parserDef.parent) {
            is PyFunction -> parserDef.parent as PyFunction
            else -> parserDef.containingFile
        }
        val defaults = collectParserDefaults(context, parserVarName, parserTarget)

        // 新增：参数存在性检查
        val normalizedParam = paramName.replace("_", "-")  // 支持snake_case转kebab-case
        val isParamDefined = defaults.keys.any { key ->
            key == paramName || key == normalizedParam
        }
        val paramInfo = defaults[paramName] ?: return """<i><b>$fullText</b></i><br/><b><font color="red">Undefined key.</font></b>"""

//        println(paramInfo)
        // 构建文档
        return buildString {
            paramInfo.actionValue?.let {
                append("<b>Action:</b> $it")
                if (paramInfo.defaultValue != null) append("<br/>")
            }
            paramInfo.defaultValue?.let {
                append("<b>Default:</b> $it")
            }
            if (isEmpty()) append("<b>No default value.</b>")

            // 显示所有别名
            val aliases = defaults.filter { it.value == paramInfo }.keys - paramName
            if (aliases.isNotEmpty()) {
                insert(0, "<b>Aliases:</b> ${aliases.joinToString()}<br/>")
            }
            insert(0, "<i><b>$fullText</b></i><br/>")
            }
    }

    // 获取调用表达式接收者（兼容链式调用）
    private fun PyCallExpression.getReceiver(): PyExpression? {
        return when (val parent = this.parent) {
            is PyQualifiedExpression -> parent.qualifier
            else -> (this.callee?.children?.firstOrNull()) as PyExpression?
        }
    }

    private fun resolveParserFromReference(ref: PyReferenceExpression)
        = ArgparseResolver.resolveParserFromReference(ref)

    private fun resolveParserFromFunctionCall(call: PyCallExpression)
        = ArgparseResolver.resolveParserFromFunctionCall(call)

    private fun extractCallExprFromTarget(target: PyTargetExpression?)
            = ArgparseResolver.extractCallExprFromTarget(target)

    private fun collectParserDefaults(context: PsiElement, targetParserName: String?, parserTarget: PyTargetExpression)
        = ArgparseResolver.collectParserDefaults(context, targetParserName, parserTarget)

}