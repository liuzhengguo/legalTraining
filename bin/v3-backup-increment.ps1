#requires -Version 7.0
param(
 [Parameter(Mandatory)][ValidateSet('Increment','Replay','Prune')][string]$Action,
 [Parameter(Mandatory)][string]$BackupRoot,
 [string]$MysqlBin,
 [string]$ClientOptions,
 [Parameter(Mandatory)][ValidatePattern('^[A-Za-z][A-Za-z0-9_]{0,63}$')][string]$Database,
 [string]$UploadPath,
 [ValidatePattern('^\d{8}T\d{6}Z-[a-f0-9]{12}$')][string]$BackupId,
 [ValidatePattern('^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\dZ$')][string]$RecoverBeforeUtc,
 [ValidateRange(1,3650)][int]$RetainDays=30,
 [switch]$WithMediaJournal
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$rootItem=Get-Item -LiteralPath $BackupRoot
if (!$rootItem.PSIsContainer -or ($rootItem.Attributes -band [IO.FileAttributes]::ReparsePoint)) {throw '备份根目录必须为实际目录'}
$root=$rootItem.FullName
$full=Join-Path $PSScriptRoot 'v3-backup.ps1'

function Safe([string]$Parent,[string]$Name) {
 if ($Name -notmatch '^[a-zA-Z0-9_.-]+$' -or $Name -in '.', '..') {throw '无效备份路径'}
 $path=[IO.Path]::GetFullPath((Join-Path $Parent $Name))
 $prefix=$Parent.TrimEnd([IO.Path]::DirectorySeparatorChar)+[IO.Path]::DirectorySeparatorChar
 if (!$path.StartsWith($prefix,[StringComparison]::Ordinal)) {throw '备份路径越界'}
 if ((Test-Path -LiteralPath $path) -and ((Get-Item -LiteralPath $path).Attributes -band [IO.FileAttributes]::ReparsePoint)) {
  throw '备份路径包含链接'
 }
 $path
}
function Manifest([string]$Id) {
 if ($Id -notmatch '^\d{8}T\d{6}Z-[a-f0-9]{12}$') {throw '无效备份编号'}
 $entry=Get-Content -LiteralPath (Safe (Safe $root $Id) 'manifest.json') -Raw | ConvertFrom-Json
 if ($entry.id -ne $Id -or $entry.format -ne 1) {throw '无效备份清单'}
 $entry
}
function Check([string]$Text) { if ($LASTEXITCODE -ne 0) {throw "$Text （退出码 $LASTEXITCODE）"} }
function Query([string]$Sql) {
 & $mysql $options --batch --skip-column-names "--execute=$Sql"
 Check '读取 MySQL 状态失败'
}
function New-BackupId {
 [DateTimeOffset]::UtcNow.ToString("yyyyMMdd'T'HHmmss'Z'")+'-'+[Guid]::NewGuid().ToString('N').Substring(0,12)
}
if ($Action -ne 'Prune') {
 if (!$MysqlBin -or !$ClientOptions -or !$UploadPath) {throw '需要 MysqlBin、ClientOptions 和 UploadPath'}
 $ext=if ($IsWindows) {'.exe'} else {''}
 $mysql=(Get-Item -LiteralPath (Join-Path $MysqlBin ('mysql'+$ext))).FullName
 $decoder=(Get-Item -LiteralPath (Join-Path $MysqlBin ('mysqlbinlog'+$ext))).FullName
 $options='--defaults-file='+(Get-Item -LiteralPath $ClientOptions).FullName
}
$lockName=if ($Action -eq 'Replay') {'.replay.lock'} else {'.backup.lock'}
$pruneGuard=$null
if ($Action -eq 'Prune') {$pruneGuard=[IO.File]::Open((Safe $root '.replay.lock'),'OpenOrCreate','ReadWrite','None')}
$lock=$null
try {
 $lock=[IO.File]::Open((Safe $root $lockName),'OpenOrCreate','ReadWrite','None')
 if ($Action -eq 'Prune') {
  $entries=@(& $full -Action List -BackupRoot $root)
  $cutoff=[DateTimeOffset]::UtcNow.AddDays(-$RetainDays)
  $protected=@($entries | Where-Object {$_.type -eq 'incr' -and [DateTimeOffset]$_.completedUtc -ge $cutoff} | ForEach-Object baseId)
  $latest=$entries | Where-Object {$_.type -eq 'full' -and $_.purpose -eq 'manual' -and $_.database -eq $Database} | Select-Object -First 1
  foreach ($entry in $entries) {
   if ($entry.database -ne $Database -or $entry.purpose -eq 'before-restore' -or $entry.id -in $protected -or
       ($latest -and $latest.id -eq $entry.id) -or [DateTimeOffset]$entry.completedUtc -ge $cutoff) {continue}
   $checked=Manifest $entry.id
   $target=Safe $root $checked.id
   Remove-Item -LiteralPath $target -Recurse -Force
   $checked.id
  }
  return
 }
 if ($Action -eq 'Increment') {
  $base=if ($BackupId) {Manifest $BackupId} else {
   & $full -Action List -BackupRoot $root |
    Where-Object {$_.type -eq 'full' -and $_.purpose -eq 'manual' -and $_.database -eq $Database} | Select-Object -First 1
  }
  if (!$base -or !$base.PSObject.Properties['binlog'] -or $base.database -ne $Database -or $base.type -ne 'full') {
   throw '未找到带二进制日志坐标的同库全量备份'
  }
  $server=(Query 'select @@server_uuid,@@binlog_format') -split "\t"
  if ($server[0] -ne $base.serverUuid -or $server[1] -ne 'ROW') {throw '增量需要同一 MySQL 实例和 ROW 日志'}
  $end=(Query 'show master status') -split "\t"
  if ($end.Count -lt 2) {throw 'MySQL 二进制日志不可用'}
  $through=[DateTimeOffset]::UtcNow
  $logs=@(Query 'show binary logs' | ForEach-Object {($_ -split "\t")[0]})
  $first=[array]::IndexOf($logs,[string]$base.binlog)
  $last=[array]::IndexOf($logs,$end[0])
  if ($first -lt 0 -or $last -lt $first) {throw '日志已清理，请重新生成全量备份'}
  $names=@($logs[$first..$last])
  if (@($names | Where-Object {$_ -notmatch '^[A-Za-z0-9_-]+\.\d+$'}).Count) {throw '无效日志文件名'}
  $id=New-BackupId
  $staging=Safe $root ('.staging-'+$id)
  New-Item -ItemType Directory $staging | Out-Null
  $prefix=$staging+[IO.Path]::DirectorySeparatorChar
  & $decoder $options '--read-from-remote-server' '--raw' "--result-file=$prefix" @names
  Check '归档二进制日志失败'
  $files=@($names | ForEach-Object {@{name=$_; sha256=(Get-FileHash -LiteralPath (Safe $staging $_)).Hash}})
  $uploads=(Get-Item -LiteralPath $UploadPath).FullName
  $relative=[IO.Path]::GetRelativePath($uploads,$root)
  if ($relative -eq '.' -or ($relative -ne '..' -and !$relative.StartsWith('..'+[IO.Path]::DirectorySeparatorChar) -and
      ![IO.Path]::IsPathRooted($relative))) {throw '备份目录不能位于上传目录内'}
  if (Get-ChildItem -LiteralPath $uploads -Recurse -Force -Attributes ReparsePoint | Select-Object -First 1) {throw '上传目录包含链接'}
  $archive=Safe $staging 'uploads.zip'
  [IO.Compression.ZipFile]::CreateFromDirectory($uploads,$archive)
  $entry=[pscustomobject]@{
   format=1; id=$id; type='incr'; purpose='manual'; database=$Database; baseId=$base.id
   mediaJournal=[bool]($WithMediaJournal -and $base.PSObject.Properties['mediaJournal'] -and $base.mediaJournal)
   firstPosition=[long]$base.position; lastPosition=[long]$end[1]; replayThroughUtc=$through.ToString('o')
   completedUtc=[DateTimeOffset]::UtcNow.ToString('o'); uploadsSha256=(Get-FileHash -LiteralPath $archive).Hash; logs=$files
  }
  $entry | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Safe $staging 'manifest.json') -Encoding utf8NoBOM
  Move-Item -LiteralPath $staging -Destination (Safe $root $id)
  $entry
  return
 }
 if (!$BackupId) {throw '回放需要增量备份编号'}
 $delta=Manifest $BackupId
 if ($delta.type -ne 'incr') {throw '回放需要增量备份'}
 $base=Manifest $delta.baseId
 if ($base.type -ne 'full' -or $base.database -ne $delta.database -or $base.database -eq $Database -or
     $base.database -notmatch '^[A-Za-z][A-Za-z0-9_]{0,63}$') {throw '备份链或恢复目标无效'}
 $folder=Safe $root $delta.id
 $archive=Safe $folder 'uploads.zip'
 $managed=[bool]($delta.PSObject.Properties['mediaJournal'] -and $delta.mediaJournal -and $base.PSObject.Properties['mediaJournal'] -and $base.mediaJournal)
 if ((Get-FileHash -LiteralPath $archive).Hash -ne $delta.uploadsSha256) {throw '素材归档校验失败'}
 $arguments=@('--skip-gtids',"--rewrite-db=$($base.database)->$Database","--database=$Database",
  "--start-position=$([long]$delta.firstPosition)","--stop-position=$([long]$delta.lastPosition)")
 if ($RecoverBeforeUtc) {
  $point=[DateTimeOffset]::Parse($RecoverBeforeUtc)
  if ($point -le [DateTimeOffset]$base.completedUtc -or $point -gt [DateTimeOffset]$delta.replayThroughUtc) {throw '指定时点不在备份区间内'}
  if (!$managed -and $base.uploadsSha256 -ne $delta.uploadsSha256) {throw '此备份链的附件变化未记录，请恢复到增量归档点'}
  $arguments+="--stop-datetime=$($point.ToLocalTime().ToString('yyyy-MM-dd HH:mm:ss'))"
 }
 foreach ($record in $delta.logs) {
  if ($record.name -notmatch '^[A-Za-z0-9_-]+\.\d+$') {throw '无效日志文件名'}
  $file=Safe $folder $record.name
  if ((Get-FileHash -LiteralPath $file).Hash -ne $record.sha256) {throw '日志校验失败'}
  $arguments+=$file
 }
 $temp=Safe $root ('.replay-'+(New-BackupId)+'.sql')
 try {
  & $decoder --no-defaults "--result-file=$temp" @arguments
  Check '生成回放 SQL 失败'
  $initial=& $full -Action Restore -BackupRoot $root -MysqlBin $MysqlBin -ClientOptions $ClientOptions -Database $Database -UploadPath $UploadPath -BackupId $base.id -SkipUploads
  $invocation=[Diagnostics.ProcessStartInfo]::new($mysql)
  $invocation.UseShellExecute=$false
  $invocation.RedirectStandardInput=$true
  foreach ($arg in @($options,'--binary-mode',$Database)) {$invocation.ArgumentList.Add($arg)}
  $client=[Diagnostics.Process]::Start($invocation)
  $inputFile=[IO.File]::OpenRead($temp)
  try {
   $inputFile.CopyTo($client.StandardInput.BaseStream)
   $client.StandardInput.Close()
   $client.WaitForExit()
   if ($client.ExitCode -ne 0) {throw "回放日志失败（退出码 $($client.ExitCode)）"}
  } finally {$inputFile.Dispose(); $client.Dispose()}
  $destination=(Resolve-Path -LiteralPath $UploadPath).ProviderPath
  $media=if ($RecoverBeforeUtc -and $managed) {Safe (Safe $root $base.id) 'uploads.zip'} else {$archive}
  if ($RecoverBeforeUtc -and $managed -and (Get-FileHash -LiteralPath $media).Hash -ne $base.uploadsSha256) {throw '全量素材校验失败'}
  [IO.Compression.ZipFile]::ExtractToDirectory($media,$destination)
  if ($managed) {
   if ($base.PSObject.Properties['authorizationsSha256']) {
    $privateArchive=Safe (Safe $root $base.id) 'authorizations.zip'
    if ((Get-FileHash -LiteralPath $privateArchive).Hash -ne $base.authorizationsSha256) {throw '授权书归档校验失败'}
    [IO.Compression.ZipFile]::ExtractToDirectory($privateArchive,$destination+'-authorizations')
   }
   & (Join-Path $PSScriptRoot 'v3-media-restore.ps1') -Mysql $mysql -ClientOptionsArgument $options -Database $Database -UploadPath $destination
  }
  [pscustomobject]@{backupId=$BackupId;database=$Database;recoverBeforeUtc=$RecoverBeforeUtc;preRestoreBackupId=$initial.preRestoreBackupId}
 } finally {if (Test-Path -LiteralPath $temp) {Remove-Item -LiteralPath $temp}}
} finally {
 if ($lock) {$lock.Dispose()}
 if ($pruneGuard) {$pruneGuard.Dispose()}
}
