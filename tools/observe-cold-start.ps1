[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Serial,

    [Parameter(Mandatory = $true)]
    [string]$DeviceLabel,

    [ValidateRange(3, 21)]
    [int]$Runs = 5,

    [switch]$AllowInstall
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if (-not $AllowInstall) {
    throw "This observation replaces the selected device's plugin APK. Re-run with -AllowInstall on a dedicated validation device."
}
if (($Runs % 2) -eq 0) {
    throw "-Runs must be odd so the report has an observed median sample."
}
if ([string]::IsNullOrWhiteSpace($DeviceLabel)) {
    throw "-DeviceLabel must be a stable, non-empty comparison identity."
}

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$gradle = Join-Path $repositoryRoot "gradlew.bat"
$testClass =
    "io.github.supermonster003.autojs6.plugin.typescript." +
    "R2ProjectCompilerInstrumentationTest#" +
    "packagedStandardLibrariesCrossTheFormerSingleMessageLimit"
$testPackage = "io.github.supermonster003.autojs6.plugin.typescript.test"
$runner = "$testPackage/androidx.test.runner.AndroidJUnitRunner"
$logTag = "R2P3bCapacity"

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    throw "adb is not available on PATH."
}
if (-not (Test-Path -LiteralPath $gradle -PathType Leaf)) {
    throw "Gradle wrapper is missing: $gradle"
}
if ((& adb -s $Serial get-state 2>$null) -ne "device") {
    throw "ADB device is not online: $Serial"
}

Push-Location $repositoryRoot
try {
    & $gradle :app:assembleDebug :app:assembleDebugAndroidTest --offline --console=plain
    if ($LASTEXITCODE -ne 0) {
        throw "Cold-start observation APK build failed."
    }
}
finally {
    Pop-Location
}

$debugDirectory = Join-Path $repositoryRoot "app/build/outputs/apk/debug"
$pluginApks = @(
    Get-ChildItem -LiteralPath $debugDirectory -File -Filter "*.apk"
)
if ($pluginApks.Count -ne 1) {
    throw "Expected exactly one debug plugin APK in $debugDirectory; found $($pluginApks.Count)."
}
$testApk = Join-Path $repositoryRoot "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
if (-not (Test-Path -LiteralPath $testApk -PathType Leaf)) {
    throw "AndroidTest APK is missing: $testApk"
}

function Invoke-AdbChecked {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments,

        [Parameter(Mandatory = $true)]
        [string]$Failure
    )

    $output = @(& adb -s $Serial @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "$Failure`n$($output -join [Environment]::NewLine)"
    }
    return $output
}

function Get-ObservedMedian {
    param(
        [Parameter(Mandatory = $true)]
        [int[]]$Values
    )

    if ($Values.Count -eq 0 -or ($Values.Count % 2) -eq 0) {
        throw "Median input must contain an odd, non-empty number of values."
    }
    $sorted = @($Values | Sort-Object)
    return [int]($sorted[[Math]::Floor($sorted.Count / 2)])
}

$api = [int]((& adb -s $Serial shell getprop ro.build.version.sdk).Trim())
$model = (& adb -s $Serial shell getprop ro.product.model).Trim()
$abi = (& adb -s $Serial shell getprop ro.product.cpu.abi).Trim()
$fingerprint = (& adb -s $Serial shell getprop ro.build.fingerprint).Trim()
$testInstalled = $false
$instrumentationRuns = [System.Collections.Generic.List[string]]::new()

