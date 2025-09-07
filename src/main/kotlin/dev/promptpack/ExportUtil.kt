package dev.promptpack

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Export helper that writes via NIO on a background thread,
 * then refreshes VFS and opens index on EDT.
 */
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
    val dirPath = ensureExportDirNio(project)
    val (partsText, total) = assembleParts(treeHeaderOrEmpty, blocks, chunkLimit)

    // Heavy I/O via NIO (не трогаем VFS тут)
    val partPaths = writePartsNio(dirPath, partsText)
    val indexPath =
      writeIndexNio(
        dirPath,
        treeHeaderOrEmpty,
        partPaths,
        blocks.size,
        partsText.size,
        chunkLimit,
      )

    // Refresh VFS и получить VirtualFile на EDT
    val (exportDirVf, writtenVf, indexVf) = refreshAndResolveVfsOnEdt(dirPath, partPaths, indexPath)

    // Открыть index.md на EDT (UI)
    openIndexOnEdt(project, indexVf)

    return Result(parts = writtenVf, index = indexVf, dir = exportDirVf, totalChars = total)
  }

  // -- NIO helpers --

  private fun ensureExportDirNio(project: Project): Path {
    val ts =
      DateTimeFormatter
        .ofPattern("yyyyMMdd-HHmmss")
        .withZone(ZoneId.systemDefault())
        .format(Instant.now())

    val base = project.basePath ?: System.getProperty("user.home")
    val dirPath: Path = Paths.get(base, ".promptpack", "exports", ts)
    Files.createDirectories(dirPath)
    return dirPath
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

  private fun writePartsNio(
    exportDir: Path,
    partsText: List<String>,
  ): List<Path> {
    val out = mutableListOf<Path>()
    for (i in partsText.indices) {
      val name = if (partsText.size == 1) "content.md" else "part-%02d.md".format(i + 1)
      val p = exportDir.resolve(name)
      Files.writeString(
        p,
        partsText[i],
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING,
        StandardOpenOption.WRITE,
      )
      out.add(p)
    }
    return out
  }

  private fun writeIndexNio(
    exportDir: Path,
    treeHeaderOrEmpty: String,
    partPaths: List<Path>,
    blocksCount: Int,
    partsCount: Int,
    chunkLimit: Int,
  ): Path {
    val content =
      buildString {
        append("# ")
          .append(PromptPackBundle.message("export.index.title"))
          .append('\n')
          .append(
            PromptPackBundle.message(
              "export.index.meta",
              blocksCount,
              partsCount,
              chunkLimit,
            ),
          )
          .append("\n\n")
        if (treeHeaderOrEmpty.isNotBlank()) {
          append(treeHeaderOrEmpty).append('\n')
        }
        append("## ")
          .append(PromptPackBundle.message("export.index.parts"))
          .append('\n')
        for (p in partPaths) {
          val fn = p.fileName.toString()
          append("- [").append(fn).append("](").append(fn).append(")\n")
        }
      }
    val idx = exportDir.resolve("index.md")
    Files.writeString(
      idx,
      content,
      StandardCharsets.UTF_8,
      StandardOpenOption.CREATE,
      StandardOpenOption.TRUNCATE_EXISTING,
      StandardOpenOption.WRITE,
    )
    return idx
  }

  // -- VFS/UI helpers (EDT) --

  private data class ResolvedVfs(
    val dir: VirtualFile,
    val parts: List<VirtualFile>,
    val index: VirtualFile,
  )

  private fun refreshAndResolveVfsOnEdt(
    dirPath: Path,
    partPaths: List<Path>,
    indexPath: Path,
  ): ResolvedVfs {
    val lfs = LocalFileSystem.getInstance()
    var dirVf: VirtualFile? = null
    var indexVf: VirtualFile? = null
    val partsVf = mutableListOf<VirtualFile>()

    ApplicationManager.getApplication().invokeAndWait {
      dirVf = lfs.refreshAndFindFileByNioFile(dirPath)
      dirVf?.refresh(false, true)

      for (p in partPaths) {
        lfs.refreshAndFindFileByNioFile(p)?.let { partsVf.add(it) }
      }
      indexVf = lfs.refreshAndFindFileByNioFile(indexPath)
    }

    val finalDir = dirVf ?: error("Cannot resolve export directory in VFS: $dirPath")
    val finalIndex = indexVf ?: error("Cannot resolve index.md in VFS: $indexPath")
    return ResolvedVfs(finalDir, partsVf, finalIndex)
  }

  private fun openIndexOnEdt(
    project: Project,
    indexVf: VirtualFile,
  ) {
    val app = ApplicationManager.getApplication()
    if (app.isUnitTestMode || app.isHeadlessEnvironment) return
    app.invokeLater {
      FileEditorManager.getInstance(project).openFile(indexVf, true)
    }
  }
}
