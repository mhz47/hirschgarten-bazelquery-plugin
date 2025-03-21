package org.jetbrains.bazel.magicmetamodel.impl.workspacemodel.impl.updaters.transformers

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.jetbrains.bazel.label.Label
import org.jetbrains.bazel.workspacemodel.entities.IntermediateLibraryDependency
import org.jetbrains.bsp.protocol.DependencySourcesItem
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("DependencySourcesItemToLibraryDependencyTransformer.transformer(library) tests")
class DependencySourcesItemToIntermediateLibraryDependencyTransformerTest {
  @Test
  fun `should return no library dependencies for no dependency sources`() {
    // given
    val emptyDependencySourcesItems = listOf<DependencySourcesAndJvmClassPaths>()

    // when
    val librariesDependencies =
      DependencySourcesItemToLibraryDependencyTransformer.transform(emptyDependencySourcesItems)

    // then
    librariesDependencies shouldBe emptyList()
  }

  @Test
  fun `should return single library dependency for dependency sources item with one dependency`() {
    // given
    val dependencySource = "file:///dependency/test/test-1.0.0-sources.jar"

    val dependencySourceItem =
      DependencySourcesAndJvmClassPaths(
        dependencySources =
          DependencySourcesItem(
            Label.parse("//target"),
            listOf(dependencySource),
          ),
        listOf("file:///dependency/test/test-1.0.0.jar"),
      )

    // when
    val librariesDependencies = DependencySourcesItemToLibraryDependencyTransformer.transform(dependencySourceItem)

    // then
    val expectedIntermediateLibraryDependency =
      IntermediateLibraryDependency(
        libraryName = "BSP: file:///dependency/test/test-1.0.0.jar",
      )

    librariesDependencies shouldContainExactlyInAnyOrder listOf(expectedIntermediateLibraryDependency)
  }

  @Test
  fun `should return multiple libraries dependencies for dependency sources item with multiple dependencies`() {
    // given
    val dependencySource1 = "file:///dependency/test1/test1-1.0.0-sources.jar"
    val dependencySource2 = "file:///dependency/test2/test2-1.0.0-sources.jar"
    val dependencySource3 = "file:///dependency/test3/test3-1.0.0-sources.jar"

    val dependencySourceItem =
      DependencySourcesAndJvmClassPaths(
        dependencySources =
          DependencySourcesItem(
            Label.parse("//target"),
            listOf(dependencySource1, dependencySource2, dependencySource3),
          ),
        listOf(
          "file:///dependency/test1/test1-1.0.0.jar",
          "file:///dependency/test2/test2-1.0.0.jar",
          "file:///dependency/test3/test3-1.0.0.jar",
        ),
      )

    // when
    val librariesDependencies = DependencySourcesItemToLibraryDependencyTransformer.transform(dependencySourceItem)

    // then
    val expectedIntermediateLibraryDependency1 =
      IntermediateLibraryDependency(
        libraryName = "BSP: file:///dependency/test1/test1-1.0.0.jar",
      )
    val expectedIntermediateLibraryDependency2 =
      IntermediateLibraryDependency(
        libraryName = "BSP: file:///dependency/test2/test2-1.0.0.jar",
      )
    val expectedIntermediateLibraryDependency3 =
      IntermediateLibraryDependency(
        libraryName = "BSP: file:///dependency/test3/test3-1.0.0.jar",
      )

    librariesDependencies shouldContainExactlyInAnyOrder
      listOf(
        expectedIntermediateLibraryDependency1,
        expectedIntermediateLibraryDependency2,
        expectedIntermediateLibraryDependency3,
      )
  }

  @Test
  fun `should return multiple libraries dependencies for multiple dependency sources items`() {
    // given
    val dependencySource1 = "file:///dependency/test1/test1-1.0.0-sources.jar"
    val dependencySource2 = "file:///dependency/test2/test2-1.0.0-sources.jar"
    val dependencySource3 = "file:///dependency/test3/test3-1.0.0-sources.jar"

    val dependencySourceItem1 =
      DependencySourcesAndJvmClassPaths(
        dependencySources =
          DependencySourcesItem(
            Label.parse("//target"),
            listOf(dependencySource1, dependencySource2),
          ),
        listOf(
          "file:///dependency/test1/test1-1.0.0.jar",
          "file:///dependency/test2/test2-1.0.0.jar",
        ),
      )
    val dependencySourceItem2 =
      DependencySourcesAndJvmClassPaths(
        dependencySources =
          DependencySourcesItem(
            Label.parse("//target"),
            listOf(dependencySource2, dependencySource3),
          ),
        listOf(
          "file:///dependency/test2/test2-1.0.0.jar",
          "file:///dependency/test3/test3-1.0.0.jar",
        ),
      )

    val dependencySourceItems = listOf(dependencySourceItem1, dependencySourceItem2)

    // when
    val librariesDependencies = DependencySourcesItemToLibraryDependencyTransformer.transform(dependencySourceItems)

    // then
    val expectedIntermediateLibraryDependency1 =
      IntermediateLibraryDependency(
        libraryName = "BSP: file:///dependency/test1/test1-1.0.0.jar",
      )
    val expectedIntermediateLibraryDependency2 =
      IntermediateLibraryDependency(
        libraryName = "BSP: file:///dependency/test2/test2-1.0.0.jar",
      )
    val expectedIntermediateLibraryDependency3 =
      IntermediateLibraryDependency(
        libraryName = "BSP: file:///dependency/test3/test3-1.0.0.jar",
      )

    librariesDependencies shouldContainExactlyInAnyOrder
      listOf(
        expectedIntermediateLibraryDependency1,
        expectedIntermediateLibraryDependency2,
        expectedIntermediateLibraryDependency3,
      )
  }
}
