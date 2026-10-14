# Packs a ready-to-use Sonar into one zip for people who only want to run it.
# The zip holds the built app, so its Setup needs neither Git nor a build.
# Run Setup.bat first so run\Sonar.jar is current, then:
#   powershell -ExecutionPolicy Bypass -File tools\make-release.ps1 -Version 1.0.0
param(
    # Defaults to the version the app itself reports.
    [string]$Version
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
if (-not $Version) {
    $source = Get-Content (Join-Path $root 'src\main\java\sonar\ui\Updater.java') -Raw
    if ($source -notmatch 'CURRENT = "([0-9.]+)"') { throw "Could not read the version from Updater.java." }
    $Version = $Matches[1]
}
# The freshly built app; run\Sonar.jar can be older when Sonar was open and Setup could not replace it
$jar = Get-ChildItem (Join-Path $root 'build\libs') -Filter 'Sonar.jar' -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1 -ExpandProperty FullName
if (-not $jar) { $jar = Join-Path $root 'run\Sonar.jar' }
if (-not (Test-Path $jar)) { throw "No built app found. Run Setup.bat first." }

# The version is compiled into the jar. Packaging one built before the version was raised ships a
# zip whose name says one thing and whose contents say another: the updater then sees a mismatch,
# offers the update again, installs the same jar and loops forever. Caught here rather than by a
# user stuck on the download prompt.
Add-Type -AssemblyName System.IO.Compression.FileSystem
$check = [IO.Compression.ZipFile]::OpenRead($jar)
try {
    $entry = $check.Entries | Where-Object { $_.FullName -eq 'sonar/ui/Updater.class' }
    if ($entry) {
        $stream = $entry.Open()
        $mem = New-Object IO.MemoryStream
        $stream.CopyTo($mem)
        $stream.Close()
        $text = [Text.Encoding]::ASCII.GetString($mem.ToArray())
        $mem.Close()
        $found = @([regex]::Matches($text, '\b\d+\.\d+\.\d+\b') | ForEach-Object { $_.Value } | Sort-Object -Unique)
        if ($found.Count -gt 0 -and ($found -notcontains $Version)) {
            throw "The built app reports $($found -join ' / ') but this release is $Version. Rebuild first - shipping this would leave users in an update loop."
        }
    }
} finally {
    $check.Dispose()
}

$dist = Join-Path $root 'dist'
$stage = Join-Path $dist 'Sonar'
if (Test-Path $stage) { Remove-Item $stage -Recurse -Force -Confirm:$false }
New-Item -ItemType Directory -Force (Join-Path $stage 'run'), (Join-Path $stage 'tools\launcher') | Out-Null

Copy-Item $jar (Join-Path $stage 'run\Sonar.jar')
Copy-Item (Join-Path $root 'Setup.bat'), (Join-Path $root 'Diagnose.bat'), (Join-Path $root 'LICENSE.md') $stage
Copy-Item (Join-Path $PSScriptRoot 'release\INSTRUCTIONS.txt') $stage
Copy-Item (Join-Path $PSScriptRoot 'setup.ps1') (Join-Path $stage 'tools')
Copy-Item (Join-Path $PSScriptRoot 'launcher\Launcher.cs'), (Join-Path $PSScriptRoot 'launcher\Shortcut.cs'), (Join-Path $PSScriptRoot 'launcher\sonar.ico') (Join-Path $stage 'tools\launcher')

# Setup stamps this version and the publisher into Sonar.exe
Set-Content -Path (Join-Path $stage 'tools\launcher\version.txt') -Value $Version -Encoding ascii

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
Remove-Item $stage -Recurse -Force -Confirm:$false

$size = [Math]::Round((Get-Item $zip).Length / 1MB, 1)
Write-Host "Created $zip ($size MB)"
