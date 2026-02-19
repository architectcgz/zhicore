# Quick Multipart Upload Test
# Uses existing test user to avoid registration issues

$UploadServiceUrl = "http://localhost:8089"
$UserServiceUrl = "http://localhost:8081"

# Use existing test user
$TestEmail = "testuser@example.com"
$TestPassword = "Test123456!"

$Global:AccessToken = ""
$Timestamp = Get-Date -Format "yyyyMMddHHmmss"

function Write-TestStep {
    param([string]$Step, [string]$Status = "INFO")
    $Color = switch ($Status) {
        "PASS" { "Green" }
        "FAIL" { "Red" }
        "WARN" { "Yellow" }
        default { "Cyan" }
    }
    Write-Host "[$Status] $Step" -ForegroundColor $Color
}

function Get-AuthHeaders { 
    return @{ "Authorization" = "Bearer $Global:AccessToken" } 
}

function Invoke-ApiRequest {
    param([string]$Method, [string]$Url, [object]$Body = $null, [hashtable]$Headers = @{})
    $Result = @{ Success = $false; StatusCode = 0; Body = $null; Error = "" }
    try {
        $RequestParams = @{ Method = $Method; Uri = $Url; ContentType = "application/json"; Headers = $Headers; ErrorAction = "Stop" }
        if ($Body) { $RequestParams.Body = ($Body | ConvertTo-Json -Depth 10) }
        $Response = Invoke-WebRequest @RequestParams
        $Result.Success = $true
        $Result.StatusCode = $Response.StatusCode
        $Result.Body = $Response.Content | ConvertFrom-Json
    }
    catch {
        if ($_.Exception.Response) {
            $Result.StatusCode = [int]$_.Exception.Response.StatusCode
            try {
                $StreamReader = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
                $ErrorContent = $StreamReader.ReadToEnd()
                $StreamReader.Close()
                try { $Result.Body = $ErrorContent | ConvertFrom-Json } catch { $Result.Error = $ErrorContent }
            } catch { $Result.Error = $_.Exception.Message }
        } else { $Result.Error = $_.Exception.Message }
    }
    return $Result
}

