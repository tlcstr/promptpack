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
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import java.io.File
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.Locale

class CopyChangedFilesContentsAction :
  AnAction(
    PromptPackBundle.message("action.copyChangedContents.text"),
    PromptPackBundle.message("action.copyChangedContents.description"),
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
        PromptPackBundle.message("progress.copyChanged.title"),
      ) {
        override fun run(indicator: ProgressIndicator) {
          indicator.isIndeterminate = true
          processChanged(project, selection)
        }
      },
    )
  }

  private fun processChanged(
    project: Project,
    selection: Array<VirtualFile>,
  ) {
    val st = PromptPackSettingsService.getInstance().state

    val repoRoot = GitSupport.findRepoRoot(project, selection)
    if (repoRoot == null) {
      notify(project, PromptPackBundle.message("notify.noGitRepo"), NotificationType.WARNING)
      return
    }

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

    // If invoked from an editor tab (single file), fall back to the repo root scope
    val scopeSelection =
      if (selection.isEmpty() || (selection.size == 1 && !selection[0].isDirectory)) {
        arrayOf(repoRoot)
      } else {
        selection
      }

    val candidates = LinkedHashSet<VirtualFile>()
    scopeSelection.forEach {
      VfsFilters.collectFiles(
        start = it,
        out = candidates,
        ignoredDirs = effectiveIgnoredDirs,
        ignoredExts = ignoredExts,
        ignoredFiles = ignoredFiles,
      )
    }
    if (candidates.isEmpty()) {
      notify(project, PromptPackBundle.message("notify.noTextFiles"), NotificationType.WARNING)
      return
    }

    val pathToFile = LinkedHashMap<String, VirtualFile>()
    for (vf in candidates) {
      val rel = VfsUtilCore.getRelativePath(vf, repoRoot, '/')
      if (rel != null) {
        pathToFile[rel] = vf
      }
    }

    if (pathToFile.isEmpty()) {
      notify(project, PromptPackBundle.message("notify.noTextFiles"), NotificationType.WARNING)
      return
    }

    val changedResult =
      GitSupport.listWorkingTreeChanges(
        rootDir = File(repoRoot.path),
        relPathsFilter = pathToFile.keys.toList(),
      )

    if (changedResult.exitCode != 0) {
      val err = changedResult.stderr.ifBlank { "exitCode=${changedResult.exitCode}" }
      notify(project, PromptPackBundle.message("notify.gitFailed", err), NotificationType.ERROR)
      return
    }

    if (changedResult.paths.isEmpty()) {
      notify(project, PromptPackBundle.message("notify.noLocalChanges"), NotificationType.INFORMATION)
      return
    }

    val changedFiles = changedResult.paths.mapNotNull { pathToFile[it] }.distinct()
    if (changedFiles.isEmpty()) {
      notify(project, PromptPackBundle.message("notify.noTextFiles"), NotificationType.WARNING)
      return
    }

    CopyContentsEngine.run(
      CopyContentsEngine.Input(
        project = project,
        state = st,
        selectionForTree = scopeSelection,
        mainFiles = changedFiles,
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
