plugins {
  kotlin("jvm") version "2.0.20"
  id("org.jetbrains.intellij.platform") version "2.8.0"
}

repositories {
  mavenCentral()
  intellijPlatform { defaultRepositories() }
}

dependencies {
  intellijPlatform {
    local("/Applications/WebStorm.app/Contents")
  }
}

kotlin {
  jvmToolchain(17)
}

intellijPlatform {
  pluginConfiguration {
    id = "dev.promptpack"                 // новый plugin id
    name = "PromptPack"                   // новое имя
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
  buildSearchableOptions = false
}
