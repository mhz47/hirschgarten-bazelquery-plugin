package org.jetbrains.bazel.bazelrunner.outputs

import com.intellij.execution.process.OSProcessUtil
import com.intellij.util.io.awaitExit
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.bazel.logger.BspClientLogger
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

abstract class OutputProcessor(private val process: Process, vararg loggers: OutputHandler) {
  val stdoutCollector = OutputCollector()
  val stderrCollector = OutputCollector()

  private val executorService = Executors.newCachedThreadPool()
  protected val runningProcessors = mutableListOf<Future<*>>()

  init {
    start(process.inputStream, stdoutCollector, *loggers)
    start(process.errorStream, stderrCollector, *loggers)
  }

  protected open fun shutdown() {
    executorService.shutdown()
  }

  protected abstract fun isRunning(): Boolean

  protected fun start(inputStream: InputStream, vararg handlers: OutputHandler) {
    val runnable =
      Runnable {
        try {
          BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
            var prevLine: String? = null

            while (!Thread.currentThread().isInterrupted) {
              val line = reader.readLine() ?: return@Runnable
              if (line == prevLine) continue
              prevLine = line
              if (isRunning()) {
                handlers.forEach { it.onNextLine(line) }
              } else {
                break
              }
            }
          }
        } catch (e: IOException) {
          if (Thread.currentThread().isInterrupted) return@Runnable
          throw RuntimeException(e)
        }
      }

    executorService.submit(runnable).also { runningProcessors.add(it) }
  }

  suspend fun waitForExit(serverPidFuture: CompletableFuture<Long>?, logger: BspClientLogger?): Int =
    coroutineScope {
      var isFinished = false
      while (!isFinished) {
        isFinished = process.waitFor(500, TimeUnit.MILLISECONDS)
        if (!isActive) {
          process.destroy()
          withTimeoutOrNull(2000) {
            serverPidFuture
              ?.await() // we don't want to wait forever if server never gave us its PID
              ?.let { pid -> OSProcessUtil.killProcess(pid.toInt()) }
              ?: logger?.error("Could not cancel the task. Bazel server needs to be interrupted manually.")
          }
        }
      }
      // Return values of waitFor() and waitFor(long, TimeUnit) differ
      // so we can't just return value from waitFor(long, TimeUnit) here
      val exitCode = process.awaitExit()
      shutdown()
      return@coroutineScope exitCode
    }
}
