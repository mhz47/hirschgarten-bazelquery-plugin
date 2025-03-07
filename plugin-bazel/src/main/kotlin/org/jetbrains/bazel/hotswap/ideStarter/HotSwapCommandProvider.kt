package org.jetbrains.bazel.hotswap.ideStarter

import com.jetbrains.performancePlugin.CommandProvider
import com.jetbrains.performancePlugin.CreateCommand

class HotSwapCommandProvider : CommandProvider {
  override fun getCommands(): Map<String, CreateCommand> =
    mapOf(
      DebugLocalJvmSimpleKotlinTestCommand.PREFIX to CreateCommand(::DebugLocalJvmSimpleKotlinTestCommand),
    )
}
