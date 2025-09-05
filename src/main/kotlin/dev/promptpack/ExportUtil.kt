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

  /** Порог на размер одного куска (символов). При необходимости вынесем в настройки. */
  const val DEFAULT_CHUNK_LIMIT = 150_000

  data class Result(
    val parts: List<VirtualFile>,
    val index: VirtualFile,
    val dir: VirtualFile,
    val totalChars: Int
  )

  /** Делим список блоков на части, не рвём блоки внутри. */
  fun splitIntoParts(blocks: List<String>, limit: Int): List<String> {
    if (blocks.isEmpty()) return emptyList()
    val parts = mutableListOf<StringBuilder>()
    var current = StringBuilder()

    fun push() { parts += current; current = StringBuilder() }

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

  /**
   * Пишем markdown-части и index.md в .promptpack/exports/<timestamp>.
   * Возвращаем VirtualFile'ы и открываем index.md.
   */
  fun exportMarkdown(
    project: Project,
    treeHeaderOrEmpty: String,
    blocks: List<String>,
    chunkLimit: Int = DEFAULT_CHUNK_LIMIT
  ): Result {
    val ts = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
      .withZone(ZoneId.systemDefault())
      .format(Instant.now())

    val base = project.basePath ?: System.getProperty("user.home")
    val dirPath: Path = Paths.get(base, ".promptpack", "exports", ts)
    Files.createDirectories(dirPath)

    val lfs = LocalFileSystem.getInstance()
    lfs.refreshAndFindFileByNioFile(dirPath)
    val exportDirVf = lfs.refreshAndFindFileByNioFile(dirPath)
      ?: lfs.refreshAndFindFileByPath(dirPath.toString())
      ?: error("Cannot resolve export directory in VFS: $dirPath")

    val joined = blocks.joinToString("\n\n")
    val total = treeHeaderOrEmpty.length + 1 + joined.length
    val partsText = if (total <= chunkLimit) listOf(joined) else splitIntoParts(blocks, chunkLimit)

    val writtenParts = mutableListOf<VirtualFile>()
    val indexVf: VirtualFile = ApplicationManager.getApplication().runWriteAction<VirtualFile> {
      // part-N
      for (i in partsText.indices) {
        val name = if (partsText.size == 1) "content.md" else "part-%02d.md".format(i + 1)
        val vf = exportDirVf.findChild(name) ?: exportDirVf.createChildData(this, name)
        VfsUtil.saveText(vf, partsText[i])
        writtenParts += vf
      }

      // index.md
      val idx = exportDirVf.findChild("index.md") ?: exportDirVf.createChildData(this, "index.md")
      val indexContent = buildString {
        append("# ").append(PromptPackBundle.message("export.index.title")).append('\n')
        append(
          PromptPackBundle.message(
            "export.index.meta",
            blocks.size,
            partsText.size,
            chunkLimit
          )
        ).append("\n\n")
        if (treeHeaderOrEmpty.isNotBlank()) {
          append(treeHeaderOrEmpty).append('\n')
        }
        append("## ").append(PromptPackBundle.message("export.index.parts")).append('\n')
        for (p in writtenParts) {
          append("- [").append(p.name).append("](").append(p.name).append(")\n")
        }
      }
      VfsUtil.saveText(idx, indexContent)
      idx
    }

    FileEditorManager.getInstance(project).openFile(indexVf, true)

    return Result(
      parts = writtenParts,
      index = indexVf,
      dir = exportDirVf,
      totalChars = total
    )
  }
}
