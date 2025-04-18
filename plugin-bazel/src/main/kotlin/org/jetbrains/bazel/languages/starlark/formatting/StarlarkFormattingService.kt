package org.jetbrains.bazel.languages.starlark.formatting

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.hint.HintUtil
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessAdapter
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.formatting.service.AsyncDocumentFormattingService
import com.intellij.formatting.service.AsyncFormattingRequest
import com.intellij.formatting.service.FormattingService.Feature
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiEditorUtil
import com.intellij.ui.LightweightHint
import org.jetbrains.bazel.config.BazelPluginBundle
import org.jetbrains.bazel.config.rootDir
import org.jetbrains.bazel.languages.starlark.StarlarkFileType
import org.jetbrains.bazel.settings.bazel.bazelProjectSettings

private val LOG = logger<StarlarkFormattingService>()
private const val NOTIFICATION_GROUP_ID = "Buildifier"

class StarlarkFormattingService : AsyncDocumentFormattingService() {
  override fun getFeatures(): Set<Feature> = emptySet()

  override fun canFormat(file: PsiFile): Boolean {
    val virtualFile = file.virtualFile ?: return false
    if (!FileTypeRegistry.getInstance().isFileOfType(virtualFile, StarlarkFileType)) return false
    return file.project.bazelProjectSettings.getBuildifierPathString() != null
  }

  override fun createFormattingTask(request: AsyncFormattingRequest): FormattingTask? {
    val formattingContext = request.context
    val project = formattingContext.project
    val buildifierPath = project.bazelProjectSettings.getBuildifierPathString() ?: return null

    if (!checkDocumentExists(request)) {
      LOG.warn("Document for file ${request.context.containingFile.name} is null")
      return null
    }

    val handler = createProcessHandler(request, buildifierPath) ?: return null

    return object : FormattingTask {
      override fun run() {
        try {
          handler.addProcessListener(BuildifierProcessListener(request))
          handler.startNotify()
        } catch (e: Exception) {
          LOG.warn(e.localizedMessage)
          request.onTextReady(null)
        }
      }

      override fun cancel(): Boolean {
        handler.destroyProcess()
        return true
      }

      override fun isRunUnderProgress(): Boolean = true
    }
  }

  private fun checkDocumentExists(request: AsyncFormattingRequest): Boolean {
    val psiFile = request.context.containingFile
    val project = request.context.project
    val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)

    return document != null
  }

  private fun createProcessHandler(request: AsyncFormattingRequest, buildifierPath: String): CapturingProcessHandler? {
    val ioFile = request.ioFile ?: return null
    val commandLine =
      GeneralCommandLine()
        .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
        .withExePath(buildifierPath)
        .withInput(ioFile)
        // Set the work directory to workspace root so that Buildifier can read .buildifier-tables.json
        .withWorkDirectory(VfsUtil.virtualToIoFile(request.context.project.rootDir))

    return CapturingProcessHandler(commandLine)
  }

  override fun getNotificationGroupId(): String = NOTIFICATION_GROUP_ID

  override fun getName(): String = BazelPluginBundle.message("buildifier.formatting.service.name")
}

private open class BuildifierProcessListener(private val request: AsyncFormattingRequest) : CapturingProcessAdapter() {
  override fun processTerminated(event: ProcessEvent) {
    val exitCode = event.exitCode
    when (exitCode) {
      0 -> showFormattedOutput()
      else -> request.onError(BazelPluginBundle.message("buildifier.run.error.message"), output.stderr)
    }
  }

  private fun showFormattedOutput() {
    if (output.stdout.isEmpty()) {
      showFormattedLinesInfo(BazelPluginBundle.message("buildifier.formatted.ignored"))
      request.onTextReady(null)
    } else {
      showFormattedLinesInfo(BazelPluginBundle.message("buildifier.formatted.success"))
      request.onTextReady(output.stdout)
    }
  }

  private fun showFormattedLinesInfo(text: String) =
    ApplicationManager
      .getApplication()
      .invokeLater(
        {
          val editor = PsiEditorUtil.findEditor(request.context.containingFile) ?: return@invokeLater
          val component = HintUtil.createInformationLabel(text)
          val hint = LightweightHint(component)
          HintManagerImpl
            .getInstanceImpl()
            .showEditorHint(
              hint,
              editor,
              HintManager.ABOVE,
              HintManager.HIDE_BY_ANY_KEY or HintManager.HIDE_BY_SCROLLING,
              0,
              false,
            )
        },
        ModalityState.defaultModalityState(),
      )
}
