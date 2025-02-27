package org.jetbrains.bazel.server.connection

import ch.epfl.scala.bsp4j.InitializeBuildParams
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.diagnostic.telemetry.OtlpConfiguration
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.bazel.config.BspPluginBundle
import org.jetbrains.bazel.config.FeatureFlagsProvider
import org.jetbrains.bazel.config.rootDir
import org.jetbrains.bazel.server.chunking.ChunkingBuildServer
import org.jetbrains.bazel.server.client.BspClient
import org.jetbrains.bazel.server.client.GenericConnection
import org.jetbrains.bazel.server.utils.CancellableFuture
import org.jetbrains.bazel.server.utils.TimeoutHandler
import org.jetbrains.bazel.server.utils.reactToExceptionIn
import org.jetbrains.bazel.settings.bazel.bazelProjectSettings
import org.jetbrains.bazel.ui.console.BspConsoleService
import org.jetbrains.bazel.ui.console.ids.CONNECT_TASK_ID
import org.jetbrains.bsp.bazel.install.EnvironmentCreator
import org.jetbrains.bsp.protocol.BSP_CLIENT_NAME
import org.jetbrains.bsp.protocol.BSP_CLIENT_VERSION
import org.jetbrains.bsp.protocol.BSP_VERSION
import org.jetbrains.bsp.protocol.BazelBuildServerCapabilities
import org.jetbrains.bsp.protocol.CLIENT_CAPABILITIES
import org.jetbrains.bsp.protocol.InitializeBuildData
import org.jetbrains.bsp.protocol.JoinedBuildServer
import java.lang.System.currentTimeMillis
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeoutException
import kotlin.time.Duration.Companion.seconds

/**
 * a temporary piece of data that signals built-in connection to reset to account for changes in configs
 * TODO: purge along with BSP complete removal
 */
private data class ConnectionResetConfig(val projectViewFile: Path?, val initializeBuildData: InitializeBuildData)

private class CancelableInvocationHandlerWithTimeout(
  private val remoteProxy: JoinedBuildServer,
  private val cancelOnFuture: CompletableFuture<Void>,
  private val timeoutHandler: TimeoutHandler,
) : InvocationHandler {
  override fun invoke(
    proxy: Any,
    method: Method,
    args: Array<out Any>?,
  ): Any? {
    val startTime = currentTimeMillis()
    log.debug("Running BSP request '${method.name}'")

    return when (val result = invokeMethod(method, args)) {
      is CompletableFuture<*> -> addTimeoutAndHandler(result, startTime, method.name)
      else -> result
    }
  }

  private fun invokeMethod(method: Method, args: Array<out Any>?): Any? = method.invoke(remoteProxy, *args ?: emptyArray())

  private fun addTimeoutAndHandler(
    result: CompletableFuture<*>,
    startTime: Long,
    methodName: String,
  ): CompletableFuture<Any?> =
    CancellableFuture
      .from(result)
      .reactToExceptionIn(cancelOnFuture)
      .reactToExceptionIn(timeoutHandler.getUnfinishedTimeoutFuture())
      .handle { value, error -> doHandle(value, error, startTime, methodName) }

  private fun doHandle(
    value: Any?,
    error: Throwable?,
    startTime: Long,
    methodName: String,
  ): Any? {
    val elapsedTime = calculateElapsedTime(startTime)
    log.debug(
      "BSP method '$methodName' call took ${elapsedTime}ms. " +
        "Result: ${if (error == null) "SUCCESS" else "FAILURE"}",
    )

    return when (error) {
      null -> value
      else -> handleError(error, methodName, elapsedTime)
    }
  }

  private fun calculateElapsedTime(startTime: Long): Long = currentTimeMillis() - startTime

  private fun handleError(
    error: Throwable,
    methodName: String,
    elapsedTime: Long,
  ): Nothing {
    when (error) {
      is TimeoutException -> log.error("BSP request '$methodName' timed out after ${elapsedTime}ms", error)
    }
    throw error
  }

  private companion object {
    private var log = logger<CancelableInvocationHandlerWithTimeout>()
  }
}

private val log = logger<DefaultBspConnection>()

class DotBazelBspCreator(projectPath: VirtualFile) : EnvironmentCreator(projectPath.toNioPath()) {
  override fun create() {
    createDotBazelBsp()
  }
}

class DefaultBspConnection(private val project: Project) : BspConnection {
  @Volatile
  private var server: JoinedBuildServer? = null
  private var capabilities: BazelBuildServerCapabilities? = null

  private var connectionResetConfig: ConnectionResetConfig? = null

  private val timeoutHandler = TimeoutHandler { Registry.intValue("bsp.request.timeout.seconds").seconds }
  private val mutex = Mutex()

  override suspend fun connect() {
    mutex.withLock {
      ensureConnected()
    }
  }

