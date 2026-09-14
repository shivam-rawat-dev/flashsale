$ProgressPreference = 'SilentlyContinue'
$ErrorActionPreference = 'Continue'

$email = "orderchk_" + [System.DateTime]::UtcNow.Ticks + "@test.com"
$body = '{"email":"' + $email + '","password":"Password123!","role":"ROLE_USER"}'
$reg = Invoke-RestMethod -Uri "http://flashsale-alb-1698354449.ap-south-1.elb.amazonaws.com/api/v1/auth/register" -Method Post -Body $body -ContentType "application/json"
$token = $reg.token
$userId = $reg.userId

[Console]::WriteLine("User: " + $userId)

$tests = @(
    @{ Name = "POST /orders/checkout (Valid JSON body)"; Path = "/api/v1/orders/checkout"; Body = '{"reservationId":"RES-dummy","itemId":101,"amount":49.99}'; Headers = @{ Authorization = "Bearer $token"; "X-Idempotency-Key" = "key1" } },
    @{ Name = "POST /reservations (Valid JSON body)"; Path = "/api/v1/reservations"; Body = '{"productId":101,"quantity":1}'; Headers = @{ Authorization = "Bearer $token"; "X-User-Id" = "$userId" } },
    @{ Name = "POST /orders/123/payment-success"; Path = "/api/v1/orders/ORD-123/payment-success"; Body = ''; Headers = @{ Authorization = "Bearer $token"; "X-Idempotency-Key" = "key2" } }
)

foreach ($t in $tests) {
    try {
        $res = Invoke-WebRequest -Uri ("http://flashsale-alb-1698354449.ap-south-1.elb.amazonaws.com" + $t.Path) -Method Post -Headers $t.Headers -Body $t.Body -ContentType "application/json"
        [Console]::WriteLine($t.Name + " -> Status: " + [int]$res.StatusCode)
    } catch [System.Net.WebException] {
        $resp = $_.Exception.Response
        $stream = $resp.GetResponseStream()
        $reader = New-Object System.IO.StreamReader($stream)
        $respBody = $reader.ReadToEnd()
        [Console]::WriteLine($t.Name + " -> Status: " + [int]$resp.StatusCode + " Body: " + $respBody)
    }
}
