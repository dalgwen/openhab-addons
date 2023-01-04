$scriptpath = $MyInvocation.MyCommand.Path
$dir = Split-Path $scriptpath
cd $dir\..\
$HABSPEAKER_VERSION = (Get-Content package.json) -join "`n" | ConvertFrom-Json | Select -ExpandProperty "version"
$LIBRESPOT_VERSION = "v0.4.2"
Write-Host "Builing HABSpeaker $HABSPEAKER_VERSION electron exe for windown x86_64"
if (-not(Test-Path -Path librespot-src\Cargo.toml -PathType Leaf)) {
    Write-Host "Clonning Librespot $LIBRESPOT_VERSION"
    git -c advice.detachedHead=false clone --quiet --branch $LIBRESPOT_VERSION https://github.com/librespot-org/librespot.git librespot-src
}
 else {
     Write-Host "Skipping Librespot clone"
}
$RUST_TARGET="x86_64-pc-windows-msvc"
$LIBRESPOT_BINARY="librespot-src\target\x86_64-pc-windows-msvc\release\librespot.exe"
$LIBRESPOT_LIBRARY="librespot-src\target\x86_64-pc-windows-msvc\release\liblibrespot.rlib"
if (-not(Test-Path -Path $LIBRESPOT_BINARY -PathType Leaf)) {
    Write-Host "Builing Librespot"
    cd librespot-src
    cargo build --release --target $RUST_TARGET
    cd ..\
    sleep 5
}
 else {
     Write-Host "Skipping Librespot build"
}
Write-Host "Copying Librespot binaries"
cp $LIBRESPOT_BINARY librespot\
cp $LIBRESPOT_LIBRARY librespot\
Write-Host "Build HABSpeaker electron exe"
npm ci
$env:ELECTRON_BUILD="true"
Start-Process "$(npm bin)\vite.cmd" -wait -NoNewWindow -ArgumentList "build","--mode","electron"
Start-Process "$(npm bin)\electron-builder.cmd" -wait -NoNewWindow
