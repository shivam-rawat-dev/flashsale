# Load .NET Http Assembly for Windows PowerShell
Add-Type -AssemblyName System.Net.Http

# 1. Warm up inventory to 10 units for Item 101
Write-Host "Warming up inventory: 10 units for Product 101..." -ForegroundColor Yellow
$warmup = Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:8080/api/v1/inventory/warmup?itemId=101&totalStock=10"
Write-Host "Warmup response: $($warmup.message)" -ForegroundColor Gray

Write-Host "`nFiring 50 ULTRA-FAST concurrent requests for 10 items..." -ForegroundColor Cyan

$url = "http://127.0.0.1:8080/api/v1/reservations"
$client = [System.Net.Http.HttpClient]::new()
$stopwatch = [System.Diagnostics.Stopwatch]::StartNew()

# Fire 50 asynchronous HTTP requests simultaneously
$tasks = 1..50 | ForEach-Object {
    $userId = $_
    $json = "{`"productId`":101,`"quantity`":1}"
    $content = [System.Net.Http.StringContent]::new($json, [System.Text.Encoding]::UTF8, "application/json")

    $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Post, $url)
    $request.Content = $content
    $request.Headers.Add("X-User-Id", "$userId")

    $client.SendAsync($request)
}

# Await all 50 HTTP responses in parallel
[System.Threading.Tasks.Task]::WaitAll($tasks)
$stopwatch.Stop()

# Parse status codes
$successCount = 0
$failedCount = 0

foreach ($task in $tasks) {
    $response = $task.Result
    if ($response.IsSuccessStatusCode) {
        $successCount++
    } else {
        $failedCount++
    }
}

Write-Host "`n================ CONCURRENCY TEST RESULTS ================" -ForegroundColor Green
Write-Host "Execution Duration:     $($stopwatch.ElapsedMilliseconds) ms" -ForegroundColor Yellow
Write-Host "Total Requests:         50"
Write-Host "Successful Reservations: $successCount (Expected: 10)" -ForegroundColor Green
Write-Host "Rejected / Sold Out:     $failedCount  (Expected: 40)" -ForegroundColor Red
Write-Host "==========================================================" -ForegroundColor Green

$client.Dispose()