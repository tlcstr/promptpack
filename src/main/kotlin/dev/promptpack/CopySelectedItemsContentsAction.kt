package dev.promptpack

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.util.LinkedHashSet
import java.util.Locale

/**
 * "Copy Contents of Selected Items" + optional Public API collection:
 * - Heavy work in a background task (EDT remains free).
 * - Reads under ReadAction (includes unsaved buffers).
 * - Optional file tree header.
 * - Optional: detect modules and collect files from their public folders (limits + test exclusion).
 * - Small output -> clipboard; large -> export to .promptpack/exports/<ts>/.
 */
class CopySelectedItemsContentsAction :
  AnAction(
    PromptPackBundle.message("action.copyContentsSelected.text"),
    PromptPackBundle.message("action.copyContentsSelected.description"),
    null,
  ) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val sel = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
    e.presentation.isEnabledAndVisible = sel != null && sel.isNotEmpty()
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val selection = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)?.copyOf() ?: emptyArray()
    if (selection.isEmpty()) {
      notify(project, PromptPackBundle.message("notify.noSelection"), NotificationType.WARNING)
      return
    }
    ProgressManager.getInstance().run(
      object : Task.Backgroundable(
        project,
        PromptPackBundle.message("progress.copy.title"),
      ) {
        override fun run(indicator: ProgressIndicator) {
          indicator.isIndeterminate = true
          processSelection(project, selection)
        }
      },
    )
  }

  private fun processSelection(
    project: Project,
    selection: Array<VirtualFile>,
  ) {
    val st = PromptPackSettingsService.getInstance().state

    val ignoredDirsBase = st.ignoredDirs.map { it.lowercase(Locale.ROOT) }.toMutableSet()
    val ignoredExts = st.ignoredExts.map { it.lowercase(Locale.ROOT) }.toSet()
    val ignoredFiles = st.ignoredFiles.map { it.lowercase(Locale.ROOT) }.toSet()
    val testDirs = st.testDirs.map { it.lowercase(Locale.ROOT) }.toSet()
    val effectiveIgnoredDirs =
      if (st.testFilesMode == TestFilesMode.EXCLUDE) {
        ignoredDirsBase.apply { addAll(testDirs) }
      } else {
        ignoredDirsBase
      }

    val mainFiles = collectMainFiles(selection, effectiveIgnoredDirs, ignoredExts, ignoredFiles)

    CopyContentsEngine.run(
      CopyContentsEngine.Input(
        project = project,
        state = st,
        selectionForTree = selection,
        mainFiles = mainFiles.toList(),
      ),
    )
  }

  // ---- helpers (small, focused) ----

  private fun collectMainFiles(
    selection: Array<VirtualFile>,
    ignoredDirs: Set<String>,
    ignoredExts: Set<String>,
    ignoredFiles: Set<String>,
  ): LinkedHashSet<VirtualFile> {
    val files = LinkedHashSet<VirtualFile>()
    selection.forEach { VfsFilters.collectFiles(it, files, ignoredDirs, ignoredExts, ignoredFiles) }
    return files
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
