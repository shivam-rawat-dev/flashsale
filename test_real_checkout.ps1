$ProgressPreference = 'SilentlyContinue'
$ErrorActionPreference = 'Continue'

$buyer = Invoke-RestMethod -Uri "http://flashsale-alb-1698354449.ap-south-1.elb.amazonaws.com/api/v1/auth/register" -Method Post -Body (@{ email = "buyer_$([System.DateTime]::UtcNow.Ticks)@test.com"; password = "Password123!"; role = "ROLE_USER" } | ConvertTo-Json) -ContentType "application/json"
$token = $buyer.token
$userId = $buyer.userId

$admin = Invoke-RestMethod -Uri "http://flashsale-alb-1698354449.ap-south-1.elb.amazonaws.com/api/v1/auth/register" -Method Post -Body (@{ email = "admin_$([System.DateTime]::UtcNow.Ticks)@test.com"; password = "Password123!"; role = "ROLE_ADMIN" } | ConvertTo-Json) -ContentType "application/json"
$warm = Invoke-RestMethod -Uri "http://flashsale-alb-1698354449.ap-south-1.elb.amazonaws.com/api/v1/inventory/warmup?itemId=101&totalStock=100" -Method Post -Headers @{ Authorization = "Bearer $($admin.token)" }

$res = Invoke-RestMethod -Uri "http://flashsale-alb-1698354449.ap-south-1.elb.amazonaws.com/api/v1/reservations" -Method Post -Headers @{ Authorization = "Bearer $token"; "X-User-Id" = "$userId" } -Body (@{ productId = 101; quantity = 1 } | ConvertTo-Json) -ContentType "application/json"
$resId = $res.reservationId

[Console]::WriteLine("Reservation: " + $resId)
[Console]::WriteLine("UserId: " + $userId)

$body = @{
    reservationId = $resId
    itemId        = 101
    amount        = 49.99
} | ConvertTo-Json

$headers = @(
    "X-User-Id",
    "X-User-ID",
    "x-user-id",
    "User-Id",
    "UserId",
    "user_id"
)

foreach ($h in $headers) {
    try {
        $reqHeaders = @{
            Authorization       = "Bearer $token"
            "X-Idempotency-Key" = [System.Guid]::NewGuid().ToString()
        }
        $reqHeaders[$h] = "$userId"
        
        $r = Invoke-WebRequest -Uri "http://flashsale-alb-1698354449.ap-south-1.elb.amazonaws.com/api/v1/orders/checkout" -Method Post -Headers $reqHeaders -Body $body -ContentType "application/json"
        [Console]::WriteLine("Header [" + $h + "] -> SUCCESS Status: " + [int]$r.StatusCode + " Body: " + $r.Content)
    } catch [System.Net.WebException] {
        $resp = $_.Exception.Response
        [Console]::WriteLine("Header [" + $h + "] -> FAILED Status: " + [int]$resp.StatusCode)
    }
}
