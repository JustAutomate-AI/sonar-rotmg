# Builds the one-click package: Sonar.exe with its own Java runtime, so nothing
# has to be installed except Npcap.
#
# Run Setup.bat or gradlew shadowJar first so build\libs\Sonar.jar is current, then:
#   powershell -ExecutionPolicy Bypass -File tools\make-package.ps1
#
# Produces two things in dist\:
#   Sonar-<version>-windows.zip   portable - unpack anywhere and run Sonar.exe
#   Sonar-<version>-setup.msi     the installer most people should use
#
# The installer needs WiX 3.x (candle.exe and light.exe). Pass -Wix with the folder
# holding them, or leave it and the usual install locations are tried. GitHub's
# windows runners already carry WiX, so CI needs no argument. Without WiX the zip is
# still built and the installer is skipped with a warning.

param([string]$Version, [string]$Wix)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

if (-not $Version) {
    $source = Get-Content (Join-Path $root 'src\main\java\sonar\ui\Updater.java') -Raw
    if ($source -match 'CURRENT\s*=\s*"([^"]+)"') { $Version = $Matches[1] }
    else { throw "Could not read the version from Updater.java" }
}

$jar = Join-Path $root 'build\libs\Sonar.jar'
if (-not (Test-Path $jar)) { throw "No build\libs\Sonar.jar - run the build first" }

# The JDK that built the jar, so the bundled runtime matches what it was compiled against.
$jdk = $env:JAVA_HOME
if (-not $jdk -or -not (Test-Path (Join-Path $jdk 'bin\jpackage.exe'))) {
    $jdk = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot'
}
$jlink = Join-Path $jdk 'bin\jlink.exe'
$jpackage = Join-Path $jdk 'bin\jpackage.exe'
foreach ($t in $jlink, $jpackage) {
    if (-not (Test-Path $t)) { throw "Not found: $t (set JAVA_HOME to a JDK 17)" }
}

$work = Join-Path $root 'build\package'
if (Test-Path $work) { Remove-Item $work -Recurse -Force -Confirm:$false }
New-Item -ItemType Directory -Force (Join-Path $work 'in') | Out-Null
Copy-Item $jar (Join-Path $work 'in\Sonar.jar')

# Trimming the runtime takes it from ~130 MB to ~43 MB. Only what is actually used is
# kept, with one exception that is not obvious: jdk.crypto.ec.
#
# Nothing in Sonar names that module, so it was left out, and the runtime then had no
# elliptic-curve provider at all - 17 cipher suites and not one of them ECDHE. GitHub
# accepts nothing else, so every update check from a packaged build died on
# "handshake_failure" while the same code run on an ordinary JDK was fine. It costs 91 KB.
Write-Host "Building the Java runtime..." -ForegroundColor Cyan
& $jlink --add-modules java.base,java.desktop,java.sql,jdk.crypto.ec `
    --strip-debug --no-header-files --no-man-pages --compress=2 `
    --output (Join-Path $work 'runtime')
if ($LASTEXITCODE -ne 0) { throw "jlink failed" }

