# One stop setup for Sonar (a damage tracker for Realm of the Mad God).
#   Setup.bat     -> checks the prerequisites, offers to install what is missing, builds, tests, creates Sonar.exe.
#   Diagnose.bat  -> same checks with -CheckOnly: changes nothing, just reports.
param(
    [switch]$CheckOnly,
    # Builds and tests without creating Sonar.exe or the desktop shortcut.
    [switch]$SkipLauncher,
    # Only rebuilds Sonar.exe and the shortcuts. Sonar runs this by itself after an update, so it may be open meanwhile.
    [switch]$LauncherOnly,
    # For testing the prompts: treat these prerequisites as missing (Java, Npcap, Git, Internet, Compiler, Game).
    [string[]]$PretendMissing = @()
)

$ErrorActionPreference = 'Stop'
$PretendMissing = @($PretendMissing | ForEach-Object { $_ -split ',' } | Where-Object { $_ })
$appName = 'Sonar'
$publisher = 'JustAutomate-AI'
# Must stay the same as APP_ID in src\main\java\sonar\ui\WindowsShell.java
$appId = 'JustAutomateAI.Sonar'
$root = Split-Path -Parent $PSScriptRoot
$runDir = Join-Path $root 'run'
$jar = Join-Path $runDir "$appName.jar"
$csc = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
$results = New-Object System.Collections.ArrayList
# A release zip carries the built app and no source: nothing to build, so Git, a JDK and the build downloads are not needed.
$prebuilt = -not (Test-Path (Join-Path $root 'build.gradle'))
$releasesUrl = "https://github.com/$publisher/sonar-rotmg/releases/latest"
# Source with no .git folder is GitHub's "Download ZIP" of the repo, not a real clone and not the release package -
# building needs real git history to fetch the packet library branch from, which this folder does not have.
$sourceZipMistake = (-not $prebuilt) -and -not (Test-Path (Join-Path $root '.git'))

$links = @{
    Java     = 'https://adoptium.net/temurin/releases/?version=17&os=windows&arch=x64&package=jdk'
    Npcap    = 'https://npcap.com/#download'
    Git      = 'https://git-scm.com/download/win'
    Compiler = 'https://dotnet.microsoft.com/download/dotnet-framework/net48'
    Game     = 'https://www.realmofthemadgod.com/'
}

function Step($text) { Write-Host ""; Write-Host "== $text" -ForegroundColor Cyan }
function Pass($text) { Write-Host "  [ OK ] $text" -ForegroundColor Green; [void]$results.Add("OK   $text") }
function Warn($text) { Write-Host "  [WARN] $text" -ForegroundColor Yellow; [void]$results.Add("WARN $text") }
function Fail($text) { Write-Host "  [FAIL] $text" -ForegroundColor Red; [void]$results.Add("FAIL $text") }
function Info($text) { Write-Host "         $text" -ForegroundColor Gray }

function Finish($code) {
    Write-Host ""
    Write-Host "== Summary" -ForegroundColor Cyan
    foreach ($r in $results) {
        $color = 'Green'
        if ($r.StartsWith('WARN')) { $color = 'Yellow' }
        if ($r.StartsWith('FAIL')) { $color = 'Red' }
        Write-Host "  $r" -ForegroundColor $color
    }
    Write-Host ""
    exit $code
}

# Asks the user to pick one of the numbered options. Returns the option's Id, or 'skip' when there is no one to ask.
function Ask-Choice($options) {
    for ($i = 0; $i -lt $options.Count; $i++) {
        Write-Host ("           [{0}] {1}" -f ($i + 1), $options[$i].Label)
    }
    for ($try = 0; $try -lt 20; $try++) {
        $answer = $null
        try { $answer = Read-Host "           Choose 1-$($options.Count)" } catch { return 'skip' }
        if ($null -eq $answer) { return 'skip' }
        $n = 0
        if ([int]::TryParse($answer.Trim(), [ref]$n) -and $n -ge 1 -and $n -le $options.Count) { return $options[$n - 1].Id }
    }
    return 'skip'
}

