package dev.promptpack

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction

/**
 * Toggle Public API section generation for "Copy Contents of Selected Items".
 * - Runs on BGT, never blocks the EDT.
 * - Reflects the current state with a checkmark in the menu.
 */
class TogglePublicApiAction :
  ToggleAction(PromptPackBundle.message("action.togglePublic.text")) {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  private val svc get() = PromptPackSettingsService.getInstance()

  override fun isSelected(e: AnActionEvent): Boolean = svc.state.publicEnabled

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    svc.state.publicEnabled = state
    e.project?.let { project ->
      val key = if (state) "notify.public.enabled" else "notify.public.disabled"
      NotificationGroupManager
        .getInstance()
        .getNotificationGroup("PromptPack")
        .createNotification(PromptPackBundle.message(key), NotificationType.INFORMATION)
        .notify(project)
    }
  }
}
