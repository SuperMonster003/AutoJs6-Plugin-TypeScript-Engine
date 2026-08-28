[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $Serial,

    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[a-z0-9][a-z0-9._-]{1,63}$')]
    [string] $DeviceLabel,

    [switch] $AllowInstall,
    [switch] $Promote,

    [string] $OutputDirectory = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$testClass = "io.github.supermonster003.autojs6.plugin.typescript.R2BinderCompilationInstrumentationTest"
$testMethod = "recordsTypicalCompilationTimingAndActivePssWithoutPerformanceThreshold"
$testPackage = "io.github.supermonster003.autojs6.plugin.typescript.test"
$runner = "$testPackage/androidx.test.runner.AndroidJUnitRunner"
$logTag = "TypeScriptPerf"

if (-not $AllowInstall) {
    throw @"
This observation installs/replaces the TypeScript plugin debug APK on the selected device.
Use a dedicated validation device, then repeat the command with -AllowInstall.
The script removes only the AndroidTest package; restore the release APK yourself afterward.
"@
}

$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$gradleWrapper = Join-Path $repositoryRoot "gradlew.bat"
$historyPath = Join-Path $repositoryRoot "docs/maintenance/memory-observations.json"

if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $resolvedOutputDirectory = Join-Path $repositoryRoot "build/reports/release-memory"
} elseif ([System.IO.Path]::IsPathRooted($OutputDirectory)) {
    $resolvedOutputDirectory = [System.IO.Path]::GetFullPath($OutputDirectory)
} else {
    $resolvedOutputDirectory =
        [System.IO.Path]::GetFullPath((Join-Path $repositoryRoot $OutputDirectory))
}

function Write-Utf8NoBom {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Path,
        [Parameter(Mandatory = $true)]
        [string] $Content
    )

    $encoding = [System.Text.UTF8Encoding]::new($false)
    [System.IO.File]::WriteAllText($Path, $Content, $encoding)
}

function Invoke-CapturedCommand {
    param(
        [Parameter(Mandatory = $true)]
        [string] $FilePath,
        [Parameter(Mandatory = $true)]
        [string[]] $Arguments,
        [Parameter(Mandatory = $true)]
        [string] $Label
    )

    Write-Host "==> $Label"
    $lines = @(& $FilePath @Arguments 2>&1 | ForEach-Object { "$_" })
    $exitCode = $LASTEXITCODE
    return [pscustomobject]@{
        ExitCode = $exitCode
        Output = $lines
        Text = $lines -join "`n"
    }
}

function Assert-CommandSucceeded {
    param(
        [Parameter(Mandatory = $true)]
        [psobject] $Result,
        [Parameter(Mandatory = $true)]
        [string] $Label
    )

    if ($Result.ExitCode -ne 0) {
        throw "$Label failed with exit code $($Result.ExitCode).`n$($Result.Text)"
    }
}

function Resolve-Adb {
    $command = Get-Command "adb" -ErrorAction SilentlyContinue
    if ($null -ne $command) {
        return $command.Source
    }

    $localProperties = Join-Path $repositoryRoot "local.properties"
    if (Test-Path -LiteralPath $localProperties) {
        $sdkLine = Get-Content -LiteralPath $localProperties |
            Where-Object { $_ -match '^sdk\.dir=' } |
            Select-Object -First 1
        if ($null -ne $sdkLine) {
            $sdkRoot = $sdkLine.Substring("sdk.dir=".Length)
            $sdkRoot = $sdkRoot.Replace('\:', ':').Replace('\\', '\')
            $candidate = Join-Path $sdkRoot "platform-tools/adb.exe"
            if (Test-Path -LiteralPath $candidate) {
                return $candidate
            }
        }
    }

    throw "adb was not found on PATH or through local.properties sdk.dir."
}

function Resolve-ApkFromMetadata {
    param(
        [Parameter(Mandatory = $true)]
        [string] $MetadataPath
    )

    if (-not (Test-Path -LiteralPath $MetadataPath)) {
        throw "Missing APK output metadata: $MetadataPath"
    }
    $metadata = Get-Content -LiteralPath $MetadataPath -Raw | ConvertFrom-Json
    $elements = @($metadata.elements)
    if ($elements.Count -ne 1) {
        throw "Expected one APK output in $MetadataPath, found $($elements.Count)."
    }
    $apk = Join-Path (Split-Path -Parent $MetadataPath) $elements[0].outputFile
    if (-not (Test-Path -LiteralPath $apk)) {
        throw "APK declared by output metadata does not exist: $apk"
    }
    return [System.IO.Path]::GetFullPath($apk)
}

function Read-VersionProperty {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Name
    )

    $line = Get-Content -LiteralPath (Join-Path $repositoryRoot "version.properties") |
        Where-Object { $_ -match "^$([regex]::Escape($Name))=" } |
        Select-Object -First 1
    if ($null -eq $line) {
        throw "Missing $Name in version.properties."
    }
    return $line.Substring($Name.Length + 1).Trim()
}

