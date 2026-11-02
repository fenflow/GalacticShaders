param([Parameter(Mandatory)] [string[]]$Classpath)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    New-Item -Force -ItemType Directory build/classes | Out-Null
    $sources = (Get-ChildItem src/main/java -Recurse -Filter *.java).FullName
    & javac --release 8 -proc:none -cp ($Classpath -join ';') -d build/classes @sources
    if ($LASTEXITCODE) { throw 'Compilation failed' }
    & jar cfm build/GalacticShaders-0.3.4-alpha.jar manifest.mf -C build/classes . -C src/main/resources .
    if ($LASTEXITCODE) { throw 'Packaging failed' }
} finally { Pop-Location }
