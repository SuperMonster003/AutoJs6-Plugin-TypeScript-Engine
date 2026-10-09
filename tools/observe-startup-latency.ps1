[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Serial,

    [Parameter(Mandatory = $true)]
    [string]$DeviceLabel,

    [ValidateRange(1, 21)]
    [int]$Runs = 5,

    [ValidateSet("S1", "S2", "S3", "all")]
    [string]$Scenario = "all",

    [string]$HostPackage = "org.autojs.autojs6",

    [string]$PluginPackage = "io.github.supermonster003.autojs6.plugin.typescript",

    [string]$DevicePath = "/sdcard/Download/autojs6-startup-latency/single-file.ts",

    [ValidateRange(5, 300)]
    [int]$RunTimeoutSeconds = 90,

    [ValidateRange(0, 120)]
    [int]$ColdProcessWaitSeconds = 20,

    [string]$OutputDirectory
)

<#
.SYNOPSIS
Collects the startup-latency-roadmap.md L0 baseline for one device: S1 (cold compiler
process, cache miss), S2 (warm process, cache miss) and S3 (cache hit).

.DESCRIPTION
The script never installs or uninstalls anything. It pushes tools/fixtures/startup-latency/
single-file.ts to the device, launches it through the host's exported RunIntentActivity, and
reads the structured logcat lines emitted by the host (`typescript-compile-perf`,
`typescript-execution-perf`, `typescript-prewarm`) and by the plugin
(`typescript-compiler-boot`, `typescript-compiler-warmup`). Every sample keeps the raw key=value
fields; medians are
computed per scenario over odd sample counts only. `gate=none`: nothing here fails on a
slow number.

S1 requires the compiler process to be absent. `am kill` only removes background processes,
so a host that still holds a prewarm binding keeps the process alive; in that case release
the binding first (idle for more than the host keepalive, or close the host) and re-run.
#>

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if (($Runs % 2) -eq 0) {
    throw "-Runs must be odd so every scenario has an observed median sample."
}
if ([string]::IsNullOrWhiteSpace($DeviceLabel)) {
    throw "-DeviceLabel must be a stable, non-empty comparison identity."
}
if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    throw "adb is not available on PATH."
}
if ((& adb -s $Serial get-state 2>$null) -ne "device") {
    throw "ADB device is not online: $Serial"
}

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$fixturePath = Join-Path $repositoryRoot "tools/fixtures/startup-latency/single-file.ts"
if (-not (Test-Path -LiteralPath $fixturePath -PathType Leaf)) {
    throw "Fixture is missing: $fixturePath"
}
if (-not $OutputDirectory) {
    $OutputDirectory = Join-Path $repositoryRoot "build/reports/startup-latency"
}
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

$logTag = "TypeScriptCompiler"
$runActivity = "$HostPackage/org.autojs.autojs.external.open.RunIntentActivity"
$compilerProcess = "$PluginPackage`:typescript_compiler"
$stampPath = $DevicePath.Substring(0, $DevicePath.LastIndexOf("/")) + "/first-statement.txt"
$scenarios = if ($Scenario -eq "all") { @("S1", "S2", "S3") } else { @($Scenario) }

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

function Get-AdbProperty {
    param([Parameter(Mandatory = $true)][string]$Name)
    return ((& adb -s $Serial shell getprop $Name) -join "").Trim()
}

function Get-PackageVersion {
    param([Parameter(Mandatory = $true)][string]$Package)
    $dump = @(& adb -s $Serial shell dumpsys package $Package 2>$null)
    $name = ($dump | Where-Object { $_ -match "versionName=" } | Select-Object -First 1)
    $code = ($dump | Where-Object { $_ -match "versionCode=" } | Select-Object -First 1)
    if (-not $name) {
        return $null
    }
    $versionName = ([regex]::Match($name, "versionName=(\S+)")).Groups[1].Value
    $versionCode = ([regex]::Match($code, "versionCode=(\d+)")).Groups[1].Value
    return [ordered]@{ versionName = $versionName; versionCode = [long]$versionCode }
}

