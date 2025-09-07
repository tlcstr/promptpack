package dev.promptpack

import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

/**
 * Settings UI without UI-DSL property bindings to keep 242.* compatibility.
 * We store component refs and sync with settings in apply/reset/isModified.
 */
class PromptPackConfigurable :
  BoundSearchableConfigurable(PromptPackBundle.message("configurable.name"), "dev.promptpack") {
  private val svc get() = PromptPackSettingsService.getInstance()

  // UI state (mirrors svc.state while editing the form)
  private var selectedScope: TreeScope = svc.state.treeScope
  private lateinit var ignoredDirsField: JBTextField
  private lateinit var ignoredExtsField: JBTextField
  private lateinit var ignoredFilesField: JBTextField

  // NEW: tests handling
  private var selectedTestsMode: TestFilesMode = svc.state.testFilesMode
  private lateinit var testDirsField: JBTextField

  override fun createPanel() =
    panel {
      // File tree scope (radio buttons)
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
          ignoredDirsField =
            textField()
              .align(AlignX.FILL)
              .resizableColumn()
              .component
        }
        row(PromptPackBundle.message("ui.ignored.extensions")) {
          ignoredExtsField =
            textField()
              .align(AlignX.FILL)
              .resizableColumn()
              .component
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

      // NEW: test folders handling
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
    }

  override fun getPreferredFocusedComponent(): JComponent? = null

  override fun isModified(): Boolean {
    val st = svc.state
    val dirsSetNow = ignoredDirsField.text.normalizeCsv().toMutableSet()
    val extsSetNow = ignoredExtsField.text.normalizeCsv(lowercase = true, trimDots = true).toMutableSet()
    val filesSetNow = ignoredFilesField.text.normalizeCsv(lowercase = true).toMutableSet()
    val testDirsNow = testDirsField.text.normalizeCsv(lowercase = true).toMutableSet()

    return selectedScope != st.treeScope ||
      dirsSetNow != st.ignoredDirs ||
      extsSetNow != st.ignoredExts ||
      filesSetNow != st.ignoredFiles ||
      selectedTestsMode != st.testFilesMode ||
      testDirsNow != st.testDirs
  }

  override fun apply() {
    val st = svc.state
    st.treeScope = selectedScope
    st.ignoredDirs = ignoredDirsField.text.normalizeCsv().toMutableSet()
    st.ignoredExts = ignoredExtsField.text.normalizeCsv(lowercase = true, trimDots = true).toMutableSet()
    st.ignoredFiles = ignoredFilesField.text.normalizeCsv(lowercase = true).toMutableSet()
    st.testFilesMode = selectedTestsMode
    st.testDirs = testDirsField.text.normalizeCsv(lowercase = true).toMutableSet()
  }

  override fun reset() {
    val st = svc.state
    selectedScope = st.treeScope
    ignoredDirsField.text = st.ignoredDirs.joinToString(",")
    ignoredExtsField.text = st.ignoredExts.joinToString(",")
    ignoredFilesField.text = st.ignoredFiles.joinToString(",")

    selectedTestsMode = st.testFilesMode
    testDirsField.text = st.testDirs.joinToString(",")
  }

  private fun String.normalizeCsv(
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
}
