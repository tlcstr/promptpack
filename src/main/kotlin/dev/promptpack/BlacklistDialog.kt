package dev.promptpack

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import java.awt.BorderLayout
import java.awt.GridLayout
import java.awt.event.ActionEvent
import java.util.*
import javax.swing.*
import javax.swing.border.TitledBorder

class BlacklistDialog(project: Project?) : DialogWrapper(project, /*canBeParent=*/true) {

  private val svc = PromptPackSettingsService.getInstance()
  private val state get() = svc.state

  private val dirsModel  = CollectionListModel(state.ignoredDirs.sortedBy  { it.lowercase(Locale.ROOT) })
  private val extsModel  = CollectionListModel(state.ignoredExts.sortedBy  { it.lowercase(Locale.ROOT) })
  private val filesModel = CollectionListModel(state.ignoredFiles.sortedBy { it.lowercase(Locale.ROOT) })

  init {
    title = PromptPackBundle.message("blacklist.title")
    init()
  }

  override fun createCenterPanel(): JComponent {
    val root = JPanel(GridLayout(1, 3, 16, 0))
    root.add(createListPanel(PromptPackBundle.message("blacklist.dirs.title"),  dirsModel,  kind = Kind.DIR))
    root.add(createListPanel(PromptPackBundle.message("blacklist.exts.title"),  extsModel,  kind = Kind.EXT))
    root.add(createListPanel(PromptPackBundle.message("blacklist.files.title"), filesModel, kind = Kind.FILE))
    return root
  }

  private enum class Kind { DIR, EXT, FILE }

  private fun createListPanel(title: String, model: CollectionListModel<String>, kind: Kind): JComponent {
    val list = JBList(model)
    list.visibleRowCount = 14
    list.emptyText.text = PromptPackBundle.message("blacklist.empty")
    val panel = JPanel(BorderLayout()).apply { border = TitledBorder(title) }

    fun normalize(input: String): List<String> =
      input.split(',', '\n', '\r', '\t')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map {
          when (kind) {
            Kind.EXT  -> it.lowercase(Locale.ROOT).removePrefix(".")
            Kind.FILE -> it.lowercase(Locale.ROOT)
            Kind.DIR  -> it
          }
        }

    val decorator = ToolbarDecorator.createDecorator(list)
      .setAddAction {
        val key = when (kind) {
          Kind.EXT  -> "blacklist.exts.add.msg"
          Kind.FILE -> "blacklist.files.add.msg"
          Kind.DIR  -> "blacklist.dirs.add.msg"
        }
        val txt = Messages.showInputDialog(list,
          PromptPackBundle.message(key),
          PromptPackBundle.message("blacklist.add.title"),
          null
        ) ?: return@setAddAction
        val values = normalize(txt)
        val current = model.items.toMutableSet()
        var added = 0
        for (v in values) if (current.add(v)) added++
        if (added > 0) {
          model.removeAll()
          current.sortedBy { it.lowercase(Locale.ROOT) }.forEach(model::add)
        }
      }
      .setEditAction {
        val idx = list.selectedIndex
        if (idx < 0) return@setEditAction
        val old = model.getElementAt(idx)
        val key = when (kind) {
          Kind.EXT  -> "blacklist.exts.edit.msg"
          Kind.FILE -> "blacklist.files.edit.msg"
          Kind.DIR  -> "blacklist.dirs.edit.msg"
        }
        val txt = Messages.showInputDialog(list,
          PromptPackBundle.message(key),
          PromptPackBundle.message("blacklist.edit.title"),
          null, old, null
        ) ?: return@setEditAction
        val values = normalize(txt)
        if (values.isEmpty()) return@setEditAction
        val newValue = values.first()
        if (newValue == old) return@setEditAction
        val current = model.items.toMutableList()
        current[idx] = newValue
        val dedup = LinkedHashSet(current.map { if (kind == Kind.EXT) it.lowercase(Locale.ROOT) else it })
        model.removeAll()
        dedup.sortedBy { it.lowercase(Locale.ROOT) }.forEach(model::add)
      }
      .setRemoveAction { list.selectedValuesList.forEach { model.remove(it) } }

    panel.add(JBLabel(""), BorderLayout.NORTH)
    panel.add(decorator.createPanel(), BorderLayout.CENTER)
    return panel
  }

  override fun createActions(): Array<Action> {
    val reset = object : DialogWrapperAction(PromptPackBundle.message("blacklist.reset")) {
      override fun doAction(e: ActionEvent) {
        dirsModel.removeAll();  PromptPackDefaults.IGNORED_DIRS.sortedBy  { it.lowercase(Locale.ROOT) }.forEach(dirsModel::add)
        extsModel.removeAll();  PromptPackDefaults.IGNORED_EXTS.sortedBy  { it.lowercase(Locale.ROOT) }.forEach(extsModel::add)
        filesModel.removeAll(); PromptPackDefaults.IGNORED_FILES.map { it.lowercase(Locale.ROOT) }
          .sortedBy { it }.forEach(filesModel::add)
      }
    }
    return arrayOf(reset, cancelAction, okAction)
  }

  override fun doOKAction() {
    state.ignoredDirs = dirsModel.items.map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
    state.ignoredExts = extsModel.items.map { it.trim().lowercase(Locale.ROOT).removePrefix(".") }
      .filter { it.isNotEmpty() }.toMutableSet()
    state.ignoredFiles = filesModel.items.map { it.trim().lowercase(Locale.ROOT) }
      .filter { it.isNotEmpty() }.toMutableSet()
    super.doOKAction()
  }
}
