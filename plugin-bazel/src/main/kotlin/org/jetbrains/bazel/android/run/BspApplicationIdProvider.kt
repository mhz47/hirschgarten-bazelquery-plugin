package org.jetbrains.bazel.android.run

import com.android.tools.idea.run.ApkProvisionException
import com.android.tools.idea.run.ApplicationIdProvider
import com.intellij.openapi.project.Project
import org.jetbrains.bazel.label.Label
import org.jetbrains.bazel.target.getModule
import org.jetbrains.bazel.target.moduleEntity
import org.jetbrains.bazel.workspacemodel.entities.androidAddendumEntity
import com.android.tools.idea.project.getPackageName as getApplicationIdFromManifest

private const val MANIFEST_APPLICATION_ID = "applicationId"

class BspApplicationIdProvider(private val project: Project, private val target: Label) : ApplicationIdProvider {
  override fun getPackageName(): String {
    val module = target.getModule(project) ?: throw ApkProvisionException("Could not find module for target $target")
    module
      .moduleEntity
      ?.androidAddendumEntity
      ?.manifestOverrides
      ?.get(MANIFEST_APPLICATION_ID)
      ?.let { return it }
    return getApplicationIdFromManifest(module) ?: throw ApkProvisionException("Could not get applicationId from manifest")
  }

  override fun getTestPackageName(): String? = null
}
