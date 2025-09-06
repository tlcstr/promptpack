package dev.promptpack

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class CycleTreeScopeAction : AnAction(PromptPackBundle.message("action.cycleTreeScope.text")) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val svc = PromptPackSettingsService.getInstance()
    val next =
      when (svc.state.treeScope) {
        TreeScope.PROJECT -> TreeScope.SELECTION
        TreeScope.SELECTION -> TreeScope.NONE
        TreeScope.NONE -> TreeScope.PROJECT
      }
    svc.state.treeScope = next
    val label =
      when (next) {
        TreeScope.PROJECT -> PromptPackBundle.message("label.tree.project")
        TreeScope.SELECTION -> PromptPackBundle.message("label.tree.selection")
        TreeScope.NONE -> PromptPackBundle.message("label.tree.off")
      }
    e.project?.let {
      NotificationGroupManager
        .getInstance()
        .getNotificationGroup("PromptPack")
        .createNotification(PromptPackBundle.message("notify.treeMode", label), NotificationType.INFORMATION)
        .notify(it)
    }
  }
}