function Get-DeviceProperty {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Name
    )

    $result = Invoke-CapturedCommand `
        -FilePath $adb `
        -Arguments @("-s", $Serial, "shell", "getprop", $Name) `
        -Label "Read Android property $Name"
    Assert-CommandSucceeded -Result $result -Label "adb getprop $Name"
    return $result.Text.Trim()
}

$adb = Resolve-Adb
New-Item -ItemType Directory -Force -Path $resolvedOutputDirectory | Out-Null

$state = Invoke-CapturedCommand `
    -FilePath $adb `
    -Arguments @("-s", $Serial, "get-state") `
    -Label "Resolve device $Serial"
Assert-CommandSucceeded -Result $state -Label "adb get-state"
if ($state.Text.Trim() -ne "device") {
    throw "Device $Serial is not ready: $($state.Text.Trim())"
}

$api = [int](Get-DeviceProperty "ro.build.version.sdk")
$model = Get-DeviceProperty "ro.product.model"
$abi = Get-DeviceProperty "ro.product.cpu.abi"
$buildFingerprint = Get-DeviceProperty "ro.build.fingerprint"
if ($api -lt 24) {
    throw "Android API $api is below the supported API 24 baseline."
}

$webViewDump = Invoke-CapturedCommand `
    -FilePath $adb `
    -Arguments @("-s", $Serial, "shell", "dumpsys", "webviewupdate") `
    -Label "Resolve the active WebView provider"
Assert-CommandSucceeded -Result $webViewDump -Label "dumpsys webviewupdate"
$webViewMatch = [regex]::Match(
    $webViewDump.Text,
    'Current WebView package \(name, version\): \(([^,]+),\s*([^\)]+)\)'
)
if (-not $webViewMatch.Success) {
    throw "Could not parse the active WebView provider from dumpsys webviewupdate."
}
$webViewPackage = $webViewMatch.Groups[1].Value.Trim()
$webViewVersion = $webViewMatch.Groups[2].Value.Trim()
$webViewMajorMatch = [regex]::Match($webViewVersion, '^(\d+)')
if (-not $webViewMajorMatch.Success) {
    throw "Could not parse the WebView major version from $webViewVersion."
}
$webViewMajor = [int]$webViewMajorMatch.Groups[1].Value
if ($webViewMajor -lt 119) {
    throw "WebView $webViewVersion is below the supported major 119 observation baseline."
}

Write-Warning (
    "The next step installs/replaces the debug target package on $DeviceLabel " +
    "($model, API $api, WebView $webViewVersion)."
)

$build = Invoke-CapturedCommand `
    -FilePath $gradleWrapper `
    -Arguments @(
        ":app:assembleDebug",
        ":app:assembleDebugAndroidTest",
        "--offline",
        "--console=plain"
    ) `
    -Label "Build the debug target and AndroidTest APKs offline"
Assert-CommandSucceeded -Result $build -Label "Gradle AndroidTest build"

$targetApk = Resolve-ApkFromMetadata (
    Join-Path $repositoryRoot "app/build/outputs/apk/debug/output-metadata.json"
)
$testApk = Resolve-ApkFromMetadata (
    Join-Path $repositoryRoot "app/build/outputs/apk/androidTest/debug/output-metadata.json"
)

