package dev.promptpack

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import java.util.Locale

object PublicApiCollector {
  data class Limits(
    val perModule: Int,
    val total: Int,
  )

  /** Aggregates all switches and lists to keep function signatures short. */
  data class Config(
    val publicNames: Set<String>, // lowercased
    val ignoredExts: Set<String>, // lowercased
    val ignoredFiles: Set<String>, // lowercased exact file names
    val testDirs: Set<String>, // lowercased
    val excludeTests: Boolean,
    val ignoredDirs: Set<String>, // lowercased dir names to skip at any depth
    val limits: Limits,
  )

  data class Result(
    val files: List<VirtualFile>, // sorted by project-relative path
    val trimmedPerModuleCount: Int, // how many modules were trimmed by per-module cap
    val trimmedTotal: Boolean, // true if total cap applied
  )

  fun collect(
    project: Project,
    modules: List<VirtualFile>,
    cfg: Config,
  ): Result {
    val root = project.basePath?.let { LocalFileSystem.getInstance().findFileByPath(it) }
    val perModuleTrimmed = mutableListOf<Boolean>()
    val all = LinkedHashMap<String, VirtualFile>(1024)

    for (m in modules) {
      val pubDirs =
        findPublicDirs(
          m,
          cfg.publicNames,
          cfg.excludeTests,
          cfg.testDirs,
          cfg.ignoredDirs,
        )
      val perModuleFiles = LinkedHashSet<VirtualFile>()
      for (pub in pubDirs) {
        collectPublicTextFiles(
          start = pub,
          out = perModuleFiles,
          ignoredExts = cfg.ignoredExts,
          ignoredFiles = cfg.ignoredFiles,
          excludeTests = cfg.excludeTests,
          testDirs = cfg.testDirs,
          ignoredDirs = cfg.ignoredDirs,
        )
      }
      val ordered = perModuleFiles.toList().sortedBy { relPathOrAbs(it, root) }
      val limited = if (cfg.limits.perModule > 0) ordered.take(cfg.limits.perModule) else ordered
      perModuleTrimmed += (limited.size < ordered.size)
      for (vf in limited) all.putIfAbsent(vf.path, vf)
    }

    val combinedOrdered = all.values.toList().sortedBy { relPathOrAbs(it, root) }
    val totalTrim = cfg.limits.total > 0 && combinedOrdered.size > cfg.limits.total
    val finalList = if (totalTrim) combinedOrdered.take(cfg.limits.total) else combinedOrdered

    return Result(
      files = finalList,
      trimmedPerModuleCount = perModuleTrimmed.count { it },
      trimmedTotal = totalTrim,
    )
  }

  private fun relPathOrAbs(vf: VirtualFile, root: VirtualFile?): String {
    val rel = root?.let { VfsUtilCore.getRelativePath(vf, it, '/') }
    return (rel ?: vf.path).lowercase(Locale.ROOT)
  }

  private fun findPublicDirs(
    moduleRoot: VirtualFile,
    publicNames: Set<String>,
    excludeTests: Boolean,
    testDirs: Set<String>,
    ignoredDirs: Set<String>,
  ): List<VirtualFile> {
    val out = mutableListOf<VirtualFile>()
    VfsUtilCore.visitChildrenRecursively(
      moduleRoot,
      object : VirtualFileVisitor<Any>() {
        override fun visitFile(file: VirtualFile): Boolean {
          if (!file.isDirectory) return false
          val nameLc = file.name.lowercase(Locale.ROOT)
          if (ignoredDirs.contains(nameLc)) return false
          if (excludeTests && testDirs.contains(nameLc)) return false
          if (publicNames.contains(nameLc)) out += file
          return true
        }
      },
    )
    return out
  }

  private fun collectPublicTextFiles(
    start: VirtualFile,
    out: MutableSet<VirtualFile>,
    ignoredExts: Set<String>,
    ignoredFiles: Set<String>,
    excludeTests: Boolean,
    testDirs: Set<String>,
    ignoredDirs: Set<String>,
  ) {
    VfsUtilCore.visitChildrenRecursively(
      start,
      object : VirtualFileVisitor<Any>() {
        override fun visitFile(file: VirtualFile): Boolean {
          if (file.isDirectory) {
            val nameLc = file.name.lowercase(Locale.ROOT)
            if (ignoredDirs.contains(nameLc)) return false
            if (excludeTests && testDirs.contains(nameLc)) return false
            return true
          }
          if (VfsFilters.shouldInclude(file, ignoredExts, ignoredFiles)) out += file
          return false
        }
      },
    )
  }
}
