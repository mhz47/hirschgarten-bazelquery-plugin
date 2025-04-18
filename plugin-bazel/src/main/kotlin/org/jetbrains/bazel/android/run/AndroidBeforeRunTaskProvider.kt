package org.jetbrains.bazel.android.run

import com.android.ddmlib.IDevice
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.execution.BeforeRunTask
import com.intellij.execution.BeforeRunTaskProvider
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import kotlinx.coroutines.runBlocking
import org.jetbrains.bazel.config.BazelPluginBundle
import org.jetbrains.bazel.label.Label
import org.jetbrains.bazel.run.config.BazelRunConfiguration
import org.jetbrains.bazel.server.tasks.runBuildTargetTask
import org.jetbrains.bsp.protocol.MobileInstallStartType
import org.jetbrains.bsp.protocol.StatusCode

private val PROVIDER_ID = Key.create<AndroidBeforeRunTaskProvider.Task>("AndroidBeforeRunTaskProvider")

class AndroidBeforeRunTaskProvider : BeforeRunTaskProvider<AndroidBeforeRunTaskProvider.Task>() {
  private val log = logger<AndroidBeforeRunTaskProvider>()

  class Task : BeforeRunTask<Task>(PROVIDER_ID)

  override fun getId(): Key<Task> = PROVIDER_ID

  override fun getName(): String = BazelPluginBundle.message("console.task.build.title")

  override fun createTask(configuration: RunConfiguration): Task? =
    if (configuration is BazelRunConfiguration) {
      Task()
    } else {
      null
    }

  override fun executeTask(
    context: DataContext,
    configuration: RunConfiguration,
    environment: ExecutionEnvironment,
    task: Task,
  ): Boolean {
    val runConfiguration = environment.runProfile as? BazelRunConfiguration ?: return false
    val handler = runConfiguration.handler
    if (handler !is AndroidBazelRunHandler) return false

    val targetId = runConfiguration.targets.singleOrNull() ?: return false
    val deviceFuture = environment.getCopyableUserData(DEVICE_FUTURE_KEY) ?: return false

    val useMobileInstall = handler.state.useMobileInstall

    return if (useMobileInstall) {
      runMobileInstall(environment, targetId, deviceFuture)
    } else {
      buildApkWithoutInstall(environment.project, targetId)
    }
  }

  private fun runMobileInstall(
    environment: ExecutionEnvironment,
    targetId: Label,
    deviceFuture: ListenableFuture<IDevice>,
  ): Boolean {
    val startType =
      when (environment.executor.id) {
        DefaultRunExecutor.EXECUTOR_ID -> MobileInstallStartType.COLD
        DefaultDebugExecutor.EXECUTOR_ID -> MobileInstallStartType.DEBUG
        else -> return false
      }

    val mobileInstallResult =
      runBlocking {
        runMobileInstallTargetTask(targetId, deviceFuture, startType, environment.project, log)
      }
    return mobileInstallResult?.statusCode == StatusCode.OK
  }

  private fun buildApkWithoutInstall(project: Project, targetId: Label): Boolean {
    val buildResult =
      runBlocking {
        runBuildTargetTask(listOf(targetId), project)
      }
    return buildResult?.statusCode == StatusCode.OK
  }
}
