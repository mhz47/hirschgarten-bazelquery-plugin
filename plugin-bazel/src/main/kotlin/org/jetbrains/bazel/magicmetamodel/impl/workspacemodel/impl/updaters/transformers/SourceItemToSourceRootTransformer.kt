package org.jetbrains.bazel.magicmetamodel.impl.workspacemodel.impl.updaters.transformers

import ch.epfl.scala.bsp4j.SourceItem
import ch.epfl.scala.bsp4j.SourceItemKind
import org.jetbrains.bazel.utils.safeCastToURI
import org.jetbrains.bazel.workspacemodel.entities.WorkspaceModelEntity
import org.jetbrains.bsp.protocol.EnhancedSourceItem
import org.jetbrains.bsp.protocol.EnhancedSourceItemData
import java.nio.file.Path
import kotlin.io.path.toPath

public data class SourceRoot(
  public val sourcePath: Path,
  public val generated: Boolean,
  public val isFile: Boolean,
  public val additionalData: EnhancedSourceItemData? = null,
) : WorkspaceModelEntity()

internal object SourceItemToSourceRootTransformer :
  WorkspaceModelEntityTransformer<SourceItem, SourceRoot> {
  override fun transform(inputEntity: SourceItem): SourceRoot {
    val sourcePath = inputEntity.uri.safeCastToURI().toPath()

    return SourceRoot(
      sourcePath = sourcePath,
      generated = inputEntity.generated,
      isFile = inputEntity.kind == SourceItemKind.FILE,
      additionalData = (inputEntity as? EnhancedSourceItem)?.data,
    )
  }
}
