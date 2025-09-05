package dev.promptpack

import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

class PromptPackConfigurable
  : BoundSearchableConfigurable(PromptPackBundle.message("configurable.name"), "dev.promptpack") {

  private val svc get() = PromptPackSettingsService.getInstance()

  override fun createPanel() = panel {
    group(PromptPackBundle.message("ui.group.treeScope")) {
      buttonsGroup {
        row { radioButton(PromptPackBundle.message("ui.radio.project"),   TreeScope.PROJECT) }
        row { radioButton(PromptPackBundle.message("ui.radio.selection"), TreeScope.SELECTION) }
        row { radioButton(PromptPackBundle.message("ui.radio.none"),      TreeScope.NONE) }
      }.bind(
        getter = { svc.state.treeScope },
        setter = { svc.state.treeScope = it }
      )
    }
    group(PromptPackBundle.message("ui.group.filters")) {
      row(PromptPackBundle.message("ui.ignored.folders")) {
        textField().bindText(
          getter = { svc.state.ignoredDirs.joinToString(",") },
          setter = {
            svc.state.ignoredDirs = it.split(',')
              .map(String::trim).filter(String::isNotEmpty).toMutableSet()
          }
        )
      }
      row(PromptPackBundle.message("ui.ignored.extensions")) {
        textField().bindText(
          getter = { svc.state.ignoredExts.joinToString(",") },
          setter = {
            svc.state.ignoredExts = it.split(',')
              .map { s -> s.trim().lowercase() }
              .filter(String::isNotEmpty).toMutableSet()
          }
        )
      }
      row(PromptPackBundle.message("ui.ignored.files")) {
        textField().bindText(
          getter = { svc.state.ignoredFiles.joinToString(",") },
          setter = {
            svc.state.ignoredFiles = it.split(',')
              .map { s -> s.trim().lowercase() }
              .filter(String::isNotEmpty).toMutableSet()
          }
        ).comment(PromptPackBundle.message("ui.ignored.files.hint"))
      }
    }
  }

  override fun getPreferredFocusedComponent(): JComponent? = null
}
