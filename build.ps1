# Сборка ШагоСад на ПК (аналог build.sh с телефона).
# aapt2 -> javac -> d8 -> zip -> apksigner
# Использование: pwsh -File build.ps1 [-Out out-pc] [-Install] [-FreshRes]

param(
    [string]$Out = 'out-pc',
    [switch]$Install,
    [switch]$FreshRes
)

# Continue, а не Stop: PowerShell 5.1 считает любой stderr нативной программы
# (например «Note: ...deprecated API» от javac) фатальной ошибкой.
# Реальные сбои ловит Check() по $LASTEXITCODE после каждого шага.
$ErrorActionPreference = 'Continue'

$root = 'C:\Godot\stepbuild'
$env:JAVA_HOME = 'C:\Godot\_env\jdk-17.0.20.1+1'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$bt   = 'C:\Godot\_env\android-sdk\build-tools\36.1.0'
$adb  = 'C:\Godot\_env\android-sdk\platform-tools\adb.exe'
$jar  = Join-Path $root 'sdk\android-35\android.jar'
$app  = Join-Path $root 'stepapp'
$ks   = 'C:\Godot\_from_phone\keys\stepkey.keystore'
# Пароль ключа подписи в скрипте не хранится: он берётся из переменной STEP_KS_PASS
# или из secrets.local.ps1 (этот файл в репозиторий не попадает).
$ksPass = $env:STEP_KS_PASS
if (-not $ksPass) {
    $secFile = Join-Path $root 'secrets.local.ps1'
    if (Test-Path $secFile) { . $secFile }
}
if (-not $ksPass) { throw 'Нет пароля от ключа: задайте STEP_KS_PASS или создайте secrets.local.ps1' }
$out  = Join-Path $root $Out
$resCache = Join-Path $root '.rescache'

# отпечаток сертификата, которым подписан установленный на телефоне APK
$expectedCert = 'fdf6f3e789eb18d8e23e10f802b0f93de09c761a43846b137237bda4bc691dce'

$MIN_SDK = 26
$TARGET_SDK = 35

function Step($n, $name) {
    $el = [math]::Round($script:sw.Elapsed.TotalSeconds, 1)
    Write-Host ("[{0}/5] {1}  (t+{2}s)" -f $n, $name, $el) -ForegroundColor Cyan
}
function Check($what) {
    if ($LASTEXITCODE -ne 0) { throw "$what завершился с кодом $LASTEXITCODE" }
}

$sw = [Diagnostics.Stopwatch]::StartNew()
$phase = [Diagnostics.Stopwatch]::StartNew()

Remove-Item $out -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path "$out\res", "$out\gen", "$out\classes", "$out\dex" | Out-Null

# ---------- 1/5 aapt2 compile (с кэшем) ----------
Step 1 'aapt2 compile'
New-Item -ItemType Directory -Force -Path $resCache | Out-Null
$cached = Join-Path $resCache 'res.zip'
$newestRes = Get-ChildItem "$app\res" -Recurse -File | Sort-Object LastWriteTime -Descending | Select-Object -First 1
$needCompile = $FreshRes -or (-not (Test-Path $cached)) -or ($newestRes.LastWriteTime -gt (Get-Item $cached).LastWriteTime)
if ($needCompile) {
    & "$bt\aapt2.exe" compile --dir "$app\res" -o $cached
    Check 'aapt2 compile'
    Write-Host ("      скомпилировано: {0} КБ, {1}s" -f [math]::Round((Get-Item $cached).Length / 1KB), [math]::Round($phase.Elapsed.TotalSeconds, 1))
} else {
    Write-Host ("      ресурсы не изменились, беру кэш .rescache ({0}s)" -f [math]::Round($phase.Elapsed.TotalSeconds, 1))
}
Copy-Item $cached "$out\res.zip" -Force

# ---------- 2/5 aapt2 link ----------
$phase.Restart()
Step 2 'aapt2 link'
& "$bt\aapt2.exe" link -o "$out\base.apk" -I $jar --manifest "$app\AndroidManifest.xml" `
    --java "$out\gen" --min-sdk-version $MIN_SDK --target-sdk-version $TARGET_SDK `
    --auto-add-overlay "$out\res.zip"
Check 'aapt2 link'
Write-Host ("      base.apk: {0} МБ, {1}s" -f [math]::Round((Get-Item "$out\base.apk").Length / 1MB, 1), [math]::Round($phase.Elapsed.TotalSeconds, 1))

# ---------- 3/5 javac ----------
$phase.Restart()
Step 3 'javac'
$sources = @(Get-ChildItem "$app\src" -Recurse -Filter *.java | ForEach-Object { $_.FullName })
$sources += @(Get-ChildItem "$out\gen" -Recurse -Filter R.java | ForEach-Object { $_.FullName })
# ASCII без BOM: javac не переносит BOM в @argfile и падает на «Invalid filename»
Set-Content "$out\sources.txt" $sources -Encoding ASCII
Write-Host ("      файлов к компиляции: {0}" -f $sources.Count)

