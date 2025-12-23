param(
  [string]$Use,
  [switch]$List
)

$RootDir = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$WrappersDir = Join-Path $RootDir "gradle\wrappers"
$BackupRoot = Join-Path $WrappersDir "backup"

function Extract-Version([string]$PropsPath) {
  $line = Select-String -Path $PropsPath -Pattern '^\s*distributionUrl=' | Select-Object -First 1
  if (-not $line) { return "unknown" }
  $url = ($line.Line -split '=', 2)[1]
  $m = [regex]::Match($url, 'gradle-([0-9A-Za-z\.\-]+)-(bin|all)\.zip')
  if ($m.Success) { return $m.Groups[1].Value }
  return "unknown"
}

function Major-Of([string]$Version) {
  $m = [regex]::Match($Version, '^(\d+)')
  if ($m.Success) { return [int]$m.Groups[1].Value }
  return 0
}

function Get-Wrappers() {
  if (-not (Test-Path $WrappersDir)) { return @() }
  Get-ChildItem -Path $WrappersDir -Directory |
    Where-Object { $_.Name -match '^g\d+$' } |
    ForEach-Object {
      $dir = $_.FullName
      $props = Join-Path $dir "gradle\wrapper\gradle-wrapper.properties"
      $jar   = Join-Path $dir "gradle\wrapper\gradle-wrapper.jar"
      $sh    = Join-Path $dir "gradlew"
      $bat   = Join-Path $dir "gradlew.bat"
      if ((Test-Path $props) -and (Test-Path $jar) -and (Test-Path $sh) -and (Test-Path $bat)) {
        $ver = Extract-Version $props
        [pscustomobject]@{
          Name  = $_.Name
          Dir   = $dir
          Ver   = $ver
          Major = (Major-Of $ver)
        }
      }
    } | Sort-Object Major
}

function Backup-Current() {
  New-Item -ItemType Directory -Force $BackupRoot | Out-Null
  $stamp = Get-Date -Format "yyyyMMdd-HHmmss"
  $dest = Join-Path $BackupRoot $stamp
  New-Item -ItemType Directory -Force (Join-Path $dest "gradle\wrapper") | Out-Null

  $files = @(
    (Join-Path $RootDir "gradlew"),
    (Join-Path $RootDir "gradlew.bat"),
    (Join-Path $RootDir "gradle\wrapper\gradle-wrapper.jar"),
    (Join-Path $RootDir "gradle\wrapper\gradle-wrapper.properties")
  )
  foreach ($f in $files) {
    if (Test-Path $f) {
      $rel = $f.Substring($RootDir.Length).TrimStart('\')
      $target = Join-Path $dest $rel
      $targetDir = Split-Path -Parent $target
      New-Item -ItemType Directory -Force $targetDir | Out-Null
      Copy-Item $f $target -Force
    }
  }

  Write-Host "Backed up current wrapper to: $dest"

  # Cleanup old backups (keep last 10)
  $backups = Get-ChildItem -Path $BackupRoot -Directory | Sort-Object CreationTime
  if ($backups.Count -gt 10) {
    $toDelete = $backups.Count - 10
    $backups | Select-Object -First $toDelete | Remove-Item -Recurse -Force
    Write-Host "Deleted $toDelete old backup(s)."
  }
}

function Apply-Wrapper([string]$WrapperDir) {
  # best-effort stop
  $gradlewBat = Join-Path $RootDir "gradlew.bat"
  if (Test-Path $gradlewBat) {
    cmd /c "`"$gradlewBat`" --stop" *> $null
  }

  Backup-Current

  New-Item -ItemType Directory -Force (Join-Path $RootDir "gradle\wrapper") | Out-Null
  Copy-Item (Join-Path $WrapperDir "gradlew") (Join-Path $RootDir "gradlew") -Force
  Copy-Item (Join-Path $WrapperDir "gradlew.bat") (Join-Path $RootDir "gradlew.bat") -Force
  Copy-Item (Join-Path $WrapperDir "gradle\wrapper\gradle-wrapper.jar") (Join-Path $RootDir "gradle\wrapper\gradle-wrapper.jar") -Force
  Copy-Item (Join-Path $WrapperDir "gradle\wrapper\gradle-wrapper.properties") (Join-Path $RootDir "gradle\wrapper\gradle-wrapper.properties") -Force
}

$wrappers = Get-Wrappers
if ($List) {
  $wrappers | ForEach-Object { Write-Host ("{0} -> Gradle {1}.x ({2})" -f $_.Name, $_.Major, $_.Ver) }
  exit 0
}

if ($Use) {
  $target = $wrappers | Where-Object { $_.Name -eq $Use } | Select-Object -First 1
  if (-not $target) { Write-Error "Wrapper not found: $Use"; exit 1 }
  Apply-Wrapper $target.Dir
  Write-Host ("Switched to: {0}.x (Pinned version: {1})" -f ($target.Name -replace '\D', ''), $target.Ver)
  Write-Host "Tip: In IntelliJ IDEA or Android Studio, use ""Reload All Gradle Projects"" if needed."
  exit 0
}

if (-not $wrappers -or $wrappers.Count -eq 0) {
  Write-Error "No valid wrappers found under: $WrappersDir"
  exit 1
}

Write-Host "Please choose the Gradle version to switch to:"
for ($i=0; $i -lt $wrappers.Count; $i++) {
  $w = $wrappers[$i]
  Write-Host ("{0}. Gradle {1}.x (Pinned version: {2})" -f ($i+1), $w.Major, $w.Ver)
}

$choice = Read-Host "> "
if ($choice -notmatch '^\d+$') { Write-Error "Invalid selection."; exit 1 }
$idx = [int]$choice - 1
if ($idx -lt 0 -or $idx -ge $wrappers.Count) { Write-Error "Out of range."; exit 1 }

$picked = $wrappers[$idx]
Apply-Wrapper $picked.Dir
Write-Host ("Switched to: Gradle {0}.x (Pinned version: {1})" -f ($picked.Name -replace '\D', ''), $picked.Ver)
Write-Host "Tip: In IntelliJ IDEA or Android Studio, use ""Reload All Gradle Projects"" if needed."
