load("@//rules/testing:intellij.bzl", "kt_intellij_junit4_test")

kt_intellij_junit4_test(
    name = "StarlarkEnterBetweenBracesTest",
    src = "StarlarkEnterBetweenBracesTest.kt",
    deps = ["//plugin-bazel"],
)

kt_intellij_junit4_test(
    name = "StarlarkLineIndentProviderTest",
    src = "StarlarkLineIndentProviderTest.kt",
    data = ["//plugin-bazel/src/test/testData/starlark/indentation"],
    deps = [
        "//plugin-bazel",
        "//plugin-bazel/src/test/kotlin/org/jetbrains/bazel/languages/starlark/fixtures",
    ],
)
