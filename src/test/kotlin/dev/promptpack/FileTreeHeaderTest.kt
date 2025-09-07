package dev.promptpack

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileTreeHeaderTest : BasePlatformTestCase() {
  fun testBuildTreeHeader_selectionScope_respectsFilters() {
    val root = myFixture.tempDirFixture.findOrCreateDir("root")
    myFixture.tempDirFixture.createFile("root/src/Main.kt", "fun main()=Unit")
    myFixture.tempDirFixture.createFile("root/node_modules/lib.js", "")

    val text =
      FileTreeUtil.buildTreeHeaderAndText(
        project,
        FileTreeUtil.TreeInput(
          scope = TreeScope.SELECTION,
          selection = arrayOf(root),
          ignoredDirs = setOf("node_modules"),
          ignoredExts = emptySet(),
        ),
      )

    assertTrue(text.startsWith("File tree (selection):"))
    assertTrue(text.contains("Main.kt"))
    assertFalse(text.contains("node_modules"))
  }
}