Write-Host "Building Sonar.exe..." -ForegroundColor Cyan
# $ROOTDIR is expanded by the launcher at startup, so Sonar's data sits beside the
# program wherever the folder is moved to, rather than following the working directory.
& $jpackage --type app-image --name Sonar `
    --input (Join-Path $work 'in') `
    --main-jar Sonar.jar --main-class sonar.Sonar `
    --runtime-image (Join-Path $work 'runtime') `
    --dest (Join-Path $work 'out') `
    --icon (Join-Path $root 'tools\launcher\sonar.ico') `
    --app-version $Version --vendor 'JustAutomate-AI' `
    --description 'Damage tracker for Realm of the Mad God' `
    --copyright 'MIT - see LICENSE.md' `
    --java-options '-Dsonar.data=$ROOTDIR'
if ($LASTEXITCODE -ne 0) { throw "jpackage failed" }

$stage = Join-Path $work 'out\Sonar'
Copy-Item (Join-Path $root 'LICENSE.md') $stage
Copy-Item (Join-Path $PSScriptRoot 'release\INSTRUCTIONS-packaged.txt') (Join-Path $stage 'INSTRUCTIONS.txt')

$dist = Join-Path $root 'dist'
New-Item -ItemType Directory -Force $dist | Out-Null
$zip = Join-Path $dist "Sonar-$Version-windows.zip"
if (Test-Path $zip) { Remove-Item $zip -Force -Confirm:$false }

# Compress-Archive writes backslashes into the entry names, which only Windows Explorer unpacks correctly
Add-Type -AssemblyName System.IO.Compression, System.IO.Compression.FileSystem
$stream = [System.IO.File]::Open($zip, [System.IO.FileMode]::Create)
$archive = New-Object System.IO.Compression.ZipArchive($stream, [System.IO.Compression.ZipArchiveMode]::Create)
foreach ($file in Get-ChildItem $stage -Recurse -File) {
    $entry = 'Sonar/' + $file.FullName.Substring($stage.Length + 1).Replace('\', '/')
    [void][System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $file.FullName, $entry, [System.IO.Compression.CompressionLevel]::Optimal)
}
$archive.Dispose()
$stream.Dispose()

$size = [Math]::Round((Get-Item $zip).Length / 1MB, 1)
Write-Host "Created $zip ($size MB)" -ForegroundColor Green
Write-Host "  unpack it anywhere and run Sonar.exe - no Java needed"

# ---- the installer ----
#
# Built from the app image above, so the installer and the portable zip are the same
# program - there is no second build to go out of step.
#
# It installs per user. That is the whole point: a per-user install writes only inside
# the user's own profile, so Windows never raises the administrator prompt, and the
# "unknown publisher" warning that comes with it never appears. Sonar is not code
# signed, and until it is, not asking for admin is the difference between an install
# that looks ordinary and one that looks alarming. The only prompt left in the whole
# process belongs to Npcap, which is signed by the Nmap project.
#
# No desktop shortcut is created here, deliberately. Windows raises error 1909 if the
# shortcut cannot be written - a name already taken on the Desktop will do it - and the
# installer then blocks on an error dialog that, in this sequence, never becomes
# answerable. It hangs, with the progress bar sitting on "Creating shortcuts" and no
# way out but Task Manager. That is not a rare corner: a Desktop redirected by OneDrive
# or a junction is extremely common, and 2.8.0 shipped with this fault. The Start menu
# entry is written to a folder of its own and has never failed. Sonar offers a desktop
# shortcut itself on first run (see FirstRun.java), where a refusal is just a message
# rather than a wedged installer.
if (-not $Wix) {
    foreach ($c in @("${env:ProgramFiles(x86)}\WiX Toolset v3.14in",
                     "${env:ProgramFiles(x86)}\WiX Toolset v3.11in",
                     "$env:WIXin")) {
        if ($c -and (Test-Path (Join-Path $c 'candle.exe'))) { $Wix = $c; break }
    }
}
$haveWix = $Wix -and (Test-Path (Join-Path $Wix 'candle.exe'))
if (-not $haveWix) {
    Write-Warning "WiX not found - the .msi was skipped. The portable zip above is unaffected."
}

if ($haveWix) {
Write-Host "Building the installer..." -ForegroundColor Cyan
$env:PATH = "$Wix;$env:PATH"
# A fixed id, which is what tells Windows one version replaces the last rather than
# installing beside it. It must never change.
$upgradeUuid = '9fc754e4-6e35-4968-923a-fb3a351bc9b0'
& $jpackage --type msi --app-image $stage --name Sonar `
    --app-version $Version --vendor 'JustAutomate-AI' `
    --description 'Damage tracker for Realm of the Mad God' `
    --copyright 'MIT - see LICENSE.md' `
    --license-file (Join-Path $root 'LICENSE.md') `
    --about-url 'https://github.com/JustAutomate-AI/sonar-rotmg' `
    --win-per-user-install --win-dir-chooser `
    --win-menu --win-menu-group 'Sonar' `
    --win-upgrade-uuid $upgradeUuid `
    --dest $dist
if ($LASTEXITCODE -ne 0) { throw "jpackage could not build the installer" }

$built = Get-ChildItem $dist -Filter '*.msi' | Sort-Object LastWriteTime -Descending | Select-Object -First 1
$msi = Join-Path $dist "Sonar-$Version-setup.msi"
if ($built.FullName -ne $msi) { Move-Item $built.FullName $msi -Force }
$msiSize = [Math]::Round((Get-Item $msi).Length / 1MB, 1)
Write-Host "Created $msi ($msiSize MB)" -ForegroundColor Green
Write-Host "  installs per user, so Windows asks for no administrator rights"
}

# Checksums, over whatever was actually built, so anyone can confirm the file they
# downloaded is the file this script produced.
$built = @($zip) + @(if ($haveWix) { $msi })
$sums = Join-Path $dist "SHA256SUMS-$Version.txt"
Get-FileHash $built -Algorithm SHA256 |
    ForEach-Object { "{0}  {1}" -f $_.Hash.ToLower(), (Split-Path $_.Path -Leaf) } |
    Set-Content $sums -Encoding ascii
Write-Host "Created $sums" -ForegroundColor Green
