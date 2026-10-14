# Builds the one-click package: Sonar.exe with its own Java runtime, so nothing
# has to be installed except Npcap.
#
# Run Setup.bat or gradlew shadowJar first so build\libs\Sonar.jar is current, then:
#   powershell -ExecutionPolicy Bypass -File tools\make-package.ps1
#
# Produces dist\Sonar-<version>-windows.zip - unpack it anywhere and run Sonar.exe.

param([string]$Version)

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

# Only three modules are actually used, which takes the runtime from ~130 MB to ~43 MB.
Write-Host "Building the Java runtime..." -ForegroundColor Cyan
& $jlink --add-modules java.base,java.desktop,java.sql `
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
