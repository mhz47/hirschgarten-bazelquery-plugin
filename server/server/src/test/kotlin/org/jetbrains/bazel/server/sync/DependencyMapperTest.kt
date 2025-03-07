package org.jetbrains.bazel.server.sync

import io.kotest.matchers.shouldBe
import org.jetbrains.bazel.bazelrunner.utils.BazelRelease
import org.jetbrains.bazel.label.Label
import org.jetbrains.bazel.server.model.AspectSyncProject
import org.jetbrains.bazel.server.model.Library
import org.jetbrains.bazel.server.model.Module
import org.jetbrains.bazel.server.model.SourceSet
import org.jetbrains.bsp.protocol.MavenDependencyModule
import org.jetbrains.bsp.protocol.MavenDependencyModuleArtifact
import org.junit.jupiter.api.Test
import java.net.URI
import java.nio.file.Paths

class DependencyMapperTest {
  private val cacheLocation = "file:///home/user/.cache/bazel/_bazel_user/ae7b7b315151086e31e3b97f9ddba009/execroot/monorepo/bazel-out/k8-fastbuild-ST-4a519fd6d3e4"

  private val mavenCoordinatesResolver = MavenCoordinatesResolver()

  @Test
  fun `should translate dependency`() {
    val jarUri =
      URI.create(
        "$cacheLocation/bin/external/maven/org/scala-lang/scala-library/2.13.11/processed_scala-library-2.13.11.jar",
      )
    val jarSourcesUri =
      URI.create(
        "$cacheLocation/bin/external/maven/org/scala-lang/scala-library/2.13.11/scala-library-2.13.11-sources.jar",
      )
    val lib1 =
      createLibraryWithMavenCoordinates(
        Label.parse("@maven//:org_scala_lang_scala_library"),
        setOf(jarUri),
        setOf(jarSourcesUri),
        emptyList(),
      )
    val expectedMavenArtifact = MavenDependencyModuleArtifact(jarUri.toString())
    val expectedMavenSourcesArtifact = MavenDependencyModuleArtifact(jarSourcesUri.toString(), classifier = "sources")
    val expectedDependency =
      MavenDependencyModule(
        "org.scala-lang",
        "scala-library",
        "2.13.11",
        listOf(
          expectedMavenArtifact,
          expectedMavenSourcesArtifact,
        ),
      )
    val dependency = DependencyMapper.extractMavenDependencyInfo(lib1)

    dependency shouldBe expectedDependency
  }

  @Test
  fun `should bazelmod translate dependency`() {
    val jarUri =
      URI.create(
        "$cacheLocation/bin/external/rules_jvm_external~~maven~name/v1/https/repo1.maven.org/maven2/com/google/auto/service/auto-service-annotations/1.1.1/header_auto-service-annotations-1.1.1.jar",
      )
    val jarSourcesUri =
      URI.create(
        "$cacheLocation/bin/external/rules_jvm_external~~maven~name/v1/https/repo1.maven.org/maven2/com/google/auto/service/auto-service-annotations/1.1.1/header_auto-service-annotations-1.1.1-sources.jar",
      )
    val lib1 =
      createLibraryWithMavenCoordinates(
        Label.parse("@@rules_jvm_external~override~maven~maven//:com_google_auto_service_auto_service_annotations"),
        setOf(jarUri),
        setOf(jarSourcesUri),
        emptyList(),
      )
    val expectedMavenArtifact = MavenDependencyModuleArtifact(jarUri.toString())
    val expectedMavenSourcesArtifact = MavenDependencyModuleArtifact(jarSourcesUri.toString(), classifier = "sources")
    val expectedDependency =
      MavenDependencyModule(
        "com.google.auto.service",
        "auto-service-annotations",
        "1.1.1",
        listOf(
          expectedMavenArtifact,
          expectedMavenSourcesArtifact,
        ),
      )
    val dependency = DependencyMapper.extractMavenDependencyInfo(lib1)

    dependency shouldBe expectedDependency
  }

  @Test
  fun `should not translate non maven dependency`() {
    val lib1 =
      Library(
        Label.parse("@//projects/v1:scheduler"),
        emptySet(),
        emptySet(),
        emptyList(),
      )
    val dependency = DependencyMapper.extractMavenDependencyInfo(lib1)

    dependency shouldBe null
  }

