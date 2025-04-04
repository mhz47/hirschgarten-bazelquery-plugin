package org.jetbrains.bazel.android

import com.android.tools.idea.model.ClassJarProvider
import com.android.tools.idea.projectsystem.AndroidModuleSystem
import com.android.tools.idea.projectsystem.AndroidProjectSystem
import com.android.tools.idea.projectsystem.LightResourceClassService
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.projectsystem.SourceProvidersFactory
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.res.AndroidInnerClassFinder
import com.android.tools.idea.res.AndroidResourceClassPsiElementFinder
import com.android.tools.idea.res.ProjectLightResourceClassService
import com.intellij.facet.ProjectFacetManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElementFinder
import org.jetbrains.android.facet.AndroidFacet
import java.nio.file.Path

public class BazelAndroidProjectSystem(override val project: Project) : AndroidProjectSystem {
  private val psiElementFinders =
    listOf(AndroidInnerClassFinder.INSTANCE, AndroidResourceClassPsiElementFinder(getLightResourceClassService()))

  private val syncManager = BazelProjectSystemSyncManager(project)

  override fun getKnownApplicationIds(): Set<String> = emptySet()

  override fun allowsFileCreation(): Boolean = true

  override fun getAndroidFacetsWithPackageName(packageName: String): Collection<AndroidFacet> =
    ProjectFacetManager
      .getInstance(project)
      .getFacets(AndroidFacet.ID)
      .filter { it.module.getModuleSystem().getPackageName() == packageName }

  override fun getBootClasspath(module: Module): Collection<String> = emptyList()

  override fun getBuildManager(): ProjectSystemBuildManager = BazelProjectSystemBuildManager(project)

  override fun getClassJarProvider(): ClassJarProvider = BazelClassJarProvider()

  override fun getDefaultApkFile(): VirtualFile? = null

  override fun getLightResourceClassService(): LightResourceClassService = ProjectLightResourceClassService.getInstance(project)

  override fun getModuleSystem(module: Module): AndroidModuleSystem = BazelAndroidModuleSystem(module)

  override fun getPathToAapt(): Path = TODO("Not implemented")

  override fun getPsiElementFinders(): Collection<PsiElementFinder> = psiElementFinders

  override fun getSourceProvidersFactory(): SourceProvidersFactory = BazelSourceProvidersFactory()

  override fun getSyncManager(): ProjectSystemSyncManager = syncManager

  override fun isNamespaceOrParentPackage(packageName: String): Boolean = false

  override fun findModulesWithApplicationId(applicationId: String): Collection<Module> = emptyList()

  override fun isAndroidProject(): Boolean = true
}
