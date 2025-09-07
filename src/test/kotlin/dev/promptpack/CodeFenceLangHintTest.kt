package dev.promptpack

import com.intellij.testFramework.LightVirtualFile
import org.junit.Assert.assertEquals
import org.junit.Test

class CodeFenceLangHintTest {
  @Test
  fun mapsKnownExtensions() {
    val vf = LightVirtualFile("A.kt", "")
    assertEquals("kotlin", FileTreeUtil.codeFenceLanguageHint(vf))
  }
}
