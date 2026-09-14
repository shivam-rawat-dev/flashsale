$ProgressPreference = 'SilentlyContinue'
$ErrorActionPreference = 'Continue'

$email1 = "waf1_" + [System.DateTime]::UtcNow.Ticks + "@test.com"
$body1 = '{"email":"' + $email1 + '","password":"Password123!","role":"ROLE_USER"}'
$r1 = Invoke-RestMethod -Uri "http://flashsale-alb-1698354449.ap-south-1.elb.amazonaws.com/api/v1/auth/register" -Method Post -Body $body1 -ContentType "application/json"
[Console]::WriteLine("Test 1 UserId: " + $r1.userId)

$email2 = "waf2_" + [System.DateTime]::UtcNow.Ticks + "@test.com"
$body2 = '{"email":"' + $email2 + '","password":"Password123!","role":"ROLE_USER"}'
$r2 = Invoke-RestMethod -Uri "http://flashsale-alb-1698354449.ap-south-1.elb.amazonaws.com/api/v1/auth/register" -Method Post -Headers @{ "X-Idempotency-Key" = "test-123" } -Body $body2 -ContentType "application/json"
[Console]::WriteLine("Test 2 UserId: " + $r2.userId)
