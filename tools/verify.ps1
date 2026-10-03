# 嗷呜计算器 —— 本地验证脚本
#
# 用法（在项目根目录）：
#     powershell -File tools\verify.ps1
#
# 做两件事：
#   1. 不依赖 Android SDK / Gradle，用一份最小化的 Android API 桩把
#      MainActivity、FunctionBubbleView、CrownScroll、MathEngine 编译一遍，
#      抓出 Java 层面的错误；
#   2. 跑 tools/MathEngineTest.java 的全部数学用例（含所有「踩过的坑」的回归用例）。
#
# tools 目录下另外还有一套更狠的独立验证程序（不参与本脚本，需要时手动跑）：
#     New-Item -ItemType Directory -Force build\indep | Out-Null
#     javac -encoding UTF-8 -d build\indep app\src\main\java\com\nickwoluff\wearcalculator\MathEngine.java tools\IndependentTest.java tools\IndependentSweep.java
#     java "-Dfile.encoding=UTF-8" -cp build\indep com.nickwoluff.wearcalculator.IndependentTest
#     java "-Dfile.encoding=UTF-8" -cp build\indep com.nickwoluff.wearcalculator.IndependentSweep
# 它们自带一份 120 位精度的 π 和独立实现，用来交叉验证引擎的数值正确性
# （注意 PowerShell 里 -Dfile.encoding=UTF-8 一定要加引号，否则会被拆成两个参数）。
#
# 注意：脚本里的桩是按本项目实际用到的 API 手写的，只能证明「Java 代码在语法和
# 方法签名上自洽」，不能替代真机 / 模拟器运行验证。

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$build = Join-Path $root 'build'
$stubGen = Join-Path $build 'stubgen'
$stubSrc = Join-Path $build 'android-stubs'
$stubCls = Join-Path $build 'stub-classes'
$appCls = Join-Path $build 'appcheck'
$testCls = Join-Path $build 'tools'

foreach ($dir in @($stubGen, $stubCls, $appCls, $testCls)) {
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
}

$failed = 0

Write-Host '=== 1/3 生成 Android API 桩 ===' -ForegroundColor Cyan
javac -encoding UTF-8 -nowarn -d $stubGen (Join-Path $root 'tools\GenerateAndroidStubs.java')
if ($LASTEXITCODE -ne 0) { Write-Host '生成桩的代码编译失败' -ForegroundColor Red; exit 1 }
java -cp $stubGen GenerateAndroidStubs $stubSrc
if ($LASTEXITCODE -ne 0) { Write-Host '生成桩失败' -ForegroundColor Red; exit 1 }

Write-Host '=== 2/3 编译 app 的 Java 源码（对照桩） ===' -ForegroundColor Cyan
$stubFiles = Get-ChildItem $stubSrc -Recurse -Filter *.java | ForEach-Object { $_.FullName }
javac -encoding UTF-8 -nowarn -d $stubCls $stubFiles
if ($LASTEXITCODE -ne 0) { Write-Host '桩自身编译失败' -ForegroundColor Red; exit 1 }

$appSources = Get-ChildItem (Join-Path $root 'app\src\main\java\com\nickwoluff\wearcalculator') -Filter *.java |
    ForEach-Object { $_.FullName }
javac -encoding UTF-8 -nowarn -cp $stubCls -sourcepath "app\src\main\java;tools\stub" -d $appCls $appSources
if ($LASTEXITCODE -ne 0) {
    Write-Host 'app 源码编译失败（见上面的错误）' -ForegroundColor Red
    $failed++
} else {
    Write-Host "app 源码编译通过（$($appSources.Count) 个文件）" -ForegroundColor Green
}

Write-Host '=== 3/3 数学引擎用例 ===' -ForegroundColor Cyan
javac -encoding UTF-8 -nowarn -cp $stubCls -d $testCls `
    'app\src\main\java\com\nickwoluff\wearcalculator\MathEngine.java' `
    'tools\MathEngineTest.java'
if ($LASTEXITCODE -ne 0) { Write-Host '测试编译失败' -ForegroundColor Red; exit 1 }
java -cp $testCls com.nickwoluff.wearcalculator.MathEngineTest
if ($LASTEXITCODE -ne 0) { $failed++ }

Write-Host ''
if ($failed -eq 0) {
    Write-Host '全部通过 ✓' -ForegroundColor Green
    exit 0
} else {
    Write-Host "有 $failed 项未通过 ✗" -ForegroundColor Red
    exit 1
}
