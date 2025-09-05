package dev.promptpack

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class EditBlacklistAction : AnAction(PromptPackBundle.message("action.editBlacklist.text")) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    BlacklistDialog(e.project).show()
  }
}
