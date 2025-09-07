package dev.promptpack

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import java.awt.datatransfer.StringSelection
import java.util.LinkedHashSet
import java.util.Locale

/**
 * "Copy Contents of Selected Items" + optional Public API collection:
 * - Heavy work in a background task (EDT remains free).
 * - Reads under ReadAction (includes unsaved buffers).
 * - Optional file tree header.
 * - Optional: detect modules and collect files from their public folders (limits + test exclusion).
 * - Small output -> clipboard; large -> export to .promptpack/exports/<ts>/.
 */
class CopySelectedItemsContentsAction :
  AnAction(
    PromptPackBundle.message("action.copyContentsSelected.text"),
    PromptPackBundle.message("action.copyContentsSelected.description"),
    null,
  ) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val sel = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
    e.presentation.isEnabledAndVisible = sel != null && sel.isNotEmpty()
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val selection = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)?.copyOf() ?: emptyArray()
    if (selection.isEmpty()) {
      notify(project, PromptPackBundle.message("notify.noSelection"), NotificationType.WARNING)
      return
    }
    ProgressManager.getInstance().run(
      object : Task.Backgroundable(
        project,
        PromptPackBundle.message("progress.copy.title"),
      ) {
        override fun run(indicator: ProgressIndicator) {
          indicator.isIndeterminate = true
          processSelection(project, selection)
        }
      },
    )
  }

  private fun processSelection(
    project: Project,
    selection: Array<VirtualFile>,
  ) {
    val st = PromptPackSettingsService.getInstance().state

    val ignoredDirsBase = st.ignoredDirs.map { it.lowercase(Locale.ROOT) }.toMutableSet()
    val ignoredExts = st.ignoredExts.map { it.lowercase(Locale.ROOT) }.toSet()
    val ignoredFiles = st.ignoredFiles.map { it.lowercase(Locale.ROOT) }.toSet()
    val testDirs = st.testDirs.map { it.lowercase(Locale.ROOT) }.toSet()
    val effectiveIgnoredDirs =
      if (st.testFilesMode == TestFilesMode.EXCLUDE) {
        ignoredDirsBase.apply { addAll(testDirs) }
      } else {
        ignoredDirsBase
      }

    val treeHeader = buildTreeHeader(project, st, selection, effectiveIgnoredDirs, ignoredExts)
    val mainFiles = collectMainFiles(selection, effectiveIgnoredDirs, ignoredExts, ignoredFiles)

    val publicRes =
      if (st.publicEnabled) {
        collectPublicApiFiles(
          project,
          selection,
          st,
          effectiveIgnoredDirs,
          ignoredExts,
          ignoredFiles,
          testDirs,
        ).also { notifyTrimmedIfAny(project, st, it) }
      } else {
        PublicCollectResult(emptyList(), 0, false)
      }

    val mainFilesFiltered =
      if (st.publicEnabled && st.publicSkipDuplicatesInMain && publicRes.files.isNotEmpty()) {
        val publicPaths =
          publicRes.files
            .asSequence()
            .map { it.path }
            .toHashSet()
        mainFiles.filter { it.path !in publicPaths }
      } else {
        mainFiles.toList()
      }

    if (mainFilesFiltered.isEmpty() && publicRes.files.isEmpty()) {
      notify(project, PromptPackBundle.message("notify.noTextFiles"), NotificationType.WARNING)
      return
    }

    val blocks = mutableListOf<String>()
    var total = 0

    if (st.publicEnabled && publicRes.files.isNotEmpty()) {
      blocks += "# " + PromptPackBundle.message("header.publicApi")
      blocks += buildBlocks(project, LinkedHashSet(publicRes.files))
      total += publicRes.files.size
    }

    if (mainFilesFiltered.isNotEmpty()) {
      if (st.publicEnabled && publicRes.files.isNotEmpty()) {
        blocks += "# " + PromptPackBundle.message("header.selectedFiles")
      }
      blocks += buildBlocks(project, LinkedHashSet(mainFilesFiltered))
      total += mainFilesFiltered.size
    }

    val singleText =
      buildString {
        if (treeHeader.isNotEmpty()) {
          append(treeHeader).append('\n').append('\n')
        }
        append(blocks.joinToString("\n\n"))
      }

    if (singleText.length <= ExportUtil.DEFAULT_CHUNK_LIMIT) {
      ApplicationManager.getApplication().invokeLater {
        CopyPasteManager.getInstance().setContents(StringSelection(singleText))
        notify(
          project,
          PromptPackBundle.message("notify.copiedSelectedContents", total),
          NotificationType.INFORMATION,
        )
      }
    } else {
      val result = ExportUtil.exportMarkdown(project, treeHeader, blocks, ExportUtil.DEFAULT_CHUNK_LIMIT)
      ApplicationManager.getApplication().invokeLater {
        CopyPasteManager.getInstance().setContents(StringSelection(result.index.presentableUrl))
        notify(
          project,
          PromptPackBundle.message(
            "notify.export.done",
            result.dir.presentableUrl,
            result.parts.size,
          ),
          NotificationType.INFORMATION,
        )
      }
    }
  }

  // ---- helpers (small, focused) ----

  private fun buildTreeHeader(
    project: Project,
    st: PromptPackState,
    selection: Array<VirtualFile>,
    ignoredDirs: Set<String>,
    ignoredExts: Set<String>,
  ): String =
    if (st.treeScope == TreeScope.NONE) {
      ""
    } else {
      FileTreeUtil.buildTreeHeaderAndText(
        project,
        FileTreeUtil.TreeInput(
          scope = st.treeScope,
          selection = selection,
          ignoredDirs = ignoredDirs,
          ignoredExts = ignoredExts,
        ),
      )
    }

  private fun collectMainFiles(
    selection: Array<VirtualFile>,
    ignoredDirs: Set<String>,
    ignoredExts: Set<String>,
    ignoredFiles: Set<String>,
  ): LinkedHashSet<VirtualFile> {
    val files = LinkedHashSet<VirtualFile>()
    selection.forEach { VfsFilters.collectFiles(it, files, ignoredDirs, ignoredExts, ignoredFiles) }
    return files
  }

  private data class PublicCollectResult(
    val files: List<VirtualFile>,
    val trimmedPerModule: Int,
    val trimmedTotal: Boolean,
  )

  private fun collectPublicApiFiles(
    project: Project,
    selection: Array<VirtualFile>,
    st: PromptPackState,
    ignoredDirs: Set<String>,
    ignoredExts: Set<String>,
    ignoredFiles: Set<String>,
    testDirs: Set<String>,
  ): PublicCollectResult {
    val publicNames = st.publicFolderNames.map { it.lowercase(Locale.ROOT) }.toSet()
    val modules =
      ModuleDetector.detectModules(
        project,
        selection,
        ModuleDetector.Config(
          detectByManifest = st.moduleDetectByManifest,
          manifestNames = st.moduleManifestNames.map { it.lowercase(Locale.ROOT) }.toSet(),
          detectByPathPatterns = st.moduleDetectByPathPatterns,
          pathPatterns = st.modulePathPatterns.map { it.lowercase(Locale.ROOT) }.toSet(),
          requirePublicFolder = st.moduleRequirePublicFolder,
          publicFolderNames = publicNames,
          ignoredDirs = ignoredDirs,
        ),
      )
    if (modules.isEmpty()) return PublicCollectResult(emptyList(), 0, false)

    val res =
      PublicApiCollector.collect(
        project = project,
        modules = modules,
        cfg =
          PublicApiCollector.Config(
            publicNames = publicNames,
            ignoredExts = ignoredExts,
            ignoredFiles = ignoredFiles,
            testDirs = testDirs,
            excludeTests = (st.testFilesMode == TestFilesMode.EXCLUDE),
            ignoredDirs = ignoredDirs,
            limits =
              PublicApiCollector.Limits(
                perModule = st.publicMaxPerModule.coerceAtLeast(0),
                total = st.publicMaxTotal.coerceAtLeast(0),
              ),
          ),
      )
    return PublicCollectResult(res.files, res.trimmedPerModuleCount, res.trimmedTotal)
  }

  private fun notifyTrimmedIfAny(
    project: Project,
    st: PromptPackState,
    res: PublicCollectResult,
  ) {
    if (res.trimmedPerModule > 0) {
      notify(
        project,
        PromptPackBundle.message("notify.public.trim.perModule", st.publicMaxPerModule, res.trimmedPerModule),
        NotificationType.INFORMATION,
      )
    }
    if (res.trimmedTotal) {
      notify(
        project,
        PromptPackBundle.message("notify.public.trim.total", st.publicMaxTotal),
        NotificationType.INFORMATION,
      )
    }
  }

  private fun buildBlocks(
    project: Project,
    files: Set<VirtualFile>,
  ): List<String> {
    val root = project.basePath?.let { LocalFileSystem.getInstance().findFileByPath(it) }
    return ApplicationManager.getApplication().runReadAction(
      Computable {
        files.map { file ->
          val rel = root?.let { VfsUtilCore.getRelativePath(file, it, '/') }
          val path = rel?.let { "./$it" } ?: file.presentableUrl
          val content = FileTreeUtil.readTextFor(file)
          val lang = FileTreeUtil.codeFenceLanguageHint(file) ?: "text"
          val fenced = FileTreeUtil.fenced(content, lang)
          "$path:\n$fenced"
        }
      },
    )
  }

  private fun notify(project: Project, msg: String, type: NotificationType) {
    NotificationGroupManager
      .getInstance()
      .getNotificationGroup("PromptPack")
      .createNotification(msg, type)
      .notify(project)
  }
}
