package org.jetbrains.bazel.server.sync

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import org.jetbrains.bazel.bazelrunner.BazelRunner
import org.jetbrains.bazel.label.Label
import org.jetbrains.bazel.server.bsp.info.BspInfo

object ClasspathQuery {
  suspend fun classPathQuery(
    target: Label,
    bspInfo: BspInfo,
    bazelRunner: BazelRunner,
  ): JvmClasspath {
    val queryFile = bspInfo.bazelBspDir().resolve("aspects/runtime_classpath_query.bzl")
    val command =
      bazelRunner.buildBazelCommand(inheritProjectviewOptionsOverride = true) {
        cquery {
          targets.add(target)
          options.addAll(listOf("--starlark:file=$queryFile", "--output=starlark"))
        }
      }
    val cqueryResult =
      bazelRunner
        .runBazelCommand(command, logProcessOutput = false, serverPidFuture = null)
        .waitAndGetResult(ensureAllOutputRead = true)
    if (cqueryResult.isNotSuccess) throw RuntimeException("Could not query target '$target' for runtime classpath")
    try {
      val classpaths = Gson().fromJson(cqueryResult.stdout, JvmClasspath::class.java)
      return classpaths
    } catch (e: JsonSyntaxException) {
      // sometimes Bazel returns two values to a query when multiple configurations apply to a target
      return if (cqueryResult.stdoutLines.size > 1) {
        val allOpts = cqueryResult.stdoutLines.map { Gson().fromJson(it, JvmClasspath::class.java) }
        allOpts.maxByOrNull { it.runtime_classpath.size + it.compile_classpath.size }!!
      } else {
        throw e
      }
    }
  }

  data class JvmClasspath(val runtime_classpath: List<String>, val compile_classpath: List<String>)
}
