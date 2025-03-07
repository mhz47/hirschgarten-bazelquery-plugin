package org.jetbrains.bazel.server.sync.languages.rust

import org.jetbrains.bazel.label.Label
import org.jetbrains.bazel.server.model.Language
import org.jetbrains.bazel.server.model.Module
import org.jetbrains.bazel.server.model.SourceSet
import org.jetbrains.bazel.server.model.SourceWithData
import org.jetbrains.bazel.server.model.Tag
import java.net.URI

fun createModule(
  label: Label,
  directDependencies: List<Label>,
  baseDirectory: URI,
  sources: Set<SourceWithData>,
  rustModule: RustModule,
): Module =
  Module(
    label = label,
    isSynthetic = false,
    directDependencies = directDependencies,
    languages = setOf(Language.RUST),
    tags = setOf(Tag.APPLICATION),
    baseDirectory = baseDirectory,
    sourceSet =
      SourceSet(
        sources = sources,
        generatedSources = setOf(),
        sourceRoots = setOf(),
      ),
    resources = setOf(),
    outputs = setOf(),
    sourceDependencies = setOf(),
    languageData = rustModule,
    environmentVariables = mapOf(),
  )

fun createRustModule(
  crateId: String,
  crateRoot: String,
  location: RustCrateLocation = RustCrateLocation.EXEC_ROOT,
  crateFeatures: List<String> = emptyList(),
  dependencies: List<String> = emptyList(),
  procMacroArtifacts: List<String> = emptyList(),
): RustModule =
  RustModule(
    crateId = crateId,
    location = location,
    fromWorkspace = true,
    name = crateId.split("/")[0],
    kind = "bin",
    edition = "2018",
    crateFeatures = crateFeatures,
    dependenciesCrateIds = dependencies,
    crateRoot = crateRoot,
    version = "1.2.3",
    procMacroArtifacts = procMacroArtifacts,
  )

fun createTarget(moduleName: String, directDependencies: List<String>): Module {
  val pathPrefix = "file:///path/to/targets"
  val crateRoot = "$pathPrefix/dir$moduleName/src/lib.rs"

  return createModule(
    label = Label.parse("@//pkg$moduleName:$moduleName"),
    directDependencies = directDependencies.map { Label.parse(it) },
    rustModule =
      createRustModule(
        crateId = moduleName,
        crateRoot = crateRoot,
        dependencies = directDependencies,
      ),
    sources = setOf(URI.create(crateRoot).toSourceWithEmptyData()),
    baseDirectory = URI.create("$pathPrefix/dir$moduleName/"),
  )
}

fun URI.toSourceWithEmptyData() = SourceWithData(this)

fun getModuleWithoutDependencies(features: List<String> = emptyList()): Pair<RustModule, Module> {
  val rustModule =
    createRustModule(
      crateId = "sample_target/src/lib.rs",
      crateRoot = "file:///path/to/sample_target/src/lib.rs",
      crateFeatures = features,
    )
  val module =
    createModule(
      label = Label.parse("@//sample_target:sample_target"),
      directDependencies = emptyList(),
      sources =
        setOf(
          URI.create("file:///path/to/sample_target/src/lib.rs").toSourceWithEmptyData(),
        ),
      baseDirectory = URI.create("file:///path/to/sample_target/"),
      rustModule = rustModule,
    )

  return Pair(rustModule, module)
}

fun getModulesWithDependency(): List<Module> {
  //   B
  //   |
  //   A

  val rustModuleA =
    createRustModule(
      crateId = "dirA/src/lib.rs",
      crateRoot = "file:///path/to/targetA/src/lib.rs",
    )
  val moduleA =
    createModule(
      label = Label.parse("@//dirA:targetA"),
      directDependencies = emptyList(),
      sources = setOf(URI.create("file:///path/to/dirA/src/lib.rs").toSourceWithEmptyData()),
      baseDirectory = URI.create("file:///path/to/dirA/"),
      rustModule = rustModuleA,
    )

  val rustModuleB =
    createRustModule(
      crateId = "dirB/src/lib.rs",
      crateRoot = "file:///path/to/dirB/src/lib.rs",
      dependencies = listOf(rustModuleA.crateId),
    )
  val moduleB =
    createModule(
      label = Label.parse("@//dirB:targetB"),
      directDependencies = listOf(moduleA.label),
      sources = setOf(URI.create("file:///path/to/dirB/src/lib.rs").toSourceWithEmptyData()),
      baseDirectory = URI.create("file:///path/to/dirB/"),
      rustModule = rustModuleB,
    )

  return listOf(moduleA, moduleB)
}

fun getSampleModules(): Pair<List<Module>, Map<String, Module>> {
  // B    A
  // | \ / \
  // C  D   E
  // \ /  \ | \
  //  F     G  H

  val moduleA =
    createTarget(
      moduleName = "A",
      directDependencies = listOf("@//pkgD:D", "@//pkgE:E"),
    )
  val moduleB =
    createTarget(
      moduleName = "B",
      directDependencies = listOf("@//pkgC:C", "@//pkgD:D"),
    )
  val moduleC =
    createTarget(
      moduleName = "C",
      directDependencies = listOf("@//pkgF:F"),
    )
  val moduleD =
    createTarget(
      moduleName = "D",
      directDependencies = listOf("@//pkgF:F", "@//pkgG:G"),
    )
  val moduleE =
    createTarget(
      moduleName = "E",
      directDependencies = listOf("@//pkgG:G", "@//pkgH:H"),
    )
  val moduleF =
    createTarget(
      moduleName = "F",
      directDependencies = emptyList(),
    )
  val moduleG =
    createTarget(
      moduleName = "G",
      directDependencies = emptyList(),
    )
  val moduleH =
    createTarget(
      moduleName = "H",
      directDependencies = emptyList(),
    )

  val modules = listOf(moduleA, moduleB, moduleC, moduleD, moduleE, moduleF, moduleG, moduleH)
  val modulesMap =
    mapOf(
      "A" to moduleA,
      "B" to moduleB,
      "C" to moduleC,
      "D" to moduleD,
      "E" to moduleE,
      "F" to moduleF,
      "G" to moduleG,
      "H" to moduleH,
    )

  return Pair(modules, modulesMap)
}
