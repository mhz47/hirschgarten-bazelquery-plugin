load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library")

kt_jvm_library(
    name = "outputs",
    srcs = glob(["*.kt"]),
    visibility = ["//visibility:public"],
    deps = [
        "//protocol",
        "//server/bazelrunner/src/main/kotlin/org/jetbrains/bazel/bazelrunner/utils",
        "//server/commons",
        "//server/logger",
        "@maven//:org_jetbrains_kotlinx_kotlinx_coroutines_core_jvm",
    ],
)