  @Test
  fun `should gather deps transitively`() {
    val jarUri =
      URI.create(
        "$cacheLocation/bin/external/maven/org/scala-lang/scala-library/2.13.11/processed_scala-library-2.13.11.jar",
      )
    val jarSourcesUri =
      URI.create(
        "$cacheLocation/bin/external/maven/org/scala-lang/scala-library/2.13.11/scala-library-2.13.11-sources.jar",
      )
    val lib1 =
      createLibraryWithMavenCoordinates(
        Label.parse("@maven//:org_scala_lang_scala_library"),
        setOf(jarUri),
        setOf(jarSourcesUri),
        emptyList(),
      )
    val lib2 =
      Library(
        Label.parse("@maven//:org_scala_lang_scala_library2"),
        emptySet(),
        emptySet(),
        listOf(lib1.label),
      )
    val lib3 =
      Library(
        Label.parse("@maven//:org_scala_lang_scala_library3"),
        emptySet(),
        emptySet(),
        listOf(lib1.label, lib2.label),
      )
    val lib4 =
      Library(
        Label.parse("@maven//:org_scala_lang_scala_library4"),
        emptySet(),
        emptySet(),
        listOf(lib3.label, lib2.label),
      )
    val libraries = mapOf(lib1.label to lib1, lib2.label to lib2, lib3.label to lib3, lib4.label to lib4)
    val currentUri = Paths.get(".").toUri()
    val project =
      AspectSyncProject(
        workspaceRoot = currentUri,
        bazelRelease = BazelRelease(6),
        modules = emptyList(),
        libraries = libraries,
        goLibraries = emptyMap(),
        invalidTargets = emptyList(),
        nonModuleTargets = emptyList(),
      )
    val module =
      Module(
        Label.parse(""),
        true,
        listOf(lib4.label),
        emptySet(),
        emptySet(),
        currentUri,
        SourceSet(emptySet(), emptySet(), emptySet()),
        emptySet(),
        emptySet(),
        emptySet(),
        null,
        emptyMap(),
      )
    val foundLibraries = DependencyMapper.allModuleDependencies(project, module)

    foundLibraries shouldBe setOf(lib1, lib2, lib3, lib4)
  }

  @Test
  fun `should gather deps transitively quickly`() {
    // Make sure we can traverse a large, dense dependency list quickly.
    // Large enough to time out if using a non-optimized traversal algorithm.
    val libCount = 10000
    val allLibraries = mutableListOf<Library>()
    for (i in 0..libCount - 1) {
      val deps = mutableListOf<Label>()
      for (j in 0..i - 1) {
        deps.add(allLibraries[j].label)
      }
      allLibraries.add(
        Library(
          Label.parse("@maven//:org_scala_lang_scala_library" + i),
          emptySet(),
          emptySet(),
          deps,
        ),
      )
    }

    val jarUri =
      URI.create(
        "$cacheLocation/bin/external/maven/org/scala-lang/scala-library/2.13.11/processed_scala-library-2.13.11.jar",
      )
    val jarSourcesUri =
      URI.create(
        "$cacheLocation/bin/external/maven/org/scala-lang/scala-library/2.13.11/scala-library-2.13.11-sources.jar",
      )
    val libraries = allLibraries.associate({ it.label to it })
    val currentUri = Paths.get(".").toUri()
    val project =
      AspectSyncProject(
        workspaceRoot = currentUri,
        bazelRelease = BazelRelease(6),
        modules = emptyList(),
        libraries = libraries,
        goLibraries = emptyMap(),
        invalidTargets = emptyList(),
        nonModuleTargets = emptyList(),
      )
    val module =
      Module(
        Label.parse(""),
        true,
        listOf(allLibraries[libCount - 1].label),
        emptySet(),
        emptySet(),
        currentUri,
        SourceSet(emptySet(), emptySet(), emptySet()),
        emptySet(),
        emptySet(),
        emptySet(),
        null,
        emptyMap(),
      )
    val foundLibraries = DependencyMapper.allModuleDependencies(project, module)

    foundLibraries shouldBe allLibraries.toSet()
  }

  private fun createLibraryWithMavenCoordinates(
    label: Label,
    outputs: Set<URI>,
    sources: Set<URI>,
    dependencies: List<Label>,
    interfaceJars: Set<URI> = emptySet(),
  ): Library =
    Library(
      label = label,
      outputs = outputs,
      sources = sources,
      dependencies = dependencies,
      interfaceJars = interfaceJars,
      mavenCoordinates = mavenCoordinatesResolver.resolveMavenCoordinates(label, outputs.first()),
    )
}