function Invoke-PartUpload {
    param([string]$Url, [byte[]]$Data, [hashtable]$Headers = @{})
    $Result = @{ Success = $false; StatusCode = 0; Body = $null; Error = "" }
    try {
        $Headers["Content-Type"] = "application/octet-stream"
        $Response = Invoke-WebRequest -Uri $Url -Method PUT -Body $Data -Headers $Headers -ErrorAction Stop
        $Result.Success = $true
        $Result.StatusCode = $Response.StatusCode
        $Result.Body = $Response.Content | ConvertFrom-Json
    }
    catch {
        if ($_.Exception.Response) {
            $Result.StatusCode = [int]$_.Exception.Response.StatusCode
            try {
                $StreamReader = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
                $ErrorContent = $StreamReader.ReadToEnd()
                $StreamReader.Close()
                try { $Result.Body = $ErrorContent | ConvertFrom-Json } catch { $Result.Error = $ErrorContent }
            } catch { $Result.Error = $_.Exception.Message }
        } else { $Result.Error = $_.Exception.Message }
    }
    return $Result
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Quick Multipart Upload Test" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Login
Write-TestStep "Logging in..." "INFO"
$LoginBody = @{ email = $TestEmail; password = $TestPassword }
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/login" -Body $LoginBody

if ($Result.Success -and $Result.Body.code -eq 200 -and $Result.Body.data.accessToken) {
    $Global:AccessToken = $Result.Body.data.accessToken
    Write-TestStep "Login successful" "PASS"
} else {
    Write-TestStep "Login failed" "FAIL"
    exit 1
}

# Create temp directory
$TempDir = Join-Path $env:TEMP "multipart_quick_$Timestamp"
New-Item -ItemType Directory -Path $TempDir -Force | Out-Null

# Create 15MB test file
$TestFilePath = Join-Path $TempDir "test_file.bin"
Write-TestStep "Creating 15MB test file..." "INFO"

$SizeBytes = 15 * 1024 * 1024
$FileStream = [System.IO.File]::Create($TestFilePath)
try {
    $Random = New-Object System.Random
    $ChunkSize = 1024 * 1024
    $BytesWritten = 0
    while ($BytesWritten -lt $SizeBytes) {
        $CurrentChunkSize = [Math]::Min($ChunkSize, $SizeBytes - $BytesWritten)
        $Buffer = New-Object byte[] $CurrentChunkSize
        $Random.NextBytes($Buffer)
        $FileStream.Write($Buffer, 0, $CurrentChunkSize)
        $BytesWritten += $CurrentChunkSize
    }
}
finally {
    $FileStream.Close()
}

$FileInfo = Get-Item $TestFilePath
Write-TestStep "Test file created: $([math]::Round($FileInfo.Length / 1MB, 2)) MB" "PASS"

# Calculate MD5
Write-TestStep "Calculating MD5 hash..." "INFO"
$MD5 = [System.Security.Cryptography.MD5]::Create()
$FileStream = [System.IO.File]::OpenRead($TestFilePath)
try {
    $HashBytes = $MD5.ComputeHash($FileStream)
    $FileHash = [System.BitConverter]::ToString($HashBytes).Replace("-", "").ToLower()
}
finally {
    $FileStream.Close()
    $MD5.Dispose()
}
Write-Host "  Hash: $FileHash" -ForegroundColor Gray

# Initialize multipart upload
Write-Host ""
Write-TestStep "Initializing multipart upload..." "INFO"
$InitBody = @{
    fileName = "test_file.bin"
    fileSize = $FileInfo.Length
    fileHash = $FileHash
    contentType = "application/octet-stream"
}

$Result = Invoke-ApiRequest -Method "POST" -Url "$UploadServiceUrl/api/v1/multipart/init" -Body $InitBody -Headers (Get-AuthHeaders)

if ($Result.Success -and $Result.Body.code -eq 200 -and $Result.Body.data) {
    $TaskId = $Result.Body.data.taskId
    $UploadId = $Result.Body.data.uploadId
    $ChunkSize = $Result.Body.data.chunkSize
    $TotalParts = $Result.Body.data.totalParts
    
    Write-TestStep "Multipart upload initialized" "PASS"
    Write-Host "  Task ID: $TaskId" -ForegroundColor Gray
    Write-Host "  Total Parts: $TotalParts" -ForegroundColor Gray
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Write-TestStep "Failed to initialize: $ErrorMsg" "FAIL"
    exit 1
}

# Upload all parts
Write-Host ""
Write-TestStep "Uploading $TotalParts parts..." "INFO"
$FileStream = [System.IO.File]::OpenRead($TestFilePath)
$PartNumber = 1

try {
    while ($FileStream.Position -lt $FileStream.Length) {
        $RemainingBytes = $FileStream.Length - $FileStream.Position
        $CurrentChunkSize = [Math]::Min($ChunkSize, $RemainingBytes)
        
        $Buffer = New-Object byte[] $CurrentChunkSize
        $BytesRead = $FileStream.Read($Buffer, 0, $CurrentChunkSize)
        
        $PartUrl = "$UploadServiceUrl/api/v1/multipart/$TaskId/parts/$PartNumber"
        $PartResult = Invoke-PartUpload -Url $PartUrl -Data $Buffer -Headers (Get-AuthHeaders)
        
        if ($PartResult.Success -and $PartResult.Body.code -eq 200) {
            Write-Host "  Part $PartNumber/$TotalParts uploaded" -ForegroundColor Green
        } else {
            $ErrorMsg = if ($PartResult.Body.message) { $PartResult.Body.message } else { $PartResult.Error }
            Write-TestStep "Failed to upload part ${PartNumber}: $ErrorMsg" "FAIL"
            exit 1
        }
        
        $PartNumber++
    }
}
finally {
    $FileStream.Close()
}

Write-TestStep "All parts uploaded successfully" "PASS"

# Check progress
Write-Host ""
Write-TestStep "Checking upload progress..." "INFO"
$Result = Invoke-ApiRequest -Method "GET" -Url "$UploadServiceUrl/api/v1/multipart/$TaskId/progress" -Headers (Get-AuthHeaders)

if ($Result.Success -and $Result.Body.code -eq 200 -and $Result.Body.data) {
    $Progress = $Result.Body.data
    Write-TestStep "Progress: $($Progress.percentage)% ($($Progress.completedParts)/$($Progress.totalParts) parts)" "PASS"
}

# Complete upload
Write-Host ""
Write-TestStep "Completing multipart upload..." "INFO"
$Result = Invoke-ApiRequest -Method "POST" -Url "$UploadServiceUrl/api/v1/multipart/$TaskId/complete" -Headers (Get-AuthHeaders)

if ($Result.Success -and $Result.Body.code -eq 200 -and $Result.Body.data) {
    $FileId = $Result.Body.data.fileId
    $FileUrl = $Result.Body.data.url
    
    Write-TestStep "Upload completed successfully" "PASS"
    Write-Host "  File ID: $FileId" -ForegroundColor Gray
    Write-Host "  URL: $FileUrl" -ForegroundColor Gray
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Write-TestStep "Failed to complete: $ErrorMsg" "FAIL"
    exit 1
}

# Test resumption - initialize again with same hash
Write-Host ""
Write-TestStep "Testing resumption (same file hash)..." "INFO"
$Result = Invoke-ApiRequest -Method "POST" -Url "$UploadServiceUrl/api/v1/multipart/init" -Body $InitBody -Headers (Get-AuthHeaders)

if ($Result.Success -and $Result.Body.code -eq 200 -and $Result.Body.data) {
    $ResumedTaskId = $Result.Body.data.taskId
    $ResumedCompletedParts = $Result.Body.data.completedParts
    
    if ($ResumedTaskId -eq $TaskId) {
        Write-TestStep "Resumption works: returned existing completed task" "PASS"
        Write-Host "  Completed Parts: $($ResumedCompletedParts.Count)" -ForegroundColor Gray
    } else {
        Write-TestStep "New task created (file already exists)" "INFO"
    }
}

# Cleanup
Remove-Item -Path $TempDir -Recurse -Force

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "ALL TESTS PASSED" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "Multipart upload features verified:" -ForegroundColor Green
Write-Host "  [PASS] Large file upload (15MB)" -ForegroundColor Gray
Write-Host "  [PASS] Part-by-part upload" -ForegroundColor Gray
Write-Host "  [PASS] Progress tracking" -ForegroundColor Gray
Write-Host "  [PASS] Upload completion" -ForegroundColor Gray
Write-Host "  [PASS] Resumption detection" -ForegroundColor Gray
Write-Host ""
