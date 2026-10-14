# Maven Wrapper para Windows PowerShell
# Descarga Apache Maven 3.9.6 en ~/.mvn/wrapper si no está instalado.
#
# Uso:
#   .\mvnw.ps1 clean install
#   .\mvnw.ps1 test
#   .\mvnw.ps1 spring-boot:run
#
# Este script NO requiere que Maven esté en el PATH del sistema.

$ErrorActionPreference = 'Stop'

$MavenVersion = "3.9.6"
$MavenCache   = Join-Path $env:USERPROFILE ".mvn\wrapper"
$MavenHome    = Join-Path $MavenCache "apache-maven-$MavenVersion"
$MavenCmd     = Join-Path $MavenHome "bin\mvn.cmd"
$DistUrl      = "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$MavenVersion/apache-maven-$MavenVersion-bin.zip"
$DistZip      = Join-Path $MavenCache "mvn-dist-$MavenVersion.zip"

if (-not (Test-Path $MavenCmd)) {
    Write-Host "Descargando Apache Maven $MavenVersion en $MavenHome ..."
    if (-not (Test-Path $MavenCache)) {
        New-Item -ItemType Directory -Path $MavenCache -Force | Out-Null
    }
    Invoke-WebRequest -Uri $DistUrl -OutFile $DistZip
    Expand-Archive -Path $DistZip -DestinationPath $MavenCache -Force
    Remove-Item $DistZip -Force
    Write-Host "Maven $MavenVersion descargado correctamente."
}

& $MavenCmd $args
exit $LASTEXITCODE
