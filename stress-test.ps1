param(
    [string]$BaseUrl = "http://flashsale-alb-1698354449.ap-south-1.elb.amazonaws.com",
    [int]$TotalBuyers = 500,
    [int]$TotalStock = 100,
    [long]$ProductId = 202,
    [int]$Concurrency = 25
)

$BaseUrl = $BaseUrl.TrimEnd('/')

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " FLASH-SALE HIGH-CONCURRENCY STRESS & ZERO-OVERBOOKING TEST" -ForegroundColor Cyan
Write-Host " Target URL:         $BaseUrl" -ForegroundColor Cyan
Write-Host " Total Buyers:       $TotalBuyers" -ForegroundColor Cyan
Write-Host " Product ID:         $ProductId" -ForegroundColor Cyan
Write-Host " Initial Stock:      $TotalStock" -ForegroundColor Cyan
Write-Host " Concurrency (Jobs): $Concurrency" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# -------------------------------------------------------------------------
# Step 1: Register Admin and Pre-warm Stock
# -------------------------------------------------------------------------
Write-Host "`n[1/3] Pre-warming Inventory for Product $ProductId with Stock: $TotalStock..." -ForegroundColor Yellow
$rnd = Get-Random -Minimum 1000 -Maximum 9999
$adminEmail = "admin_loadtest_" + $rnd + "@flashsale.com"
$adminBody = @{
    email    = $adminEmail
    password = "AdminSecret123!"
    role     = "ROLE_ADMIN"
} | ConvertTo-Json

try {
    $adminRes = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/register" -Method Post -Body $adminBody -ContentType "application/json"
    $adminToken = $adminRes.token
    
    $warmupHeaders = @{ Authorization = "Bearer " + $adminToken }
    $warmupUri = "$BaseUrl/api/v1/inventory/warmup?itemId=" + $ProductId + "`&totalStock=" + $TotalStock
    $warmupRes = Invoke-RestMethod -Uri $warmupUri -Method Post -Headers $warmupHeaders
    Write-Host " [OK] Stock initialized to $TotalStock units in PostgreSQL and Redis!" -ForegroundColor Green
} catch {
    Write-Host " [FAILED] Failed to initialize inventory: $_" -ForegroundColor Red
    exit 1
}

# -------------------------------------------------------------------------
# Step 2: Fire Concurrent Buyer Threads Competing for Stock
# -------------------------------------------------------------------------
Write-Host "`n[2/3] Firing $TotalBuyers concurrent reservation requests across $Concurrency threads..." -ForegroundColor Yellow

$scriptBlock = {
    param($BaseUrl, $ProductId, $BuyerIndex)
    
    $rndSuffix = Get-Random -Minimum 1000 -Maximum 9999
    $buyerEmail = "buyer_load_" + $BuyerIndex + "_" + $rndSuffix + "@flashsale.com"
    $regBody = @{
        email    = $buyerEmail
        password = "BuyerPassword123!"
        role     = "ROLE_USER"
    } | ConvertTo-Json

    $result = [PSCustomObject]@{
        BuyerIndex    = $BuyerIndex
        Reserved      = $false
        StatusCode    = 0
        ReservationId = $null
        LatencyMs     = 0
        ErrorMessage  = $null
    }

    try {
        $regRes = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/register" -Method Post -Body $regBody -ContentType "application/json" -TimeoutSec 15
        $buyerToken = $regRes.token
        $buyerId = $regRes.userId

        $reserveHeaders = @{ Authorization = "Bearer " + $buyerToken }
        $reserveBody = @{
            productId = $ProductId
            quantity  = 1
            userId    = $buyerId
        } | ConvertTo-Json

        $sw = [System.Diagnostics.Stopwatch]::StartNew()
        $res = Invoke-RestMethod -Uri "$BaseUrl/api/v1/reservations" -Method Post -Headers $reserveHeaders -Body $reserveBody -ContentType "application/json" -TimeoutSec 15
        $sw.Stop()

        $result.Reserved = $true
        $result.StatusCode = 201
        $result.ReservationId = $res.reservationId
        $result.LatencyMs = $sw.ElapsedMilliseconds
    } catch [System.Net.WebException] {
        $resp = $_.Exception.Response
        if ($resp) {
            $result.StatusCode = [int]$resp.StatusCode
        } else {
            $result.StatusCode = 500
        }
        $result.ErrorMessage = $_.Exception.Message
    } catch {
        $result.StatusCode = 500
        $result.ErrorMessage = $_.ToString()
    }

    return $result
}

