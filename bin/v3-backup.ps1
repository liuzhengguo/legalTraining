#requires -Version 7.0
[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('Backup', 'Restore', 'List')][string]$Action,
    [Parameter(Mandatory)][string]$BackupRoot,
    [string]$MysqlBin,
    [string]$ClientOptions,
    [ValidatePattern('^[A-Za-z][A-Za-z0-9_]{0,63}$')][string]$Database,
    [string]$UploadPath,
    [ValidatePattern('^\d{8}T\d{6}Z-[a-f0-9]{12}$')][string]$BackupId,
    [switch]$WithBinlog,
    [switch]$SkipUploads,
    [switch]$WithMediaJournal
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Resolve-Directory([string]$Path) {
    $item = Get-Item -LiteralPath $Path
    if (!$item.PSIsContainer -or ($item.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
        throw "目录必须是实际目录：$Path"
    }
    return $item.FullName
}

function Get-VerifiedChild([string]$Parent, [string]$Name) {
    $path = [IO.Path]::GetFullPath([IO.Path]::Combine($Parent, $Name))
    $separator = [IO.Path]::DirectorySeparatorChar
    if (!$path.StartsWith($Parent.TrimEnd($separator) + $separator, [StringComparison]::Ordinal)) {
        throw "路径越出备份目录：$Name"
    }
    return $path
}

function Get-Client([string]$Name) {
    $suffix = if ($IsWindows) { '.exe' } else { '' }
    return (Get-Item -LiteralPath (Join-Path $MysqlBin ($Name + $suffix))).FullName
}

function Assert-Success([string]$Operation) {
    if ($LASTEXITCODE -ne 0) { throw "$Operation 失败（退出码 $LASTEXITCODE）" }
}

function New-FullBackup([string]$Purpose) {
    $startedUtc = [DateTimeOffset]::UtcNow.ToString('o')
    $id = [DateTime]::UtcNow.ToString("yyyyMMdd'T'HHmmss'Z'") + '-' + [Guid]::NewGuid().ToString('N').Substring(0, 12)
    $staging = Get-VerifiedChild $root ('.staging-' + $id)
    $published = Get-VerifiedChild $root $id
    New-Item -ItemType Directory -Path $staging | Out-Null
    $dump = Join-Path $staging 'database.sql'
    $archive = Join-Path $staging 'uploads.zip'
    Write-Progress -Activity '生成全量备份' -Status '导出 MySQL'
    $dumpClient = Get-Client 'mysqldump'
    if ($WithMediaJournal -and $Purpose -eq 'manual') {
        $mediaClient = Get-Client 'mysql'
        & $mediaClient $optionArgument $Database '--execute=SELECT 1 FROM ops_media_state LIMIT 1; SELECT 1 FROM ops_media_chunk LIMIT 1' | Out-Null
        Assert-Success '检查素材日志表'
    }
    $coordinates = @(if ($WithBinlog -and $Purpose -eq 'manual') { '--source-data=2' })
    & $dumpClient $optionArgument @coordinates '--single-transaction' '--quick' '--hex-blob' '--no-tablespaces' '--set-gtid-purged=OFF' "--result-file=$dump" $Database
    Assert-Success '导出数据库'
    Write-Progress -Activity '生成全量备份' -Status '归档附件与素材'
    $link = Get-ChildItem -LiteralPath $uploads -Recurse -Force -Attributes ReparsePoint | Select-Object -First 1
    if ($link) { throw '上传目录含符号链接或目录联接' }
    [IO.Compression.ZipFile]::CreateFromDirectory($uploads, $archive, [IO.Compression.CompressionLevel]::Optimal, $false)
    $authorizationArchive = $null
    $authorizationPath = $uploads + '-authorizations'
    if ($WithMediaJournal -and $Purpose -eq 'manual' -and (Test-Path -LiteralPath $authorizationPath)) {
        $authorizationRoot = Resolve-Directory $authorizationPath
        if (Get-ChildItem -LiteralPath $authorizationRoot -Recurse -Force -Attributes ReparsePoint | Select-Object -First 1) {
            throw '私有授权书目录包含链接'
        }
        $authorizationArchive = Join-Path $staging 'authorizations.zip'
        [IO.Compression.ZipFile]::CreateFromDirectory($authorizationRoot, $authorizationArchive)
    }
    $manifest = [ordered]@{
        format = 1
        id = $id
        type = 'full'
        mediaJournal = [bool]($WithMediaJournal -and $Purpose -eq 'manual')
        purpose = $Purpose
        database = $Database
        startedUtc = $startedUtc
        completedUtc = [DateTime]::UtcNow.ToString('o')
        databaseSha256 = (Get-FileHash -LiteralPath $dump -Algorithm SHA256).Hash
        uploadsSha256 = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash
    }
    if ($authorizationArchive) { $manifest.authorizationsSha256 = (Get-FileHash -LiteralPath $authorizationArchive).Hash }
    if ($coordinates.Count) {
        $coordinate = Get-Content -LiteralPath $dump -TotalCount 80 | Where-Object { $_ -match 'LOG_FILE=.*LOG_POS=' } | Select-Object -First 1
        if ($coordinate -notmatch "LOG_FILE='([A-Za-z0-9_.-]+)',\s*(?:SOURCE|MASTER)_LOG_POS=(\d+)") {
            throw '全量备份缺少二进制日志坐标'
        }
        $manifest.binlog = $Matches[1]
        $manifest.position = [long]$Matches[2]
        $mysql = Get-Client 'mysql'
        $manifest.serverUuid = & $mysql $optionArgument --batch --skip-column-names '--execute=SELECT @@server_uuid'
        Assert-Success '读取 MySQL 实例标识'
    }
    $manifest | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $staging 'manifest.json') -Encoding utf8NoBOM
    Move-Item -LiteralPath $staging -Destination $published
    Write-Progress -Activity '生成全量备份' -Completed
    return [pscustomobject]$manifest
}

