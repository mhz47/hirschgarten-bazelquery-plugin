package org.jetbrains.bazel.runnerAction

import ch.epfl.scala.bsp4j.JvmEnvironmentItem
import com.intellij.openapi.project.Project
import org.jetbrains.bazel.config.BspPluginBundle
import org.jetbrains.bazel.server.tasks.JvmTestEnvironmentTask
import org.jetbrains.bazel.workspacemodel.entities.BuildTargetInfo

public class TestWithLocalJvmRunnerAction(
  targetInfo: BuildTargetInfo,
  text: (() -> String)? = null,
  isDebugMode: Boolean = false,
  verboseText: Boolean = false,
) : LocalJvmRunnerAction(
    targetInfo = targetInfo,
    text = {
      if (text != null) {
        text()
      } else if (isDebugMode) {
        BspPluginBundle.message(
          "target.debug.with.jvm.runner.action.text",
          if (verboseText) targetInfo.buildTargetName else "",
        )
      } else {
        BspPluginBundle.message(
          "target.test.with.jvm.runner.action.text",
          if (verboseText) targetInfo.buildTargetName else "",
        )
      }
    },
    isDebugMode = isDebugMode,
  ) {
  override suspend fun getEnvironment(project: Project): JvmEnvironmentItem? =
    JvmTestEnvironmentTask(project).connectAndExecute(targetInfo.id)?.items?.first()
}
