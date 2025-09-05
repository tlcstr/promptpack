package dev.promptpack

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware

class TreeScopeGroupAction
  : DefaultActionGroup(PromptPackBundle.message("group.treeScope.text"), /*popup=*/true),
    DumbAware {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
