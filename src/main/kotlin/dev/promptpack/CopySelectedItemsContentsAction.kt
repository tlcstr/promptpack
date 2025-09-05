package dev.promptpack

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.*
import java.awt.datatransfer.StringSelection
import java.util.LinkedHashSet
import java.util.Locale

class CopySelectedItemsContentsAction : AnAction(
  PromptPackBundle.message("action.copyContentsSelected.text"),
  PromptPackBundle.message("action.copyContentsSelected.description"),
  null
) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val sel = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
    e.presentation.isEnabledAndVisible = (sel != null && sel.isNotEmpty())
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return

    val selectionData = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
    val selection: Array<VirtualFile> = selectionData?.copyOf() ?: emptyArray()
    if (selection.isEmpty()) {
      notify(project, PromptPackBundle.message("notify.noSelection"), NotificationType.WARNING)
      return
    }

    val svc = PromptPackSettingsService.getInstance()
    val state = svc.state
    val ignoredDirs  = state.ignoredDirs.map { it.lowercase(Locale.ROOT) }.toSet()
    val ignoredExts  = state.ignoredExts.map { it.lowercase(Locale.ROOT) }.toSet()
    val ignoredFiles = state.ignoredFiles.map { it.lowercase(Locale.ROOT) }.toSet()

    val treeHeader =
      if (state.treeScope != TreeScope.NONE)
        FileTreeUtil.buildTreeHeaderAndText(
          project,
          FileTreeUtil.TreeInput(
            scope = state.treeScope,
            selection = selection,
            ignoredDirs = ignoredDirs,
            ignoredExts = ignoredExts
          )
        )
      else ""

    val files = LinkedHashSet<VirtualFile>()
    for (vf in selection) collectFiles(vf, files, ignoredDirs, ignoredExts, ignoredFiles)

    if (files.isEmpty()) {
      notify(project, PromptPackBundle.message("notify.noTextFiles"), NotificationType.WARNING)
      return
    }

    val root = project.basePath?.let { LocalFileSystem.getInstance().findFileByPath(it) }
    val blocks = ApplicationManager.getApplication().runReadAction(Computable {
      files.map { file ->
        val rel = root?.let { VfsUtilCore.getRelativePath(file, it, '/') }
        val path = rel?.let { "./$it" } ?: file.presentableUrl

        val content = FileTreeUtil.readTextFor(file)
        val lang = FileTreeUtil.codeFenceLanguageHint(file) ?: "text"
        val fenced = FileTreeUtil.fenced(content, lang)

        "$path:\n$fenced"
      }
    })

    val singleText = buildString {
      if (treeHeader.isNotEmpty()) append(treeHeader).append('\n')
      append(blocks.joinToString("\n\n"))
    }

    if (singleText.length <= ExportUtil.DEFAULT_CHUNK_LIMIT) {
      CopyPasteManager.getInstance().setContents(StringSelection(singleText))
      notify(project, PromptPackBundle.message("notify.copiedSelectedContents", blocks.size), NotificationType.INFORMATION)
      return
    }

    val result = ExportUtil.exportMarkdown(project, treeHeader, blocks, ExportUtil.DEFAULT_CHUNK_LIMIT)
    val indexPath = result.index.presentableUrl
    CopyPasteManager.getInstance().setContents(StringSelection(indexPath))
    notify(
      project,
      PromptPackBundle.message("notify.export.done", result.dir.presentableUrl, result.parts.size),
      NotificationType.INFORMATION
    )
  }

  private fun collectFiles(
    vf: VirtualFile,
    out: MutableSet<VirtualFile>,
    ignoredDirs: Set<String>,
    ignoredExts: Set<String>,
    ignoredFiles: Set<String>
  ) {
    if (vf.isDirectory) {
      if (ignoredDirs.contains(vf.name.lowercase(Locale.ROOT))) return
      VfsUtilCore.visitChildrenRecursively(vf, object : VirtualFileVisitor<Any>() {
        override fun visitFile(file: VirtualFile): Boolean {
          if (file.isDirectory) {
            if (ignoredDirs.contains(file.name.lowercase(Locale.ROOT))) return false
            return true
          }
          if (shouldInclude(file, ignoredExts, ignoredFiles)) out.add(file)
          return false
        }
      })
    } else if (shouldInclude(vf, ignoredExts, ignoredFiles)) {
      out.add(vf)
    }
  }

  private fun shouldInclude(file: VirtualFile, ignoredExts: Set<String>, ignoredFiles: Set<String>): Boolean {
    val name = file.name.lowercase(Locale.ROOT)
    if (ignoredFiles.contains(name)) return false
    val ext = file.extension?.lowercase(Locale.ROOT)
    if (ext != null && ignoredExts.contains(ext)) return false
    if (file.fileType.isBinary) return false
    return true
  }

  private fun notify(project: Project, msg: String, type: NotificationType) {
    NotificationGroupManager.getInstance()
      .getNotificationGroup("PromptPack")
      .createNotification(msg, type)
      .notify(project)
  }
}
