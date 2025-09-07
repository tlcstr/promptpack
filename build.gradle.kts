import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease

plugins {
  kotlin("jvm") version "2.0.20"
  id("org.jetbrains.intellij.platform") version "2.8.0"
  id("org.jlleitschuh.gradle.ktlint") version "13.1.0"
  id("io.gitlab.arturbosch.detekt") version "1.23.8"
}

repositories {
  mavenCentral()
  intellijPlatform { defaultRepositories() }
}

dependencies {
  intellijPlatform {
    // Local IDE for development/runIde
    local("/Applications/WebStorm.app/Contents")

    // IntelliJ Platform Test Framework (for BasePlatformTestCase, etc.)
    testFramework(TestFrameworkType.Platform)
  }

  // Test dependencies (JUnit 4)
  testImplementation("junit:junit:4.13.2")
  // Use kotlin-test with JUnit4 to avoid JUnit5 engine clashes
  testImplementation(kotlin("test-junit"))

  // Workarounds for 2.x: missing runtime artifacts sometimes needed by the test framework
  testRuntimeOnly("org.opentest4j:opentest4j:1.3.0")
  testRuntimeOnly("junit:junit:4.13.2")
}

kotlin {
  jvmToolchain(17)
}

intellijPlatform {
  pluginConfiguration {
    id = "dev.promptpack"
    name = "PromptPack"
    version = "0.1.0"
    description = "Copy selected files/folders as LLM-ready fenced blocks with an optional project file tree."
    vendor {
      name = "You"
      email = "you@example.com"
    }
    ideaVersion {
      sinceBuild = "242"
      untilBuild = "242.*"
    }
  }

  // Do not generate searchable options
  buildSearchableOptions = false

  // Verify the plugin against WebStorm 242.* releases
  pluginVerification {
    ides {
      select {
        types = listOf(IntelliJPlatformType.WebStorm)
        channels = listOf(ProductRelease.Channel.RELEASE)
        sinceBuild = "242"
        untilBuild = "242.*"
      }
    }
  }
}

ktlint {
  version.set("1.4.1")
  filter { exclude("**/build/**") }
}

detekt {
  buildUponDefaultConfig = true
  allRules = false
  config.setFrom(files("$rootDir/detekt.yml"))
  baseline = file("$rootDir/detekt-baseline.xml")
  source.setFrom("src/main/kotlin", "src/test/kotlin")
}

tasks.named("check") {
  dependsOn("ktlintCheck", "detekt")
}

tasks.test {
  // Force the JUnit4 runner (BasePlatformTestCase is JUnit3-style but compatible)
  useJUnit()
  systemProperty("java.awt.headless", "true")
  maxParallelForks = 1
}
