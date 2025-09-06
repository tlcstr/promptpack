package dev.promptpack

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.Project

abstract class BaseTreeScopeToggleAction(
  private val myScope: TreeScope,
  text: String,
) : ToggleAction(text) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  private val svc get() = PromptPackSettingsService.getInstance()

  override fun isSelected(e: AnActionEvent): Boolean = (svc.state.treeScope == myScope)

  override fun setSelected(
    e: AnActionEvent,
    state: Boolean,
  ) {
    if (state) {
      svc.state.treeScope = myScope
      notifyMode(e.project, myScope)
    }
  }

  private fun notifyMode(
    project: Project?,
    scope: TreeScope,
  ) {
    if (project == null) return
    val label =
      when (scope) {
        TreeScope.PROJECT -> PromptPackBundle.message("label.tree.project")
        TreeScope.SELECTION -> PromptPackBundle.message("label.tree.selection")
        TreeScope.NONE -> PromptPackBundle.message("label.tree.off")
      }
    NotificationGroupManager
      .getInstance()
      .getNotificationGroup("PromptPack")
      .createNotification(PromptPackBundle.message("notify.treeMode", label), NotificationType.INFORMATION)
      .notify(project)
  }
}

class TreeScopeProjectToggleAction :
  BaseTreeScopeToggleAction(
    TreeScope.PROJECT,
    PromptPackBundle.message("menu.toggle.project"),
  )

class TreeScopeSelectionToggleAction :
  BaseTreeScopeToggleAction(
    TreeScope.SELECTION,
    PromptPackBundle.message("menu.toggle.selection"),
  )

class TreeScopeNoneToggleAction :
  BaseTreeScopeToggleAction(
    TreeScope.NONE,
    PromptPackBundle.message("menu.toggle.none"),
  )
