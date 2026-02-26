param(
    [string]$BaseUrl = "http://localhost:8090",
    [int]$RequestTimeoutSec = 20
)

$ErrorActionPreference = "Stop"

$endpoint = "$BaseUrl/check-ebook"
$samples = @(
    @{ title = "Honja Java - 1:1 Subtitle"; author = ""; publisher = "" },
    @{ title = "JPA ORM Guide: Practical"; author = ""; publisher = "" },
    @{ title = "Seolguk (Revised)"; author = ""; publisher = "" }
)

Write-Host "Endpoint: $endpoint"
Write-Host "Timeout : ${RequestTimeoutSec}s"
Write-Host ""

foreach ($sample in $samples) {
    $payload = $sample | ConvertTo-Json -Compress
    $sw = [System.Diagnostics.Stopwatch]::StartNew()

    try {
        $resp = Invoke-RestMethod `
            -Method POST `
            -Uri $endpoint `
            -ContentType "application/json; charset=utf-8" `
            -Body $payload `
            -TimeoutSec $RequestTimeoutSec

        $sw.Stop()
        $elapsed = [Math]::Round($sw.Elapsed.TotalMilliseconds, 0)
        $title = $resp.title
        $status = $resp.status_text
        $found = $resp.found
        $total = $resp.total_holdings
        $avail = $resp.available_holdings
        $url = $resp.deep_link_url
        $err = $resp.error_message

        Write-Host "-----"
        Write-Host "title            : $title"
        Write-Host "elapsed_ms       : $elapsed"
        Write-Host "status_text      : $status"
        Write-Host "found            : $found"
        Write-Host "total_holdings   : $total"
        Write-Host "available_holdings: $avail"
        Write-Host "deep_link_url    : $url"
        Write-Host "error_message    : $err"
    }
    catch {
        $sw.Stop()
        $elapsed = [Math]::Round($sw.Elapsed.TotalMilliseconds, 0)
        Write-Host "-----"
        Write-Host "title            : $($sample.title)"
        Write-Host "elapsed_ms       : $elapsed"
        Write-Host "request_error    : $($_.Exception.Message)"
    }
}

Write-Host ""
Write-Host "Done."
