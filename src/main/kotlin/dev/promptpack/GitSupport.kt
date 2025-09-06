package dev.promptpack

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

object GitSupport {
  fun findRepoRoot(
    project: Project,
    selection: Array<VirtualFile>,
  ): VirtualFile? {
    val base = project.basePath ?: return null
    val lfs = LocalFileSystem.getInstance()
    val projectRoot = lfs.findFileByPath(base) ?: return null

    fun hasGitDir(dir: VirtualFile?): Boolean = dir != null && dir.isDirectory && dir.findChild(".git") != null

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

  fun resolveDefaultRef(
    root: VirtualFile,
    configured: String?,
  ): String {
    val cfg = configured?.trim().orEmpty()
    if (cfg.isNotEmpty()) return cfg

    val sym = runGit(File(root.path), listOf("symbolic-ref", "--quiet", "--short", "refs/remotes/origin/HEAD"))
    if (sym.exitCode == 0) {
      val remoteHead = sym.stdout.trim()
      if (remoteHead.isNotEmpty()) return remoteHead
    }

    val show = runGit(File(root.path), listOf("remote", "show", "origin"))
    if (show.exitCode == 0) {
      val head =
        show.stdout
          .lineSequence()
          .map { it.trim() }
          .firstOrNull { it.startsWith("HEAD branch: ") }
          ?.removePrefix("HEAD branch: ")
          ?.trim()
      if (!head.isNullOrBlank()) return "origin/$head"
    }

    // Fallback
    return "origin/main"
  }

  fun runGit(
    workDir: File,
    params: List<String>,
  ): ProcessOutput {
    val cmd =
      GeneralCommandLine("git")
        .withWorkDirectory(workDir)
        .withParameters(params)
    val handler = CapturingProcessHandler(cmd)
    return handler.runProcess(60_000)
  }
}