function Get-WebViewVersion {
    $update = @(& adb -s $Serial shell dumpsys webviewupdate 2>$null)
    $line = $update | Where-Object { $_ -match "Current WebView package" } | Select-Object -First 1
    if ($line -and $line -match ":\s*\((?<package>[^,]+),\s*(?<version>[^)]+)\)\s*$") {
        return [ordered]@{ package = $Matches["package"].Trim(); version = $Matches["version"].Trim() }
    }
    return [ordered]@{ package = "unknown"; version = "unknown" }
}

function Get-CompilerProcessPid {
    $output = @(& adb -s $Serial shell pidof $compilerProcess 2>$null)
    $joined = ($output -join " ").Trim()
    if ([string]::IsNullOrWhiteSpace($joined)) {
        return $null
    }
    return $joined
}

function Wait-ForColdCompilerProcess {
    $deadline = (Get-Date).AddSeconds($ColdProcessWaitSeconds)
    while ($true) {
        $alive = Get-CompilerProcessPid
        if (-not $alive) {
            return
        }
        & adb -s $Serial shell am kill $PluginPackage 2>$null | Out-Null
        Start-Sleep -Milliseconds 500
        if ((Get-Date) -gt $deadline) {
            throw "S1 needs a dead compiler process but $compilerProcess (pid $alive) survived " +
                "am kill for $ColdProcessWaitSeconds s. The host still holds a binding: let it " +
                "idle past its keepalive or close the host, then re-run."
        }
    }
}

