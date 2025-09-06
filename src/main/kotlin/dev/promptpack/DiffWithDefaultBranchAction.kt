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
import java.nio.charset.StandardCharsets
import java.util.LinkedHashSet
import java.util.Locale

class DiffWithDefaultBranchAction :
  AnAction(
    PromptPackBundle.message("action.diff.text"),
    PromptPackBundle.message("action.diff.description"),
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
      object : Task.Backgroundable(project, PromptPackBundle.message("progress.diff.title")) {
        override fun run(indicator: ProgressIndicator) {
          indicator.isIndeterminate = true
          runDiff(project, selection)
        }
      },
    )
  }

  private fun runDiff(
    project: Project,
    selection: Array<VirtualFile>,
  ) {
    val repoRoot = GitSupport.findRepoRoot(project, selection)
    if (repoRoot == null) {
      notify(project, PromptPackBundle.message("notify.noGitRepo"), NotificationType.WARNING)
      return
    }

    val state = PromptPackSettingsService.getInstance().state
    val ignoredDirs = state.ignoredDirs.map { it.lowercase(Locale.ROOT) }.toSet()
    val ignoredExts = state.ignoredExts.map { it.lowercase(Locale.ROOT) }.toSet()
    val ignoredFiles = state.ignoredFiles.map { it.lowercase(Locale.ROOT) }.toSet()

    val files = LinkedHashSet<VirtualFile>()
    selection.forEach { VfsFilters.collectFiles(it, files, ignoredDirs, ignoredExts, ignoredFiles) }

    if (files.isEmpty()) {
      notify(project, PromptPackBundle.message("notify.noTextFiles"), NotificationType.WARNING)
      return
    }

    val resolvedRef = GitSupport.resolveDefaultRef(repoRoot, state.defaultDiffRef)
    val relPaths = files.mapNotNull { v -> VfsUtilCore.getRelativePath(v, repoRoot, '/') }

    val treeHeader =
      if (state.treeScope != TreeScope.NONE) {
        FileTreeUtil.buildTreeHeaderAndText(
          project,
          FileTreeUtil.TreeInput(
            scope = state.treeScope,
            selection = selection,
            ignoredDirs = ignoredDirs,
            ignoredExts = ignoredExts,
          ),
        )
      } else {
        ""
      }

    val out =
      GitSupport.runGit(
        File(repoRoot.path),
        listOf("diff", "--no-color", "--unified=3", "-M", resolvedRef, "--") + relPaths,
      )

    if (out.exitCode != 0 && out.stdout.isBlank()) {
      val err = out.stderr.ifBlank { "exitCode=${out.exitCode}" }
      notify(project, PromptPackBundle.message("notify.gitFailed", err), NotificationType.ERROR)
      return
    }

    deliverResult(
      project = project,
      treeHeader = treeHeader,
      diffText = out.stdout.trim(),
      resolvedRef = resolvedRef,
      limitBytes = (state.maxClipboardKb.coerceAtLeast(1)) * 1024,
    )
  }

  private fun deliverResult(
    project: Project,
    treeHeader: String,
    diffText: String,
    resolvedRef: String,
    limitBytes: Int,
  ) {
    if (diffText.isBlank()) {
      notify(project, PromptPackBundle.message("notify.diffEmpty", resolvedRef), NotificationType.INFORMATION)
      return
    }

    val body =
      buildString {
        append("# ").append(PromptPackBundle.message("diff.title", resolvedRef)).append("\n\n")
        append("```diff\n").append(diffText).append('\n').append("```")
      }

    val bytes =
      buildString {
        if (treeHeader.isNotEmpty()) append(treeHeader).append('\n').append('\n')
        append(body)
      }.toByteArray(StandardCharsets.UTF_8)

    if (bytes.size > limitBytes) {
      val result =
        ExportUtil.exportMarkdown(
          project,
          treeHeader,
          listOf(body),
          ExportUtil.DEFAULT_CHUNK_LIMIT,
        )
      notify(
        project,
        PromptPackBundle.message("notify.export.done", result.dir.presentableUrl, result.parts.size),
        NotificationType.INFORMATION,
      )
    } else {
      com.intellij.openapi.ide.CopyPasteManager
        .getInstance()
        .setContents(
          java.awt.datatransfer.StringSelection(
            (if (treeHeader.isNotEmpty()) "$treeHeader\n\n" else "") + body,
          ),
        )
      notify(project, PromptPackBundle.message("notify.diffToClipboard"), NotificationType.INFORMATION)
    }
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
