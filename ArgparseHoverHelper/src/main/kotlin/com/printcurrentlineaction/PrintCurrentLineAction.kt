package com.printcurrentlineaction

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys

class PrintCurrentLineAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
            ?: return

        val caretModel = editor.caretModel
        val lineNumber = caretModel.logicalPosition.line
        val document = editor.document

        val lineText = document.text.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[lineNumber]
        println("Line $lineNumber: $lineText")
    }
}