function Wait-Enter($text) {
    try { [void](Read-Host "           $text") } catch { }
}

function Install-WithWinget($id) {
    Info "Installing $id with winget. Accept the Windows prompt if one appears."
    winget install -e --id $id --accept-source-agreements --accept-package-agreements
}

$hasWinget = [bool](Get-Command winget -ErrorAction SilentlyContinue)

function Test-SonarRunning {
    $java = @(Get-CimInstance Win32_Process -Filter "Name='javaw.exe' OR Name='java.exe'" -ErrorAction SilentlyContinue)
    return [bool]($java | Where-Object { $_.CommandLine -match "$appName\.jar" -and $_.CommandLine -notmatch 'SetupTool' })
}

# ---------------------------------------------------------------- Prerequisite checks

# Returns version, bitness and whether it is a full JDK for a java.exe path.
function Get-JavaInfo($javaExe) {
    if (-not (Test-Path $javaExe)) { return $null }
    $text = cmd /c "`"$javaExe`" -XshowSettings:properties -version 2>&1"
    $version = ($text | Select-String 'java\.version = (.+)' | Select-Object -First 1)
    $bits = ($text | Select-String 'sun\.arch\.data\.model = (\d+)' | Select-Object -First 1)
    if (-not $version) { return $null }
    $v = $version.Matches[0].Groups[1].Value.Trim()
    $parts = $v.Split('.')
    $major = 0
    if ($parts[0] -eq '1') { [void][int]::TryParse($parts[1], [ref]$major) }
    else { [void][int]::TryParse(($parts[0] -replace '[^0-9].*$', ''), [ref]$major) }
    $bin = Split-Path -Parent $javaExe
    New-Object PSObject -Property @{
        Exe     = $javaExe
        Home    = Split-Path -Parent $bin
        Version = $v
        Major   = $major
        Bits    = $(if ($bits) { $bits.Matches[0].Groups[1].Value } else { '?' })
        IsJdk   = (Test-Path (Join-Path $bin 'javac.exe'))
    }
}

function Find-Javas {
    $paths = New-Object System.Collections.ArrayList
    if ($env:JAVA_HOME) { [void]$paths.Add((Join-Path $env:JAVA_HOME 'bin\java.exe')) }
    foreach ($c in @(Get-Command java -All -ErrorAction SilentlyContinue)) { [void]$paths.Add($c.Source) }
    $vendors = 'Java', 'Eclipse Adoptium', 'Microsoft', 'Zulu', 'Amazon Corretto', 'BellSoft', 'Semeru', 'OpenJDK'
    foreach ($pf in @($env:ProgramFiles, ${env:ProgramFiles(x86)})) {
        if (-not $pf) { continue }
        foreach ($vendor in $vendors) {
            $dir = Join-Path $pf $vendor
            if (-not (Test-Path $dir)) { continue }
            foreach ($d in Get-ChildItem $dir -Directory -ErrorAction SilentlyContinue) {
                [void]$paths.Add((Join-Path $d.FullName 'bin\java.exe'))
            }
        }
    }
    $seen = @{}
    foreach ($p in $paths) {
        if (-not $p -or -not (Test-Path $p)) { continue }
        $full = (Resolve-Path $p).Path
        if ($seen.ContainsKey($full.ToLower())) { continue }
        $seen[$full.ToLower()] = $true
        $info = Get-JavaInfo $full
        if ($info) { $info }
    }
}

# The build tooling (Gradle 7.6) runs on Java 8 to 19, and the sniffer needs 64-bit.
function Select-Java($javas) {
    if ($prebuilt) {
        # only running the app: any 64-bit Java from 11 up, the closest to 17 first
        return ($javas | Where-Object { $_.Bits -eq '64' -and $_.Major -ge 11 } | Sort-Object { [Math]::Abs($_.Major - 17) } | Select-Object -First 1)
    }
    $usable = @($javas | Where-Object { $_.IsJdk -and $_.Bits -eq '64' -and $_.Major -ge 8 -and $_.Major -le 19 })
    $preferred = $usable | Where-Object { $_.Major -eq 17 } | Select-Object -First 1
    if ($preferred) { return $preferred }
    $usable | Sort-Object Major -Descending | Select-Object -First 1
}

function Find-Git {
    $cmd = Get-Command git -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    # A git installed a moment ago is not on this window's PATH yet.
    $default = Join-Path $env:ProgramFiles 'Git\cmd\git.exe'
    if (Test-Path $default) {
        $env:Path = "$env:Path;$(Split-Path -Parent $default)"
        return $default
    }
    return $null
}

function Find-GameAssets {
    $candidates = @(
        (Join-Path $env:LOCALAPPDATA 'RealmOfTheMadGod\Production\RotMG Exalt_Data\resources.assets'),
        (Join-Path ([Environment]::GetFolderPath('MyDocuments')) 'RealmOfTheMadGod\Production\RotMG Exalt_Data\resources.assets')
    )
    $candidates | Where-Object { Test-Path $_ } | Select-Object -First 1
}

function Test-Online {
    foreach ($h in 'services.gradle.org', 'repo.maven.apache.org') {
        $client = New-Object System.Net.Sockets.TcpClient
        try {
            $async = $client.BeginConnect($h, 443, $null, $null)
            if (-not $async.AsyncWaitHandle.WaitOne(4000) -or -not $client.Connected) { return $false }
        } catch { return $false } finally { $client.Close() }
    }
    return $true
}


# Each check returns Ok + Detail; Required says whether setup can go on without it.
function Get-Prerequisite($name) {
    $pretend = $script:PretendMissing -contains $name
    $p = New-Object PSObject -Property @{ Name = $name; Title = ''; Ok = $false; Required = $true; Detail = ''; Value = $null }
    switch ($name) {
        'Windows' {
            $p.Title = 'Windows 10/11, 64-bit'
            $os = [Environment]::OSVersion.Version
            $p.Ok = [Environment]::Is64BitOperatingSystem -and $os.Major -ge 10
            $p.Detail = "Windows $($os.Major).$($os.Minor) build $($os.Build)"
        }
        'Java' {
            $p.Title = 'Java JDK (64-bit, version 8 to 19)'
            if ($prebuilt) { $p.Title = 'Java (64-bit, version 11 or newer)' }
            $javas = @(Find-Javas)
            $p.Value = Select-Java $javas
            if ($pretend) { $p.Value = $null; $javas = @() }
            if ($p.Value) {
                $p.Ok = $true
                $p.Detail = "$($p.Value.Version) at $($p.Value.Home)"
            } elseif ($javas | Where-Object { $_.Bits -eq '32' }) {
                $p.Detail = 'only a 32-bit Java was found; it crashes with out of memory and never finishes extracting'
            } elseif ($javas.Count -gt 0) {
                $p.Detail = "found Java $((($javas | ForEach-Object { $_.Version }) -join ', ')), but none is $(if ($prebuilt) { '64-bit and version 11 or newer' } else { 'a JDK between 8 and 19' })"
            } else {
                $p.Detail = 'no Java installed'
            }
        }
        'Npcap' {
            $p.Title = 'Npcap packet capture driver'
            $dll = Join-Path $env:WINDIR 'System32\Npcap\wpcap.dll'
            $svc = Get-Service -Name npcap -ErrorAction SilentlyContinue
            if ($svc -and (Test-Path $dll) -and -not $pretend) {
                $p.Ok = ($svc.Status -eq 'Running')
                $p.Detail = "libpcap $((Get-Item $dll).VersionInfo.ProductVersion), driver $($svc.Status.ToString().ToLower())"
                if (-not $p.Ok) { $p.Detail += ' - reboot, or reinstall Npcap' }
            } elseif ((Test-Path (Join-Path $env:WINDIR 'System32\wpcap.dll')) -and -not $pretend) {
                $p.Detail = 'only the old WinPcap was found; it does not work on Windows 10/11'
            } else {
                $p.Detail = 'not installed'
            }
        }
        'Git' {
            $p.Title = 'Git (optional)'
            $p.Required = $false
            $p.Value = Find-Git
            if ($pretend) { $p.Value = $null }
            $p.Ok = [bool]$p.Value
            if ($p.Ok) { $p.Detail = $p.Value }
            elseif ($p.Required) { $p.Detail = 'not installed' }
            else { $p.Detail = 'not installed (not needed right now, the library source is already here)' }
        }
        'Internet' {
            $p.Title = 'Internet access for the build downloads'
            $gradleCached = Test-Path (Join-Path $env:USERPROFILE '.gradle\wrapper\dists\gradle-7.6.4-bin')
            $p.Required = -not $gradleCached
            $p.Ok = (-not $pretend) -and (Test-Online)
            if ($p.Ok) { $p.Detail = 'services.gradle.org and repo.maven.apache.org reachable' }
            elseif ($gradleCached) { $p.Detail = 'offline (the build tools are cached, the build may still work)' }
            else { $p.Detail = 'can not reach services.gradle.org / repo.maven.apache.org' }
        }
        'Compiler' {
            $p.Title = ".NET Framework 4 compiler (builds $appName.exe)"
            $p.Required = $false
            $p.Ok = (Test-Path $csc) -and -not $pretend
            if ($p.Ok) { $p.Detail = 'present' } else { $p.Detail = "missing; $appName still works through the .bat launcher" }
        }
        'Game' {
            $p.Title = 'Realm of the Mad God'
            $p.Required = $false
            $p.Value = Find-GameAssets
            if ($pretend) { $p.Value = $null }
            $p.Ok = [bool]$p.Value
            if ($p.Ok) { $p.Detail = $p.Value } else { $p.Detail = 'not found in the default location; item and enemy names/icons need it' }
        }
    }
    $p
}

function Show-Prerequisite($p) {
    if ($p.Ok) { Write-Host "  [ OK ] $($p.Title)" -ForegroundColor Green }
    elseif ($p.Required) { Write-Host "  [MISSING] $($p.Title)" -ForegroundColor Red }
    else { Write-Host "  [OPTIONAL] $($p.Title)" -ForegroundColor Yellow }
    Info $p.Detail
}

# Offers the ways to get one missing prerequisite and performs the chosen one.
# Returns $true when it is worth checking again.
function Resolve-Prerequisite($p) {
    Write-Host ""
    Write-Host "  $($p.Title) - $($p.Detail)" -ForegroundColor Yellow
    $options = New-Object System.Collections.ArrayList
    switch ($p.Name) {
        'Java' {
            if ($hasWinget) { [void]$options.Add(@{ Id = 'auto'; Label = 'Install Eclipse Temurin 17 automatically (recommended)' }) }
            [void]$options.Add(@{ Id = 'link'; Label = "Open the download page, I will install it myself ($($links.Java))" })
        }
        'Npcap' {
            Info "Npcap's license does not allow automatic installs, so this one takes a few clicks."
            [void]$options.Add(@{ Id = 'link'; Label = "Open the download page ($($links.Npcap))" })
        }
        'Git' {
            if ($hasWinget) { [void]$options.Add(@{ Id = 'auto'; Label = 'Install Git automatically (recommended)' }) }
            [void]$options.Add(@{ Id = 'link'; Label = "Open the download page, I will install it myself ($($links.Git))" })
        }
        'Internet' {
            [void]$options.Add(@{ Id = 'retry'; Label = 'I fixed my connection / turned the VPN or firewall off, check again' })
        }
        'Compiler' {
            [void]$options.Add(@{ Id = 'link'; Label = "Open the .NET Framework 4.8 download page ($($links.Compiler))" })
        }
        'Game' {
            [void]$options.Add(@{ Id = 'link'; Label = "Open the game's website to install it ($($links.Game))" })
        }
        default { return $false }
    }
    if ($p.Required) { [void]$options.Add(@{ Id = 'skip'; Label = 'Stop setup' }) }
    else { [void]$options.Add(@{ Id = 'skip'; Label = 'Continue without it' }) }

    $choice = Ask-Choice $options
    switch ($choice) {
        'auto' {
            if ($p.Name -eq 'Java') { Install-WithWinget 'EclipseAdoptium.Temurin.17.JDK' }
            if ($p.Name -eq 'Git') { Install-WithWinget 'Git.Git' }
            return $true
        }
        'link' {
            Start-Process $links[$p.Name]
            if ($p.Name -eq 'Java') {
                Info "Download the Windows x64 JDK .msi and run it with the default options."
            }
            if ($p.Name -eq 'Npcap') {
                Info "1. Download the 'Npcap installer' and run it."
                Info "2. Keep 'Install Npcap in WinPcap API-compatible Mode' ticked."
                Info "3. Leave 'Restrict Npcap driver's access to Administrators only' unticked."
            }
            if ($p.Name -eq 'Game') {
                Info "Install the game and start it once so it downloads its files."
            }
            Wait-Enter 'Press Enter here when the install has finished'
            return $true
        }
        'retry' { return $true }
        default { return $false }
    }
}

Write-Host ""
Write-Host "  $appName setup" -ForegroundColor Magenta
Write-Host "  Damage tracker for Realm of the Mad God" -ForegroundColor DarkGray

if ($sourceZipMistake) {
    Write-Host ""
    Write-Host "  This looks like GitHub's 'Download ZIP' of the source code, not the ready-to-run release." -ForegroundColor Red
    Info "There is a build.gradle here but no .git folder, so there is source to build but no git history to build it from."
    Write-Host ""
    Write-Host "  Easiest fix - you do not need to build $appName at all:" -ForegroundColor Green
    Info "1. Download $releasesUrl"
    Info "2. Unpack Sonar-<version>-windows.zip to its own folder (not over this one)"
    Info "3. Run Setup.bat there"
    Finish 1
}

Step "Step 1 of 3 - Checking prerequisites"
$names = 'Windows', 'Java', 'Npcap', 'Git', 'Internet', 'Compiler', 'Game'
if ($prebuilt) { $names = 'Windows', 'Java', 'Npcap', 'Compiler', 'Game' }
$prereqs = @{}
foreach ($n in $names) {
    $prereqs[$n] = Get-Prerequisite $n
    Show-Prerequisite $prereqs[$n]
}

$missing = @($names | Where-Object { -not $prereqs[$_].Ok })
if ($missing.Count -eq 0) {
    Info ""
    Info "Everything needed is installed."
} elseif (-not $CheckOnly) {
    Step "Step 2 of 3 - Installing what is missing ($($missing.Count))"
    foreach ($n in $missing) {
        while (-not $prereqs[$n].Ok) {
            if (-not (Resolve-Prerequisite $prereqs[$n])) { break }
            # a prerequisite is only pretended missing until the user has acted on it
            $script:PretendMissing = @($script:PretendMissing | Where-Object { $_ -ne $n })
            $prereqs[$n] = Get-Prerequisite $n
            Show-Prerequisite $prereqs[$n]
        }
    }
}

foreach ($n in $names) {
    $p = $prereqs[$n]
    # already shown above, so these only go into the summary
    $state = 'WARN'
    if ($p.Ok) { $state = 'OK  ' } elseif ($p.Required) { $state = 'FAIL' }
    [void]$results.Add("$state $($p.Title): $($p.Detail)")
    if (-not $p.Ok -and $links.ContainsKey($n)) { [void]$results.Add("     get it here: $($links[$n])") }
}
if (@($names | Where-Object { $prereqs[$_].Required -and -not $prereqs[$_].Ok }).Count -gt 0) {
    Write-Host ""
    Write-Host "  Setup can not continue until the items marked FAIL are installed. Run Setup.bat again afterwards." -ForegroundColor Red
    Finish 1
}

$java = $prereqs['Java'].Value
$gameAssets = $prereqs['Game'].Value
$env:JAVA_HOME = $java.Home

# ---------------------------------------------------------------- Environment warnings

Step "Environment"
$npcapReg = Get-ItemProperty 'HKLM:\SOFTWARE\WOW6432Node\Npcap' -ErrorAction SilentlyContinue
if (-not $npcapReg) { $npcapReg = Get-ItemProperty 'HKLM:\SOFTWARE\Npcap' -ErrorAction SilentlyContinue }
if ($npcapReg) {
    if ($npcapReg.AdminOnly -eq 1) { Warn "Npcap is restricted to administrators. Run $appName as administrator, or reinstall Npcap with that option unticked." }
    if ($npcapReg.WinPcapCompatible -ne 1) { Warn "Npcap is not in WinPcap API-compatible mode. If $appName says Npcap is missing, reinstall Npcap with that option ticked." }
}
$gameCount = @(Get-Process -ErrorAction SilentlyContinue | Where-Object { $_.ProcessName -eq 'RotMG Exalt' }).Count
if ($gameCount -gt 1) { Warn "$gameCount game clients are running. The sniffer supports one client at a time." }

$adapters = @(Get-NetAdapter -ErrorAction SilentlyContinue | Where-Object { $_.Status -eq 'Up' })
foreach ($a in $adapters) { Info ("network up: {0} ({1})" -f $a.Name, $a.InterfaceDescription) }
$tunnelPattern = 'WireGuard|Wintun|TAP-|OpenVPN|NordLynx|NordVPN|ProtonVPN|Mullvad|ExpressVPN|Surfshark|AnyConnect|Fortinet|PANGP|Windscribe|CyberGhost'
$tunnels = @($adapters | Where-Object { $_.InterfaceDescription -match $tunnelPattern -or $_.Name -match $tunnelPattern })
if ($tunnels.Count -gt 0) {
    Warn ("VPN adapter active: {0}. $appName sees no packets while the game runs through a VPN, turn it off while playing." -f (($tunnels | ForEach-Object { $_.Name }) -join ', '))
} else {
    Pass "No internet VPN adapter active"
}
$wired = @($adapters | Where-Object { $_.PhysicalMediaType -match '802\.3' -and $_.InterfaceDescription -notmatch 'VPN|Virtual' })
$wifi = @($adapters | Where-Object { $_.PhysicalMediaType -match '802\.11|Native 802' })
if ($wired.Count -eq 0 -and $wifi.Count -gt 0) {
    Warn "Wi-Fi only. Dropped Wi-Fi packets can stop the sniffer ('Stream Constructor reached 50 packets'). Use a cable if that happens."
}

# ---------------------------------------------------------------- Build

if (-not $CheckOnly -and -not $prebuilt) {
    Step "Step 3 of 3 - Building $appName"
    $gradlew = Join-Path $root 'gradlew.bat'

    Info "Building the app."
    & $gradlew -p $root --console=plain -q shadowJar
    if ($LASTEXITCODE -ne 0) { Fail "$appName build failed (see the output above)."; Finish 1 }
    $appJar = Get-ChildItem (Join-Path $root 'build\libs') -Filter 'Sonar.jar' | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    # The app writes its assets, settings and logs next to the jar, so it gets a folder of its own.
    New-Item -ItemType Directory -Force $runDir | Out-Null
    # Replacing the app file under a running Sonar leaves that Sonar half old, half new, and it misbehaves
    if (Test-SonarRunning) {
        Info "$appName is open. Close it so the new version can be put in place."
        Wait-Enter "Press Enter here when $appName is closed"
        if (Test-SonarRunning) {
            Fail "$appName is still open. Close it and run Setup again."
            Finish 1
        }
    }
    try {
        Copy-Item $appJar.FullName $jar -Force
    } catch {
        Fail "Could not replace run\$appName.jar. Close $appName and run Setup again."
        Finish 1
    }
    Pass "Built $appName.jar (from $($appJar.Name))"
}

# ---------------------------------------------------------------- Self test

if (-not $LauncherOnly) {
Step "Capture self test"
if (-not (Test-Path $jar)) {
    Fail $(if ($prebuilt) { "run\$appName.jar is missing. Unpack the whole zip again." } else { "run\$appName.jar does not exist yet. Run Setup.bat first." })
    Finish 1
}
Push-Location $runDir
& $java.Exe -cp "$appName.jar" sonar.SetupTool selftest
$selfTest = $LASTEXITCODE
Pop-Location
switch ($selfTest) {
    0 { Pass "Java can open the network interfaces through Npcap" }
    2 { Fail "Java can not load Npcap. Reinstall Npcap with 'WinPcap API-compatible Mode' ticked, then reboot. $($links.Npcap)" }
    3 { Fail "No network interface could be opened. Reinstall Npcap and reboot. If Npcap is admin-only, run as administrator." }
    4 { Fail "The selected Java is not 64-bit." }
    5 { Fail "The run folder is not writable. Move this project out of protected folders (Program Files, OneDrive, a zip)." }
    default { Fail "Self test failed with code $selfTest (see the output above)." }
}
if ($selfTest -ne 0) { Finish 1 }
}

# ---------------------------------------------------------------- Assets and launcher

if (-not $CheckOnly) {
    if ($gameAssets -and -not $LauncherOnly) {
        Step "Extract game assets"
        Push-Location $runDir
        $extractLog = & $java.Exe -cp "$appName.jar" sonar.SetupTool extract 2>&1
        $extract = $LASTEXITCODE
        Pop-Location
        if ($extract -eq 0) {
            Pass "Game assets extracted"
        } else {
            $extractLog | Select-Object -Last 15 | ForEach-Object { Info $_ }
            Warn "Asset extraction failed. $appName will offer to extract them when it starts."
        }
    }

}
if (-not $CheckOnly -and -not $SkipLauncher) {
    Step "Launcher"
    $desktop = [Environment]::GetFolderPath('Desktop')
    $shell = New-Object -ComObject WScript.Shell

    # Leftovers from the names the app had before: Tomato, then Peach.
    foreach ($old in 'Tomato.exe', 'Start Tomato.bat', 'Start Tomato (debug console).bat', 'run\Tomato.jar',
                     'Peach.exe', 'Start Peach.bat', 'Start Peach (debug console).bat', 'run\Peach.jar',
                     'tools\launcher\peach.ico') {
        $oldPath = Join-Path $root $old
        if (Test-Path $oldPath) { Remove-Item $oldPath -Force -Confirm:$false }
    }
    # Only ours: a shortcut of the same name pointing somewhere else is someone else's.
    $startMenu = Join-Path ([Environment]::GetFolderPath('Programs')) ''
    foreach ($place in @($desktop, $startMenu)) {
        foreach ($oldName in 'Tomato.lnk', 'Peach.lnk') {
            $oldShortcut = Join-Path $place $oldName
            if ((Test-Path $oldShortcut) -and ($shell.CreateShortcut($oldShortcut).TargetPath -like (Join-Path $root '*'))) {
                Remove-Item $oldShortcut -Force -Confirm:$false
            }
        }
    }

    $javaw = Join-Path $java.Home 'bin\javaw.exe'
    # The launcher starts java by full path, which avoids broken .jar file associations (the 'jarfix' problem).
    Set-Content -Path (Join-Path $runDir 'java-path.txt') -Value $javaw -Encoding ascii
    $debugLauncher = @"
@echo off
cd /d "%~dp0run"
"$($java.Exe)" -jar $appName.jar %*
echo.
echo $appName closed. Any errors are shown above.
pause
"@
    Set-Content -Path (Join-Path $root "Start $appName (debug console).bat") -Value $debugLauncher -Encoding ascii

    $exe = Join-Path $root "$appName.exe"
    $launcherDir = Join-Path $PSScriptRoot 'launcher'
    $built = $false
    # Publisher details shown in the file's Properties: taken from the app's own version, or the release's version file
    $appVersion = '1.0.0'
    $versionSource = Join-Path $root 'src\main\java\sonar\ui\Updater.java'
    $versionFile = Join-Path $launcherDir 'version.txt'
    if ((Test-Path $versionSource) -and ((Get-Content $versionSource -Raw) -match 'CURRENT = "([0-9.]+)"')) { $appVersion = $Matches[1] }
    elseif (Test-Path $versionFile) { $appVersion = (Get-Content $versionFile -TotalCount 1).Trim() }
    $assemblyInfo = Join-Path $env:TEMP 'SonarAssemblyInfo.cs'
    Set-Content -Path $assemblyInfo -Encoding ascii -Value @"
using System.Reflection;
[assembly: AssemblyTitle("$appName")]
[assembly: AssemblyDescription("Damage tracker for Realm of the Mad God")]
[assembly: AssemblyCompany("$publisher")]
[assembly: AssemblyProduct("$appName")]
[assembly: AssemblyCopyright("$publisher. MIT licence, see LICENSE.md.")]
[assembly: AssemblyVersion("$appVersion.0")]
[assembly: AssemblyFileVersion("$appVersion.0")]
"@
    if ($prereqs['Compiler'].Ok) {
        try {
            & $csc /nologo /target:winexe /optimize+ "/out:$exe" "/win32icon:$(Join-Path $launcherDir 'sonar.ico')" /r:System.Windows.Forms.dll (Join-Path $launcherDir 'Launcher.cs') $assemblyInfo
            $built = ($LASTEXITCODE -eq 0) -and (Test-Path $exe)
        } catch { $built = $false }
    }
    if ($built) {
        # Sonar compares this with its own version at startup and reruns this step when they differ
        Set-Content -Path (Join-Path $runDir 'setup-version.txt') -Value $appVersion -Encoding ascii
        Pass "Created $appName.exe"
        # The shortcuts carry the id the running window announces, so a pinned Sonar and the open Sonar are one taskbar button
        $description = "$appName by $publisher - Realm of the Mad God damage tracker"
        $places = @($desktop, [Environment]::GetFolderPath('Programs'))
        $pinnable = $false
        # SONAR_NO_SHORTCUTS leaves the user's shortcuts alone, for copies that are only being tested
        if ($env:SONAR_NO_SHORTCUTS) { $places = @() }
        try {
            Add-Type -Path (Join-Path $launcherDir 'Shortcut.cs')
            foreach ($place in $places) { [SonarShortcut]::Create((Join-Path $place "$appName.lnk"), $exe, $root, $description, $appId) }
            $pinnable = ($places.Count -eq 0) -or ([SonarShortcut]::ReadAppId((Join-Path $desktop "$appName.lnk")) -eq $appId)
        } catch {
            $pinnable = $false
        }
        if ($places.Count -eq 0) {
            Pass "Shortcuts left as they are"
        } elseif ($pinnable) {
            Pass "Shortcuts '$appName' on the desktop and in the Start menu"
            Info "To keep it on the taskbar: right-click the shortcut, or the $appName button while it is running, and choose 'Pin to taskbar'."
        } else {
            foreach ($place in $places) {
                $shortcut = $shell.CreateShortcut((Join-Path $place "$appName.lnk"))
                $shortcut.TargetPath = $exe
                $shortcut.WorkingDirectory = $root
                $shortcut.Description = $description
                $shortcut.Save()
            }
            Warn "Shortcuts created, but without the taskbar identity. A pinned $appName will show as a second button while it runs."
        }
    } elseif (Test-Path $exe) {
        Warn "Could not rebuild $appName.exe (is $appName running?). The existing one still works."
    } else {
        Warn "Could not build $appName.exe. Use 'Start $appName (debug console).bat' instead."
    }
} else {
    $errorDir = Join-Path $runDir 'error'
    if (Test-Path $errorDir) {
        $lastLog = Get-ChildItem $errorDir -File | Sort-Object LastWriteTime -Descending | Select-Object -First 1
        if ($lastLog) {
            Step "Latest $appName error log ($($lastLog.Name), $($lastLog.LastWriteTime))"
            Get-Content $lastLog.FullName -Tail 25 | ForEach-Object { Info $_ }
        }
    }
}

$failed = @($results | Where-Object { $_.StartsWith('FAIL') }).Count
if ($failed -eq 0 -and -not $CheckOnly) {
    Write-Host ""
    Write-Host "Ready. Start $appName with $appName.exe (or the desktop shortcut), press Start sniffer," -ForegroundColor Green
    Write-Host "then enter a new area in game (for example the Nexus) so it can sync." -ForegroundColor Green
}
Finish $(if ($failed -eq 0) { 0 } else { 1 })
