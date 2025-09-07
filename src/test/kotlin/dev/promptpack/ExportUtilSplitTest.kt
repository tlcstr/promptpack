package dev.promptpack

import org.junit.Assert.assertEquals
import org.junit.Test

class ExportUtilSplitTest {
  @Test
  fun splitIntoParts_respectsLimitAndSeparators() {
    val blocks = listOf("A".repeat(10), "B".repeat(15), "C".repeat(5))
    val parts = ExportUtil.splitIntoParts(blocks, limit = 16)
    assertEquals(3, parts.size)
    assertEquals("A".repeat(10), parts[0])
    assertEquals("B".repeat(15), parts[1])
    assertEquals("C".repeat(5), parts[2])
  }
}
