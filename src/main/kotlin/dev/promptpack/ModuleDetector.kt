package dev.promptpack

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import java.util.Locale

/**
 * Finds module roots within the user's selection area and along ancestor chains.
 * Rules are OR-combined: by manifest, by path pattern; both can be gated by "require public folder".
 */
object ModuleDetector {
  data class Config(
    val detectByManifest: Boolean,
    val manifestNames: Set<String>, // case-insensitive, supports * and ?
    val detectByPathPatterns: Boolean,
    val pathPatterns: Set<String>, // project-relative, glob, case-insensitive
    val requirePublicFolder: Boolean,
    val publicFolderNames: Set<String>, // case-insensitive
    val ignoredDirs: Set<String>, // names, case-insensitive
  )

  fun detectModules(
    project: Project,
    selection: Array<VirtualFile>,
    cfg: Config,
  ): List<VirtualFile> {
    val root =
      project.basePath
        ?.let { LocalFileSystem.getInstance().findFileByPath(it) }
        ?: return emptyList()
    val res = LinkedHashSet<VirtualFile>()

    fun String.lc() = lowercase(Locale.ROOT)

    fun isModuleDir(dir: VirtualFile): Boolean {
      if (!dir.isDirectory) return false
      val byManifest = cfg.detectByManifest && hasManifest(dir, cfg.manifestNames)
      val byPath = cfg.detectByPathPatterns && matchesPathPatterns(root, dir, cfg.pathPatterns)
      val base =
        byManifest ||
          byPath ||
          (!cfg.detectByManifest && !cfg.detectByPathPatterns && cfg.requirePublicFolder)
      return base && (!cfg.requirePublicFolder || hasPublicFolder(dir, cfg.publicFolderNames))
    }

    fun collectDescendantModules(start: VirtualFile) {
      if (!start.isDirectory) return
      VfsUtilCore.visitChildrenRecursively(
        start,
        object : VirtualFileVisitor<Any>() {
          override fun visitFile(file: VirtualFile): Boolean {
            if (file.isDirectory) {
              val nameLc = file.name.lc()
              if (cfg.ignoredDirs.contains(nameLc)) return false
              if (isModuleDir(file)) res.add(file)
              return true
            }
            return false
          }
        },
      )
    }

    fun collectAncestorModules(start: VirtualFile) {
      var cur: VirtualFile? = if (start.isDirectory) start else start.parent
      while (cur != null && cur != root.parent) {
        if (cur.isDirectory && isModuleDir(cur)) res.add(cur)
        cur = cur.parent
      }
    }

    for (vf in selection) {
      if (vf.isDirectory) collectDescendantModules(vf)
      collectAncestorModules(vf)
    }

    return res.toList().sortedBy {
      VfsUtilCore.getRelativePath(it, root, '/')
        ?.lc()
        ?: it.path.lc()
    }
  }

  // --- helpers ---

  private fun hasManifest(
    dir: VirtualFile,
    patterns: Set<String>,
  ): Boolean {
    val names =
      dir.children
        ?.asList()
        .orEmpty()
        .filter { !it.isDirectory }
        .map { it.name }
    return names.any { name ->
      patterns.any { pat -> glob(name.lowercase(Locale.ROOT), pat.lowercase(Locale.ROOT)) }
    }
  }

  private fun hasPublicFolder(
    dir: VirtualFile,
    publicNames: Set<String>,
  ): Boolean {
    var found = false
    VfsUtilCore.visitChildrenRecursively(
      dir,
      object : VirtualFileVisitor<Any>() {
        override fun visitFile(file: VirtualFile): Boolean {
          if (!file.isDirectory) return false
          val nameLc = file.name.lowercase(Locale.ROOT)
          if (publicNames.contains(nameLc)) {
            found = true
            return false
          }
          return !found
        }
      },
    )
    return found
  }

  private fun matchesPathPatterns(
    projectRoot: VirtualFile,
    dir: VirtualFile,
    patterns: Set<String>,
  ): Boolean {
    val rel =
      VfsUtilCore.getRelativePath(dir, projectRoot, '/')
        ?.lowercase(Locale.ROOT)
        ?: return false
    return patterns.any { pat -> glob(rel, pat.lowercase(Locale.ROOT)) }
  }

  /**
   * Tiny glob: '*' (any run), '?' (single char). Anchored.
   */
  private fun glob(text: String, pattern: String): Boolean {
    val sb = StringBuilder("^")
    for (ch in pattern) {
      when (ch) {
        '*' -> sb.append(".*")
        '?' -> sb.append('.')
        '.', '(', ')', '[', ']', '{', '}', '^', '$', '+', '|', '\\' -> sb.append('\\').append(ch)
        else -> sb.append(ch)
      }
    }
    sb.append('$')
    return Regex(sb.toString()).matches(text)
  }
}
