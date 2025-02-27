package org.jetbrains.bazel.server.tasks

import ch.epfl.scala.bsp4j.BuildServerCapabilities
import ch.epfl.scala.bsp4j.BuildTargetIdentifier
import ch.epfl.scala.bsp4j.JvmRunEnvironmentParams
import ch.epfl.scala.bsp4j.JvmRunEnvironmentResult
import com.intellij.openapi.project.Project
import kotlinx.coroutines.future.await
import org.jetbrains.bsp.protocol.JoinedBuildServer

class JvmRunEnvironmentTask(project: Project) : BspServerSingleTargetTask<JvmRunEnvironmentResult>("jvmRunEnvironment", project) {
  override suspend fun executeWithServer(
    server: JoinedBuildServer,
    capabilities: BuildServerCapabilities,
    targetId: BuildTargetIdentifier,
  ): JvmRunEnvironmentResult {
    val params = createJvmRunEnvironmentParams(targetId)
    return server.buildTargetJvmRunEnvironment(params).await()
  }

  private fun createJvmRunEnvironmentParams(targetId: BuildTargetIdentifier) = JvmRunEnvironmentParams(listOf(targetId))
}
