param(
    [string]$BaseUrl = "http://localhost:8080"
)

# Windows PowerShell 5.1 (built on .NET Framework) doesn't auto-load System.Net.Http
# the way PowerShell 7 (.NET Core) does — load it explicitly so HttpClient etc. resolve.
Add-Type -AssemblyName System.Net.Http

$Pass = 0
$Fail = 0

function Write-Result {
    param([bool]$Ok, [string]$Desc, [string]$Detail = "")
    if ($Ok) {
        Write-Host "  PASS: $Desc $Detail" -ForegroundColor Green
        $script:Pass++
    } else {
        Write-Host "  FAIL: $Desc $Detail" -ForegroundColor Red
        $script:Fail++
    }
}

# AllowAutoRedirect=$false so we can inspect the 302 + Location header ourselves,
# instead of PowerShell silently following it for us.
$handler = New-Object System.Net.Http.HttpClientHandler
$handler.AllowAutoRedirect = $false
$client = New-Object System.Net.Http.HttpClient($handler)

function Invoke-Api {
    param([string]$Method, [string]$Path, [string]$Body = $null)
    $req = New-Object System.Net.Http.HttpRequestMessage([System.Net.Http.HttpMethod]::new($Method), "$BaseUrl$Path")
    if ($Body) {
        $req.Content = New-Object System.Net.Http.StringContent($Body, [System.Text.Encoding]::UTF8, "application/json")
    }
    $resp = $client.SendAsync($req).GetAwaiter().GetResult()
    $text = $resp.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    [PSCustomObject]@{
        StatusCode = [int]$resp.StatusCode
        Body       = $text
        Location   = $(if ($resp.Headers.Location) { $resp.Headers.Location.ToString() } else { $null })
    }
}

Write-Host "=== ShortLink API smoke test against $BaseUrl ===`n"

# 1. Create a short link
Write-Host "[1] Create a short link"
$createBody = '{"longUrl": "https://www.autodesk.com/products/fusion-360", "expiresInDays": 30}'
$create = Invoke-Api -Method POST -Path "/api/urls" -Body $createBody
Write-Result -Ok ($create.StatusCode -eq 201) -Desc "POST /api/urls returns 201" -Detail "(got $($create.StatusCode))"

$shortCode = $null
if ($create.Body -match '"shortCode"\s*:\s*"([^"]+)"') {
    $shortCode = $Matches[1]
    Write-Host "  Got shortCode: $shortCode" -ForegroundColor Green
    $Pass++
} else {
    Write-Host "  FAIL: no shortCode in response: $($create.Body)" -ForegroundColor Red
    $Fail++
}
Write-Host ""

# 2. Reject an invalid URL
Write-Host "[2] Reject an invalid longUrl"
$invalid = Invoke-Api -Method POST -Path "/api/urls" -Body '{"longUrl": "not-a-real-url"}'
Write-Result -Ok ($invalid.StatusCode -eq 400) -Desc "POST with bad longUrl returns 400" -Detail "(got $($invalid.StatusCode))"
Write-Host ""

# 3. Follow the redirect
Write-Host "[3] Follow the short link"
if ($shortCode) {
    $redir = Invoke-Api -Method GET -Path "/$shortCode"
    Write-Result -Ok ($redir.StatusCode -eq 302) -Desc "GET /$shortCode returns 302" -Detail "(got $($redir.StatusCode))"
    $okLoc = $redir.Location -and ($redir.Location -like "*autodesk.com*")
    Write-Result -Ok $okLoc -Desc "redirects to the original URL" -Detail "($($redir.Location))"
} else {
    Write-Host "  SKIPPED: no shortCode from step 1" -ForegroundColor Red
}
Write-Host ""

# 4. Analytics
Write-Host "[4] Check analytics"
if ($shortCode) {
    $analytics = Invoke-Api -Method GET -Path "/api/urls/$shortCode/analytics"
    Write-Result -Ok ($analytics.StatusCode -eq 200) -Desc "GET analytics returns 200" -Detail "(got $($analytics.StatusCode))"
    Write-Host "  Response: $($analytics.Body)"
} else {
    Write-Host "  SKIPPED: no shortCode from step 1" -ForegroundColor Red
}
Write-Host ""

# 5. Unknown short code
Write-Host "[5] Look up a nonexistent short code"
$notFound = Invoke-Api -Method GET -Path "/zzzzzzz"
Write-Result -Ok ($notFound.StatusCode -eq 404) -Desc "GET /zzzzzzz returns 404" -Detail "(got $($notFound.StatusCode))"
Write-Host ""

# 6. Rate limit — fire 12 requests fast, expect a 429 near the end
Write-Host "[6] Rate limit test (default: 10 requests/minute/client)"
$hit429 = $false
for ($i = 1; $i -le 12; $i++) {
    $r = Invoke-Api -Method POST -Path "/api/urls" -Body ('{"longUrl": "https://example.com/rl-' + $i + '"}')
    Write-Host "  request #$i -> $($r.StatusCode)"
    if ($r.StatusCode -eq 429) { $hit429 = $true }
}
Write-Result -Ok $hit429 -Desc "rate limiter kicked in (got a 429)"
Write-Host ""

Write-Host "=== Summary: $Pass passed, $Fail failed ==="
if ($Fail -gt 0) { exit 1 }
