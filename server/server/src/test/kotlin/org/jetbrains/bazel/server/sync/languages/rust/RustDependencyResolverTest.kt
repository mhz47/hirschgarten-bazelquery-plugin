package org.jetbrains.bazel.server.sync.languages.rust

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.jetbrains.bazel.bazelrunner.utils.BazelInfo
import org.jetbrains.bazel.bazelrunner.utils.BazelRelease
import org.jetbrains.bazel.bazelrunner.utils.orLatestSupported
import org.jetbrains.bazel.label.Label
import org.jetbrains.bazel.server.model.Module
import org.jetbrains.bazel.server.paths.BazelPathsResolver
import org.jetbrains.bsp.protocol.RustPackage
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.nio.file.Paths
import kotlin.io.path.Path

@Disabled
class RustDependencyResolverTest {
  private val outputBase = "/private/var/tmp/_bazel/125c7a6ca879ed16a4b4b1a74bc5f27b"
  private val execRoot = "$outputBase/execroot/bazel_bsp"
  private lateinit var rustPackageResolver: RustPackageResolver
  private lateinit var resolver: RustDependencyResolver

  @BeforeEach
  fun beforeEach() {
    // given
    val bazelInfo =
      BazelInfo(
        execRoot = execRoot,
        outputBase = Paths.get(outputBase),
        workspaceRoot = Paths.get("/Users/user/workspace/bazel-bsp"),
        bazelBin = Path("bazel-bin"),
        release = BazelRelease.fromReleaseString("release 6.0.0").orLatestSupported(),
        false,
        true,
        emptyList<String>(),
      )

    rustPackageResolver = RustPackageResolver(BazelPathsResolver(bazelInfo))
    resolver = RustDependencyResolver()
  }

  @Test
  fun `should return empty dependency list for empty package list`() {
    // given
    val modules = emptyList<Module>()
    val packages = emptyList<RustPackage>()

    // when
    val (dependencies, rawDependencies) = resolver.rustDependencies(packages, modules)

    // then
    dependencies shouldBe emptyMap()

    rawDependencies shouldBe emptyMap()
  }

  @Test
  fun `should return empty dependencies for a single local target`() {
    // given
    val (_, module) = getModuleWithoutDependencies()
    val modules = listOf(module)
    val packages = rustPackageResolver.rustPackages(modules)

    // when
    val (dependencies, rawDependencies) = resolver.rustDependencies(packages, modules)

    // then
    dependencies shouldBe emptyMap()

    rawDependencies shouldBe emptyMap()
  }

  @Test
  fun `should return proper dependency list for a target with one dependency`() {
    // given
    //   B
    //   |
    //   A

    val modules = getModulesWithDependency()
    val packages = rustPackageResolver.rustPackages(modules)

    // when
    val (dependencies, rawDependencies) = resolver.rustDependencies(packages, modules)

    // then
    dependencies.size shouldBe 1

    val dependency = dependencies.entries.first()
    dependency.key shouldBe modules[1].label.packagePath.toString()
    dependency.value[0].pkg shouldBe modules[0].label.packagePath.toString()
    dependency.value[0].name shouldBe modules[0].label.targetName

    rawDependencies.size shouldBe 1

    val rawDependency = rawDependencies.entries.first()
    rawDependency.value[0].name shouldBe modules[0].label.toString()
    rawDependency.key shouldBe modules[1].label.packagePath.toString()
  }

  @Test
  fun `should return proper dependency graph for multiple targets with multiple dependencies`() {
    // given
    // B    A
    // | \ / \
    // C  D   E
    // \ /  \ | \
    //  F     G  H

    val (modules, _) = getSampleModules()
    val packages = rustPackageResolver.rustPackages(modules)

    // when
    val (dependencies, rawDependencies) = resolver.rustDependencies(packages, modules)

    // then
    dependencies.keys.toSet() shouldContainExactlyInAnyOrder
      listOf("A", "B", "C", "D", "E").map { "pkg$it" }

    val dependenciesNames =
      dependencies
        .mapValues { (_, deps) -> deps.map { it.name } }
    val trueDependenciesNames =
      mapOf(
        "A" to listOf("D", "E"),
        "B" to listOf("C", "D"),
        "C" to listOf("F"),
        "D" to listOf("F", "G"),
        "E" to listOf("G", "H"),
      ).mapKeys { (name, _) -> "pkg$name" }
    dependenciesNames shouldContainExactly trueDependenciesNames

    rawDependencies.keys.toSet() shouldContainExactlyInAnyOrder
      listOf("A", "B", "C", "D", "E").map { "pkg$it" }

    val rawDependenciesNames =
      rawDependencies
        .mapValues { (_, deps) -> deps.map { Label.parse(it.name).targetName } }
    val trueRawDependenciesNames =
      trueDependenciesNames
        .mapValues { (_, names) -> names }
    rawDependenciesNames shouldContainExactly trueRawDependenciesNames
  }
}
