$ErrorActionPreference = 'Stop'
if (-not $env:OPEN_PLATFORM_APP_ID -or -not $env:OPEN_PLATFORM_APP_SECRET) { throw '请设置 OPEN_PLATFORM_APP_ID 和 OPEN_PLATFORM_APP_SECRET' }
$baseUrl = if ($env:OPEN_PLATFORM_BASE_URL) { $env:OPEN_PLATFORM_BASE_URL } else { 'https://sandbox.example.invalid/sandbox/v1' }
$baseUrl = $baseUrl.TrimEnd('/')
$query = 'endTime=2026-08-18T02%3A00%3A00Z&page=1&pageSize=20&startTime=2026-08-18T01%3A00%3A00Z'
$timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds().ToString()
$nonce = [Guid]::NewGuid().ToString('N')
$sha256 = [Security.Cryptography.SHA256]::Create()
$emptyHash = ([BitConverter]::ToString($sha256.ComputeHash([byte[]]::new(0)))).Replace('-', '').ToLowerInvariant()
$sha256.Dispose()
$canonical = "GET`n/orders`n$query`n$emptyHash`n$($env:OPEN_PLATFORM_APP_ID)`n$timestamp`n$nonce"
$hmac = [Security.Cryptography.HMACSHA256]::new([Text.Encoding]::UTF8.GetBytes($env:OPEN_PLATFORM_APP_SECRET))
$signature = ([BitConverter]::ToString($hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes($canonical)))).Replace('-', '').ToLowerInvariant()
$hmac.Dispose()
$arguments = @('--connect-timeout','10','--max-time','30','--fail-with-body',"$baseUrl/orders?$query",'-H',"X-App-ID: $($env:OPEN_PLATFORM_APP_ID)",'-H',"X-Timestamp: $timestamp",'-H',"X-Nonce: $nonce",'-H',"X-Signature: $signature")
if ($env:OPEN_PLATFORM_DRY_RUN -eq '1') { Write-Output 'cURL request prepared with X-App-ID, X-Timestamp, X-Nonce and X-Signature'; exit 0 }
& curl.exe @arguments
