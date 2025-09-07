package dev.promptpack

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import java.awt.datatransfer.StringSelection
import java.util.LinkedHashSet
import java.util.Locale

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
    } else {
      processSelection(project, selection)
    }
  }

  /** Keep actionPerformed short; do the heavy work here without early returns. */
  private fun processSelection(
    project: Project,
    selection: Array<VirtualFile>,
  ) {
    val state = PromptPackSettingsService.getInstance().state
    val ignoredDirsBase = state.ignoredDirs.map { it.lowercase(Locale.ROOT) }.toMutableSet()
    val ignoredExts = state.ignoredExts.map { it.lowercase(Locale.ROOT) }.toSet()
    val ignoredFiles = state.ignoredFiles.map { it.lowercase(Locale.ROOT) }.toSet()
    val testDirs = state.testDirs.map { it.lowercase(Locale.ROOT) }.toSet()

    // If tests are excluded, extend ignoredDirs with testDirs for this operation.
    val effectiveIgnoredDirs =
      if (state.testFilesMode == TestFilesMode.EXCLUDE) ignoredDirsBase.apply { addAll(testDirs) } else ignoredDirsBase

    val treeHeader =
      if (state.treeScope != TreeScope.NONE) {
        FileTreeUtil.buildTreeHeaderAndText(
          project,
          FileTreeUtil.TreeInput(
            scope = state.treeScope,
            selection = selection,
            ignoredDirs = effectiveIgnoredDirs,
            ignoredExts = ignoredExts,
          ),
        )
      } else {
        ""
      }

    val files = LinkedHashSet<VirtualFile>()
    selection.forEach {
      VfsFilters.collectFiles(it, files, effectiveIgnoredDirs, ignoredExts, ignoredFiles)
    }

    if (files.isEmpty()) {
      notify(project, PromptPackBundle.message("notify.noTextFiles"), NotificationType.WARNING)
    } else {
      val blocks = buildBlocks(project, files)
      val singleText =
        buildString {
          if (treeHeader.isNotEmpty()) append(treeHeader).append('\n')
          append(blocks.joinToString("\n\n"))
        }

      if (singleText.length <= ExportUtil.DEFAULT_CHUNK_LIMIT) {
        CopyPasteManager.getInstance().setContents(StringSelection(singleText))
        notify(
          project,
          PromptPackBundle.message("notify.copiedSelectedContents", blocks.size),
          NotificationType.INFORMATION,
        )
      } else {
        val result =
          ExportUtil.exportMarkdown(
            project,
            treeHeader,
            blocks,
            ExportUtil.DEFAULT_CHUNK_LIMIT,
          )
        CopyPasteManager.getInstance().setContents(StringSelection(result.index.presentableUrl))
        notify(
          project,
          PromptPackBundle.message("notify.export.done", result.dir.presentableUrl, result.parts.size),
          NotificationType.INFORMATION,
        )
      }
    }
  }

  private fun buildBlocks(
    project: Project,
    files: Set<VirtualFile>,
  ): List<String> {
    val root = project.basePath?.let { LocalFileSystem.getInstance().findFileByPath(it) }
    return ApplicationManager.getApplication().runReadAction(
      Computable {
        files.map { file ->
          val rel = root?.let { VfsUtilCore.getRelativePath(file, it, '/') }
          val path = rel?.let { "./$it" } ?: file.presentableUrl
          val content = FileTreeUtil.readTextFor(file)
          val lang = FileTreeUtil.codeFenceLanguageHint(file) ?: "text"
          val fenced = FileTreeUtil.fenced(content, lang)
          "$path:\n$fenced"
        }
      },
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
