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
  // Directories to ignore at any depth (names, case-insensitive)
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
      ".turbo",
      "target",
      "coverage",
      "venv",
      ".venv",
      ".intellijPlatform",
      ".promptpack",
      "android",
      "ios",
    )

  // Extensions to ignore (without dot), case-insensitive
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

  // --- NEW: Module detection / Public API defaults ---

  /** File names (with wildcards) that mark a directory as a module by manifest (checked at the directory root). */
  val MODULE_MANIFESTS: List<String> =
    listOf(
      "package.json",
      "pyproject.toml",
      "go.mod",
      "Cargo.toml",
      "pom.xml",
      "build.gradle",
      "build.gradle.kts",
      "composer.json",
      "Gemfile",
      "*.csproj",
    )

  /** Project-relative glob patterns that mark a directory as a module by path. */
  val MODULE_PATH_PATTERNS: List<String> =
    listOf(
      "packages/*",
      "libs/*",
      "modules/*",
    )

  /** Public folder names that expose module API (matched case-insensitive). */
  val PUBLIC_FOLDER_NAMES: List<String> =
    listOf(
      "public",
      "public-api",
      "publicApi",
    )
}

data class PromptPackState(
  // existing settings
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
  // NEW: Module detection
  var moduleDetectByManifest: Boolean = true,
  var moduleDetectByPathPatterns: Boolean = true,
  var moduleRequirePublicFolder: Boolean = true,
  var moduleManifestNames: MutableSet<String> = PromptPackDefaults.MODULE_MANIFESTS.toMutableSet(),
  var modulePathPatterns: MutableSet<String> = PromptPackDefaults.MODULE_PATH_PATTERNS.toMutableSet(),
  // NEW: Public API (Copy Contents)
  var publicEnabled: Boolean = true,
  var publicFolderNames: MutableSet<String> = PromptPackDefaults.PUBLIC_FOLDER_NAMES.toMutableSet(),
  var publicSkipDuplicatesInMain: Boolean = false,
  var publicMaxPerModule: Int = 200,
  var publicMaxTotal: Int = 500,
)

@Service(Service.Level.APP)
@State(
  name = "PromptPackState",
  storages = [
    Storage("promptpack.xml"),
  ],
  category = SettingsCategory.PLUGINS,
)
class PromptPackSettingsService : PersistentStateComponent<PromptPackState> {
  private var state = PromptPackState()

  override fun getState(): PromptPackState = state

  override fun loadState(s: PromptPackState) {
    state = s
  }

  companion object {
    fun getInstance(): PromptPackSettingsService =
      com.intellij.openapi.application.ApplicationManager
        .getApplication()
        .getService(PromptPackSettingsService::class.java)
  }
}
