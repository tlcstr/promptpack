package dev.promptpack

import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

/**
 * Settings UI for 242.* without UI-DSL property bindings.
 * We store component refs and sync with settings in apply/reset/isModified.
 */
class PromptPackConfigurable :
  BoundSearchableConfigurable(
    PromptPackBundle.message("configurable.name"),
    "dev.promptpack",
  ) {
  private val svc get() = PromptPackSettingsService.getInstance()

  // File tree
  private var selectedScope: TreeScope = svc.state.treeScope
  private lateinit var ignoredDirsField: JBTextField
  private lateinit var ignoredExtsField: JBTextField
  private lateinit var ignoredFilesField: JBTextField

  // Tests
  private var selectedTestsMode: TestFilesMode = svc.state.testFilesMode
  private lateinit var testDirsField: JBTextField

  // Modules
  private lateinit var detectByManifestCb: JBCheckBox
  private lateinit var manifestCsvField: JBTextField
  private lateinit var detectByPathCb: JBCheckBox
  private lateinit var pathPatternsField: JBTextField
  private lateinit var requirePublicCb: JBCheckBox

  // Public API
  private lateinit var publicEnabledCb: JBCheckBox
  private lateinit var publicNamesField: JBTextField
  private lateinit var skipDupMainCb: JBCheckBox
  private lateinit var maxPerModuleField: JBTextField
  private lateinit var maxTotalField: JBTextField

  override fun createPanel() =
    panel {
      // File tree scope
      group(PromptPackBundle.message("ui.group.treeScope")) {
        buttonsGroup {
          row {
            val rbProject = radioButton(PromptPackBundle.message("ui.radio.project"))
            val rbSelection = radioButton(PromptPackBundle.message("ui.radio.selection"))
            val rbNone = radioButton(PromptPackBundle.message("ui.radio.none"))
            rbProject.component.isSelected = (selectedScope == TreeScope.PROJECT)
            rbSelection.component.isSelected = (selectedScope == TreeScope.SELECTION)
            rbNone.component.isSelected = (selectedScope == TreeScope.NONE)
            rbProject.component.addActionListener { selectedScope = TreeScope.PROJECT }
            rbSelection.component.addActionListener { selectedScope = TreeScope.SELECTION }
            rbNone.component.addActionListener { selectedScope = TreeScope.NONE }
          }
        }
      }

      // Filters
      group(PromptPackBundle.message("ui.group.filters")) {
        row(PromptPackBundle.message("ui.ignored.folders")) {
          ignoredDirsField = textField().align(AlignX.FILL).resizableColumn().component
        }
        row(PromptPackBundle.message("ui.ignored.extensions")) {
          ignoredExtsField = textField().align(AlignX.FILL).resizableColumn().component
        }
        row(PromptPackBundle.message("ui.ignored.files")) {
          ignoredFilesField =
            textField()
              .align(AlignX.FILL)
              .resizableColumn()
              .comment(PromptPackBundle.message("ui.ignored.files.hint"))
              .component
        }
      }

      // Tests handling
      group(PromptPackBundle.message("ui.group.tests")) {
        buttonsGroup(PromptPackBundle.message("ui.tests.mode")) {
          row {
            val rbInclude = radioButton(PromptPackBundle.message("ui.tests.include"))
            val rbExclude = radioButton(PromptPackBundle.message("ui.tests.exclude"))
            rbInclude.component.isSelected = (selectedTestsMode == TestFilesMode.INCLUDE)
            rbExclude.component.isSelected = (selectedTestsMode == TestFilesMode.EXCLUDE)
            rbInclude.component.addActionListener { selectedTestsMode = TestFilesMode.INCLUDE }
            rbExclude.component.addActionListener { selectedTestsMode = TestFilesMode.EXCLUDE }
          }
        }
        row(PromptPackBundle.message("ui.tests.folders")) {
          testDirsField =
            textField()
              .align(AlignX.FILL)
              .resizableColumn()
              .comment(PromptPackBundle.message("ui.tests.folders.hint"))
              .component
        }
      }

      // Module Detection (NEW)
      group(PromptPackBundle.message("ui.group.modules")) {
        row {
          detectByManifestCb = checkBox(PromptPackBundle.message("ui.modules.detect.manifest")).component
        }
        row(PromptPackBundle.message("ui.modules.detect.manifest.csv")) {
          manifestCsvField = textField().align(AlignX.FILL).resizableColumn().component
        }
        row {
          detectByPathCb = checkBox(PromptPackBundle.message("ui.modules.detect.path")).component
        }
        row(PromptPackBundle.message("ui.modules.detect.path.csv")) {
          pathPatternsField = textField().align(AlignX.FILL).resizableColumn().component
        }
        row {
          requirePublicCb = checkBox(PromptPackBundle.message("ui.modules.requirePublic")).component
        }
      }

      // Public API (NEW)
      group(PromptPackBundle.message("ui.group.publicApi")) {
        row {
          // <— NEW: master toggle for Public API section
          publicEnabledCb = checkBox(PromptPackBundle.message("ui.public.enabled")).component
        }
        row(PromptPackBundle.message("ui.public.names")) {
          publicNamesField = textField().align(AlignX.FILL).resizableColumn().component
        }
        row {
          skipDupMainCb = checkBox(PromptPackBundle.message("ui.public.skipDupMain")).component
        }
        row(PromptPackBundle.message("ui.public.maxPerModule")) {
          maxPerModuleField = textField().align(AlignX.FILL).resizableColumn().component
        }
        row(PromptPackBundle.message("ui.public.maxTotal")) {
          maxTotalField = textField().align(AlignX.FILL).resizableColumn().component
        }
      }
    }

  override fun getPreferredFocusedComponent(): JComponent? = null

  override fun isModified(): Boolean {
    val st = svc.state
    fun String.normalizeCsv(
      lowercase: Boolean = false,
      trimDots: Boolean = false,
    ): List<String> =
      this
        .split(',', '\n', '\r', '\t')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map {
          var v = it
          if (lowercase) v = v.lowercase()
          if (trimDots) v = v.removePrefix(".")
          v
        }

    return selectedScope != st.treeScope ||
      ignoredDirsField.text.normalizeCsv().toMutableSet() != st.ignoredDirs ||
      ignoredExtsField.text.normalizeCsv(lowercase = true, trimDots = true).toMutableSet() != st.ignoredExts ||
      ignoredFilesField.text.normalizeCsv(lowercase = true).toMutableSet() != st.ignoredFiles ||
      selectedTestsMode != st.testFilesMode ||
      testDirsField.text.normalizeCsv(lowercase = true).toMutableSet() != st.testDirs ||
      // modules
      detectByManifestCb.isSelected != st.moduleDetectByManifest ||
      manifestCsvField.text.normalizeCsv(lowercase = true).toMutableSet() != st.moduleManifestNames ||
      detectByPathCb.isSelected != st.moduleDetectByPathPatterns ||
      pathPatternsField.text.normalizeCsv(lowercase = true).toMutableSet() != st.modulePathPatterns ||
      requirePublicCb.isSelected != st.moduleRequirePublicFolder ||
      // public
      publicEnabledCb.isSelected != st.publicEnabled || // <— NEW
      publicNamesField.text.normalizeCsv(lowercase = true).toMutableSet() != st.publicFolderNames ||
      skipDupMainCb.isSelected != st.publicSkipDuplicatesInMain ||
      maxPerModuleField.text.trim().toIntOrNull() != st.publicMaxPerModule ||
      maxTotalField.text.trim().toIntOrNull() != st.publicMaxTotal
  }

  override fun apply() {
    val st = svc.state
    fun String.normalizeCsv(
      lowercase: Boolean = false,
      trimDots: Boolean = false,
    ): List<String> =
      this
        .split(',', '\n', '\r', '\t')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map {
          var v = it
          if (lowercase) v = v.lowercase()
          if (trimDots) v = v.removePrefix(".")
          v
        }

    st.treeScope = selectedScope
    st.ignoredDirs = ignoredDirsField.text.normalizeCsv().toMutableSet()
    st.ignoredExts = ignoredExtsField.text.normalizeCsv(lowercase = true, trimDots = true).toMutableSet()
    st.ignoredFiles = ignoredFilesField.text.normalizeCsv(lowercase = true).toMutableSet()
    st.testFilesMode = selectedTestsMode
    st.testDirs = testDirsField.text.normalizeCsv(lowercase = true).toMutableSet()

    st.moduleDetectByManifest = detectByManifestCb.isSelected
    st.moduleManifestNames = manifestCsvField.text.normalizeCsv(lowercase = true).toMutableSet()
    st.moduleDetectByPathPatterns = detectByPathCb.isSelected
    st.modulePathPatterns = pathPatternsField.text.normalizeCsv(lowercase = true).toMutableSet()
    st.moduleRequirePublicFolder = requirePublicCb.isSelected

    // Public API
    st.publicEnabled = publicEnabledCb.isSelected         // <— NEW
    st.publicFolderNames = publicNamesField.text.normalizeCsv(lowercase = true).toMutableSet()
    st.publicSkipDuplicatesInMain = skipDupMainCb.isSelected
    st.publicMaxPerModule = maxPerModuleField.text.trim().toIntOrNull() ?: st.publicMaxPerModule
    st.publicMaxTotal = maxTotalField.text.trim().toIntOrNull() ?: st.publicMaxTotal
  }

  override fun reset() {
    val st = svc.state
    selectedScope = st.treeScope
    ignoredDirsField.text = st.ignoredDirs.joinToString(",")
    ignoredExtsField.text = st.ignoredExts.joinToString(",")
    ignoredFilesField.text = st.ignoredFiles.joinToString(",")

    selectedTestsMode = st.testFilesMode
    testDirsField.text = st.testDirs.joinToString(",")

    detectByManifestCb.isSelected = st.moduleDetectByManifest
    manifestCsvField.text = st.moduleManifestNames.joinToString(",")
    detectByPathCb.isSelected = st.moduleDetectByPathPatterns
    pathPatternsField.text = st.modulePathPatterns.joinToString(",")
    requirePublicCb.isSelected = st.moduleRequirePublicFolder

    publicEnabledCb.isSelected = st.publicEnabled         // <— NEW
    publicNamesField.text = st.publicFolderNames.joinToString(",")
    skipDupMainCb.isSelected = st.publicSkipDuplicatesInMain
    maxPerModuleField.text = st.publicMaxPerModule.toString()
    maxTotalField.text = st.publicMaxTotal.toString()
  }
}