  private suspend fun ensureConnected() {
    log.debug("ensuring the connection is established")
    val bspClient = createBspClient()
    val newConnectionResetConfig = generateNewConnectionResetConfig()
    if (!isConnected() || newConnectionResetConfig != connectionResetConfig) {
      connectionResetConfig = newConnectionResetConfig
      val inMemoryConnection =
        object : GenericConnection {
          val installationDirectory = project.rootDir.toNioPath()
          val conn =
            Connection(
              installationDirectory,
              null,
              newConnectionResetConfig.projectViewFile,
              installationDirectory,
              bspClient,
              propagateTelemetryContext = true,
            )
          val projectPath = VfsUtil.findFile(installationDirectory, true) ?: error("Project doesn't exist")

          init {
            DotBazelBspCreator(projectPath).create()
          }

          override val server: JoinedBuildServer
            get() = conn.clientLauncher.remoteProxy

          override fun shutdown() {
            conn.stop()
          }
        }
      connectBuiltIn(inMemoryConnection, newConnectionResetConfig.initializeBuildData)
    }
  }

  private fun generateNewConnectionResetConfig(): ConnectionResetConfig =
    ConnectionResetConfig(
      projectViewFile = project.bazelProjectSettings.projectViewPath?.toAbsolutePath(),
      initializeBuildData =
        InitializeBuildData(
          clientClassesRootDir = "${project.rootDir}/out",
          openTelemetryEndpoint = getOpenTelemetryEndPoint(),
          featureFlags = FeatureFlagsProvider.accumulateFeatureFlags(),
        ),
    )

  private suspend fun connectBuiltIn(connection: GenericConnection, initializeBuildData: InitializeBuildData) {
    coroutineScope {
      val bspSyncConsole = BspConsoleService.getInstance(project).bspSyncConsole
      bspSyncConsole.startTask(
        taskId = CONNECT_TASK_ID,
        title = BspPluginBundle.message("console.task.auto.connect.title"),
        message = BspPluginBundle.message("console.task.auto.connect.in.progress"),
        cancelAction = { coroutineContext.cancel() },
      )
      bspSyncConsole.addMessage(
        CONNECT_TASK_ID,
        BspPluginBundle.message("console.message.initialize.server.in.progress"),
      )
      server = connection.server.wrapInChunkingServerIfRequired()
      capabilities = server?.initializeAndObtainCapabilities(initializeBuildData)
      bspSyncConsole.addMessage(
        CONNECT_TASK_ID,
        BspPluginBundle.message("console.message.initialize.server.success"),
      )
      bspSyncConsole.finishTask(CONNECT_TASK_ID, BspPluginBundle.message("console.task.auto.connect.success"))
    }
  }

  private fun createBspClient(): BspClient {
    val bspConsoleService = BspConsoleService.getInstance(project)

    return BspClient(
      bspConsoleService.bspSyncConsole,
      bspConsoleService.bspBuildConsole,
      timeoutHandler,
      project,
    )
  }

  private fun JoinedBuildServer.wrapInChunkingServerIfRequired(): JoinedBuildServer =
    if (Registry.`is`("bsp.request.chunking.enable")) {
      val minChunkSize = Registry.intValue("bsp.request.chunking.size.min")
      ChunkingBuildServer(this, minChunkSize)
    } else {
      this
    }

  private suspend fun JoinedBuildServer.initializeAndObtainCapabilities(
    initializeBuildData: InitializeBuildData,
  ): BazelBuildServerCapabilities {
    val buildInitializeResults = buildInitialize(createInitializeBuildParams(initializeBuildData)).asDeferred().await()
    onBuildInitialized()
    // cast is safe because we registered a custom type adapter
    return buildInitializeResults.capabilities as BazelBuildServerCapabilities
  }

  private fun createInitializeBuildParams(initializeBuildData: InitializeBuildData): InitializeBuildParams {
    val projectBaseDir = project.rootDir
    val params =
      InitializeBuildParams(
        BSP_CLIENT_NAME,
        BSP_CLIENT_VERSION,
        BSP_VERSION,
        projectBaseDir.toString(),
        CLIENT_CAPABILITIES,
      )

    params.data = initializeBuildData

    return params
  }

  private fun getOpenTelemetryEndPoint(): String? =
    try {
      OtlpConfiguration.getTraceEndpoint()
    } catch (_: NoSuchMethodError) {
      null
    }

  override suspend fun <T> runWithServer(task: suspend (server: JoinedBuildServer, capabilities: BazelBuildServerCapabilities) -> T): T {
    connect()
    val server = server ?: error("Cannot execute the task. Server not available.")
    val capabilities = capabilities ?: error("Cannot execute the task. Capabilities not available.")

    return task(server, capabilities)
  }

  override suspend fun disconnect() {}

  override fun isConnected(): Boolean = server != null
}
