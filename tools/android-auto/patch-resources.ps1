param(
  [string]$Original = "$env:USERPROFILE\Documents\AndroidAutoOfflinePatch\original-splits\base.apk",
  [string]$Output = "$env:USERPROFILE\Documents\AndroidAutoOfflinePatch\base-minimal-unsigned.apk",
  [ValidateRange(1,127)][int]$ValueDp = 1
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
if (-not (Test-Path -LiteralPath $Original)) { throw 'Original base APK missing' }
if (Test-Path -LiteralPath $Output) { throw 'Output already exists; choose a new path' }
$source=[IO.Compression.ZipFile]::OpenRead($Original)
try {
  $entry=$source.GetEntry('resources.arsc'); if (-not $entry) { throw 'resources.arsc missing' }
  $stream=$entry.Open(); $memory=[IO.MemoryStream]::new()
  try { $stream.CopyTo($memory); $data=$memory.ToArray() } finally { $stream.Dispose(); $memory.Dispose() }
} finally { $source.Dispose() }
function U16([int]$p) { [BitConverter]::ToUInt16($data,$p) }
function U32([int]$p) { [BitConverter]::ToUInt32($data,$p) }
if ((U16 0) -ne 2 -or (U32 4) -ne $data.Length) { throw 'Unexpected resource table header' }
$targets=@{0x165='car_rail_height';0x17b='cielo_rail_height';0x347='floating_rail_space_height';0xba8='rail_height'}
$found=@{}; $changes=New-Object 'System.Collections.Generic.List[string]'
for($p=[int](U16 2); $p -lt $data.Length;) {
  $size=[int](U32 ($p+4)); $head=[int](U16 ($p+2)); if($size -lt $head -or $size -le 0 -or ($p+$size) -gt $data.Length){throw 'Invalid table chunk'}
  if((U16 $p) -eq 0x200 -and (U32 ($p+8)) -eq 0x7f) {
    $typeOffset=if($head -ge 288){[int](U32 ($p+284))}else{0}
    for($q=$p+$head; $q -lt $p+$size;) {
      $cs=[int](U32 ($q+4)); $ch=[int](U16 ($q+2)); if($cs -lt $ch -or $cs -le 0 -or ($q+$cs) -gt ($p+$size)){throw 'Invalid package chunk'}
      if((U16 $q) -eq 0x201 -and ([int]$data[$q+8]+$typeOffset) -eq 7) {
        $flags=[int]$data[$q+9]; $count=[int](U32 ($q+12)); $start=[int](U32 ($q+16))
        foreach($index in $targets.Keys) {
          $off=-1
          if($flags -band 1) { for($i=0;$i -lt $count;$i++){ $at=$q+$ch+4*$i; if((U16 $at) -eq $index){$off=[int](U16 ($at+2))*4;break} } }
          elseif($index -lt $count) { if($flags -band 2){$v=U16 ($q+$ch+2*$index);if($v -ne 0xffff){$off=[int]$v*4}}else{$v=U32 ($q+$ch+4*$index);if($v -ne 0xffffffff){$off=[int]$v}} }
          if($off -ge 0) {
            $e=$q+$start+$off; if($e -lt $q -or ($e+16) -gt ($q+$cs)){throw 'Entry outside chunk'}
            $entrySize=[int](U16 $e); $entryFlags=U16 ($e+2); if($entryFlags -band 1){throw "Complex value: $($targets[$index])"}
            $value=$e+$entrySize; if((U16 $value) -ne 8 -or $data[$value+3] -ne 5){throw "Not a dimension: $($targets[$index])"}
            $old=U32 ($value+4); if(($old -band 0xf) -ne 1){throw "Not dp: $($targets[$index])"}
            [BitConverter]::GetBytes([uint32](($ValueDp -shl 8) -bor 1)).CopyTo($data,$value+4)
            $found[$index]=1+$found[$index]; $changes.Add("$($targets[$index]) old=0x$($old.ToString('x8')) offset=$($value+4)")
          }
        }
      }
      $q += $cs
    }
  }
  $p += $size
}
foreach($id in $targets.Keys){if(-not $found.ContainsKey($id)){throw "Missing $($targets[$id])"}}
Copy-Item -LiteralPath $Original -Destination $Output
$zip=[IO.Compression.ZipFile]::Open($Output,[IO.Compression.ZipArchiveMode]::Update)
try {$old=$zip.GetEntry('resources.arsc');$old.Delete();$new=$zip.CreateEntry('resources.arsc',[IO.Compression.CompressionLevel]::NoCompression);$s=$new.Open();try{$s.Write($data,0,$data.Length)}finally{$s.Dispose()}}finally{$zip.Dispose()}
$changes | ForEach-Object { Write-Output $_ }
Write-Output "Unsigned output: $Output"

