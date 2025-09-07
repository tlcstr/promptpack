package dev.promptpack

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.util.LinkedHashSet
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VfsFiltersTest : BasePlatformTestCase() {
  fun testCollectFiles_appliesDirExtAndFileNameFilters() {
    val root = myFixture.tempDirFixture.findOrCreateDir("root")
    val keep = myFixture.tempDirFixture.createFile("root/keep.txt", "ok")
    myFixture.tempDirFixture.createFile("root/skip.png", "bin")
    myFixture.tempDirFixture.createFile("root/package-lock.json", "{}")
    myFixture.tempDirFixture.createFile("root/node_modules/a.js", "js")

    val out = LinkedHashSet<VirtualFile>()
    VfsFilters.collectFiles(
      start = root,
      out = out,
      ignoredDirs = setOf("node_modules"),
      ignoredExts = setOf("png"),
      ignoredFiles = setOf("package-lock.json"),
    )

    assertTrue(out.contains(keep))
    assertEquals(1, out.size)
  }
}
