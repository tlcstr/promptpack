// src/main/kotlin/dev/promptpack/FileTree.kt
package dev.promptpack

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import java.util.Locale
import kotlin.math.max

object FileTreeUtil {

  data class TreeInput(
    val scope: TreeScope,
    val selection: Array<VirtualFile>?, // может быть null/пусто
    val ignoredDirs: Set<String>,
    val ignoredExts: Set<String>
  )

  /** Строит заголовок + дерево и ОБОРАЧИВАЕТ дерево в Markdown fenced-блок (сохраняем пробелы/инденты). */
  fun buildTreeHeaderAndText(
    project: Project,
    input: TreeInput,
    maxEntries: Int = 3000,
    indentSize: Int = 2,
    showDirSlash: Boolean = true
  ): String {
    val root: VirtualFile? =
      project.basePath?.let { LocalFileSystem.getInstance().findFileByPath(it) }

    val roots: List<VirtualFile> = when (input.scope) {
      TreeScope.PROJECT   -> listOfNotNull(root)
      TreeScope.SELECTION -> input.selection?.toList().orEmpty()
      TreeScope.NONE      -> emptyList()
    }

    val header = when (input.scope) {
      TreeScope.PROJECT   -> PromptPackBundle.message("tree.header.project")
      TreeScope.SELECTION -> PromptPackBundle.message("tree.header.selection")
      TreeScope.NONE      -> PromptPackBundle.message("tree.header.off")
    } + "\n"

    if (roots.isEmpty()) return header

    val body = ApplicationManager.getApplication().runReadAction(Computable {
      val visited = HashSet<String>(4096)
      val sb = StringBuilder()
      var count = 0

      fun lower(s: String) = s.lowercase(Locale.ROOT)
      fun skipDir(name: String) = input.ignoredDirs.contains(lower(name))
      fun skipFile(file: VirtualFile): Boolean {
        val ext = file.extension?.let(::lower)
        if (ext != null && input.ignoredExts.contains(ext)) return true
        return file.fileType.isBinary
      }

      fun childrenFiltered(dir: VirtualFile): List<VirtualFile> {
        val kids = dir.children?.toList().orEmpty()
        return kids.filter { v ->
          if (v.isDirectory) !skipDir(v.name) else !skipFile(v)
        }.sortedWith(compareBy<VirtualFile> { !it.isDirectory }.thenBy { lower(it.name) })
      }

      fun labelFor(node: VirtualFile): String {
        val name = node.name.ifEmpty { node.path }
        return if (node.isDirectory && showDirSlash) "$name/" else name
      }

      fun render(node: VirtualFile, depth: Int) {
        val key = node.path + "#" + node.isDirectory
        if (!visited.add(key)) return

        val indent = " ".repeat(depth * indentSize)
        sb.append(indent).append(labelFor(node)).append('\n')
        count++
        if (count >= maxEntries) return

        if (node.isDirectory) {
          val kids = childrenFiltered(node)
          kids.forEach { child ->
            if (count < maxEntries) render(child, depth + 1)
          }
        }
      }

      roots.forEach { r -> render(r, 0) }

      if (count >= maxEntries) sb.append(
        PromptPackBundle.message("tree.more", maxEntries)
      ).append('\n')

      sb.toString()
    })

    // Оборачиваем само дерево, чтобы Markdown не схлопывал начальные пробелы (инденты)
    val fencedTree = fenced(body.trimEnd(), "text")
    return header + fencedTree
  }

  /** Чтение содержимого файла, предпочитая несохранённые правки из Document. */
  fun readTextFor(vf: VirtualFile): String {
    val doc = FileDocumentManager.getInstance().getCachedDocument(vf)
      ?: FileDocumentManager.getInstance().getDocument(vf)
    if (doc != null) return doc.text
    if (vf.isDirectory) return PromptPackBundle.message("placeholder.directory")
    if (vf.fileType.isBinary) return PromptPackBundle.message("placeholder.binary", vf.fileType.name, vf.length)
    return VfsUtilCore.loadText(vf)
  }

  /** Подсказываем язык для Markdown-``` — по расширению (или возвращаем null). */
  fun codeFenceLanguageHint(vf: VirtualFile): String? {
    val ext = vf.extension?.lowercase(Locale.ROOT) ?: return null
    return when (ext) {
      "kt", "kts"      -> "kotlin"
      "java"           -> "java"
      "groovy", "gradle" -> "groovy"
      "js"             -> "javascript"
      "jsx"            -> "jsx"
      "ts"             -> "typescript"
      "tsx"            -> "tsx"
      "json"           -> "json"
      "yml", "yaml"    -> "yaml"
      "xml"            -> "xml"
      "html", "htm"    -> "html"
      "css", "scss", "sass" -> ext
      "md", "markdown" -> "markdown"
      "sh", "bash", "zsh" -> "bash"
      "ps1"            -> "powershell"
      "py"             -> "python"
      "rb"             -> "ruby"
      "go"             -> "go"
      "rs"             -> "rust"
      "php"            -> "php"
      "sql"            -> "sql"
      "c"              -> "c"
      "h"              -> "c"
      "hpp", "hh"      -> "cpp"
      "cpp", "cc", "cxx" -> "cpp"
      "cs"             -> "csharp"
      "ini", "conf", "properties" -> "ini"
      "toml"           -> "toml"
      else             -> ext
    }
  }

  /**
   * Безопасно оборачиваем текст в fenced-блок.
   * Если внутри уже есть последовательности бэктиков, ставим забор длинее.
   * Пример: ```lang\n...\n```
   */
  fun fenced(content: String, lang: String?): String {
    val maxRun = Regex("`+").findAll(content).map { it.value.length }.maxOrNull() ?: 0
    val fenceLen = max(3, maxRun + 1)
    val fence = "`".repeat(fenceLen)
    val info = lang?.takeIf { it.isNotBlank() } ?: "text"
    return buildString {
      append(fence).append(info).append('\n')
      append(content).append('\n')
      append(fence)
    }
  }
}
