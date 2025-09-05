# PromptPack

LLM helper for JetBrains IDEs: copy selected files/folders as clean Markdown (with language-tagged code fences), optionally prepend a **file tree**, and **auto-split/export** when the result is too large for the clipboard. Plus a quick **diff with the default Git branch**.

> Minimum IDE: 2024.2 (build 242.*). Works across JetBrains IDEs on 242.*.

---

## Features

* **Copy Contents of Selected Items**

    * Each file becomes `relative/path.ext:` followed by a fenced code block (` ```lang`).
    * Language is guessed from extension; unsaved editor changes are included.
    * Optional **file tree** (whole project / selection).
    * Skips binaries, ignored folders/extensions, and common lock files (e.g. `pnpm-lock.yaml`, `yarn.lock`, `package-lock.json`, `Cargo.lock`, `poetry.lock`).

* **Diff with Default Branch**

    * One combined `git diff` vs detected default ref (`origin/HEAD` → `origin/<main|master>`), rendered as a Markdown \`\`\`diff block.

* **Auto-export for large outputs**

    * If the content exceeds the limit, it’s saved to:

      ```
      .promptpack/exports/20240101-123456/
        index.md
        content.md | part-01.md, part-02.md, ...
      ```
    * `index.md` links all parts and (optionally) includes the file tree.

* **Robust fenced blocks**

    * Fence length adapts to backticks inside content (no broken Markdown).

---

## Install from source

```bash
git clone <repo-url>
cd jet-brains-plugins/copy-open-tab-paths
./gradlew clean runIde
```

A sandbox IDE starts with PromptPack installed.

Build plugin ZIP:

```bash
./gradlew buildPlugin
```

Artifacts: `build/distributions/`.

---

## Where to find actions

* **Tools → PromptPack → Copy Contents of Selected Items**
  Also in Project View context menu. Default shortcut: **Ctrl+Shift+Alt+C**.
* **Tools → PromptPack → Tree Scope…**
  *Whole Project / Selection / Off*. Quick toggle: **Ctrl+Shift+Alt+T**.
* **Tools → PromptPack → Diff with Default Branch**
  Also in Project View context menu.

> Can’t see them? Use **Find Action** (⇧⌘A / Ctrl+Shift+A) and type “PromptPack”.

---

## Quick usage

**Copy contents**

1. Select files/folders in Project View.
2. Run **Copy Contents of Selected Items**.
3. Small result → clipboard. Large result → `.promptpack/exports/<timestamp>/`, and the `index.md` path is copied.

**Diff**

1. Select the scope in Project View.
2. Run **Diff with Default Branch** → Markdown with a \`\`\`diff block (auto-export if large).

---

## Settings (Preferences → PromptPack)

* **File tree scope:** Project / Selection / Off
* **Filters:** ignored folders & extensions (comma-separated)
* **Diff:** default ref (leave empty for auto-detect)
* **Clipboard limit:** max size before auto-export (KB)

> Note: the lock-file list is currently built-in.

---

## Requirements

* Git available in `PATH` (for diff).
* JetBrains IDE 2024.2 (build 242.\*).
