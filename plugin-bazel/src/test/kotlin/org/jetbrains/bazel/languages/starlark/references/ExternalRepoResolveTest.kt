package org.jetbrains.bazel.languages.starlark.references

import com.intellij.ide.starter.project.GitProjectInfo
import com.intellij.ide.starter.project.ProjectInfoSpec
import com.intellij.tools.ide.performanceTesting.commands.CommandChain
import com.intellij.tools.ide.performanceTesting.commands.exitApp
import com.intellij.tools.ide.performanceTesting.commands.openFile
import com.intellij.tools.ide.performanceTesting.commands.takeScreenshot
import com.intellij.tools.ide.performanceTesting.commands.waitForSmartMode
import org.jetbrains.bazel.ideStarter.IdeStarterBaseProjectTest
import org.jetbrains.bazel.ideStarter.navigateToFile
import org.jetbrains.bazel.ideStarter.waitForBazelSync
import org.junit.jupiter.api.Test

/**
 * ```sh
 * bazel test //plugin-bazel/src/test/kotlin/org/jetbrains/bazel/languages/starlark/references:ExternalRepoResolveTest --jvmopt="-Dbazel.ide.starter.test.cache.directory=$HOME/IdeaProjects/hirschgarten" --sandbox_writable_path=/ --action_env=PATH --java_debug --test_arg=--wrapper_script_flag=--debug=8000
 * ```
 */
class ExternalRepoResolveTest : IdeStarterBaseProjectTest() {
  override val projectInfo: ProjectInfoSpec
    get() =
      GitProjectInfo(
        repositoryUrl = "https://github.com/JetBrainsBazelBot/simpleBazelProjectsForTesting.git",
        commitHash = "75c03f23963f645f33314ccd7a125ae28b1ea5f3",
        branchName = "main",
        projectHomeRelativePath = { it.resolve("starlarkResolveTest") },
        isReusable = false,
        configureProjectBeforeUse = ::configureProjectBeforeUse,
      )

  @Test
  fun openBazelProject() {
    val commands =
      CommandChain()
        .takeScreenshot("startSync")
        .waitForBazelSync()
        .waitForSmartMode()
        .openFile("src/BUILD")
        // load(":junit_test.bzl", "kt_<caret>test") -> def <caret>kt_test
        .navigateToFile(1, 29, expectedFilename = "junit_test.bzl", 3, 5)
        .openFile("src/BUILD")
        // java_<caret>library( -> def <caret>java_library
        .navigateToFile(11, 6, expectedFilename = "java_library.bzl", 18, 5)
        .openFile("src/BUILD")
        // kt_<caret>test( -> def <caret>kt_test
        .navigateToFile(4, 4, expectedFilename = "junit_test.bzl", 3, 5)
        // load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm<caret>_test") -> <caret>kt_jvm_test
        .navigateToFile(1, 46, expectedFilename = "jvm.bzl", 17, 1)
        // "//kotlin/internal:opts.<caret>bzl" -> <caret>kt_javac_options
        .navigateToFile(2, 29, expectedFilename = "opts.bzl", 1, 1)
        .openFile("src/BUILD")
        // srcs = ["nested<caret>_src/Hello.java"],
        .navigateToFile(13, 20, expectedFilename = "Hello.java", 1, 1)
        .exitApp()
    createContext().runIDE(commands = commands, runTimeout = timeout)
  }
}
