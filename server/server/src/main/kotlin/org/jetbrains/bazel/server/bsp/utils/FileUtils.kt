package org.jetbrains.bazel.server.bsp.utils

import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText

object FileUtils {
  /**
   * Important for aspect files, as writing the same content
   * updates filesystem's modification date and trigger Bazel's
   * "Checking cached action" step
   */
  @OptIn(ExperimentalPathApi::class)
  fun Path.writeIfDifferent(fileContent: String) {
    // Make sure this path is a file before writing into it.
    if (this.isDirectory()) {
      this.deleteRecursively()
    }
    if (!this.exists() || this.readText() != fileContent) {
      this.writeText(fileContent)
    }
  }
}