try {
    Invoke-AdbChecked `
        -Arguments @("install", "-r", "-t", $pluginApks[0].FullName) `
        -Failure "Debug plugin installation failed." | Out-Null
    Invoke-AdbChecked `
        -Arguments @("install", "-r", "-t", $testApk) `
        -Failure "AndroidTest helper installation failed." | Out-Null
    $testInstalled = $true

    Invoke-AdbChecked -Arguments @("logcat", "-c") -Failure "logcat clear failed." |
        Out-Null
    foreach ($run in 1..$Runs) {
        $output = Invoke-AdbChecked `
            -Arguments @(
                "shell",
                "am",
                "instrument",
                "-w",
                "-r",
                "-e",
                "class",
                $testClass,
                $runner
            ) `
            -Failure "Instrumentation run $run failed."
        $encoded = $output -join [Environment]::NewLine
        if ($encoded -notmatch "OK \(1 test\)") {
            throw "Instrumentation run $run did not complete exactly one test.`n$encoded"
        }
        $instrumentationRuns.Add("===== run $run =====`n$encoded")
    }

    $logcat = Invoke-AdbChecked `
        -Arguments @("logcat", "-d", "-v", "threadtime", "-s", "$logTag`:I", "*:S") `
        -Failure "Filtered cold-start logcat capture failed."
    $performanceLines = @(
        $logcat | Where-Object { $_ -like "*typescript-cold-start-perf schema=1*" }
    )
    if ($performanceLines.Count -ne $Runs) {
        throw "Expected $Runs cold-start records; found $($performanceLines.Count)."
    }

    $samples = foreach ($line in $performanceLines) {
        $fields = @{}
        foreach ($match in [regex]::Matches($line, "(?<key>[A-Za-z][A-Za-z0-9]*)=(?<value>[^\s]+)")) {
            $fields[$match.Groups["key"].Value] = $match.Groups["value"].Value
        }
        foreach ($required in @(
            "schema",
            "api",
            "webView",
            "batchFrames",
            "batchPayloadChunks",
            "frames",
            "envelopes",
            "maxEnvelopeBytes",
            "compilerMs",
            "ingressMs",
            "parseMs",
            "semanticMs",
            "emitMs",
            "gate"
        )) {
            if (-not $fields.ContainsKey($required)) {
                throw "Cold-start record is missing $required`: $line"
            }
        }
        if ($fields["schema"] -ne "1" -or $fields["gate"] -ne "none") {
            throw "Cold-start observation identity drifted: $line"
        }
        if (
            [int]$fields["api"] -ne $api -or
            [int]$fields["batchFrames"] -ne 6 -or
            [int]$fields["batchPayloadChunks"] -ne 4 -or
            [int]$fields["frames"] -ne 379 -or
            [int]$fields["envelopes"] -ne 68 -or
            [int]$fields["maxEnvelopeBytes"] -ne 350433
        ) {
            throw "Cold-start fixture or 6/4 transport identity drifted: $line"
        }
        [pscustomobject][ordered]@{
            compilerMs = [int]$fields["compilerMs"]
            ingressMs = [int]$fields["ingressMs"]
            parseMs = [int]$fields["parseMs"]
            semanticMs = [int]$fields["semanticMs"]
            emitMs = [int]$fields["emitMs"]
        }
    }

    $firstFields = @{}
    foreach ($match in [regex]::Matches(
        $performanceLines[0],
        "(?<key>[A-Za-z][A-Za-z0-9]*)=(?<value>[^\s]+)"
    )) {
        $firstFields[$match.Groups["key"].Value] = $match.Groups["value"].Value
    }
    $compilerValues = [int[]]@($samples | ForEach-Object { $_.compilerMs })
    $ingressValues = [int[]]@($samples | ForEach-Object { $_.ingressMs })
    $observedAt = [DateTime]::UtcNow.ToString("yyyy-MM-ddTHH:mm:ssZ")
    $safeLabel = $DeviceLabel -replace "[^A-Za-z0-9._-]", "-"
    $stamp = [DateTime]::UtcNow.ToString("yyyyMMddTHHmmssZ")
    $reportDirectory = Join-Path $repositoryRoot "build/reports/cold-start"
    New-Item -ItemType Directory -Path $reportDirectory -Force | Out-Null
    $reportFile = Join-Path $reportDirectory "$stamp-$safeLabel.json"
    $instrumentationFile = Join-Path $reportDirectory "$stamp-$safeLabel-instrumentation.txt"
    $logcatFile = Join-Path $reportDirectory "$stamp-$safeLabel-logcat.txt"

    $report = [pscustomobject][ordered]@{
        schemaVersion = 1
        observedAt = $observedAt
        gate = "none"
        device = [pscustomobject][ordered]@{
            serial = $Serial
            label = $DeviceLabel
            api = $api
            model = $model
            abi = $abi
            buildFingerprint = $fingerprint
        }
        webViewVersion = $firstFields["webView"]
        fixture = [pscustomobject][ordered]@{
            testClass = $testClass.Split("#")[0]
            testMethod = $testClass.Split("#")[1]
            profile = "rhino"
            sourceFiles = 109
            logicalFrames = [int]$firstFields["frames"]
        }
        transport = [pscustomobject][ordered]@{
            maximumMiddleFramesPerEnvelope = [int]$firstFields["batchFrames"]
            maximumPayloadChunksPerEnvelope = [int]$firstFields["batchPayloadChunks"]
            envelopes = [int]$firstFields["envelopes"]
            maximumEncodedEnvelopeBytes = [int]$firstFields["maxEnvelopeBytes"]
        }
        measurements = [pscustomobject][ordered]@{
            sampleCount = $Runs
            compilerMedianMs = Get-ObservedMedian -Values $compilerValues
            ingressMedianMs = Get-ObservedMedian -Values $ingressValues
            samples = @($samples)
        }
        provenance = [pscustomobject][ordered]@{
            command = "tools/observe-cold-start.ps1"
            rawInstrumentation = Split-Path -Leaf $instrumentationFile
            rawLogcat = Split-Path -Leaf $logcatFile
        }
    }

    $instrumentationRuns | Set-Content -LiteralPath $instrumentationFile -Encoding utf8
    $logcat | Set-Content -LiteralPath $logcatFile -Encoding utf8
    $report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $reportFile -Encoding utf8
    Write-Output "Cold-start observation written to $reportFile"
    Write-Output (
        "compiler median={0} ms; ingress median={1} ms; envelopes={2}; gate=none" -f
            $report.measurements.compilerMedianMs,
            $report.measurements.ingressMedianMs,
            $report.transport.envelopes
    )
}
finally {
    if ($testInstalled) {
        & adb -s $Serial uninstall $testPackage | Out-Null
    }
}