# -encoding UTF-8 обязателен: в строках русский текст.
# -bootclasspath как в оригинале; если JDK не примет — откат на -classpath.
# Вывод javac в файл: на успехе он пишет «Note: ...deprecated API» в stderr,
# а PowerShell 5.1 раздувает это до красной ошибки. Показываем только при сбое.
$jerr = "$out\javac.err"
& "$env:JAVA_HOME\bin\javac.exe" -source 8 -target 8 -nowarn -encoding UTF-8 `
    -bootclasspath $jar -d "$out\classes" "@$out\sources.txt" 2> $jerr
if ($LASTEXITCODE -ne 0) {
    Write-Host '      -bootclasspath не принят, повтор с -classpath' -ForegroundColor Yellow
    & "$env:JAVA_HOME\bin\javac.exe" -source 8 -target 8 -nowarn -encoding UTF-8 `
        -classpath $jar -d "$out\classes" "@$out\sources.txt" 2> $jerr
}
if ($LASTEXITCODE -ne 0) {
    Get-Content $jerr | Select-Object -First 40 | Write-Host
    throw "javac завершился с кодом $LASTEXITCODE"
}
Check 'javac'
Write-Host ("      классов: {0}, {1}s" -f (Get-ChildItem "$out\classes" -Recurse -Filter *.class).Count, [math]::Round($phase.Elapsed.TotalSeconds, 1))

# ---------- 4/5 d8 ----------
$phase.Restart()
Step 4 'd8'
$classes = @(Get-ChildItem "$out\classes" -Recurse -Filter *.class | ForEach-Object { $_.FullName })
Set-Content "$out\classes.txt" $classes -Encoding ASCII
& "$bt\d8.bat" --min-api $MIN_SDK --lib $jar --output "$out\dex" "@$out\classes.txt"
Check 'd8'
Write-Host ("      classes.dex: {0} КБ, {1}s" -f [math]::Round((Get-Item "$out\dex\classes.dex").Length / 1KB), [math]::Round($phase.Elapsed.TotalSeconds, 1))

# ---------- 5/5 упаковка и подпись ----------
$phase.Restart()
Step 5 'упаковка и подпись'
Copy-Item "$out\base.apk" "$out\unsigned.apk" -Force
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open("$out\unsigned.apk", 'Update')
[System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, "$out\dex\classes.dex", 'classes.dex') | Out-Null
$zip.Dispose()

& "$bt\apksigner.bat" sign --ks $ks --ks-pass "pass:$ksPass" --key-pass "pass:$ksPass" `
    --out "$out\ShagoSad.apk" "$out\unsigned.apk"
Check 'apksigner sign'

# подпись должна совпасть с той, что стоит на телефоне, иначе обновление не встанет
$certs = (& "$bt\apksigner.bat" verify --print-certs "$out\ShagoSad.apk") -join "`n"
if ($certs -notmatch 'Signer #1 certificate SHA-256 digest:\s*([0-9a-fA-F]{64})') {
    throw 'не удалось прочитать отпечаток сертификата'
}
$gotCert = $Matches[1].ToLower()
if ($gotCert -ne $expectedCert) { throw "сертификат не совпал: $gotCert" }
& "$bt\apksigner.bat" verify "$out\ShagoSad.apk"
Check 'apksigner verify'
Write-Host ("      подпись совпадает с APK на телефоне: $($gotCert.Substring(0,16))..., {0}s" -f [math]::Round($phase.Elapsed.TotalSeconds, 1))

$apk = Get-Item "$out\ShagoSad.apk"
Write-Host ''
Write-Host ("ГОТОВО: {0}" -f $apk.FullName) -ForegroundColor Green
Write-Host ("        {0} МБ, всего {1}s" -f [math]::Round($apk.Length / 1MB, 1), [math]::Round($sw.Elapsed.TotalSeconds, 1)) -ForegroundColor Green
Write-Host ("        SHA256 {0}" -f (Get-FileHash $apk.FullName -Algorithm SHA256).Hash) -ForegroundColor Green

