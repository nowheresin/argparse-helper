package com.argparsecompletioncontributor

import ArgparseResolver
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import com.jetbrains.python.psi.*

class ArgparseCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().withParent(PyReferenceExpression::class.java),
            ArgparseCompletionProvider()
        )
    }
}

private class ArgparseCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val position = parameters.position
        val refExpr = position.parent as? PyReferenceExpression ?: return
        val variableName= refExpr.qualifier?.text
        val mainFile = position.containingFile as? PyFile ?: return

        // 1. 查找变量赋值
        val argsAssignment = mainFile.statements
            .filterIsInstance<PyAssignmentStatement>()
            .find { (it.leftHandSideExpression as? PyTargetExpression)?.name == variableName }
            ?: return

        // 2. 解析parse_args调用链
        val parseArgsCall = argsAssignment.assignedValue as? PyCallExpression ?: return
        if ((parseArgsCall.callee as? PyReferenceExpression)?.name != "parse_args") return

        // 3. 获取调用parse_args的parser实例
        val (parserTarget, parserVarName) = when (val receiver = parseArgsCall.getReceiver()) {
            is PyReferenceExpression -> resolveParserFromReference(receiver)
            is PyCallExpression -> resolveParserFromFunctionCall(receiver)
            else -> null
        } ?: return
        val parserDef = extractCallExprFromTarget(parserTarget) ?: return

        // 4. 收集特定parser的参数
        val context = when (parserDef.parent) {
            is PyFunction -> parserDef.parent as PyFunction
            else -> parserDef.containingFile
        }

        val defaults = collectParserDefaults(context, parserVarName, parserTarget)

        defaults.forEach { (name, param) ->
            val typeHint = when {
                param.defaultValue != null -> "default=${param.defaultValue}"
                param.actionValue != null -> "action=${param.actionValue}"
                else -> "argparse parameter"
            }

            result.addElement(
                LookupElementBuilder.create(name)
                    .withIcon(AllIcons.Nodes.Parameter)
                    .withTypeText(typeHint)
            )
        }
    }

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