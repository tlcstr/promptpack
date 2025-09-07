package dev.promptpack

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

enum class TreeScope { PROJECT, SELECTION, NONE }

/** How to treat test folders. */
enum class TestFilesMode { INCLUDE, EXCLUDE }

object PromptPackDefaults {
  val IGNORED_DIRS: List<String> =
    listOf(
      ".git",
      ".idea",
      ".gradle",
      "node_modules",
      "build",
      "out",
      "dist",
      ".next",
      ".output",
      ".yarn",
      "target",
      "coverage",
      "venv",
      ".venv",
      ".intellijPlatform",
      ".promptpack",
      ".turbo",
      "android",
      "ios",
    )

  val IGNORED_EXTS: List<String> =
    listOf(
      "png",
      "jpg",
      "jpeg",
      "gif",
      "bmp",
      "webp",
      "heic",
      "heif",
      "tiff",
      "tif",
      "ico",
      "icns",
      "svg",
      "mp3",
      "wav",
      "flac",
      "ogg",
      "mp4",
      "m4v",
      "mov",
      "avi",
      "mkv",
      "webm",
      "zip",
      "tar",
      "gz",
      "tgz",
      "bz2",
      "7z",
      "rar",
      "jar",
      "aar",
      "woff",
      "woff2",
      "ttf",
      "otf",
      "class",
      "exe",
      "dll",
      "dylib",
      "so",
      "pdf",
      "psd",
      "ai",
      "sketch",
      "fig",
    )

  /** Exact file names to ignore (lock files etc.). */
  val IGNORED_FILES: List<String> =
    listOf(
      "package-lock.json",
      "yarn.lock",
      "pnpm-lock.yaml",
      "bun.lockb",
      "composer.lock",
      "poetry.lock",
      "pipfile.lock",
      "gradle.lockfile",
      "gradle-lockfile",
      "Cargo.lock",
      "Podfile.lock",
      "Gemfile.lock",
      ".DS_Store",
    )

  /** Default test folder names (matched by directory name at any depth, case-insensitive). */
  val TEST_DIRS: List<String> =
    listOf(
      "__tests__",
      "__mocks__",
    )
}

data class PromptPackState(
  var treeScope: TreeScope = TreeScope.PROJECT,
  var ignoredDirs: MutableSet<String> = PromptPackDefaults.IGNORED_DIRS.toMutableSet(),
  var ignoredExts: MutableSet<String> = PromptPackDefaults.IGNORED_EXTS.toMutableSet(),
  /** Exact file names (no paths), comparison is case-insensitive via lower-case. */
  var ignoredFiles: MutableSet<String> = PromptPackDefaults.IGNORED_FILES.toMutableSet(),
  /** NEW: test-folders handling. */
  var testFilesMode: TestFilesMode = TestFilesMode.EXCLUDE,
  var testDirs: MutableSet<String> = PromptPackDefaults.TEST_DIRS.toMutableSet(),
  var defaultDiffRef: String = "",
  var maxClipboardKb: Int = 800,
)

@Service(Service.Level.APP)
@State(
  name = "PromptPackState",
  storages = [Storage("promptpack.xml")],
  category = SettingsCategory.PLUGINS,
)
class PromptPackSettingsService : PersistentStateComponent<PromptPackState> {
  private var state = PromptPackState()

  override fun getState() = state

  override fun loadState(s: PromptPackState) {
    state = s
  }

  companion object {
    fun getInstance(): PromptPackSettingsService =
      com.intellij.openapi.application.ApplicationManager.getApplication().getService(
        PromptPackSettingsService::class.java,
      )
  }
}