function Push-Fixture {
    param([Parameter(Mandatory = $true)][string]$Marker)

    $content = Get-Content -LiteralPath $fixturePath -Raw
    $pattern = 'const RUN_MARKER = "run-[0-9A-Za-z]+";'
    if ($content -notmatch $pattern) {
        throw "Fixture lost its RUN_MARKER line."
    }
    $rewritten = [regex]::Replace($content, $pattern, "const RUN_MARKER = `"$Marker`";")
    $stampPattern = 'const STAMP_PATH = "[^"]+";'
    if ($rewritten -notmatch $stampPattern) {
        throw "Fixture lost its STAMP_PATH line."
    }
    $rewritten = [regex]::Replace($rewritten, $stampPattern, "const STAMP_PATH = `"$stampPath`";")
    $temporary = Join-Path $OutputDirectory "single-file.pushed.ts"
    [System.IO.File]::WriteAllText($temporary, $rewritten, [System.Text.UTF8Encoding]::new($false))
    $deviceDirectory = $DevicePath.Substring(0, $DevicePath.LastIndexOf("/"))
    Invoke-AdbChecked -Arguments @("shell", "mkdir", "-p", $deviceDirectory) -Failure "mkdir failed." |
        Out-Null
    Invoke-AdbChecked -Arguments @("push", $temporary, $DevicePath) -Failure "Fixture push failed." |
        Out-Null
    $sha = (Get-FileHash -LiteralPath $temporary -Algorithm SHA256).Hash.ToLowerInvariant()
    return $sha
}

function Parse-KeyValues {
    param([Parameter(Mandatory = $true)][string]$Line)
    $fields = [ordered]@{}
    foreach ($match in [regex]::Matches($Line, "(?<key>[A-Za-z][A-Za-z0-9]*)=(?<value>[^\s]+)")) {
        $fields[$match.Groups["key"].Value] = $match.Groups["value"].Value
    }
    return $fields
}

function Invoke-ScriptRun {
    param(
        [Parameter(Mandatory = $true)][string]$ScenarioId,
        [Parameter(Mandatory = $true)][int]$Index,
        [Parameter(Mandatory = $true)][string]$FixtureSha256
    )

    Invoke-AdbChecked -Arguments @("logcat", "-c") -Failure "logcat clear failed." | Out-Null
    & adb -s $Serial shell rm -f $stampPath 2>$null | Out-Null
    $pidBefore = Get-CompilerProcessPid
    # Device wall clock right before the launch request; the fixture persists Date.now() of its
    # first statement so the difference is a stopwatch independent of host instrumentation.
    $deviceLaunchEpochMs = [long]((& adb -s $Serial shell date +%s%3N) -join "").Trim()
    $launchedAt = Get-Date
    Invoke-AdbChecked `
        -Arguments @("shell", "am", "start", "-W", "-n", $runActivity, "--es", "path", $DevicePath) `
        -Failure "RunIntentActivity launch failed." | Out-Null

    $deadline = $launchedAt.AddSeconds($RunTimeoutSeconds)
    $lines = @()
    $compileSeenAt = $null
    while ($true) {
        $lines = @(
            & adb -s $Serial logcat -d -v threadtime -s "$logTag`:I" "*:S" 2>$null |
                Where-Object { $_ -match "typescript-(compile-perf|execution-perf|prewarm|warm-binding|compiler-boot|compiler-warmup|compiler-trim|compiler-realm-teardown|compiler-service-destroy)" }
        )
        if (@($lines | Where-Object { $_ -match "typescript-execution-perf schema=" }).Count -ge 1) {
            break
        }
        if (@($lines | Where-Object { $_ -match "typescript-compile-perf schema=" }).Count -ge 1) {
            # Hosts older than execution-perf schema 1 stop at the compile line.
            if (-not $compileSeenAt) {
                $compileSeenAt = Get-Date
            } elseif (((Get-Date) - $compileSeenAt).TotalSeconds -ge 3) {
                Write-Warning "Run $ScenarioId#$Index has no typescript-execution-perf line; the host predates schema 3 instrumentation."
                break
            }
        }
        if ((Get-Date) -gt $deadline) {
            throw "Run $ScenarioId#$Index did not log typescript-execution-perf within " +
                "$RunTimeoutSeconds s. Captured lines:`n$($lines -join [Environment]::NewLine)"
        }
        Start-Sleep -Milliseconds 250
    }

    $compile = $lines | Where-Object { $_ -match "typescript-compile-perf schema=" } | Select-Object -Last 1
    $execution = $lines | Where-Object { $_ -match "typescript-execution-perf schema=" } | Select-Object -Last 1
    $boot = $lines | Where-Object { $_ -match "typescript-compiler-boot schema=\d+ processUptimeMs=" } |
        Select-Object -Last 1
    $prewarm = $lines | Where-Object { $_ -match "typescript-prewarm schema=" } | Select-Object -Last 1
    $warmup = $lines | Where-Object { $_ -match "typescript-compiler-warmup schema=" } | Select-Object -Last 1

    $stopwatchMs = $null
    $stampDeadline = (Get-Date).AddSeconds(10)
    while ($true) {
        $stamp = ((& adb -s $Serial shell cat $stampPath 2>$null) -join "").Trim()
        if ($stamp -match '^\d{13}$') {
            $stopwatchMs = [long]$stamp - $deviceLaunchEpochMs
            break
        }
        if ((Get-Date) -gt $stampDeadline) {
            Write-Warning "Run $ScenarioId#$Index left no first-statement stamp; stopwatchMs stays na."
            break
        }
        Start-Sleep -Milliseconds 200
    }

    $trimEvents = @($lines | Where-Object { $_ -match "typescript-compiler-trim schema=" })
    $realmTeardowns = @($lines | Where-Object { $_ -match "typescript-compiler-realm-teardown schema=" })
    $serviceDestroys = @($lines | Where-Object { $_ -match "typescript-compiler-service-destroy schema=" })
    $warmBindingRetained = $null
    $warmBindingLine = @($lines | Where-Object { $_ -match "typescript-warm-binding schema=" }) | Select-Object -Last 1
    if ($warmBindingLine -and $warmBindingLine -match "retained=(True|False|true|false)") {
        $warmBindingRetained = [bool]::Parse($Matches[1])
    }
    $sample = [ordered]@{
        index = $Index
        launchedAt = $launchedAt.ToUniversalTime().ToString("o")
        fixtureSha256 = $FixtureSha256
        compilerProcessPidBefore = $pidBefore
        compilerProcessPidAfter = Get-CompilerProcessPid
        coldCompilerProcess = [bool]$boot
        stopwatchMs = $stopwatchMs
        trimEventCount = $trimEvents.Count
        realmTeardownCount = $realmTeardowns.Count
        serviceDestroyCount = $serviceDestroys.Count
        warmBindingRetained = $warmBindingRetained
        compile = if ($compile) { Parse-KeyValues $compile } else { $null }
        execution = if ($execution) { Parse-KeyValues $execution } else { $null }
        boot = if ($boot) { Parse-KeyValues $boot } else { $null }
        prewarm = if ($prewarm) { Parse-KeyValues $prewarm } else { $null }
        warmup = if ($warmup) { Parse-KeyValues $warmup } else { $null }
        rawLines = @($lines)
    }
    return $sample
}

function Get-ObservedMedian {
    param([Parameter(Mandatory = $true)][long[]]$Values)
    if ($Values.Count -eq 0 -or ($Values.Count % 2) -eq 0) {
        throw "Median input must contain an odd, non-empty number of values."
    }
    $sorted = @($Values | Sort-Object)
    return [long]$sorted[[Math]::Floor($sorted.Count / 2)]
}

function Get-ScenarioMedians {
    param([Parameter(Mandatory = $true)][object[]]$Samples)

    $medians = [ordered]@{}
    $groups = @(
        @{ name = "stopwatch"; keys = @("stopwatchMs") },
        @{ name = "execution"; keys = @("userWaitMs", "prepareMs", "dispatchMs", "engineCreateMs") },
        @{
            name = "compile"
            keys = @(
                "totalMs", "awaitPrewarmMs", "discoveryMs", "prepareMs", "cacheLookupMs",
                "bindPrewarmMs", "remoteRoundTripMs", "compilerMs", "compilerBeforeIngressMs",
                "compilerIngressMs", "compilerInterPhaseMs", "compilerParseMs",
                "compilerSemanticMs", "compilerEmitMs", "compilerAfterEmitMs", "outputMs",
                "cachePublishMs"
            )
        },
        @{
            name = "boot"
            keys = @(
                "serviceCreateStartMs", "serviceCreateMs", "declarationLoadMs",
                "supervisorReadyAtMs", "webViewCreatedAtMs", "pageReadyAtMs", "workerReadyAtMs",
                "workerBootstrapMs"
            )
        },
        @{
            name = "warmup"
            keys = @(
                "sinceReadyMs", "startedAtMs", "loadedAtMs", "completedAtMs",
                "commonImportMs", "commonVerifyMs", "commonDigestMs", "commonTokenMs",
                "rhinoImportMs", "rhinoVerifyMs", "rhinoDigestMs", "rhinoTokenMs",
                "parsedFiles", "parsedBytes", "queuedFiles", "parseMs", "steps", "deferredSteps"
            )
        }
    )
    foreach ($group in $groups) {
        foreach ($key in $group.keys) {
            $values = @(
                foreach ($sample in $Samples) {
                    if ($group.name -eq "stopwatch") {
                        if ($null -ne $sample["stopwatchMs"]) {
                            [long]$sample["stopwatchMs"]
                        }
                        continue
                    }
                    $record = $sample[$group.name]
                    if ($record -and $record.Contains($key) -and $record[$key] -match '^\d+$') {
                        [long]$record[$key]
                    }
                }
            )
            $label = "$($group.name).$key"
            if ($values.Count -eq $Samples.Count -and ($values.Count % 2) -eq 1) {
                $medians[$label] = Get-ObservedMedian -Values $values
            } elseif ($values.Count -gt 0) {
                $medians[$label] = "partial($($values.Count)/$($Samples.Count))"
            }
        }
    }
    return $medians
}

$observedAt = (Get-Date).ToUniversalTime()
$pluginVersion = Get-PackageVersion -Package $PluginPackage
$hostVersion = Get-PackageVersion -Package $HostPackage
if (-not $pluginVersion) {
    throw "Plugin $PluginPackage is not installed on $Serial."
}
if (-not $hostVersion) {
    throw "Host $HostPackage is not installed on $Serial."
}

$report = [ordered]@{
    schemaVersion = 1
    tool = "tools/observe-startup-latency.ps1"
    observedAt = $observedAt.ToString("o")
    deviceLabel = $DeviceLabel
    serial = $Serial
    device = [ordered]@{
        api = [int](Get-AdbProperty "ro.build.version.sdk")
        model = Get-AdbProperty "ro.product.model"
        abi = Get-AdbProperty "ro.product.cpu.abi"
        buildFingerprint = Get-AdbProperty "ro.build.fingerprint"
    }
    webView = Get-WebViewVersion
    host = [ordered]@{ package = $HostPackage; versionName = $hostVersion.versionName; versionCode = $hostVersion.versionCode }
    plugin = [ordered]@{ package = $PluginPackage; versionName = $pluginVersion.versionName; versionCode = $pluginVersion.versionCode }
    fixture = [ordered]@{ source = "tools/fixtures/startup-latency/single-file.ts"; devicePath = $DevicePath }
    runsPerScenario = $Runs
    gate = "none"
    scenarios = [ordered]@{}
}

Write-Host "Device $DeviceLabel ($Serial): API $($report.device.api), $($report.device.model), WebView $($report.webView.version); host $($hostVersion.versionName)/$($hostVersion.versionCode); plugin $($pluginVersion.versionName)/$($pluginVersion.versionCode)"

foreach ($scenarioId in $scenarios) {
    $samples = [System.Collections.Generic.List[object]]::new()
    for ($index = 1; $index -le $Runs; $index++) {
        switch ($scenarioId) {
            "S1" {
                Wait-ForColdCompilerProcess
                $sha = Push-Fixture -Marker ("run-" + (Get-Date).ToString("HHmmssfff"))
            }
            "S2" {
                if ($index -eq 1 -and $samples.Count -eq 0) {
                    # Make sure the process and binding are warm before the first S2 sample.
                    $warmSha = Push-Fixture -Marker ("run-" + (Get-Date).ToString("HHmmssfff"))
                    Invoke-ScriptRun -ScenarioId "S2-warmup" -Index 0 -FixtureSha256 $warmSha | Out-Null
                    Start-Sleep -Seconds 2
                }
                $sha = Push-Fixture -Marker ("run-" + (Get-Date).ToString("HHmmssfff"))
            }
            "S3" {
                if ($index -eq 1 -and $samples.Count -eq 0) {
                    $sha = Push-Fixture -Marker "run-cachehit"
                    Invoke-ScriptRun -ScenarioId "S3-warmup" -Index 0 -FixtureSha256 $sha | Out-Null
                    Start-Sleep -Seconds 2
                } else {
                    $sha = Push-Fixture -Marker "run-cachehit"
                }
            }
        }
        $sample = Invoke-ScriptRun -ScenarioId $scenarioId -Index $index -FixtureSha256 $sha
        $samples.Add($sample)
        $wait = if ($sample.execution) { $sample.execution["userWaitMs"] } else { "na" }
        $ingress = if ($sample.compile -and $sample.compile.Contains("compilerIngressMs")) { $sample.compile["compilerIngressMs"] } else { "na" }
        $stopwatch = if ($null -ne $sample.stopwatchMs) { $sample.stopwatchMs } else { "na" }
        $parse = if ($sample.compile -and $sample.compile.Contains("compilerParseMs")) { $sample.compile["compilerParseMs"] } else { "na" }
        $warm = if ($sample.warmup) { "$($sample.warmup["state"])/$($sample.warmup["parsedFiles"])files@$($sample.warmup["sinceReadyMs"])ms" } else { "na" }
        Write-Host "$scenarioId#$index userWaitMs=$wait stopwatchMs=$stopwatch compilerIngressMs=$ingress compilerParseMs=$parse warmUp=$warm coldProcess=$($sample.coldCompilerProcess) trims=$($sample.trimEventCount) realmTeardowns=$($sample.realmTeardownCount) serviceDestroys=$($sample.serviceDestroyCount) warmBindingRetained=$($sample.warmBindingRetained)"
        Start-Sleep -Seconds 3
    }
    $report.scenarios[$scenarioId] = [ordered]@{
        definition = switch ($scenarioId) {
            "S1" { "compiler process absent before launch; fixture content changed (persistent cache miss)" }
            "S2" { "compiler process alive and host binding retained; fixture content changed within 30 s" }
            "S3" { "fixture unchanged; persistent cache hit" }
        }
        samples = @($samples)
        medians = Get-ScenarioMedians -Samples @($samples)
    }
}

# Restore the committed fixture bytes on the device so later manual runs start from a known file.
Push-Fixture -Marker "run-00000000" | Out-Null

$stamp = $observedAt.ToString("yyyyMMdd-HHmmss")
$reportPath = Join-Path $OutputDirectory "$DeviceLabel-$stamp.json"
$report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $reportPath -Encoding UTF8
Write-Host ""
Write-Host "Report: $reportPath"
Write-Host ""
foreach ($scenarioId in $scenarios) {
    Write-Host "== $scenarioId medians =="
    $medians = $report.scenarios[$scenarioId].medians
    foreach ($key in $medians.Keys) {
        Write-Host ("  {0,-34} {1}" -f $key, $medians[$key])
    }
}
