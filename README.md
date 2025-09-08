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
* **Test folders:** include or exclude test folders; specify folder names (matched by directory name at any depth, case-insensitive, e.g. `__tests__`, `__mocks__`)
* **Diff:** default ref (leave empty for auto-detect)
* **Clipboard limit:** max size before auto-export (KB)

> Note: the lock-file list is currently built-in.

---

## Public API (Copy Contents) — how it works

The Public API feature augments **Copy Contents of Selected Items** by auto-collecting text files that represent the “public surface” of your modules (e.g., `public/` docs, samples, API files) and emitting them as a separate section **before** the rest of the selection.

### 1) Toggle & configuration

* Master toggle: **Preferences → PromptPack → Public API (Copy Contents) → “Include Public API section”**.
* Key options:
  * **Public folder names (CSV):** names matched case-insensitively (default: `public`, `public-api`, `publicApi`).
  * **Skip duplicates in main content:** if a file appears in Public API, it’s removed from the “Selected Files” section.
  * **Max public files per module / total:** caps to keep output manageable.
* Related (Module Detection):
  * **Detect by manifest:** a module root is any directory containing a known manifest (CSV, e.g., `package.json`, `pyproject.toml`, `go.mod`, `build.gradle.kts`, `Cargo.toml`, `*.csproj`, etc.).
  * **Detect by path patterns:** project-relative glob patterns (CSV, e.g., `packages/*`, `libs/*`, `modules/*`).
  * **Require public folder:** only treat a directory as a module if it (or its descendants) contains a matching public folder.

### 2) What is considered a module?

Given your **current Project View selection**, PromptPack:
1. Walks **down** into selected directories and **up** their ancestor chains.
2. Marks directories as **modules** if they match **any** enabled rule:
  * has a supported **manifest**, or
  * its project-relative path matches a configured **pattern**.
3. Optionally requires the module to contain a **public folder** (configurable).

> Modules are sorted by project-relative path for deterministic output.

### 3) What is collected into Public API?

For each detected module, PromptPack finds **public folders** (matching configured names, case-insensitively), then recursively collects **text files**:
* **Included:** all non-binary files not excluded by your filters.
* **Excluded:**
  * files with ignored extensions or exact ignored file names (lock files, binaries, etc.),
  * directories with ignored names (e.g., `node_modules`, `build`, etc.),
  * **test folders** when “Exclude test folders” is selected (by directory name, at any depth).

Per-module and total **limits** are applied:
* If a module exceeds **Max public files per module**, it’s trimmed and you get a notification.
* If combined Public API exceeds **Max public files total**, the list is trimmed globally (notification shown).

All Public API files are **de-duplicated by path** and **sorted** by project-relative path.

### 4) Output structure

When enabled and at least one public file is found, the final Markdown looks like:

```
# File tree (project/selection)…
```text
…tree…
```

# Public API
./packages/foo/public/index.ts:
```ts
export type Foo = …
```

./packages/bar/public/README.md:
```markdown
# Bar API
…
```

# Selected Files
./some/other/selected/file.kt:
```kotlin
…
```

Notes:
* If “Skip duplicates in main content” is ON, any file already listed under **Public API** is removed from **Selected Files**.
* Unsaved editor changes are included.
* Small results go to the clipboard; large results are exported to `.promptpack/exports/<timestamp>/` and `index.md` is opened in the IDE.

### 5) Why this design?

* **Deterministic**: stable ordering and explicit limits make output reproducible.
* **Safe & fast**: runs on background threads, reads via `ReadAction`, and respects your ignore lists.
* **Minimal magic**: simple rules—manifests, path patterns, and explicit public folder names.

---

## Requirements

* Git available in `PATH` (for diff).
* JetBrains IDE 2024.2 (build 242.\*).

