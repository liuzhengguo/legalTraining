#requires -Version 7.0
param(
 [Parameter(Mandatory)][string]$Mysql,
 [Parameter(Mandatory)][string]$ClientOptionsArgument,
 [Parameter(Mandatory)][string]$Database,
 [Parameter(Mandatory)][string]$UploadPath
)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$root=(Get-Item -LiteralPath $UploadPath).FullName
$privateRoot=$root+'-authorizations'
function Restore-Path([string]$Relative) {
 $directory=$root
 if ($Relative.StartsWith('@authorization/')) {
  $directory=$privateRoot
  $Relative=$Relative.Substring(15)
 }
 if (!$Relative -or $Relative -match '[\\:\x00-\x1f]' -or @($Relative.Split('/') | Where-Object {$_ -in '', '.', '..'}).Count) {
  throw '素材路径无效'
 }
 $target=[IO.Path]::GetFullPath((Join-Path $directory $Relative))
 if (!$target.StartsWith($directory+[IO.Path]::DirectorySeparatorChar,[StringComparison]::Ordinal)) {throw '素材路径越界'}
 return $target
}
$query='select hex(s.relative_path),s.deleted,s.byte_length,coalesce(c.part,-1),coalesce(hex(c.content),'''') from ops_media_state s left join ops_media_chunk c on c.path_hash=s.path_hash order by s.path_hash,c.part'
$process=[Diagnostics.ProcessStartInfo]::new($Mysql)
$process.UseShellExecute=$false
$process.RedirectStandardOutput=$true
foreach($arg in @($ClientOptionsArgument,'--batch','--raw','--skip-column-names','--quick',$Database,"--execute=$query")) {
 $process.ArgumentList.Add($arg)
}
$client=[Diagnostics.Process]::Start($process)
$openFile=$null
$current=''
$part=0
$remaining=0L
try {
 while ($null -ne ($line=$client.StandardOutput.ReadLine())) {
  $fields=$line.Split([char]9)
  if ($fields.Count -ne 5) {throw '素材记录格式无效'}
  if ($fields[0] -ne $current) {
   if ($openFile) {
    $openFile.Dispose()
    $openFile=$null
    if ($remaining -ne 0) {throw '素材记录长度不一致'}
   }
   $current=$fields[0]
   $target=Restore-Path ([Text.Encoding]::UTF8.GetString([Convert]::FromHexString($current)))
   $remaining=[long]$fields[2]
   $part=0
   if ($fields[1] -eq '1') {
    if (Test-Path -LiteralPath $target -PathType Leaf) {Remove-Item -LiteralPath $target}
    continue
   }
   [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($target)) | Out-Null
   $openFile=[IO.File]::Open($target,'Create','Write','None')
  }
  if (!$openFile) {continue}
  if ($fields[3] -eq '-1' -and $remaining -eq 0) {continue}
  if ([int]$fields[3] -ne $part) {throw '素材分片顺序不连续'}
  $data=[Convert]::FromHexString($fields[4])
  $openFile.Write($data,0,$data.Length)
  $remaining-=$data.Length
  if ($remaining -lt 0) {throw '素材记录长度不一致'}
  $part++
 }
 if ($remaining -ne 0) {throw '素材记录长度不一致'}
 $client.WaitForExit()
 if ($client.ExitCode -ne 0) {throw '读取素材记录失败'}
} finally {
 if ($openFile) {$openFile.Dispose()}
 if (!$client.HasExited) {$client.Kill()}
 $client.Dispose()
}
