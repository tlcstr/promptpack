package dev.promptpack

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.util.LinkedHashSet
import java.util.Locale

/**
 * Copies contents of all open editor tabs, respecting PromptPack filters and settings.
 */
class CopyOpenTabsContentsAction :
  AnAction(
    PromptPackBundle.message("action.copyTabContents.text"),
    PromptPackBundle.message("action.copyTabContents.description"),
    null,
  ) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val project = e.project
    if (project == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    val openFiles = FileEditorManager.getInstance(project).openFiles
    e.presentation.isEnabledAndVisible = openFiles.isNotEmpty()
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val openFiles = FileEditorManager.getInstance(project).openFiles
    if (openFiles.isEmpty()) {
      notify(project, PromptPackBundle.message("notify.noOpenTabs"), NotificationType.WARNING)
      return
    }

    ProgressManager
      .getInstance()
      .run(
        object : Task.Backgroundable(
          project,
          PromptPackBundle.message("progress.copyTabs.title"),
        ) {
          override fun run(indicator: ProgressIndicator) {
            indicator.isIndeterminate = true
            processOpenTabs(project, openFiles)
          }
        },
      )
  }

  private fun processOpenTabs(
    project: Project,
    openFiles: Array<VirtualFile>,
  ) {
    val st = PromptPackSettingsService.getInstance().state

    fun Set<String>.lc(): Set<String> = this.map { it.lowercase(Locale.ROOT) }.toSet()

    val ignoredDirsBase = st.ignoredDirs.lc().toMutableSet()
    val ignoredExts = st.ignoredExts.lc()
    val ignoredFiles = st.ignoredFiles.lc()
    val testDirs = st.testDirs.lc()

    val effectiveIgnoredDirs =
      if (st.testFilesMode == TestFilesMode.EXCLUDE) {
        ignoredDirsBase.apply { addAll(testDirs) }
      } else {
        ignoredDirsBase
      }

    fun isUnderIgnoredDir(vf: VirtualFile): Boolean {
      var cur = vf.parent
      while (cur != null) {
        if (effectiveIgnoredDirs.contains(cur.name.lowercase(Locale.ROOT))) return true
        cur = cur.parent
      }
      return false
    }

    val mainFiles = LinkedHashSet<VirtualFile>()
    openFiles.forEach { vf ->
      if (
        !vf.isDirectory &&
        !isUnderIgnoredDir(vf) &&
        VfsFilters.shouldInclude(vf, ignoredExts, ignoredFiles)
      ) {
        mainFiles += vf
      }
    }

    if (mainFiles.isEmpty()) {
      notify(project, PromptPackBundle.message("notify.noTextFiles"), NotificationType.WARNING)
      return
    }

    CopyContentsEngine.run(
      CopyContentsEngine.Input(
        project = project,
        state = st,
        selectionForTree = mainFiles.toTypedArray(),
        mainFiles = mainFiles.toList(),
      ),
    )
  }

  private fun notify(
    project: Project,
    msg: String,
    type: NotificationType,
  ) {
    NotificationGroupManager
      .getInstance()
      .getNotificationGroup("PromptPack")
      .createNotification(msg, type)
      .notify(project)
  }
}
