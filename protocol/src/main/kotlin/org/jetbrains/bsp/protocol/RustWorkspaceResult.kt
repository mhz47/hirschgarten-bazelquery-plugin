package org.jetbrains.bsp.protocol

import org.jetbrains.bazel.label.Label

data class RustWorkspaceResult(
  val packages: List<RustPackage>,
  val rawDependencies: Map<String, List<RustRawDependency>>,
  val dependencies: Map<String, List<RustDependency>>,
  val resolvedTargets: List<Label>,
)
