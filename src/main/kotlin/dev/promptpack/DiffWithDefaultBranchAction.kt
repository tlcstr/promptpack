package dev.promptpack

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.LinkedHashSet

class DiffWithDefaultBranchAction : AnAction(
  PromptPackBundle.message("action.diff.text"),
  PromptPackBundle.message("action.diff.description"),
  null
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

    ProgressManager.getInstance().run(object : Task.Backgroundable(project, PromptPackBundle.message("progress.diff.title")) {
      override fun run(indicator: ProgressIndicator) {
        indicator.isIndeterminate = true

        val repoRoot = findGitRootForSelection(project, selection)
        if (repoRoot == null) {
          notify(project, PromptPackBundle.message("notify.noGitRepo"), NotificationType.WARNING)
          return
        }

        val svc = PromptPackSettingsService.getInstance()
        val state = svc.state

        val ignoredDirs  = state.ignoredDirs.map { it.lowercase(Locale.ROOT) }.toSet()
        val ignoredExts  = state.ignoredExts.map { it.lowercase(Locale.ROOT) }.toSet()
        val ignoredFiles = state.ignoredFiles.map { it.lowercase(Locale.ROOT) }.toSet()

        val files = LinkedHashSet<VirtualFile>()
        selection.forEach { collectFiles(it, files, ignoredDirs, ignoredExts, ignoredFiles) }
        if (files.isEmpty()) {
          notify(project, PromptPackBundle.message("notify.noTextFiles"), NotificationType.WARNING)
          return
        }

        val resolvedRef = resolveDefaultRef(project, repoRoot, state.defaultDiffRef)

        val relPaths = files.mapNotNull { v -> VfsUtilCore.getRelativePath(v, repoRoot, '/') }

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

        val diffOutput = runGit(
          project,
          workDir = File(repoRoot.path),
          listOf("diff", "--no-color", "--unified=3", "-M", resolvedRef, "--") + relPaths
        )

        if (diffOutput.exitCode != 0 && diffOutput.stdout.isBlank()) {
          val err = diffOutput.stderr.ifBlank { "exitCode=${diffOutput.exitCode}" }
          notify(project, PromptPackBundle.message("notify.gitFailed", err), NotificationType.ERROR)
          return
        }

        val diffText = diffOutput.stdout.trim()
        if (diffText.isBlank()) {
          notify(project, PromptPackBundle.message("notify.diffEmpty", resolvedRef), NotificationType.INFORMATION)
          return
        }

        val header = "# " + PromptPackBundle.message("diff.title", resolvedRef) + "\n\n"
        val bodyBlock = buildString {
          append(header)
          append("```diff\n")
          append(diffText).append('\n')
          append("```")
        }

        val bytes = buildString {
          if (treeHeader.isNotEmpty()) append(treeHeader).append('\n').append('\n')
          append(bodyBlock)
        }.toByteArray(StandardCharsets.UTF_8)

        val limitBytes = (state.maxClipboardKb.coerceAtLeast(1)) * 1024
        if (bytes.size > limitBytes) {
          val result = ExportUtil.exportMarkdown(project, treeHeader, listOf(bodyBlock), ExportUtil.DEFAULT_CHUNK_LIMIT)
          notify(
            project,
            PromptPackBundle.message("notify.export.done", result.dir.presentableUrl, result.parts.size),
            NotificationType.INFORMATION
          )
        } else {
          com.intellij.openapi.ide.CopyPasteManager.getInstance()
            .setContents(java.awt.datatransfer.StringSelection(
              (if (treeHeader.isNotEmpty()) "$treeHeader\n\n" else "") + bodyBlock
            ))
          notify(project, PromptPackBundle.message("notify.diffToClipboard"), NotificationType.INFORMATION)
        }
      }
    })
  }

  private fun notify(project: Project, msg: String, type: NotificationType) {
    NotificationGroupManager.getInstance()
      .getNotificationGroup("PromptPack")
      .createNotification(msg, type)
      .notify(project)
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
      VfsUtilCore.visitChildrenRecursively(vf, object : com.intellij.openapi.vfs.VirtualFileVisitor<Any>() {
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

  private fun findGitRootForSelection(project: Project, selection: Array<VirtualFile>): VirtualFile? {
    val base = project.basePath ?: return null
    val projectRoot = LocalFileSystem.getInstance().findFileByPath(base) ?: return null

    fun hasGitDir(dir: VirtualFile?): Boolean {
      if (dir == null || !dir.isDirectory) return false
      return dir.findChild(".git") != null
    }
    if (hasGitDir(projectRoot)) return projectRoot

    for (vf in selection) {
      var cur: VirtualFile? = if (vf.isDirectory) vf else vf.parent
      while (cur != null && cur != projectRoot.parent) {
        if (hasGitDir(cur)) return cur
        cur = cur.parent
      }
    }
    return null
  }

  private fun resolveDefaultRef(project: Project, root: VirtualFile, configured: String?): String {
    val cfg = configured?.trim().orEmpty()
    if (cfg.isNotEmpty()) return cfg

    val sym = runGit(project, File(root.path),
      listOf("symbolic-ref", "--quiet", "--short", "refs/remotes/origin/HEAD"))
    if (sym.exitCode == 0) {
      val remoteHead = sym.stdout.trim()
      if (remoteHead.isNotEmpty()) return remoteHead
    }

    val show = runGit(project, File(root.path), listOf("remote", "show", "origin"))
    if (show.exitCode == 0) {
      val head = show.stdout.lineSequence()
        .map { it.trim() }
        .firstOrNull { it.startsWith("HEAD branch: ") }
        ?.removePrefix("HEAD branch: ")
        ?.trim()
      if (!head.isNullOrBlank()) return "origin/$head"
    }

    return "origin/main"
  }

  private fun runGit(project: Project, workDir: File, params: List<String>): ProcessOutput {
    val cmd = GeneralCommandLine("git").withWorkDirectory(workDir).withParameters(params)
    val handler = CapturingProcessHandler(cmd)
    return handler.runProcess(60_000)
  }

  @Suppress("unused")
  private fun timeStampedFileName(base: String, ext: String): String {
    val safe = FileUtil.sanitizeFileName(base)
    val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
    return "$safe-$stamp.$ext"
  }
}
