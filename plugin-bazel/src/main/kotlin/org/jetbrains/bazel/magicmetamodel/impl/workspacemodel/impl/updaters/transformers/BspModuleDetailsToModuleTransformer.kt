package org.jetbrains.bazel.magicmetamodel.impl.workspacemodel.impl.updaters.transformers

import com.intellij.platform.workspace.jps.entities.ModuleTypeId
import org.jetbrains.bazel.label.Label
import org.jetbrains.bazel.magicmetamodel.TargetNameReformatProvider
import org.jetbrains.bazel.workspacemodel.entities.BuildTargetInfo
import org.jetbrains.bazel.workspacemodel.entities.GenericModuleInfo
import org.jetbrains.bazel.workspacemodel.entities.IntermediateLibraryDependency
import org.jetbrains.bazel.workspacemodel.entities.IntermediateModuleDependency
import org.jetbrains.bazel.workspacemodel.entities.Library
import org.jetbrains.bazel.workspacemodel.entities.includesJavaOrScala
import org.jetbrains.bazel.workspacemodel.entities.toBuildTargetInfo
import org.jetbrains.bazel.workspacemodel.entities.toModuleCapabilities
import org.jetbrains.bsp.protocol.BuildTarget
import org.jetbrains.bsp.protocol.DependencySourcesItem
import org.jetbrains.bsp.protocol.JavacOptionsItem
import org.jetbrains.bsp.protocol.ScalacOptionsItem

internal data class BspModuleDetails(
  val target: BuildTarget,
  val dependencySources: List<DependencySourcesItem>,
  val javacOptions: JavacOptionsItem?,
  val scalacOptions: ScalacOptionsItem?,
  val type: ModuleTypeId,
  val associates: List<Label> = listOf(),
  val moduleDependencies: List<Label>,
  val libraryDependencies: List<Label>?,
)

internal class BspModuleDetailsToModuleTransformer(
  private val targetsMap: Map<Label, BuildTargetInfo>,
  private val nameProvider: TargetNameReformatProvider,
) : WorkspaceModelEntityTransformer<BspModuleDetails, GenericModuleInfo> {
  override fun transform(inputEntity: BspModuleDetails): GenericModuleInfo =
    GenericModuleInfo(
      name = nameProvider(inputEntity.target.toBuildTargetInfo()),
      type = inputEntity.type,
      modulesDependencies =
        inputEntity.moduleDependencies
          .mapNotNull { targetsMap[it] }
          .map { IntermediateModuleDependency(moduleName = nameProvider(it)) },
      librariesDependencies = calculateLibrariesDependencies(inputEntity),
      capabilities = inputEntity.target.capabilities.toModuleCapabilities(),
      languageIds = inputEntity.target.languageIds,
      associates =
        inputEntity.associates.mapNotNull {
          targetsMap[it]?.toModuleDependency(nameProvider)
        },
    )

  private fun calculateLibrariesDependencies(inputEntity: BspModuleDetails): List<IntermediateLibraryDependency> =
    inputEntity.libraryDependencies?.map { it.toLibraryDependency(nameProvider, true) }
      ?: if (inputEntity.target.languageIds.includesJavaOrScala()) {
        DependencySourcesItemToLibraryDependencyTransformer
          .transform(
            inputEntity.dependencySources.map {
              DependencySourcesAndJvmClassPaths(it, inputEntity.toJvmClassPaths())
            },
          )
      } else {
        emptyList()
      }

  private fun BspModuleDetails.toJvmClassPaths() =
    (this.javacOptions?.classpath.orEmpty() + this.scalacOptions?.classpath.orEmpty()).distinct()
}

internal object DependencySourcesItemToLibraryDependencyTransformer :
  WorkspaceModelEntityPartitionTransformer<DependencySourcesAndJvmClassPaths, IntermediateLibraryDependency> {
  override fun transform(inputEntity: DependencySourcesAndJvmClassPaths): List<IntermediateLibraryDependency> =
    DependencySourcesItemToLibraryTransformer
      .transform(inputEntity)
      .map { toLibraryDependency(it) }

  private fun toLibraryDependency(library: Library): IntermediateLibraryDependency =
    IntermediateLibraryDependency(
      libraryName = library.displayName,
      isProjectLevelLibrary = false,
    )
}

internal class BuildTargetToModuleDependencyTransformer(
  private val allTargetsIds: Set<Label>,
  private val targetsMap: Map<Label, BuildTargetInfo>,
  private val moduleNameProvider: TargetNameReformatProvider,
) : WorkspaceModelEntityPartitionTransformer<BuildTarget, IntermediateModuleDependency> {
  override fun transform(inputEntity: BuildTarget): List<IntermediateModuleDependency> =
    inputEntity
      .dependencies
      .filter { allTargetsIds.contains(it) }
      .mapNotNull { targetsMap[it]?.toModuleDependency(moduleNameProvider) }
}

internal fun BuildTargetInfo.toModuleDependency(moduleNameProvider: TargetNameReformatProvider): IntermediateModuleDependency =
  IntermediateModuleDependency(
    moduleName = moduleNameProvider(this),
  )

internal fun Label.toLibraryDependency(
  nameProvider: TargetNameReformatProvider,
  isProjectLevelLibrary: Boolean,
): IntermediateLibraryDependency =
  IntermediateLibraryDependency(
    libraryName = nameProvider(BuildTargetInfo(id = this)),
    isProjectLevelLibrary = isProjectLevelLibrary,
  )
