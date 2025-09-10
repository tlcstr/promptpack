package dev.promptpack

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import java.io.BufferedOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Creates a ZIP archive from the current selection.
 *
 * Reuses existing PromptPack utilities:
 *  - settings via [PromptPackSettingsService]
 *  - VFS traversal via [VfsFilters.collectFiles]
 *  - module discovery via [ModuleDetector]
 *  - public API selection via [PublicApiCollector]
 *
 * Heavy work is done in a background task; VFS/doc reads happen under ReadAction. :contentReference[oaicite:0]{index=0}
 * If Public API mode is on, only files from modules' public folders are included for those modules.
 */
class ZipSelectedItemsAction : AnAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val hasProject = e.project != null
    val hasSelection = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)?.isNotEmpty() == true // uses standard key :contentReference[oaicite:1]{index=1}
    e.presentation.isEnabledAndVisible = hasProject && hasSelection
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val selection = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return
    ProgressManager.getInstance().run(
      object : Task.Backgroundable(project, "PromptPack: Zipping selected files", true) {
        override fun run(indicator: ProgressIndicator) {
          runZip(project, selection, indicator)
        }
      }
    )
  }

  private fun runZip(project: Project, selection: Array<VirtualFile>, indicator: ProgressIndicator) {
    indicator.text = "Collecting files…"

    val st = PromptPackSettingsService.getInstance().state

    // Case-insensitive helpers
    fun Set<String>.lc(): Set<String> = this.map { it.lowercase(Locale.ROOT) }.toSet()

    // Effective ignores
    val ignoredDirsBase = st.ignoredDirs.lc().toMutableSet()
    val testDirs = st.testDirs.lc()
    val effectiveIgnoredDirs: Set<String> =
      if (st.testFilesMode == TestFilesMode.EXCLUDE) ignoredDirsBase.apply { addAll(testDirs) } else ignoredDirsBase
    val ignoredExts = st.ignoredExts.lc()
    val ignoredFiles = st.ignoredFiles.lc()

    // Main files by regular rules
    val mainFiles = LinkedHashSet<VirtualFile>()
    selection.forEach { start ->
      VfsFilters.collectFiles(
        start = start,
        out = mainFiles,
        ignoredDirs = effectiveIgnoredDirs,
        ignoredExts = ignoredExts,
        ignoredFiles = ignoredFiles,
      )
    }

    // Public API (modules only), if enabled
    var publicFiles: List<VirtualFile> = emptyList()
    var trimmedPerModuleCount = 0
    var trimmedTotal = false
    if (st.publicEnabled) {
      val modules =
        ModuleDetector.detectModules(
          project = project,
          selection = selection,
          cfg = ModuleDetector.Config(
            detectByManifest = st.moduleDetectByManifest,
            manifestNames = st.moduleManifestNames.lc(),
            detectByPathPatterns = st.moduleDetectByPathPatterns,
            pathPatterns = st.modulePathPatterns.lc(),
            requirePublicFolder = st.moduleRequirePublicFolder,
            publicFolderNames = st.publicFolderNames.lc(),
            ignoredDirs = effectiveIgnoredDirs,
          ),
        )

      val result =
        PublicApiCollector.collect(
          project = project,
          modules = modules,
          cfg =
            PublicApiCollector.Config(
              publicNames = st.publicFolderNames.lc(),
              ignoredExts = ignoredExts,
              ignoredFiles = ignoredFiles,
              testDirs = testDirs,
              excludeTests = (st.testFilesMode == TestFilesMode.EXCLUDE),
              ignoredDirs = effectiveIgnoredDirs,
              limits =
                PublicApiCollector.Limits(
                  perModule = st.publicMaxPerModule,
                  total = st.publicMaxTotal,
                ),
            ),
        )
      publicFiles = result.files
      trimmedPerModuleCount = result.trimmedPerModuleCount
      trimmedTotal = result.trimmedTotal
    }

    val mainFilesFiltered =
      if (st.publicEnabled && st.publicSkipDuplicatesInMain && publicFiles.isNotEmpty()) {
        val publicPaths = publicFiles.asSequence().map { it.path }.toHashSet()
        mainFiles.filter { it.path !in publicPaths }
      } else {
        mainFiles.toList()
      }

    val allFiles = LinkedHashSet<VirtualFile>().apply {
      addAll(mainFilesFiltered)
      addAll(publicFiles)
    }

    if (allFiles.isEmpty()) {
      notify(project, "PromptPack: nothing to zip (no matching files).", NotificationType.WARNING)
      return
    }

    val exportDir = ensureExportDir(project)
    val zipPath = exportDir.resolve("selected.zip")

    indicator.text = "Writing ZIP…"
    writeZip(project, allFiles.toList(), zipPath, indicator)

    // Refresh VFS and notify
    LocalFileSystem.getInstance().refreshAndFindFileByPath(zipPath.toString())
    notify(project, "ZIP written: $zipPath", NotificationType.INFORMATION)

    if (trimmedPerModuleCount > 0) {
      notify(project, "Public API: limited per-module (${trimmedPerModuleCount} modules trimmed).", NotificationType.INFORMATION)
    }
    if (trimmedTotal) {
      notify(project, "Public API: reached total cap (${st.publicMaxTotal}).", NotificationType.INFORMATION)
    }
  }

  private fun ensureExportDir(project: Project): Path {
    val base = project.basePath ?: System.getProperty("user.home")
    val ts = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now())
    val dir = Paths.get(base, ".promptpack", "exports", ts)
    Files.createDirectories(dir)
    return dir
  }

  /** Write ZIP, preserving project-relative paths when possible. */
  private fun writeZip(
    project: Project,
    files: List<VirtualFile>,
    zipPath: Path,
    indicator: ProgressIndicator,
  ) {
    val root = project.basePath?.let { LocalFileSystem.getInstance().findFileByPath(it) }
    ZipOutputStream(
      BufferedOutputStream(
        Files.newOutputStream(zipPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
      )
    ).use { zos ->
      files.forEachIndexed { idx, vf ->
        indicator.fraction = (idx + 1).toDouble() / files.size

        // getRelativePath requires non-null ancestor; guard it. :contentReference[oaicite:2]{index=2}
        val rel: String? = if (root != null) VfsUtilCore.getRelativePath(vf, root, '/') else null
        indicator.text2 = rel ?: vf.presentableUrl

        if (vf.isDirectory) return@forEachIndexed

        val entryName = rel ?: vf.name
        val bytes =
          ApplicationManager.getApplication().runReadAction(
            Computable {
              // If a document is open, prefer its text (including unsaved edits).
              val doc = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(vf)
              if (doc != null) doc.text.toByteArray(Charsets.UTF_8) else vf.contentsToByteArray()
            },
          )

        zos.putNextEntry(ZipEntry(entryName))
        zos.write(bytes)
        zos.closeEntry()
      }
    }
  }

  private fun notify(project: Project, msg: String, type: NotificationType) {
    NotificationGroupManager
      .getInstance()
      .getNotificationGroup("PromptPack")
      .createNotification(msg, type)
      .notify(project)
  }
}
