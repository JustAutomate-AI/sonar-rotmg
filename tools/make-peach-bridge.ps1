# One-off: a release asset that a Peach-era install can actually install.
#
# A Peach build's updater asks peach-rotmg for Peach-<version>-windows.zip and
# refuses anything that does not contain Peach/run/Peach.jar. The repository
# redirects, so it finds the current release and then 404s on the filename, which
# leaves it saying "the update could not be installed automatically".
#
# This builds that exact filename with the current Sonar code inside, under the
# names the old updater expects. Installing it leaves a Peach-named shell running
# Sonar: the settings and keys migrate themselves on first start, and the next
# update moves it onto the proper layout.
#
# Attach the result to the release alongside the normal zip:
#   gh release upload v<version> dist\Peach-<version>-windows.zip -R JustAutomate-AI/sonar-rotmg
#
# Only needed while someone is still on a Peach build. Delete this once nobody is.

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

$work = Join-Path $root 'build\bridge'
if (Test-Path $work) { Remove-Item $work -Recurse -Force -Confirm:$false }
$stage = Join-Path $work 'Peach'
New-Item -ItemType Directory -Force (Join-Path $stage 'run'), (Join-Path $stage 'tools') | Out-Null

# the name the old launcher starts, holding current code
Copy-Item $jar (Join-Path $stage 'run\Peach.jar')
Copy-Item (Join-Path $root 'LICENSE.md') $stage

# The old updater runs tools\setup.ps1 after copying. The Peach-era one would try
# `java -cp Peach.jar tomato.SetupTool`, a class this build no longer has, and spend
# a while failing at it. Replacing it with something that does nothing keeps the
# handover quiet; Sonar does its own checks when it opens.
Set-Content -Path (Join-Path $stage 'tools\setup.ps1') -Encoding ascii -Value @'
# Sonar does its prerequisite checks itself when it opens. Nothing to do here.
exit 0
'@

Set-Content -Path (Join-Path $stage 'READ ME FIRST.txt') -Encoding ascii -Value @"
This install has been updated to Sonar $Version.

Your settings, characters and history came across by themselves. The program files
are still named Peach because that is what this install was built as - everything
inside them is Sonar now.

Two things worth knowing:

  - Sonar ships with its own Java now, so a fresh download needs nothing installed
    except Npcap. This updated-in-place copy still uses the Java you already have.
    To move onto the self-contained build, download it from the releases page,
    unpack it anywhere, and copy your "run" folder into it - Sonar will take the
    settings and history out of it on first start.

  - The next update will move this install onto the new layout by itself.

https://github.com/JustAutomate-AI/sonar-rotmg/releases/latest
"@

$dist = Join-Path $root 'dist'
New-Item -ItemType Directory -Force $dist | Out-Null
$zip = Join-Path $dist "Peach-$Version-windows.zip"
if (Test-Path $zip) { Remove-Item $zip -Force -Confirm:$false }

Add-Type -AssemblyName System.IO.Compression, System.IO.Compression.FileSystem
$stream = [System.IO.File]::Open($zip, [System.IO.FileMode]::Create)
$archive = New-Object System.IO.Compression.ZipArchive($stream, [System.IO.Compression.ZipArchiveMode]::Create)
foreach ($file in Get-ChildItem $stage -Recurse -File) {
    $entry = 'Peach/' + $file.FullName.Substring($stage.Length + 1).Replace('\', '/')
    [void][System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $file.FullName, $entry, [System.IO.Compression.CompressionLevel]::Optimal)
}
$archive.Dispose()
$stream.Dispose()

$size = [Math]::Round((Get-Item $zip).Length / 1MB, 1)
Write-Host "Created $zip ($size MB)" -ForegroundColor Green
Write-Host "  for Peach-era installs only; attach it beside the normal release zip"
