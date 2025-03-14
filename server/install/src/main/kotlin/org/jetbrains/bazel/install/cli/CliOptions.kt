package org.jetbrains.bazel.install.cli

import java.nio.file.Path

data class HelpCliOptions internal constructor(val isHelpOptionUsed: Boolean, val printHelp: () -> Unit)

data class ProjectViewCliOptions internal constructor(
  val bazelBinary: Path?,
  val targets: List<String>?,
  val excludedTargets: List<String>?,
  val buildFlags: List<String>?,
  val syncFlags: List<String>?,
  val allowManualTargetsSync: Boolean?,
  val directories: List<String>?,
  val excludedDirectories: List<String>?,
  val deriveTargetsFromDirectories: Boolean?,
  val importDepth: Int?,
  val produceTraceLog: Boolean?,
  val enabledRules: List<String>?,
  val ideJavaHomeOverride: Path?,
  val shardSync: Boolean?,
  val targetShardSize: Int?,
  val shardApproach: String?,
)

data class CliOptions internal constructor(
  val javaPath: Path?,
  val debuggerAddress: String?,
  val helpCliOptions: HelpCliOptions,
  val workspaceRootDir: Path,
  val projectViewFilePath: Path?,
  val projectViewCliOptions: ProjectViewCliOptions?,
  val bazelWorkspaceRootDir: Path,
)
