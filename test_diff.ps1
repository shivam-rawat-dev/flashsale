$ErrorActionPreference = "Stop"

$buyer = Invoke-RestMethod -Uri "http://flashsale-alb-1698354449.ap-south-1.elb.amazonaws.com/api/v1/auth/register" -Method Post -Body (@{ email = "testchk_$([System.DateTime]::UtcNow.Ticks)@test.com"; password = "Password123!"; role = "ROLE_USER" } | ConvertTo-Json) -ContentType "application/json"
$token = $buyer.token
$userId = $buyer.userId

$admin = Invoke-RestMethod -Uri "http://flashsale-alb-1698354449.ap-south-1.elb.amazonaws.com/api/v1/auth/register" -Method Post -Body (@{ email = "adminchk_$([System.DateTime]::UtcNow.Ticks)@test.com"; password = "Password123!"; role = "ROLE_ADMIN" } | ConvertTo-Json) -ContentType "application/json"
$warm = Invoke-RestMethod -Uri "http://flashsale-alb-1698354449.ap-south-1.elb.amazonaws.com/api/v1/inventory/warmup?itemId=101&totalStock=100" -Method Post -Headers @{ Authorization = "Bearer $($admin.token)" }

$res = Invoke-RestMethod -Uri "http://flashsale-alb-1698354449.ap-south-1.elb.amazonaws.com/api/v1/reservations" -Method Post -Headers @{ Authorization = "Bearer $token"; "X-User-Id" = "$userId" } -Body (@{ productId = 101; quantity = 1 } | ConvertTo-Json) -ContentType "application/json"
$resId = $res.reservationId
Write-Host "Reservation ID: $resId"
Write-Host "Buyer User ID: $userId"

Write-Host "`nProbe 1: POST /api/v1/orders/checkout with Token + X-User-Id (NO X-Idempotency-Key)"
try {
    $p1 = Invoke-WebRequest -Uri "http://flashsale-alb-1698354449.ap-south-1.elb.amazonaws.com/api/v1/orders/checkout" -Method Post -Headers @{ Authorization = "Bearer $token"; "X-User-Id" = "$userId" } -Body (@{ reservationId = $resId; itemId = 101; amount = 49.99 } | ConvertTo-Json) -ContentType "application/json"
    Write-Host "Probe 1 Status: $($p1.StatusCode) Body: $($p1.Content)"
} catch [System.Net.WebException] {
    $r = $_.Exception.Response
    $stream = $r.GetResponseStream()
    $reader = New-Object System.IO.StreamReader($stream)
    Write-Host "Probe 1 failed Status: $([int]$r.StatusCode) Body: $($reader.ReadToEnd())"
}

Write-Host "`nProbe 2: POST /api/v1/orders/checkout with Token + X-User-Id + X-Idempotency-Key"
try {
    $p2 = Invoke-WebRequest -Uri "http://flashsale-alb-1698354449.ap-south-1.elb.amazonaws.com/api/v1/orders/checkout" -Method Post -Headers @{ Authorization = "Bearer $token"; "X-User-Id" = "$userId"; "X-Idempotency-Key" = [System.Guid]::NewGuid().ToString() } -Body (@{ reservationId = $resId; itemId = 101; amount = 49.99 } | ConvertTo-Json) -ContentType "application/json"
    Write-Host "Probe 2 Status: $($p2.StatusCode) Body: $($p2.Content)"
} catch [System.Net.WebException] {
    $r = $_.Exception.Response
    $stream = $r.GetResponseStream()
    $reader = New-Object System.IO.StreamReader($stream)
    Write-Host "Probe 2 failed Status: $([int]$r.StatusCode) Body: $($reader.ReadToEnd())"
}
