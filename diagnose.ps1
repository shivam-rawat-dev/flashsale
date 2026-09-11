$ErrorActionPreference = "Stop"

Write-Host "1. Registering Admin..."
$admin = Invoke-RestMethod -Uri "http://flashsale-alb-1698354449.ap-south-1.elb.amazonaws.com/api/v1/auth/register" -Method Post -Body (@{ email = "adm_$([System.DateTime]::UtcNow.Ticks)@test.com"; password = "Password123!"; role = "ROLE_ADMIN" } | ConvertTo-Json) -ContentType "application/json"

Write-Host "2. Pre-warming Inventory..."
$warm = Invoke-RestMethod -Uri "http://flashsale-alb-1698354449.ap-south-1.elb.amazonaws.com/api/v1/inventory/warmup?itemId=101&totalStock=100" -Method Post -Headers @{ Authorization = "Bearer $($admin.token)" }

Write-Host "3. Registering Buyer..."
$buyer = Invoke-RestMethod -Uri "http://flashsale-alb-1698354449.ap-south-1.elb.amazonaws.com/api/v1/auth/register" -Method Post -Body (@{ email = "buy_$([System.DateTime]::UtcNow.Ticks)@test.com"; password = "Password123!"; role = "ROLE_USER" } | ConvertTo-Json) -ContentType "application/json"

Write-Host "4. Reserving Stock..."
$res = Invoke-RestMethod -Uri "http://flashsale-alb-1698354449.ap-south-1.elb.amazonaws.com/api/v1/reservations" -Method Post -Headers @{ Authorization = "Bearer $($buyer.token)"; "X-User-Id" = "$($buyer.userId)" } -Body (@{ productId = 101; quantity = 1 } | ConvertTo-Json) -ContentType "application/json"
Write-Host "Reservation ID: $($res.reservationId)"

Write-Host "5. Checking out..."
try {
    $chk = Invoke-WebRequest -Uri "http://flashsale-alb-1698354449.ap-south-1.elb.amazonaws.com/api/v1/orders/checkout" -Method Post -Headers @{
        Authorization       = "Bearer $($buyer.token)"
        "X-Idempotency-Key" = [System.Guid]::NewGuid().ToString()
        "X-User-Id"         = "$($buyer.userId)"
    } -Body (@{
        reservationId = $res.reservationId
        itemId        = 101
        amount        = 49.99
    } | ConvertTo-Json) -ContentType "application/json"

    Write-Host "SUCCESS: $($chk.Content)"
} catch [System.Net.WebException] {
    $resp = $_.Exception.Response
    $stream = $resp.GetResponseStream()
    $reader = New-Object System.IO.StreamReader($stream)
    Write-Host "FAILED: Status $([int]$resp.StatusCode) ($($resp.StatusCode))"
    Write-Host "Body: $($reader.ReadToEnd())"
}
