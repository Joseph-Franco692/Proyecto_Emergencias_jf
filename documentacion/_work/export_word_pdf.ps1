param(
    [Parameter(Mandatory = $true)]
    [string]$InputDocx,
    [Parameter(Mandatory = $true)]
    [string]$OutputPdf
)

$ErrorActionPreference = "Stop"
$inputPath = (Resolve-Path -LiteralPath $InputDocx).Path
$outputPath = [IO.Path]::GetFullPath($OutputPdf)

$word = $null
$document = $null
try {
    $word = New-Object -ComObject Word.Application
    $word.Visible = $false
    $word.DisplayAlerts = 0
    $document = $word.Documents.Open($inputPath, $false, $false)
    foreach ($story in $document.StoryRanges) {
        $current = $story
        while ($null -ne $current) {
            $current.Fields.Update() | Out-Null
            $current = $current.NextStoryRange
        }
    }
    foreach ($toc in $document.TablesOfContents) {
        $toc.Update() | Out-Null
    }
    foreach ($tof in $document.TablesOfFigures) {
        $tof.Update() | Out-Null
    }
    $document.Repaginate()
    $document.Save()
    $pdfFormat = 17
    $document.ExportAsFixedFormat($outputPath, $pdfFormat)
}
finally {
    if ($null -ne $document) {
        $document.Close($true)
        [Runtime.InteropServices.Marshal]::FinalReleaseComObject($document) | Out-Null
    }
    if ($null -ne $word) {
        $word.Quit()
        [Runtime.InteropServices.Marshal]::FinalReleaseComObject($word) | Out-Null
    }
    [GC]::Collect()
    [GC]::WaitForPendingFinalizers()
}
