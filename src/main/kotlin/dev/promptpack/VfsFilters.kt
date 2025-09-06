package dev.promptpack

import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import java.util.Locale

/** Shared VFS helpers to avoid duplication and lower method complexity. */
object VfsFilters {
  fun shouldInclude(
    file: VirtualFile,
    ignoredExts: Set<String>,
    ignoredFiles: Set<String>,
  ): Boolean {
    val nameLower = file.name.lowercase(Locale.ROOT)
    val extLower = file.extension?.lowercase(Locale.ROOT)
    val isIgnoredByName = ignoredFiles.contains(nameLower)
    val isIgnoredByExt = extLower != null && ignoredExts.contains(extLower)
    return !(isIgnoredByName || isIgnoredByExt || file.fileType.isBinary)
  }

  fun collectFiles(
    start: VirtualFile,
    out: MutableSet<VirtualFile>,
    ignoredDirs: Set<String>,
    ignoredExts: Set<String>,
    ignoredFiles: Set<String>,
  ) {
    if (start.isDirectory) {
      val skipRoot = ignoredDirs.contains(start.name.lowercase(Locale.ROOT))
      if (!skipRoot) {
        VfsUtilCore.visitChildrenRecursively(
          start,
          object : VirtualFileVisitor<Any>() {
            override fun visitFile(file: VirtualFile): Boolean {
              val isDir = file.isDirectory
              if (isDir) {
                val skip = ignoredDirs.contains(file.name.lowercase(Locale.ROOT))
                return !skip
              }
              if (shouldInclude(file, ignoredExts, ignoredFiles)) out.add(file)
              return false
            }
          },
        )
      }
    } else if (shouldInclude(start, ignoredExts, ignoredFiles)) {
      out.add(start)
    }
  }
}