$installTarget = Invoke-CapturedCommand `
    -FilePath $adb `
    -Arguments @("-s", $Serial, "install", "-r", "-t", $targetApk) `
    -Label "Install the debug target APK"
Assert-CommandSucceeded -Result $installTarget -Label "Install target APK"

$installTest = Invoke-CapturedCommand `
    -FilePath $adb `
    -Arguments @("-s", $Serial, "install", "-r", "-t", $testApk) `
    -Label "Install the AndroidTest APK"
Assert-CommandSucceeded -Result $installTest -Label "Install AndroidTest APK"

$timestamp = (Get-Date).ToUniversalTime()
$recordId = "$($timestamp.ToString('yyyyMMddTHHmmssZ'))-$DeviceLabel"
$instrumentationPath = Join-Path $resolvedOutputDirectory "$recordId-instrumentation.txt"
$logcatPath = Join-Path $resolvedOutputDirectory "$recordId-logcat.txt"

try {
    $clearLogcat = Invoke-CapturedCommand `
        -FilePath $adb `
        -Arguments @("-s", $Serial, "logcat", "-c") `
        -Label "Clear logcat before the observation"
    Assert-CommandSucceeded -Result $clearLogcat -Label "Clear logcat"

    $testSpec = "$testClass#$testMethod"
    $instrumentation = Invoke-CapturedCommand `
        -FilePath $adb `
        -Arguments @(
            "-s", $Serial,
            "shell", "am", "instrument", "-w", "-r",
            "-e", "class", $testSpec,
            $runner
        ) `
        -Label "Run the fixed compiler/PSS observation"
    Write-Utf8NoBom -Path $instrumentationPath -Content ($instrumentation.Text + "`n")
    Assert-CommandSucceeded -Result $instrumentation -Label "Memory instrumentation"
    if (
        $instrumentation.Text -notmatch 'OK \(1 test\)' -or
        $instrumentation.Text -notmatch 'INSTRUMENTATION_CODE:\s*-1' -or
        $instrumentation.Text -match 'FAILURES!!!'
    ) {
        throw "The memory instrumentation did not report one successful test.`n$($instrumentation.Text)"
    }

    $logcat = Invoke-CapturedCommand `
        -FilePath $adb `
        -Arguments @("-s", $Serial, "logcat", "-d", "-v", "brief", "-s", "$logTag`:I", "*:S") `
        -Label "Capture the structured PSS observation"
    Write-Utf8NoBom -Path $logcatPath -Content ($logcat.Text + "`n")
    Assert-CommandSucceeded -Result $logcat -Label "Capture observation logcat"
} finally {
    $removeTest = Invoke-CapturedCommand `
        -FilePath $adb `
        -Arguments @("-s", $Serial, "uninstall", $testPackage) `
        -Label "Remove the AndroidTest package"
    if ($removeTest.ExitCode -ne 0) {
        Write-Warning "Could not remove $testPackage. Remove it manually before the next run."
    }
}

$observationPattern =
    'typescript-compiler-perf schema=(\d+) api=(\d+) profile=(\S+) ' +
    'sourceFiles=(\d+) inputBytes=(\d+) hostRoundTripMs=(\d+) compilerMs=(\d+) ' +
    'serviceMedianPssKiB=(\d+) rendererMedianPssKiB=(\d+) ' +
    'combinedMedianPssKiB=(\d+) activeMemoryHoldMs=(\d+) gate=(\S+)'
$observationMatches = [regex]::Matches($logcat.Text, $observationPattern)
if ($observationMatches.Count -ne 1) {
    throw "Expected exactly one structured observation, found $($observationMatches.Count). See $logcatPath"
}
$observation = $observationMatches[0]

$schema = [int]$observation.Groups[1].Value
$observedApi = [int]$observation.Groups[2].Value
$profile = $observation.Groups[3].Value
$sourceFiles = [int]$observation.Groups[4].Value
$inputBytes = [long]$observation.Groups[5].Value
$hostRoundTripMs = [long]$observation.Groups[6].Value
$compilerMs = [long]$observation.Groups[7].Value
$serviceMedianPssKiB = [long]$observation.Groups[8].Value
$rendererMedianPssKiB = [long]$observation.Groups[9].Value
$combinedMedianPssKiB = [long]$observation.Groups[10].Value
$activeMemoryHoldMs = [long]$observation.Groups[11].Value
$gate = $observation.Groups[12].Value

if (
    $schema -ne 1 -or
    $observedApi -ne $api -or
    $profile -ne "rhino" -or
    $sourceFiles -ne 8 -or
    $activeMemoryHoldMs -ne 5000 -or
    $gate -ne "none"
) {
    throw "The observation identity drifted from the frozen fixture. See $logcatPath"
}
if (
    $inputBytes -le 0 -or
    $hostRoundTripMs -le 0 -or
    $compilerMs -le 0 -or
    $serviceMedianPssKiB -le 0 -or
    $rendererMedianPssKiB -le 0 -or
    $combinedMedianPssKiB -le 0
) {
    throw "The observation contains a non-positive timing or PSS sample. See $logcatPath"
}

$fixture = [ordered]@{
    testClass = $testClass
    testMethod = $testMethod
    profile = "rhino"
    sourceFiles = 8
    activeMemoryHoldMs = 5000
    pssSampleCount = 3
    pssSampleIntervalMs = 50
    gate = "none"
}
$record = [ordered]@{
    id = $recordId
    observedAt = $timestamp.ToString("yyyy-MM-ddTHH:mm:ssZ")
    releaseVersion = Read-VersionProperty "VERSION_NAME"
    buildType = "debug"
    device = [ordered]@{
        label = $DeviceLabel
        api = $api
        model = $model
        abi = $abi
        buildFingerprint = $buildFingerprint
    }
    webView = [ordered]@{
        package = $webViewPackage
        version = $webViewVersion
    }
    measurements = [ordered]@{
        inputBytes = $inputBytes
        hostRoundTripMs = $hostRoundTripMs
        compilerMs = $compilerMs
        compilerServiceMedianPssKiB = $serviceMedianPssKiB
        rendererMedianPssKiB = $rendererMedianPssKiB
        combinedMedianPssKiB = $combinedMedianPssKiB
    }
    provenance = [ordered]@{
        kind = "live-instrumentation"
        command = "tools/observe-release-memory.ps1"
        rawInstrumentation = [System.IO.Path]::GetFileName($instrumentationPath)
        rawLogcat = [System.IO.Path]::GetFileName($logcatPath)
    }
}
$report = [ordered]@{
    schemaVersion = 1
    fixture = $fixture
    record = $record
}
$reportPath = Join-Path $resolvedOutputDirectory "$recordId.json"
Write-Utf8NoBom `
    -Path $reportPath `
    -Content (($report | ConvertTo-Json -Depth 10) + "`n")

if ($Promote) {
    if (-not (Test-Path -LiteralPath $historyPath)) {
        throw "Missing checked-in memory history: $historyPath"
    }
    $history = Get-Content -LiteralPath $historyPath -Raw | ConvertFrom-Json
    if ([int]$history.schemaVersion -ne 1) {
        throw "Unsupported memory history schema: $($history.schemaVersion)"
    }
    $historyFixture = $history.fixture | ConvertTo-Json -Depth 10 -Compress
    $reportFixture = $fixture | ConvertTo-Json -Depth 10 -Compress
    if ($historyFixture -ne $reportFixture) {
        throw "The checked-in memory fixture differs from the live observation fixture."
    }
    $existingRecords = @($history.records)
    if (@($existingRecords | Where-Object { $_.id -eq $recordId }).Count -ne 0) {
        throw "Memory history already contains record $recordId."
    }
    $updatedHistory = [ordered]@{
        schemaVersion = 1
        fixture = $fixture
        records = @($existingRecords) + @($record)
    }
    Write-Utf8NoBom `
        -Path $historyPath `
        -Content (($updatedHistory | ConvertTo-Json -Depth 10) + "`n")
    Write-Host "Promoted observation to $historyPath"
}

Write-Host "Release memory observation recorded:"
Write-Host "  report: $reportPath"
Write-Host "  device: $DeviceLabel / API $api / WebView $webViewVersion"
Write-Host "  compiler: $compilerMs ms"
Write-Host "  PSS: service $serviceMedianPssKiB KiB / renderer $rendererMedianPssKiB KiB / combined $combinedMedianPssKiB KiB"
Write-Host "  gate: none (review against the same device history)"
