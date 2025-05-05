package com.gotoargdefinitionaction

import ArgparseResolver
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*

class GoToArgDefinitionAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        // 获取当前编辑文件和光标位置
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) as? PyFile ?: return
        val offset = editor.caretModel.offset
        val element = psiFile.findElementAt(offset) ?: return

        // 查找光标所在的属性访问表达式，如 args.lr:contentReference[oaicite:4]{index=4}
        val qExpr = PsiTreeUtil.getParentOfType(element, PyQualifiedExpression::class.java) ?: return
        val attrName = qExpr.referencedName ?: return
        val qualifier = qExpr.qualifier as? PyReferenceExpression ?: return

        // 将 'args' 名称解析到其定义处，例如解析到 “args = parser.parse_args()”:contentReference[oaicite:5]{index=5}
        val qualifierTarget = qualifier.reference?.resolve() as? PyTargetExpression ?: return
        val assignment = qualifierTarget.parent as? PyAssignmentStatement ?: return

        // 判断赋值右侧是否为 parse_args() 调用
        val assignedValue = assignment.assignedValue as? PyCallExpression ?: return
        val callee = assignedValue.callee as? PyQualifiedExpression ?: return
        if (callee.referencedName != "parse_args") return

        val (parserTarget, parserVarName) = when (val receiver = assignedValue.getReceiver()) {
            is PyReferenceExpression -> resolveParserFromReference(receiver)
            is PyCallExpression -> resolveParserFromFunctionCall(receiver)
            else -> null
        } ?: return

        // 查找 parserTarget 所在文件（可能是跨文件）
        val parserFile = parserTarget.containingFile as? PyFile ?: return

        // 遍历所有 add_argument 调用
        val addCalls = mutableListOf<PyCallExpression>()
        PsiTreeUtil.findChildrenOfType(parserFile, PyCallExpression::class.java).forEach { call ->
            val callCallee = call.callee as? PyQualifiedExpression ?: return@forEach
            if (callCallee.referencedName != "add_argument") return@forEach

            val qualifierExpr = callCallee.qualifier as? PyReferenceExpression ?: return@forEach
            val resolvedQualifier = qualifierExpr.reference?.resolve()

            // 关键点：判断是不是引用了同一个 parser 对象
            if (resolvedQualifier != null && resolvedQualifier == parserTarget) {
                addCalls.add(call)
            }
        }

        // 遍历找到的所有 add_argument 调用，匹配属性名
        addCalls.forEach { addCall ->
            val argList = addCall.argumentList ?: return@forEach
            // 查找关键字参数 dest 和所有位置参数（字符串字面量）
            var destName: String? = null
            val flags = mutableListOf<String>()
            PsiTreeUtil.findChildrenOfType(argList, PyKeywordArgument::class.java).forEach { kw ->
                if (kw.keyword == "dest") {
                    (kw.valueExpression as? PyStringLiteralExpression)?.stringValue?.let {
                        destName = it
                    }
                }
            }
            PsiTreeUtil.findChildrenOfType(argList, PyStringLiteralExpression::class.java).forEach { lit ->
                // 排除作为关键字值的字符串，只取位置参数
                if (lit.parent !is PyKeywordArgument) {
                    flags.add(lit.stringValue)
                }
            }
//            println(flags)
            // 根据 dest 或选项名称推断属性名
            val propName = run {
                val longOpt = flags.firstOrNull { it.startsWith("--") }
                if (longOpt != null) {
                    longOpt.removePrefix("--").replace('-', '_')
                } else {
                    flags.firstOrNull()?.removePrefix("-") ?: ""
                }
            }
            // 如果匹配成功，跳转到该 add_argument 调用处
            if (destName == attrName) {
                addCall.navigate(true)
                return
            }
            if (propName == attrName) {
                addCall.navigate(true)
                return
            }
        }
    }

    private fun resolveParserFromReference(ref: PyReferenceExpression)
            = ArgparseResolver.resolveParserFromReference(ref)

    private fun resolveParserFromFunctionCall(call: PyCallExpression)
            = ArgparseResolver.resolveParserFromFunctionCall(call)


    private fun PyCallExpression.getReceiver(): PyExpression? {
        return when (val parent = this.parent) {
            is PyQualifiedExpression -> parent.qualifier
            else -> (this.callee?.children?.firstOrNull()) as PyExpression?
        }
    }
}