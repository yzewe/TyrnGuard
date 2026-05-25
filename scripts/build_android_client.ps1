param(
    [string]$NdkPath = "$env:LOCALAPPDATA\Android\Sdk\ndk\29.0.14206865"
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$clientDir = Join-Path $root "go_client"
$outDir = Join-Path $root "build\go-client"
$jniDir = Join-Path $root "app\src\main\jniLibs"
$clangDir = Join-Path $NdkPath "toolchains\llvm\prebuilt\windows-x86_64\bin"

if (-not (Test-Path $clangDir)) {
    throw "Android NDK clang not found: $clangDir"
}

New-Item -ItemType Directory -Force `
    -Path (Join-Path $outDir "arm64-v8a"), (Join-Path $outDir "armeabi-v7a"), (Join-Path $outDir "x86_64"), `
          (Join-Path $jniDir "arm64-v8a"), (Join-Path $jniDir "armeabi-v7a"), (Join-Path $jniDir "x86_64") | Out-Null

Push-Location $clientDir
try {
    $env:GOOS = "android"
    $env:CGO_ENABLED = "1"

    $env:GOARCH = "arm64"
    Remove-Item Env:\GOARM -ErrorAction SilentlyContinue
    $env:CC = Join-Path $clangDir "aarch64-linux-android24-clang.cmd"
    go build -trimpath -ldflags="-s -w" -o (Join-Path $outDir "arm64-v8a\libclient.so") .

    $env:GOARCH = "arm"
    $env:GOARM = "7"
    $env:CC = Join-Path $clangDir "armv7a-linux-androideabi24-clang.cmd"
    go build -trimpath -ldflags="-s -w" -o (Join-Path $outDir "armeabi-v7a\libclient.so") .

    $env:GOARCH = "amd64"
    Remove-Item Env:\GOARM -ErrorAction SilentlyContinue
    $env:CC = Join-Path $clangDir "x86_64-linux-android24-clang.cmd"
    go build -trimpath -ldflags="-s -w" -o (Join-Path $outDir "x86_64\libclient.so") .
}
finally {
    Pop-Location
}

Copy-Item -Force (Join-Path $outDir "arm64-v8a\libclient.so") (Join-Path $jniDir "arm64-v8a\libclient.so")
Copy-Item -Force (Join-Path $outDir "armeabi-v7a\libclient.so") (Join-Path $jniDir "armeabi-v7a\libclient.so")
Copy-Item -Force (Join-Path $outDir "x86_64\libclient.so") (Join-Path $jniDir "x86_64\libclient.so")

Write-Host "Android Go client binaries built as executable PIE files and copied to app/src/main/jniLibs."
