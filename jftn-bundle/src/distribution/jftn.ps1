$ErrorActionPreference = 'Stop'
$javaName = if ([Environment]::OSVersion.Platform -eq 'Win32NT') { 'java.exe' } else { 'java' }
$bundledJava = Join-Path $PSScriptRoot "jre/bin/$javaName"
if (Test-Path -LiteralPath $bundledJava -PathType Leaf) {
    $jftnJava = $bundledJava
} elseif ($env:JAVA_HOME) {
    $jftnJava = Join-Path $env:JAVA_HOME "bin/$javaName"
} else {
    $jftnJava = $javaName
}
$classpath = (@('scripts', 'lib/*', 'lib/groovy/*', 'plugins/*') | ForEach-Object {
    Join-Path $PSScriptRoot $_
}) -join [IO.Path]::PathSeparator
Push-Location -LiteralPath $PSScriptRoot
try {
    & $jftnJava '-Dfile.encoding=UTF-8' '-cp' $classpath 'org.fidonet.jftn.JFtnRunner' @args
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