$stopwatchTotal = [System.Diagnostics.Stopwatch]::StartNew()

$batches = [Math]::Ceiling($TotalBuyers / $Concurrency)
$allResults = [System.Collections.Generic.List[PSObject]]::new()

for ($b = 0; $b -lt $batches; $b++) {
    $jobs = @()
    $startIdx = $b * $Concurrency
    $endIdx = [Math]::Min($startIdx + $Concurrency, $TotalBuyers)

    for ($i = $startIdx; $i -lt $endIdx; $i++) {
        $jobs += Start-Job -ScriptBlock $scriptBlock -ArgumentList $BaseUrl, $ProductId, $i
    }

    $batchResults = $jobs | Wait-Job | Receive-Job
    $jobs | Remove-Job

    foreach ($r in $batchResults) {
        $allResults.Add($r)
    }

    $reservedSoFar = ($allResults | Where-Object { $_.Reserved -eq $true }).Count
    $processedCount = $allResults.Count
    Write-Host "   Progress: $processedCount/$TotalBuyers buyers processed ($reservedSoFar reserved)..." -ForegroundColor DarkGray
}

$stopwatchTotal.Stop()

# -------------------------------------------------------------------------
# Step 3: Analyze Results and Verify Zero Overbooking
# -------------------------------------------------------------------------
Write-Host "`n[3/3] Analyzing Stress Test Metrics..." -ForegroundColor Yellow

$successCount = ($allResults | Where-Object { $_.Reserved -eq $true }).Count
$soldOutCount = ($allResults | Where-Object { $_.StatusCode -eq 409 -or $_.StatusCode -eq 400 -or $_.StatusCode -eq 500 }).Count
$latencies = $allResults | Where-Object { $_.Reserved -eq $true } | Select-Object -ExpandProperty LatencyMs

$avgLatency = 0
$p95Latency = 0
if ($latencies.Count -gt 0) {
    $avgLatency = [Math]::Round(($latencies | Measure-Object -Average).Average, 2)
    $sorted = $latencies | Sort-Object
    $p95Index = [Math]::Floor($sorted.Count * 0.95)
    $p95Latency = $sorted[$p95Index]
}

$totalTimeSec = [Math]::Round($stopwatchTotal.Elapsed.TotalSeconds, 2)
$throughputRps = 0
if ($totalTimeSec -gt 0) {
    $throughputRps = [Math]::Round($TotalBuyers / $totalTimeSec, 2)
}

$color = "Red"
if ($successCount -le $TotalStock) {
    $color = "Green"
}

Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host " STRESS TEST RESULTS SUMMARY" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Total Requests:             $TotalBuyers"
Write-Host " Total Duration:             $totalTimeSec s"
Write-Host " Effective Throughput:       $throughputRps req/sec"
Write-Host " Successful Reservations:    $successCount (Target: <= $TotalStock)" -ForegroundColor $color
Write-Host " Rejected (Sold Out / 409):  $soldOutCount" -ForegroundColor Yellow
Write-Host " Avg Reservation Latency:    $avgLatency ms" -ForegroundColor Green
Write-Host " P95 Reservation Latency:    $p95Latency ms" -ForegroundColor Green

Write-Host "`n==========================================================" -ForegroundColor Cyan
if ($successCount -eq $TotalStock) {
    Write-Host " [SUCCESS] ZERO OVERBOOKING GUARANTEE VERIFIED!" -ForegroundColor Green
    Write-Host " Exactly $TotalStock items reserved out of $TotalBuyers competing buyers." -ForegroundColor Green
} elseif ($successCount -lt $TotalStock) {
    Write-Host " [OK] $successCount out of $TotalStock items reserved without any overbooking." -ForegroundColor Yellow
} else {
    Write-Host " [FAILED] OVERBOOKING DETECTED! Reserved: $successCount, Stock: $TotalStock" -ForegroundColor Red
}
Write-Host "==========================================================" -ForegroundColor Cyan
