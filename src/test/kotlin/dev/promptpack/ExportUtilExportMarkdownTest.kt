package dev.promptpack

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ExportUtilExportMarkdownTest : BasePlatformTestCase() {
  fun testExportMarkdown_createsIndexAndParts() {
    val blocks = listOf("# H1", "B".repeat(100))
    val res = ExportUtil.exportMarkdown(project, treeHeaderOrEmpty = "", blocks = blocks, chunkLimit = 50)

    assertNotNull(res.index)
    assertTrue(res.index.exists())
    assertEquals(2, res.parts.size)
    assertNotNull(res.dir.findChild("index.md"))
  }
}
