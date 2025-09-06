package dev.promptpack

import com.intellij.DynamicBundle
import org.jetbrains.annotations.PropertyKey

private const val BUNDLE = "messages.PromptPackBundle"

object PromptPackBundle : DynamicBundle(BUNDLE) {
  @JvmStatic
  fun message(
    @PropertyKey(resourceBundle = BUNDLE) key: String,
    vararg params: Any,
  ): String = getMessage(key, *params)
}
