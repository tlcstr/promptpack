package dev.promptpack

import org.junit.Assert.assertTrue
import org.junit.Test

class FileTreeUtilFencedTest {
  @Test
  fun fenced_growsFenceWhenContentHasBackticks() {
    val content = "```\ncode\n```"
    val out = FileTreeUtil.fenced(content, "text")
    assertTrue(out.startsWith("````text\n"))
    assertTrue(out.endsWith("\n````"))
    assertTrue(out.contains(content))
  }
}
