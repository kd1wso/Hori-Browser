#!/usr/bin/env pwsh
# Builds hori-core for Android and regenerates the UniFFI Kotlin bindings.
#
# Prerequisites: rustup targets aarch64-linux-android + x86_64-linux-android,
# cargo-ndk, Android NDK (any recent one under $env:ANDROID_HOME\ndk).
#
# Usage:  pwsh tools/build-core.ps1 [-Release]

param(
    [switch]$Release
)

$ErrorActionPreference = "Continue"
# cargo writes progress to stderr; in Windows PowerShell 5.1 that would raise
# NativeCommandError. Rely on explicit $LASTEXITCODE checks instead.
$PSNativeCommandUseErrorActionPreference = $false
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$profile = if ($Release) { "release" } else { "debug" }
$profileArgs = if ($Release) { @("--release") } else { @() }

$jniLibs = Join-Path $root "android/app/src/main/jniLibs"
$bindingsDir = Join-Path $root "android/app/src/main/kotlin"

# 1. Cross-compile the cdylib for both ABIs straight into jniLibs.
Write-Host "==> cargo ndk build ($profile)"
cargo ndk -t arm64-v8a -t x86_64 -o $jniLibs build -p hori-core @profileArgs
if ($LASTEXITCODE -ne 0) { throw "cargo ndk build failed" }

# 2. Build the host cdylib so library-mode bindgen can read the metadata.
Write-Host "==> host build for bindgen ($profile)"
cargo build -p hori-core @profileArgs
if ($LASTEXITCODE -ne 0) { throw "host build failed" }

$hostLib = Join-Path $root "target/$profile/hori_core.dll"
if (-not (Test-Path $hostLib)) { throw "host library not found: $hostLib" }

# 3. Generate Kotlin bindings from the compiled library.
Write-Host "==> uniffi-bindgen generate (kotlin)"
cargo run -p hori-core --bin uniffi-bindgen @profileArgs -- generate `
    --library $hostLib --language kotlin --out-dir $bindingsDir
if ($LASTEXITCODE -ne 0) { throw "bindgen failed" }

Write-Host "==> done. jniLibs: $jniLibs ; bindings: $bindingsDir"