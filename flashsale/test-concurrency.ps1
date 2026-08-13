# 1. Warmup stock to exactly 10 units for Item 101
Write-Host "Warming up inventory: 10 units of PlayStation5..." -ForegroundColor Yellow
Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:8080/api/v1/inventory/warmup?itemId=101&totalStock=10"

Write-Host "Firing 50 CONCURRENT reservation requests for only 10 available items..." -ForegroundColor Cyan

# Fire 50 concurrent async jobs trying to reserve 1 unit each
$jobs = 1..50 | ForEach-Object {
    Start-Job -ScriptBlock {
        param($userId)
        $body = @{ itemId = 101; quantity = 1 } | ConvertTo-Json
        try {
            $response = Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:8080/api/v1/reservations" -Headers @{"X-User-Id"="$userId"; "Content-Type"="application/json"} -Body $body
            return "SUCCESS: User $userId secured reservation $($response.reservationId)"
        } catch {
            return "FAILED: User $userId - SOLD OUT / REJECTED"
        }
    } -ArgumentList $_
}

# Wait for all background requests to complete
$results = $jobs | Receive-Job -Wait -AutoRemoveJob

$successes = ($results | Select-String "SUCCESS").Count
$failures = ($results | Select-String "FAILED").Count

Write-Host "================ CONCURRENCY TEST RESULTS ================" -ForegroundColor Green
Write-Host "Total Concurrent Requests: 50"
Write-Host "Successful Reservations: $successes (Expected: 10)" -ForegroundColor Green
Write-Host "Rejected / Sold Out:      $failures  (Expected: 40)" -ForegroundColor Red
Write-Host "==========================================================" -ForegroundColor Green