# ---------- копия на рабочий стол ----------
# На столе всегда лежит свежий APK: имя с версией, старые копии удаляются.
# Ошибка копирования не должна валить сборку: если папка недоступна, просто скажем.
try {
    $mf2 = [IO.File]::ReadAllText("$app\AndroidManifest.xml")
    $dName = ([regex]::Match($mf2, 'versionName="([^"]+)"')).Groups[1].Value
    $desktop = [Environment]::GetFolderPath('Desktop')
    if (-not $desktop -or -not (Test-Path $desktop)) { $desktop = Join-Path $env:USERPROFILE 'Desktop' }
    if (Test-Path $desktop) {
        Get-ChildItem $desktop -Filter 'ШагоСад-*.apk' -ErrorAction SilentlyContinue |
            Remove-Item -Force -ErrorAction SilentlyContinue
        $dest = Join-Path $desktop ("ШагоСад-{0}.apk" -f $dName)
        Copy-Item $apk.FullName $dest -Force
        Write-Host ("        на рабочем столе: {0}" -f $dest) -ForegroundColor Green
    } else {
        Write-Host '        рабочий стол не найден, копия пропущена' -ForegroundColor Yellow
    }
} catch {
    Write-Host ("        на рабочий стол не скопировалось: {0}" -f $_.Exception.Message) -ForegroundColor Yellow
}
# ---------- установка ----------
if ($Install) {
    Write-Host ''
    Write-Host 'Установка обновлением на телефон...' -ForegroundColor Cyan
    & $adb install -r $apk.FullName
    Check 'adb install'

    # ---------- выгрузка в папку телефона ----------
    # Папка Download/Шагомер — то, откуда APK пересылают людям. Раньше она
    # обновлялась руками и в ней остался APK от старой версии, поэтому теперь
    # каждый билд кладёт туда APK, паспорт, читай-меня, версию и исходники.
    $dist = '/sdcard/Download/Шагомер'
    $mfText = [IO.File]::ReadAllText("$app\AndroidManifest.xml")
    $vName = ([regex]::Match($mfText, 'versionName="([^"]+)"')).Groups[1].Value
    $vCode = ([regex]::Match($mfText, 'versionCode="([^"]+)"')).Groups[1].Value
    $verLabel = "$vName ($vCode)"
    $sha = (Get-FileHash $apk.FullName -Algorithm SHA256).Hash.ToLower()
    & $adb shell "mkdir -p '$dist'" | Out-Null

    # 1) APK под прежним именем — чтобы не плодить файлы на телефоне
    & $adb push $apk.FullName "$dist/ШагоСад.apk"
    Check 'adb push apk'

    # 2) версия и контрольная сумма
    $stamp = "версия $verLabel" + "`r`n" +
             "собрано " + (Get-Date -Format 'dd.MM.yyyy HH:mm') + "`r`n" +
             "SHA256 $sha" + "`r`n"
    $tmpVer = Join-Path $env:TEMP 'shagosad-version.txt'
    [IO.File]::WriteAllText($tmpVer, $stamp, (New-Object Text.UTF8Encoding($false)))
    & $adb push $tmpVer "$dist/версия.txt"
    Check 'adb push версия'

    # 3) паспорт: строку версии берём из манифеста, чтобы документ не отставал
    $proj = Join-Path $root 'PROJECT.md'
    $md = [IO.File]::ReadAllText($proj, [Text.Encoding]::UTF8)
    $md = [regex]::Replace($md, '- версия: versionCode \d+ / versionName [\w.]+',
                           "- версия: versionCode $vCode / versionName $vName")
    [IO.File]::WriteAllText($proj, $md, (New-Object Text.UTF8Encoding($false)))
    & $adb push $proj "$dist/ПАСПОРТ-ПРОЕКТА.md"
    Check 'adb push паспорт'

    # 4) читай-меня из шаблона в репозитории
    $readmeSrc = Join-Path $root 'distribution\ЧИТАЙ-МЕНЯ.txt'
    $readme = [IO.File]::ReadAllText($readmeSrc, [Text.Encoding]::UTF8)
    $readme = $readme.Replace('@VERSION@', $verLabel)
    $readme = $readme.Replace('@APK_SIZE@', ('{0:N0} КБ' -f ($apk.Length / 1KB) -replace ',', ' '))
    $tmpReadme = Join-Path $env:TEMP 'shagosad-readme.txt'
    [IO.File]::WriteAllText($tmpReadme, $readme, (New-Object Text.UTF8Encoding($false)))
    & $adb push $tmpReadme "$dist/ЧИТАЙ-МЕНЯ.txt"
    Check 'adb push readme'

    # 5) исходники
    $zipTmp = Join-Path $env:TEMP 'shagosad-src.zip'
    if (Test-Path $zipTmp) { Remove-Item $zipTmp -Force }
    Compress-Archive -Path "$app\src", "$app\res", "$app\AndroidManifest.xml", $proj, (Join-Path $root 'build.ps1') `
                     -DestinationPath $zipTmp -Force
    & $adb push $zipTmp "$dist/исходники-шагомер.zip"
    Check 'adb push исходники'

    # 6) контроль: файл на телефоне обязан совпасть с собранным
    $devSha = ((& $adb shell "sha256sum '$dist/ШагоСад.apk'") -split '\s+')[0]
    if ($devSha -ne $sha) { throw "APK на телефоне не совпал по хешу: $devSha != $sha" }
    Write-Host ("        выгружено в Download/Шагомер: {0}, хеш совпал" -f $verLabel) -ForegroundColor Green
}