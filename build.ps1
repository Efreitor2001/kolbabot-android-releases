# Keep UTF-8 with BOM so Windows PowerShell 5.1 reads Russian messages correctly.
param([switch]$Check, [switch]$DeviceTests, [switch]$Release)
$ErrorActionPreference = 'Stop'
$previousJava = $env:JAVA_HOME
$previousTools = $env:JAVA_TOOL_OPTIONS
try {
    if (-not $env:JAVA_HOME) {
        $requiredJavaVersion = '21'
        $daemonJvmFile = Join-Path $PSScriptRoot 'gradle\gradle-daemon-jvm.properties'
        if (Test-Path -LiteralPath $daemonJvmFile) {
            $versionSetting = Select-String -LiteralPath $daemonJvmFile -Pattern '^toolchainVersion\s*=\s*(\d+)\s*$' | Select-Object -First 1
            if ($versionSetting) { $requiredJavaVersion = $versionSetting.Matches[0].Groups[1].Value }
        }
        # Android Studio can be installed side by side (for example, Android Studio1).
        # Prefer its installed JDK matching Gradle's daemon criteria over a download.
        $bundledJavas = @(Get-ChildItem -Path (Join-Path $env:ProgramFiles 'Android\Android Studio*') -Directory -ErrorAction SilentlyContinue |
            ForEach-Object { Join-Path $_.FullName 'jbr' } |
            Where-Object { Test-Path -LiteralPath (Join-Path $_ 'bin\java.exe') })
        $bundledJava = $bundledJavas | Where-Object {
            $releaseFile = Join-Path $_ 'release'
            (Test-Path -LiteralPath $releaseFile) -and
                (Select-String -LiteralPath $releaseFile -Pattern ('^JAVA_VERSION="' + $requiredJavaVersion + '(\.|"|-)') -Quiet)
        } | Select-Object -First 1
        if (-not $bundledJava) { $bundledJava = $bundledJavas | Select-Object -First 1 }
        if (-not $bundledJava) { throw "Установите Android Studio или задайте JAVA_HOME на JDK $requiredJavaVersion." }
        $env:JAVA_HOME = $bundledJava
    }
    # Some Windows network filters break AF_UNIX connect in JBR. A deliberately
    # missing directory makes the JDK select its supported TCP loopback fallback.
    # This affects this build process only; it does not alter network settings.
    $unavailableSocketDir = Join-Path $env:TEMP ('kolbabot-no-unix-' + [guid]::NewGuid().ToString('N'))
    $env:JAVA_TOOL_OPTIONS = ($previousTools + ' -Djdk.net.unixdomain.tmpdir="' + $unavailableSocketDir + '"').Trim()
    Push-Location $PSScriptRoot
    try {
        if (-not (Test-Path -LiteralPath 'local.properties')) {
            $sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
            if (-not (Test-Path -LiteralPath $sdk)) { throw 'Установите Android SDK через Android Studio.' }
            $escapedSdk = $sdk.Replace('\', '/').Replace(':', '\:')
            [IO.File]::WriteAllText((Join-Path $PSScriptRoot 'local.properties'), 'sdk.dir=' + $escapedSdk)
        }
        if ($Release) {
            $homeSigningPath = Join-Path $env:USERPROFILE '.config\KolbaBot\signing\signing.properties'
            $legacySigningPath = Join-Path $env:LOCALAPPDATA 'KolbaBot\signing\signing.properties'
            $signingPath = if ($env:KOLBABOT_SIGNING_PROPERTIES) { $env:KOLBABOT_SIGNING_PROPERTIES }
                elseif (Test-Path -LiteralPath $homeSigningPath -PathType Leaf) { $homeSigningPath }
                elseif (Test-Path -LiteralPath $legacySigningPath -PathType Leaf) { $legacySigningPath }
                else { $homeSigningPath }
            if (-not (Test-Path -LiteralPath $signingPath -PathType Leaf)) {
                throw 'Создайте внешний signing.properties для ключа Колбабота. См. RELEASE.md; пароли не передавайте в аргументах команды.'
            }
            $tasks = @(':app:assembleGoogleRelease', ':app:bundleGoogleRelease', ':app:assembleRustoreRelease', ':app:assembleHuaweiRelease',
                ':app:lintGoogleRelease', ':app:lintRustoreRelease', ':app:lintHuaweiRelease')
        } else {
            $tasks = @(':app:assembleGoogleDebug')
        }
        if ($Check) { $tasks += ':app:testGoogleDebugUnitTest', ':app:lintGoogleDebug' }
        if ($DeviceTests) { $tasks += ':app:connectedGoogleDebugAndroidTest' }
        & .\gradlew.bat @tasks --no-daemon --console=plain
        if ($LASTEXITCODE -ne 0) { throw "Сборка завершилась с кодом $LASTEXITCODE" }
    } finally { Pop-Location }
} finally {
    $env:JAVA_HOME = $previousJava
    $env:JAVA_TOOL_OPTIONS = $previousTools
}
