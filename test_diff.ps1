$ErrorActionPreference = "Stop"

$buyer = Invoke-RestMethod -Uri "http://flashsale-alb-1698354449.ap-south-1.elb.amazonaws.com/api/v1/auth/register" -Method Post -Body (@{ email = "testchk_$([System.DateTime]::UtcNow.Ticks)@test.com"; password = "Password123!"; role = "ROLE_USER" } | ConvertTo-Json) -ContentType "application/json"
$token = $buyer.token

Write-Host "Register user 1001..."
$buyer1001 = Invoke-RestMethod -Uri "http://flashsale-alb-1698354449.ap-south-1.elb.amazonaws.com/api/v1/auth/register" -Method Post -Body (@{ email = "user1001_$([System.DateTime]::UtcNow.Ticks)@test.com"; password = "Password123!"; role = "ROLE_USER" } | ConvertTo-Json) -ContentType "application/json"
$token1001 = $buyer1001.token

$admin = Invoke-RestMethod -Uri "http://flashsale-alb-1698354449.ap-south-1.elb.amazonaws.com/api/v1/auth/register" -Method Post -Body (@{ email = "adminchk_$([System.DateTime]::UtcNow.Ticks)@test.com"; password = "Password123!"; role = "ROLE_ADMIN" } | ConvertTo-Json) -ContentType "application/json"
$warm = Invoke-RestMethod -Uri "http://flashsale-alb-1698354449.ap-south-1.elb.amazonaws.com/api/v1/inventory/warmup?itemId=101&totalStock=100" -Method Post -Headers @{ Authorization = "Bearer $($admin.token)" }

$res = Invoke-RestMethod -Uri "http://flashsale-alb-1698354449.ap-south-1.elb.amazonaws.com/api/v1/reservations" -Method Post -Headers @{ Authorization = "Bearer $token1001"; "X-User-Id" = "1001" } -Body (@{ productId = 101; quantity = 1 } | ConvertTo-Json) -ContentType "application/json"
$resId = $res.reservationId
Write-Host "Reservation for user 1001 ID: $resId"

Write-Host "Calling checkout with token1001 and NO X-User-Id header..."
try {
    $r = Invoke-WebRequest -Uri "http://flashsale-alb-1698354449.ap-south-1.elb.amazonaws.com/api/v1/orders/checkout" -Method Post -Headers @{
        Authorization       = "Bearer $token1001"
        "X-Idempotency-Key" = [System.Guid]::NewGuid().ToString()
    } -Body (@{
        reservationId = $resId
        itemId        = 101
        amount        = 49.99
    } | ConvertTo-Json) -ContentType "application/json"

    Write-Host "SUCCESS: $($r.Content)"
} catch [System.Net.WebException] {
    $resp = $_.Exception.Response
    $stream = $resp.GetResponseStream()
    $reader = New-Object System.IO.StreamReader($stream)
    Write-Host "Status: $([int]$resp.StatusCode) ($($resp.StatusCode)) Body: $($reader.ReadToEnd())"
}
