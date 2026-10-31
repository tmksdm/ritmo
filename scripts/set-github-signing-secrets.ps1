#Requires -Version 7.0
<#
Загружает параметры постоянного ключа Ritmo в GitHub Actions, не выводя секреты.

Использование (на машине, где лежат ключ и keystore.properties):
  pwsh -NoProfile -File scripts/set-github-signing-secrets.ps1

Секреты (Actions Secrets): RITMO_KEYSTORE_BASE64, RITMO_KEYSTORE_PASSWORD,
RITMO_KEY_PASSWORD. Публичная переменная (Actions Variable): RITMO_KEY_ALIAS.
Ожидаемый SHA-256 сертификата закреплён в workflow.

Значения передаются в stdin процесса gh через ProcessStartInfo без добавления
перевода строки; --body и аргументы командной строки для секретов не используются.
Скрипт не печатает значения ключа, паролей и alias. Повторный запуск безопасен:
значения перезаписываются.
#>
[CmdletBinding()]
param(
    [string]$Repo = 'tmksdm/ritmo',
    [string]$Keystore = 'D:\Pr\keys\ritmo-release.jks',
    [string]$PropertiesFile = (Join-Path (Split-Path -Parent $PSScriptRoot) 'keystore.properties')
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)

function ConvertFrom-JavaPropertyValue([string]$Value) {
    # Java Properties понимает escape-последовательности; повторяем основные,
    # чтобы пароль с обратной косой чертой не превратился в другой пароль.
    $builder = [System.Text.StringBuilder]::new()
    $i = 0
    while ($i -lt $Value.Length) {
        $ch = $Value[$i]
        if ($ch -ne '\') {
            [void]$builder.Append($ch)
            $i++
            continue
        }
        $i++
        if ($i -ge $Value.Length) { throw 'Некорректное значение в keystore.properties (висячая обратная косая черта).' }
        $next = [string]$Value[$i]
        switch ($next) {
            't' { [void]$builder.Append("`t"); $i++ }
            'n' { [void]$builder.Append("`n"); $i++ }
            'r' { [void]$builder.Append("`r"); $i++ }
            'f' { [void]$builder.Append("`f"); $i++ }
            'u' {
                if ($i + 4 -ge $Value.Length) { throw 'Некорректная \u-последовательность в keystore.properties.' }
                $hex = $Value.Substring($i + 1, 4)
                [void]$builder.Append([char][Convert]::ToInt32($hex, 16))
                $i += 5
            }
            default { [void]$builder.Append($next); $i++ }
        }
    }
    return $builder.ToString()
}

function Invoke-GhWithStdin([string[]]$Arguments, [string]$Value, [string]$What) {
    # gh читает значение из stdin, если не указан --body. Пишем строку как есть,
    # без завершающего перевода строки, чтобы пароль не изменился.
    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $script:GhPath
    foreach ($argument in $Arguments) { $startInfo.ArgumentList.Add($argument) }
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardInput = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.StandardInputEncoding = [System.Text.UTF8Encoding]::new($false)
    $startInfo.StandardOutputEncoding = [System.Text.UTF8Encoding]::new($false)
    $startInfo.StandardErrorEncoding = [System.Text.UTF8Encoding]::new($false)

    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    try {
        if (-not $process.Start()) { throw 'Не удалось запустить gh.' }
        $stdoutTask = $process.StandardOutput.ReadToEndAsync()
        $stderrTask = $process.StandardError.ReadToEndAsync()
        $process.StandardInput.Write($Value)
        $process.StandardInput.Close()
        $process.WaitForExit()
        [void]$stdoutTask.GetAwaiter().GetResult()
        [void]$stderrTask.GetAwaiter().GetResult()
        if ($process.ExitCode -ne 0) {
            throw "gh завершился с кодом $($process.ExitCode): $What."
        }
    } finally {
        $process.Dispose()
    }
}

function Set-RepoSecret([string]$Name, [string]$Value) {
    Invoke-GhWithStdin -Arguments @('secret', 'set', $Name, '--repo', $Repo) `
        -Value $Value -What "секрет $Name"
}

function Set-RepoVariable([string]$Name, [string]$Value) {
    Invoke-GhWithStdin -Arguments @('variable', 'set', $Name, '--repo', $Repo) `
        -Value $Value -What "переменная $Name"
}

$ghCommand = Get-Command gh -ErrorAction SilentlyContinue
if (-not $ghCommand) {
    throw 'GitHub CLI (gh) не найден в PATH.'
}
$script:GhPath = $ghCommand.Source
if ([string]::IsNullOrWhiteSpace($script:GhPath)) { $script:GhPath = $ghCommand.Name }
if (-not (Test-Path -LiteralPath $Keystore -PathType Leaf)) {
    throw "Ключ не найден: $Keystore"
}
if (-not (Test-Path -LiteralPath $PropertiesFile -PathType Leaf)) {
    throw "Файл параметров не найден: $PropertiesFile"
}

$properties = @{}
foreach ($line in [System.IO.File]::ReadAllLines($PropertiesFile)) {
    if ($line -match '^\s*[#!]' -or $line -match '^\s*$') { continue }
    $match = [regex]::Match($line, '^\s*([^=:\s]+)\s*[=:]\s*(.*)$')
    if (-not $match.Success) { continue }
    $raw = $match.Groups[2].Value
    if ($raw.EndsWith('\') -and -not $raw.EndsWith('\\')) {
        throw 'Многострочные значения в keystore.properties не поддерживаются.'
    }
    $properties[$match.Groups[1].Value] = ConvertFrom-JavaPropertyValue $raw
}

foreach ($name in 'storePassword', 'keyAlias', 'keyPassword') {
    if (-not $properties.ContainsKey($name) -or [string]::IsNullOrWhiteSpace($properties[$name])) {
        throw "В keystore.properties нет непустого поля $name."
    }
}

$keystoreBase64 = [Convert]::ToBase64String([System.IO.File]::ReadAllBytes($Keystore))
Set-RepoSecret 'RITMO_KEYSTORE_BASE64' $keystoreBase64
Set-RepoSecret 'RITMO_KEYSTORE_PASSWORD' $properties['storePassword']
Set-RepoSecret 'RITMO_KEY_PASSWORD' $properties['keyPassword']
Set-RepoVariable 'RITMO_KEY_ALIAS' $properties['keyAlias']

Write-Host "Готово: секреты ключа и переменная alias обновлены для $Repo. Значения не выводились."
Write-Host "Проверка: gh secret list --repo $Repo"
