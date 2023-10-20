$scriptpath = $MyInvocation.MyCommand.Path
$dir = Split-Path $scriptpath
cd $dir\..\
$HABSPEAKER_VERSION = (Get-Content package.json) -join "`n" | ConvertFrom-Json | Select -ExpandProperty "version"
Write-Host "Builing HABSpeaker $HABSPEAKER_VERSION electron exe for windown x86_64"
Write-Host "Build HABSpeaker electron exe"
npm ci
npm run build:electron
