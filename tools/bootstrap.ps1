[CmdletBinding()]
param(
    [switch]$Offline,
    [switch]$SystemJava,
    [switch]$Frontend,
    [switch]$Browsers,
    [switch]$Check,
    [string]$DownloadDirectory
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$toolState = Join-Path $repositoryRoot '.dev-tools'
$manifest = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'development-tools.lock.json') -Raw | ConvertFrom-Json
if ($env:OS -ne 'Windows_NT' -or $env:PROCESSOR_ARCHITECTURE -notin @('AMD64', 'amd64')) {
    throw 'This launcher supports Windows x64. Use tools/bootstrap.sh on Linux/macOS.'
}
$pythonHome = Join-Path $toolState ('python-' + $manifest.pythonBootstrap.version)
$pythonBinary = Join-Path $pythonHome 'python.exe'

if (-not (Test-Path -LiteralPath $pythonBinary)) {
    if ($Check) { throw 'Python bootstrap is not prepared; run tools/bootstrap.ps1 first.' }
    $asset = $manifest.pythonBootstrap.'windows-x64'
    $downloadRoot = if ($DownloadDirectory) { [IO.Path]::GetFullPath($DownloadDirectory) } else { Join-Path $toolState 'downloads' }
    New-Item -ItemType Directory -Force -Path $downloadRoot | Out-Null
    $archiveName = [IO.Path]::GetFileName(([Uri]$asset.url).AbsolutePath)
    $archive = Join-Path $downloadRoot $archiveName
    $previous = Join-Path $repositoryRoot ('build/' + $archiveName)
    if (-not (Test-Path -LiteralPath $archive) -and (Test-Path -LiteralPath $previous)) {
        if ((Get-FileHash -LiteralPath $previous -Algorithm SHA256).Hash.ToLowerInvariant() -eq $asset.sha256) {
            Copy-Item -LiteralPath $previous -Destination $archive
        }
    }
    if (-not (Test-Path -LiteralPath $archive)) {
        if ($Offline) { throw "Offline Python archive missing: $archive" }
        $partial = $archive + '.part'
        try {
            [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
            Invoke-WebRequest -Uri $asset.url -OutFile $partial -UseBasicParsing
            if ((Get-FileHash -LiteralPath $partial -Algorithm SHA256).Hash.ToLowerInvariant() -ne $asset.sha256) {
                throw 'Python download SHA-256 mismatch'
            }
            Move-Item -LiteralPath $partial -Destination $archive
        } finally {
            if (Test-Path -LiteralPath $partial) { Remove-Item -LiteralPath $partial }
        }
    }
    if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant() -ne $asset.sha256) {
        throw "Python archive SHA-256 mismatch: $archive"
    }
    if (Test-Path -LiteralPath $pythonHome) { throw "Incomplete Python directory: move it aside first: $pythonHome" }
    # The official archive has been hash-verified before extraction. Never replace system Python.
    Expand-Archive -LiteralPath $archive -DestinationPath $pythonHome
}

$pythonVersion = & $pythonBinary -c 'import platform; print(platform.python_version())'
if ($LASTEXITCODE -ne 0 -or $pythonVersion.Trim() -ne $manifest.pythonBootstrap.version) {
    throw 'Project-local Python does not match the development lock'
}
$bootstrapArguments = @((Join-Path $PSScriptRoot 'bootstrap.py'))
if ($Offline) { $bootstrapArguments += '--offline' }
if ($SystemJava) { $bootstrapArguments += '--system-java' }
if ($Frontend) { $bootstrapArguments += '--frontend' }
if ($Browsers) { $bootstrapArguments += '--browsers' }
if ($Check) { $bootstrapArguments += '--check' }
if ($DownloadDirectory) { $bootstrapArguments += @('--download-dir', [IO.Path]::GetFullPath($DownloadDirectory)) }
& $pythonBinary @bootstrapArguments
if ($LASTEXITCODE -ne 0) { throw "Development bootstrap failed with exit code $LASTEXITCODE" }
