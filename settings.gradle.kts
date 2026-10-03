pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
// 这里原来有 org.gradle.toolchains.foojay-resolver-convention 插件（Android Studio 模板自带），
// 本项目没有声明任何 toolchain，用不到它；而它要从 plugins.gradle.org 下载，
// 国内网络经常连不上（证书被劫持），会让整个构建在 settings 阶段就失败，所以去掉。
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "WearCalculator"
include(":app")