$root = Resolve-Directory $BackupRoot
if ($Action -eq 'List') {
    Get-ChildItem -LiteralPath $root -Directory |
        Where-Object { $_.Name -match '^\d{8}T\d{6}Z-[a-f0-9]{12}$' -and !($_.Attributes -band [IO.FileAttributes]::ReparsePoint) } |
        ForEach-Object { Get-Content -Raw -LiteralPath (Join-Path $_.FullName 'manifest.json') | ConvertFrom-Json } |
        Sort-Object completedUtc -Descending
    return
}

if (!$MysqlBin -or !$ClientOptions -or !$Database -or !$UploadPath) {
    throw '备份和恢复需要 MysqlBin、ClientOptions、Database 和 UploadPath'
}
$uploads = Resolve-Directory $UploadPath
$separator = [IO.Path]::DirectorySeparatorChar
$relativeBackup = [IO.Path]::GetRelativePath($uploads, $root)
if ($relativeBackup -eq '.' -or ($relativeBackup -ne '..' -and !$relativeBackup.StartsWith("..$separator") -and ![IO.Path]::IsPathRooted($relativeBackup))) {
    throw '备份目录必须位于上传目录之外'
}
$optionArgument = '--defaults-file=' + (Get-Item -LiteralPath $ClientOptions).FullName
$lock = [IO.File]::Open((Get-VerifiedChild $root '.backup.lock'), 'OpenOrCreate', 'ReadWrite', 'None')
try {
    if ($Action -eq 'Backup') { New-FullBackup 'manual'; return }
    if (!$BackupId) { throw '恢复时必须指定 BackupId' }
    $backup = Resolve-Directory (Get-VerifiedChild $root $BackupId)
    $manifest = Get-Content -Raw -LiteralPath (Join-Path $backup 'manifest.json') | ConvertFrom-Json
    if ($manifest.id -ne $BackupId -or $manifest.format -ne 1 -or $manifest.type -ne 'full') {
        throw '备份清单格式无效'
    }
    $dump = Join-Path $backup 'database.sql'
    $archive = Join-Path $backup 'uploads.zip'
    if ((Get-FileHash -LiteralPath $dump -Algorithm SHA256).Hash -ne $manifest.databaseSha256 -or
        (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ne $manifest.uploadsSha256) {
        throw '备份文件校验失败'
    }

    $client = Get-Client 'mysql'
    $query = '--execute=SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()'
    $tableCount = & $client $optionArgument '--batch' '--skip-column-names' $Database $query
    Assert-Success '检查目标数据库'
    if ("$tableCount".Trim() -ne '0' -or (Get-ChildItem -LiteralPath $uploads -Force | Select-Object -First 1)) {
        throw '恢复目标必须是独立的空数据库和空上传目录'
    }

    $before = New-FullBackup 'before-restore'
    if ($manifest.PSObject.Properties['mediaJournal'] -and $manifest.mediaJournal) {
        $private = $uploads + '-authorizations'
        if (Test-Path -LiteralPath $private) {
            $private = Resolve-Directory $private
            if (Get-ChildItem -LiteralPath $private -Force | Select-Object -First 1) {
                throw '恢复目标的私有授权书目录必须为空'
            }
        }
    }
    Write-Progress -Activity '恢复备份' -Status '恢复 MySQL'
    $process = [Diagnostics.ProcessStartInfo]::new($client)
    $process.UseShellExecute = $false
    $process.RedirectStandardInput = $true
    foreach ($arg in @($optionArgument, '--binary-mode', $Database)) { $process.ArgumentList.Add($arg) }
    $restore = [Diagnostics.Process]::Start($process)
    $source = [IO.File]::OpenRead($dump)
    try {
        $source.CopyTo($restore.StandardInput.BaseStream)
        $restore.StandardInput.Close()
        $restore.WaitForExit()
        if ($restore.ExitCode -ne 0) { throw "恢复数据库失败（退出码 $($restore.ExitCode)）" }
    } finally {
        $source.Dispose()
        $restore.StandardInput.Dispose()
        $restore.Dispose()
    }
    Write-Progress -Activity '恢复备份' -Status '恢复附件与素材'
    if (!$SkipUploads) {
        [IO.Compression.ZipFile]::ExtractToDirectory($archive, $uploads)
        if ($manifest.PSObject.Properties['mediaJournal'] -and $manifest.mediaJournal) {
            if ($manifest.PSObject.Properties['authorizationsSha256']) {
                $authorizationArchive = Join-Path $backup 'authorizations.zip'
                if ((Get-FileHash -LiteralPath $authorizationArchive).Hash -ne $manifest.authorizationsSha256) { throw '授权书归档校验失败' }
                [IO.Compression.ZipFile]::ExtractToDirectory($authorizationArchive, $private)
            }
            & (Join-Path $PSScriptRoot 'v3-media-restore.ps1') -Mysql $client -ClientOptionsArgument $optionArgument -Database $Database -UploadPath $uploads
        }
    }
    Write-Progress -Activity '恢复备份' -Completed
    [pscustomobject]@{ backupId = $BackupId; database = $Database; preRestoreBackupId = $before.id; restoredUtc = [DateTime]::UtcNow.ToString('o') }
} finally {
    $lock.Dispose()
}
