<#
.SYNOPSIS
Reviews build/sdk37-* artifacts and, with -Apply, removes only verified directories.
.DESCRIPTION
The default is a read-only inventory. Tracked files and reparse points cause a hard
failure before any deletion. Stop builds using these historical directories first.
The final fleet verification is stored separately under build/verification.
#>
[CmdletBinding(SupportsShouldProcess)]
param([switch]$Apply)

$ErrorActionPreference = 'Stop'
$repositoryPath = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).ProviderPath
$buildPath = (Resolve-Path -LiteralPath (Join-Path $repositoryPath 'build')).ProviderPath
if ((Get-Item -LiteralPath $buildPath -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) {
    throw 'The build directory is a reparse point.'
}
$verified = @()
foreach ($candidate in Get-ChildItem -LiteralPath $buildPath -Directory -Force) {
    if ($candidate.Name -notlike 'sdk37-*') { continue }
    $candidatePath = (Resolve-Path -LiteralPath $candidate.FullName).ProviderPath
    if ([IO.Path]::GetDirectoryName($candidatePath) -ine $buildPath -or
        $candidate.Attributes -band [IO.FileAttributes]::ReparsePoint) {
        throw "Unsafe candidate: $candidatePath"
    }
    $children = @(Get-ChildItem -LiteralPath $candidatePath -Recurse -Force)
    if (@($children | Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint }).Count) {
        throw "A descendant is a reparse point: $candidatePath"
    }
    $relativePath = 'build/' + $candidate.Name + '/'
    $trackedFiles = @(& git -C $repositoryPath ls-files -- $relativePath)
    if ($LASTEXITCODE -ne 0 -or $trackedFiles.Count) {
        throw "Git verification failed or tracked files were found: $candidatePath"
    }
    $files = @($children | Where-Object { -not $_.PSIsContainer })
    $verified += [pscustomobject]@{
        Path = $candidatePath
        Files = $files.Count
        Bytes = [long](($files | Measure-Object -Property Length -Sum).Sum)
    }
}
$verified | Format-Table Path, Files, @{Name='GiB';Expression={[math]::Round($_.Bytes / 1GB, 3)}} -AutoSize
if (-not $Apply) {
    Write-Output 'Inventory only. Use -Apply after stopping work that uses these directories.'
    return
}
foreach ($candidate in $verified) {
    if ($PSCmdlet.ShouldProcess($candidate.Path, 'Remove verified generated SDK 37 artifacts')) {
        Remove-Item -LiteralPath $candidate.Path -Recurse -Force
    }
}
