package dev.promptpack

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object ExportUtil {
  /** Chunk size threshold (characters) for a single part. */
  const val DEFAULT_CHUNK_LIMIT = 150_000

  data class Result(
    val parts: List<VirtualFile>,
    val index: VirtualFile,
    val dir: VirtualFile,
    val totalChars: Int,
  )

  fun splitIntoParts(
    blocks: List<String>,
    limit: Int,
  ): List<String> {
    if (blocks.isEmpty()) return emptyList()
    val parts = mutableListOf<StringBuilder>()
    var current = StringBuilder()

    fun push() {
      parts += current
      current = StringBuilder()
    }

    for (block in blocks) {
      val withSep = if (current.isEmpty()) block else "\n\n$block"
      if (current.isNotEmpty() && current.length + withSep.length > limit) {
        push()
        current.append(block)
      } else {
        current.append(withSep)
      }
    }
    if (current.isNotEmpty()) push()
    return parts.map { it.toString() }
  }

  fun exportMarkdown(
    project: Project,
    treeHeaderOrEmpty: String,
    blocks: List<String>,
    chunkLimit: Int = DEFAULT_CHUNK_LIMIT,
  ): Result {
    val exportDir = ensureExportDir(project)
    val (partsText, total) = assembleParts(treeHeaderOrEmpty, blocks, chunkLimit)
    val written = writeParts(exportDir, partsText)
    val index = writeIndex(exportDir, treeHeaderOrEmpty, written, blocks.size, partsText.size, chunkLimit)
    openIndex(project, index)
    return Result(parts = written, index = index, dir = exportDir, totalChars = total)
  }

  // -- helpers --

  private fun ensureExportDir(project: Project): VirtualFile {
    val ts =
      DateTimeFormatter
        .ofPattern("yyyyMMdd-HHmmss")
        .withZone(ZoneId.systemDefault())
        .format(Instant.now())

    val base = project.basePath ?: System.getProperty("user.home")
    val dirPath: Path = Paths.get(base, ".promptpack", "exports", ts)
    Files.createDirectories(dirPath)

    val lfs = LocalFileSystem.getInstance()
    return lfs.refreshAndFindFileByNioFile(dirPath)
      ?: lfs.refreshAndFindFileByPath(dirPath.toString())
      ?: error("Cannot resolve export directory in VFS: $dirPath")
  }

  private fun assembleParts(
    treeHeaderOrEmpty: String,
    blocks: List<String>,
    chunkLimit: Int,
  ): Pair<List<String>, Int> {
    val joined = blocks.joinToString("\n\n")
    val total = treeHeaderOrEmpty.length + 1 + joined.length
    val partsText = if (total <= chunkLimit) listOf(joined) else splitIntoParts(blocks, chunkLimit)
    return partsText to total
  }

  private fun writeParts(
    exportDirVf: VirtualFile,
    partsText: List<String>,
  ): List<VirtualFile> =
    ApplicationManager.getApplication().runWriteAction<List<VirtualFile>> {
      val written = mutableListOf<VirtualFile>()
      for (i in partsText.indices) {
        val name = if (partsText.size == 1) "content.md" else "part-%02d.md".format(i + 1)
        val vf = exportDirVf.findChild(name) ?: exportDirVf.createChildData(this, name)
        VfsUtil.saveText(vf, partsText[i])
        written += vf
      }
      written
    }

  private fun writeIndex(
    exportDirVf: VirtualFile,
    treeHeaderOrEmpty: String,
    parts: List<VirtualFile>,
    blocksCount: Int,
    partsCount: Int,
    chunkLimit: Int,
  ): VirtualFile =
    ApplicationManager.getApplication().runWriteAction<VirtualFile> {
      val idx = exportDirVf.findChild("index.md") ?: exportDirVf.createChildData(this, "index.md")
      val content =
        buildString {
          append("# ")
            .append(PromptPackBundle.message("export.index.title"))
            .append('\n')
          append(
            PromptPackBundle.message(
              "export.index.meta",
              blocksCount,
              partsCount,
              chunkLimit,
            ),
          ).append("\n\n")
          if (treeHeaderOrEmpty.isNotBlank()) {
            append(treeHeaderOrEmpty).append('\n')
          }
          append("## ")
            .append(PromptPackBundle.message("export.index.parts"))
            .append('\n')
          for (p in parts) {
            append("- [")
              .append(p.name)
              .append("](")
              .append(p.name)
              .append(")\n")
          }
        }
      VfsUtil.saveText(idx, content)
      idx
    }

  private fun openIndex(
    project: Project,
    indexVf: VirtualFile,
  ) {
    FileEditorManager.getInstance(project).openFile(indexVf, true)
  }
}
