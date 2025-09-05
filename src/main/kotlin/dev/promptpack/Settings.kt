package dev.promptpack

import com.intellij.openapi.components.*

enum class TreeScope { PROJECT, SELECTION, NONE }

object PromptPackDefaults {
  val IGNORED_DIRS: List<String> = listOf(
    ".git",".idea",".gradle","node_modules","build","out","dist",".next",".output",".yarn","target","coverage","venv",".venv"
  )
  val IGNORED_EXTS: List<String> = listOf(
    "png","jpg","jpeg","gif","bmp","webp","heic","heif","tiff","tif","ico","icns","svg",
    "mp3","wav","flac","ogg","mp4","m4v","mov","avi","mkv","webm",
    "zip","tar","gz","tgz","bz2","7z","rar","jar","aar",
    "woff","woff2","ttf","otf","class","exe","dll","dylib","so","pdf","psd","ai","sketch","fig"
  )
  /** NEW: точные имена файлов, которые игнорируем (lock-файлы и т.п.). */
  val IGNORED_FILES: List<String> = listOf(
    "package-lock.json", "yarn.lock", "pnpm-lock.yaml", "bun.lockb",
    "composer.lock", "poetry.lock", "pipfile.lock",
    "gradle.lockfile", "gradle-lockfile",
    "Cargo.lock", "Podfile.lock", "Gemfile.lock",
    ".DS_Store"
  )
}

data class PromptPackState(
  var treeScope: TreeScope = TreeScope.PROJECT,
  var ignoredDirs: MutableSet<String> = PromptPackDefaults.IGNORED_DIRS.toMutableSet(),
  var ignoredExts: MutableSet<String> = PromptPackDefaults.IGNORED_EXTS.toMutableSet(),
  /** NEW: имена файлов (без путей), сравнение по lower-case. */
  var ignoredFiles: MutableSet<String> = PromptPackDefaults.IGNORED_FILES.toMutableSet(),
  var defaultDiffRef: String = "",             // пусто => автоопределение (origin/HEAD)
  var maxClipboardKb: Int = 800                // лимит размера для буфера (КБ)
)

@Service(Service.Level.APP)
@State(
  name = "PromptPackState",
  storages = [Storage("promptpack.xml")],
  category = SettingsCategory.PLUGINS
)
class PromptPackSettingsService : PersistentStateComponent<PromptPackState> {
  private var state = PromptPackState()
  override fun getState() = state
  override fun loadState(s: PromptPackState) { state = s }
  companion object {
    fun getInstance(): PromptPackSettingsService =
      com.intellij.openapi.application.ApplicationManager.getApplication().getService(PromptPackSettingsService::class.java)
  }
}
