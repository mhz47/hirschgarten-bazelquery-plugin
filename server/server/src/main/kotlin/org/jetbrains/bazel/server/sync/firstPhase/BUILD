load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library")

kt_jvm_library(
    name = "firstPhase",
    srcs = glob(["*.kt"]),
    visibility = ["//visibility:public"],
    deps = [
        "//protocol",
        "//server/bazelrunner",
        "//server/executioncontext/workspacecontext/src/main/kotlin/org/jetbrains/bazel/workspacecontext/provider",
        "//server/server/src/main/kotlin/org/jetbrains/bazel/server/bsp/managers",
        "//server/server/src/main/kotlin/org/jetbrains/bazel/server/bsp/utils",
        "//server/server/src/main/kotlin/org/jetbrains/bazel/server/label",
        "//server/server/src/main/kotlin/org/jetbrains/bazel/server/sync/languages",
        "//third_party/bazel/src/main/protobuf:build_java_proto",
        "@maven//:org_jetbrains_kotlinx_kotlinx_coroutines_core",
    ],
)
