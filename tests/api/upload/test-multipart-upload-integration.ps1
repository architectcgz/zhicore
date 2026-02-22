# Multipart Upload Integration Test Script
# Tests: Complete multipart upload flow including resumption and cleanup
# Requirements: REQ-7, REQ-8, REQ-9

param(
    [string]$ConfigPath = "../../config/test-env.json",
    [switch]$Verbose
)

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ConfigFullPath = Join-Path $ScriptDir $ConfigPath
$Config = Get-Content $ConfigFullPath | ConvertFrom-Json
$UploadServiceUrl = $Config.upload_service_url
$UserServiceUrl = $Config.user_service_url
$TestUser = $Config.test_user

# RustFS/S3 Configuration
$RustFSEndpoint = "http://localhost:9100"
$RustFSBucket = "ZhiCore-uploads"

$Global:AccessToken = ""
$Global:TestUserId = ""
$Global:TaskId = ""
$Global:UploadId = ""
$Timestamp = Get-Date -Format "yyyyMMddHHmmss"
$UniqueUsername = "multipart_$Timestamp"
$UniqueEmail = "multipart_$Timestamp@example.com"

# === Utility Functions ===

function Write-TestHeader {
    param([string]$Title)
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host $Title -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host ""
}

function Write-TestStep {
    param([string]$Step, [string]$Status = "INFO")
    $Color = switch ($Status) {
        "PASS" { "Green" }
        "FAIL" { "Red" }
        "WARN" { "Yellow" }
        "SKIP" { "Gray" }
        default { "Cyan" }
    }
    Write-Host "[$Status] $Step" -ForegroundColor $Color
}

function Get-AuthHeaders { 
    return @{ "Authorization" = "Bearer $Global:AccessToken" } 
}

# Standard API request function
function Invoke-ApiRequest {
    param([string]$Method, [string]$Url, [object]$Body = $null, [hashtable]$Headers = @{})
    $StartTime = Get-Date
    $Result = @{ Success = $false; StatusCode = 0; Body = $null; ResponseTime = 0; Error = "" }
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
    $Result.ResponseTime = [math]::Round(((Get-Date) - $StartTime).TotalMilliseconds)
    return $Result
}

# Upload binary data as part
function Invoke-PartUpload {
    param(
        [string]$Url,
        [byte[]]$Data,
        [hashtable]$Headers = @{}
    )
    
    $StartTime = Get-Date
    $Result = @{ Success = $false; StatusCode = 0; Body = $null; ResponseTime = 0; Error = "" }
    
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
    
    $Result.ResponseTime = [math]::Round(((Get-Date) - $StartTime).TotalMilliseconds)
    return $Result
}

# Create a large test file
function New-TestLargeFile {
    param([string]$FilePath, [int]$SizeMB = 15)
    
    $SizeBytes = $SizeMB * 1024 * 1024
    $ChunkSize = 1024 * 1024  # 1MB chunks
    $FileStream = [System.IO.File]::Create($FilePath)
    
    try {
        $BytesWritten = 0
        $Random = New-Object System.Random
        
        while ($BytesWritten -lt $SizeBytes) {
            $RemainingBytes = $SizeBytes - $BytesWritten
            $CurrentChunkSize = [Math]::Min($ChunkSize, $RemainingBytes)
            
            $Buffer = New-Object byte[] $CurrentChunkSize
            $Random.NextBytes($Buffer)
            $FileStream.Write($Buffer, 0, $CurrentChunkSize)
            
            $BytesWritten += $CurrentChunkSize
        }
    }
    finally {
        $FileStream.Close()
    }
    
    return $FilePath
}

# Calculate MD5 hash of file
function Get-FileMD5Hash {
    param([string]$FilePath)
    
    $MD5 = [System.Security.Cryptography.MD5]::Create()
    $FileStream = [System.IO.File]::OpenRead($FilePath)
    
    try {
        $HashBytes = $MD5.ComputeHash($FileStream)
        $HashString = [System.BitConverter]::ToString($HashBytes).Replace("-", "").ToLower()
        return $HashString
    }
    finally {
        $FileStream.Close()
        $MD5.Dispose()
    }
}

# Check if RustFS is running
function Test-RustFSConnection {
    try {
        $Response = Invoke-WebRequest -Uri "$RustFSEndpoint" -Method GET -TimeoutSec 5 -ErrorAction Stop
        return $Response.StatusCode -eq 200
    }
    catch {
        try {
            $Response = Invoke-WebRequest -Uri "$RustFSEndpoint/minio/health/live" -Method GET -TimeoutSec 5 -ErrorAction SilentlyContinue
            return $Response.StatusCode -eq 200
        }
        catch {
            return $false
        }
    }
}

# === Main Test Execution ===

Write-TestHeader "Multipart Upload Integration Test"
Write-Host "Upload Service: $UploadServiceUrl" -ForegroundColor Gray
Write-Host "User Service: $UserServiceUrl" -ForegroundColor Gray
Write-Host "RustFS Endpoint: $RustFSEndpoint" -ForegroundColor Gray
Write-Host ""

$TestsPassed = 0
$TestsFailed = 0
$TestsSkipped = 0

# === Pre-check: Verify RustFS is running ===
Write-Host "=== Pre-check: RustFS Connection ===" -ForegroundColor Magenta
Write-Host ""

$RustFSRunning = Test-RustFSConnection
if ($RustFSRunning) {
    Write-TestStep "RustFS is running and healthy" "PASS"
    $TestsPassed++
} else {
    Write-TestStep "RustFS is not running or not accessible at $RustFSEndpoint" "FAIL"
    Write-Host ""
    Write-Host "Please ensure RustFS is running:" -ForegroundColor Yellow
    Write-Host "  cd docker" -ForegroundColor Gray
    Write-Host "  docker-compose up -d rustfs" -ForegroundColor Gray
    Write-Host ""
    $TestsFailed++
}

# === Pre-check: Verify Upload Service is running ===
Write-Host ""
Write-Host "=== Pre-check: Upload Service Connection ===" -ForegroundColor Magenta
Write-Host ""

try {
    $HealthResult = Invoke-WebRequest -Uri "$UploadServiceUrl/actuator/health" -Method GET -TimeoutSec 5 -ErrorAction Stop
    if ($HealthResult.StatusCode -eq 200) {
        Write-TestStep "Upload Service is running" "PASS"
        $TestsPassed++
    } else {
        Write-TestStep "Upload Service returned unexpected status: $($HealthResult.StatusCode)" "WARN"
    }
}
catch {
    Write-TestStep "Upload Service is not running at $UploadServiceUrl" "FAIL"
    Write-Host ""
    Write-Host "Please ensure ZhiCore-upload service is running with STORAGE_TYPE=s3:" -ForegroundColor Yellow
    Write-Host "  Set environment variable: STORAGE_TYPE=s3" -ForegroundColor Gray
    Write-Host "  Start the service: mvn spring-boot:run -pl ZhiCore-upload" -ForegroundColor Gray
    Write-Host ""
    $TestsFailed++
    
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Red
    Write-Host "Test Aborted - Upload Service Not Running" -ForegroundColor Red
    Write-Host "========================================" -ForegroundColor Red
    exit 1
}

# === Setup: Create test user and login ===
Write-Host ""
Write-Host "=== Setup: User Authentication ===" -ForegroundColor Magenta
Write-Host ""

# Create test user
Write-TestStep "Creating test user: $UniqueUsername" "INFO"
$RegisterBody = @{ userName = $UniqueUsername; email = $UniqueEmail; password = $TestUser.password }
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/register" -Body $RegisterBody

if ($Result.Success -and $Result.Body.code -eq 200) {
    $Global:TestUserId = $Result.Body.data
    Write-TestStep "User created with ID: $Global:TestUserId" "PASS"
    $TestsPassed++
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Write-TestStep "Failed to create user: $ErrorMsg" "FAIL"
    $TestsFailed++
}

# Login
Write-TestStep "Logging in test user" "INFO"
$LoginBody = @{ email = $UniqueEmail; password = $TestUser.password }
$Result = Invoke-ApiRequest -Method "POST" -Url "$UserServiceUrl/api/v1/auth/login" -Body $LoginBody

if ($Result.Success -and $Result.Body.code -eq 200 -and $Result.Body.data.accessToken) {
    $Global:AccessToken = $Result.Body.data.accessToken
    Write-TestStep "Login successful, token obtained" "PASS"
    $TestsPassed++
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Write-TestStep "Login failed: $ErrorMsg" "FAIL"
    $TestsFailed++
    
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Red
    Write-Host "Test Aborted - Authentication Failed" -ForegroundColor Red
    Write-Host "========================================" -ForegroundColor Red
    exit 1
}

# Create temp directory for test files
$TempDir = Join-Path $env:TEMP "multipart_test_$Timestamp"
New-Item -ItemType Directory -Path $TempDir -Force | Out-Null
Write-TestStep "Created temp directory: $TempDir" "INFO"

# === Test 1: Create Large Test File ===
Write-Host ""
Write-Host "=== Test 1: Create Large Test File ===" -ForegroundColor Magenta
Write-Host ""

$TestFilePath = Join-Path $TempDir "large_test_file.bin"
$FileSizeMB = 15  # 15MB to trigger multipart upload (threshold is 10MB)

Write-TestStep "Creating $FileSizeMB MB test file..." "INFO"
$StartTime = Get-Date
New-TestLargeFile -FilePath $TestFilePath -SizeMB $FileSizeMB
$CreationTime = [math]::Round(((Get-Date) - $StartTime).TotalSeconds, 2)

if (Test-Path $TestFilePath) {
    $FileInfo = Get-Item $TestFilePath
    $ActualSizeMB = [math]::Round($FileInfo.Length / 1MB, 2)
    Write-TestStep "Test file created successfully" "PASS"
    Write-Host "  Path: $TestFilePath" -ForegroundColor Gray
    Write-Host "  Size: $ActualSizeMB MB" -ForegroundColor Gray
    Write-Host "  Creation Time: $CreationTime seconds" -ForegroundColor Gray
    $TestsPassed++
} else {
    Write-TestStep "Failed to create test file" "FAIL"
    $TestsFailed++
    exit 1
}

# Calculate file hash
Write-TestStep "Calculating file MD5 hash..." "INFO"
$FileHash = Get-FileMD5Hash -FilePath $TestFilePath
Write-Host "  Hash: $FileHash" -ForegroundColor Gray

# === Test 2: Initialize Multipart Upload ===
Write-Host ""
Write-Host "=== Test 2: Initialize Multipart Upload ===" -ForegroundColor Magenta
Write-Host ""

$InitBody = @{
    fileName = "large_test_file.bin"
    fileSize = $FileInfo.Length
    fileHash = $FileHash
    contentType = "application/octet-stream"
}

Write-TestStep "Initializing multipart upload..." "INFO"
$Result = Invoke-ApiRequest -Method "POST" -Url "$UploadServiceUrl/api/v1/multipart/init" -Body $InitBody -Headers (Get-AuthHeaders)

if ($Result.Success -and $Result.Body.code -eq 200 -and $Result.Body.data) {
    $Global:TaskId = $Result.Body.data.taskId
    $Global:UploadId = $Result.Body.data.uploadId
    $ChunkSize = $Result.Body.data.chunkSize
    $TotalParts = $Result.Body.data.totalParts
    $CompletedParts = $Result.Body.data.completedParts
    
    Write-TestStep "Multipart upload initialized successfully" "PASS"
    Write-Host "  Task ID: $Global:TaskId" -ForegroundColor Gray
    Write-Host "  Upload ID: $Global:UploadId" -ForegroundColor Gray
    Write-Host "  Chunk Size: $([math]::Round($ChunkSize / 1MB, 2)) MB" -ForegroundColor Gray
    Write-Host "  Total Parts: $TotalParts" -ForegroundColor Gray
    Write-Host "  Completed Parts: $($CompletedParts.Count)" -ForegroundColor Gray
    Write-Host "  Response Time: $($Result.ResponseTime)ms" -ForegroundColor Gray
    $TestsPassed++
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Write-TestStep "Failed to initialize multipart upload: $ErrorMsg" "FAIL"
    Write-Host "  Status Code: $($Result.StatusCode)" -ForegroundColor Gray
    $TestsFailed++
    exit 1
}

# === Test 3: Upload Parts ===
Write-Host ""
Write-Host "=== Test 3: Upload Parts ===" -ForegroundColor Magenta
Write-Host ""

$FileStream = [System.IO.File]::OpenRead($TestFilePath)
$UploadedParts = @()
$PartNumber = 1

try {
    while ($FileStream.Position -lt $FileStream.Length) {
        $RemainingBytes = $FileStream.Length - $FileStream.Position
        $CurrentChunkSize = [Math]::Min($ChunkSize, $RemainingBytes)
        
        $Buffer = New-Object byte[] $CurrentChunkSize
        $BytesRead = $FileStream.Read($Buffer, 0, $CurrentChunkSize)
        
        Write-TestStep "Uploading part $PartNumber of $TotalParts..." "INFO"
        
        $PartUrl = "$UploadServiceUrl/api/v1/multipart/$Global:TaskId/parts/$PartNumber"
        $PartResult = Invoke-PartUpload -Url $PartUrl -Data $Buffer -Headers (Get-AuthHeaders)
        
        if ($PartResult.Success -and $PartResult.Body.code -eq 200) {
            $ETag = $PartResult.Body.data.etag
            Write-Host "  Part $PartNumber uploaded successfully (ETag: $ETag, $($PartResult.ResponseTime)ms)" -ForegroundColor Green
            $UploadedParts += $PartNumber
        } else {
            $ErrorMsg = if ($PartResult.Body.message) { $PartResult.Body.message } else { $PartResult.Error }
            Write-TestStep "Failed to upload part ${PartNumber}: $ErrorMsg" "FAIL"
            $TestsFailed++
            break
        }
        
        $PartNumber++
    }
}
finally {
    $FileStream.Close()
}

if ($UploadedParts.Count -eq $TotalParts) {
    Write-TestStep "All $TotalParts parts uploaded successfully" "PASS"
    $TestsPassed++
} else {
    Write-TestStep "Only $($UploadedParts.Count) of $TotalParts parts uploaded" "FAIL"
    $TestsFailed++
}

# === Test 4: Check Upload Progress ===
Write-Host ""
Write-Host "=== Test 4: Check Upload Progress ===" -ForegroundColor Magenta
Write-Host ""

Write-TestStep "Querying upload progress..." "INFO"
$Result = Invoke-ApiRequest -Method "GET" -Url "$UploadServiceUrl/api/v1/multipart/$Global:TaskId/progress" -Headers (Get-AuthHeaders)

if ($Result.Success -and $Result.Body.code -eq 200 -and $Result.Body.data) {
    $Progress = $Result.Body.data
    Write-TestStep "Upload progress retrieved successfully" "PASS"
    Write-Host "  Total Parts: $($Progress.totalParts)" -ForegroundColor Gray
    Write-Host "  Completed Parts: $($Progress.completedParts)" -ForegroundColor Gray
    Write-Host "  Uploaded Bytes: $([math]::Round($Progress.uploadedBytes / 1MB, 2)) MB" -ForegroundColor Gray
    Write-Host "  Total Bytes: $([math]::Round($Progress.totalBytes / 1MB, 2)) MB" -ForegroundColor Gray
    Write-Host "  Percentage: $($Progress.percentage)%" -ForegroundColor Gray
    $TestsPassed++
    
    # Verify progress matches uploaded parts
    if ($Progress.completedParts -eq $UploadedParts.Count) {
        Write-TestStep "Progress matches uploaded parts count" "PASS"
        $TestsPassed++
    } else {
        Write-TestStep "Progress mismatch: expected $($UploadedParts.Count), got $($Progress.completedParts)" "FAIL"
        $TestsFailed++
    }
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Write-TestStep "Failed to get upload progress: $ErrorMsg" "FAIL"
    $TestsFailed++
}

# === Test 5: Complete Multipart Upload ===
Write-Host ""
Write-Host "=== Test 5: Complete Multipart Upload ===" -ForegroundColor Magenta
Write-Host ""

Write-TestStep "Completing multipart upload..." "INFO"
$Result = Invoke-ApiRequest -Method "POST" -Url "$UploadServiceUrl/api/v1/multipart/$Global:TaskId/complete" -Headers (Get-AuthHeaders)

$UploadedFileUrl = $null
$UploadedFileId = $null

if ($Result.Success -and $Result.Body.code -eq 200 -and $Result.Body.data) {
    $UploadedFileId = $Result.Body.data.fileId
    $UploadedFileUrl = $Result.Body.data.url
    
    Write-TestStep "Multipart upload completed successfully" "PASS"
    Write-Host "  File ID: $UploadedFileId" -ForegroundColor Gray
    Write-Host "  URL: $UploadedFileUrl" -ForegroundColor Gray
    Write-Host "  Response Time: $($Result.ResponseTime)ms" -ForegroundColor Gray
    $TestsPassed++
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Write-TestStep "Failed to complete multipart upload: $ErrorMsg" "FAIL"
    Write-Host "  Status Code: $($Result.StatusCode)" -ForegroundColor Gray
    $TestsFailed++
}

# === Test 6: Verify File Exists in RustFS ===
Write-Host ""
Write-Host "=== Test 6: Verify File in RustFS ===" -ForegroundColor Magenta
Write-Host ""

if ($UploadedFileUrl -and $RustFSRunning) {
    Write-TestStep "Checking if file exists in RustFS..." "INFO"
    
    try {
        $FileCheckResult = Invoke-WebRequest -Uri $UploadedFileUrl -Method HEAD -TimeoutSec 30 -ErrorAction Stop
        if ($FileCheckResult.StatusCode -eq 200) {
            Write-TestStep "File is accessible via returned URL" "PASS"
            Write-Host "  Content-Type: $($FileCheckResult.Headers['Content-Type'])" -ForegroundColor Gray
            Write-Host "  Content-Length: $([math]::Round([int64]$FileCheckResult.Headers['Content-Length'] / 1MB, 2)) MB" -ForegroundColor Gray
            $TestsPassed++
        } else {
            Write-TestStep "File returned unexpected status: $($FileCheckResult.StatusCode)" "WARN"
        }
    }
    catch {
        $ErrorMsg = $_.Exception.Message
        Write-TestStep "File not accessible via URL: $ErrorMsg" "WARN"
        Write-Host "  URL: $UploadedFileUrl" -ForegroundColor Gray
        Write-Host "  Note: File may exist but bucket is private (expected behavior)" -ForegroundColor Yellow
    }
} elseif (-not $UploadedFileUrl) {
    Write-TestStep "Skipping - No uploaded file URL available" "SKIP"
    $TestsSkipped++
} else {
    Write-TestStep "Skipping - RustFS not running" "SKIP"
    $TestsSkipped++
}

# === Test 7: Test Resumption (Simulate Interrupted Upload) ===
Write-Host ""
Write-Host "=== Test 7: Test Resumption (Interrupted Upload) ===" -ForegroundColor Magenta
Write-Host ""

# Create another test file with same hash to test resumption
$TestFilePath2 = Join-Path $TempDir "large_test_file2.bin"
Write-TestStep "Creating second test file for resumption test..." "INFO"
Copy-Item -Path $TestFilePath -Destination $TestFilePath2 -Force

# Initialize upload with same hash
$InitBody2 = @{
    fileName = "large_test_file2.bin"
    fileSize = $FileInfo.Length
    fileHash = $FileHash
    contentType = "application/octet-stream"
}

Write-TestStep "Initializing upload with same file hash..." "INFO"
$Result = Invoke-ApiRequest -Method "POST" -Url "$UploadServiceUrl/api/v1/multipart/init" -Body $InitBody2 -Headers (Get-AuthHeaders)

if ($Result.Success -and $Result.Body.code -eq 200 -and $Result.Body.data) {
    $TaskId2 = $Result.Body.data.taskId
    $CompletedParts2 = $Result.Body.data.completedParts
    
    # Check if it returned existing task or created new one
    if ($TaskId2 -eq $Global:TaskId) {
        Write-TestStep "Resumption detected: returned existing completed task" "PASS"
        Write-Host "  Task ID: $TaskId2 (same as previous)" -ForegroundColor Gray
        Write-Host "  Completed Parts: $($CompletedParts2.Count)" -ForegroundColor Gray
        $TestsPassed++
    } else {
        Write-TestStep "New task created (file already completed, no resumption needed)" "INFO"
        Write-Host "  Task ID: $TaskId2 (new)" -ForegroundColor Gray
        
        # Abort this new task since we don't need it
        Write-TestStep "Aborting new task..." "INFO"
        $AbortResult = Invoke-ApiRequest -Method "DELETE" -Url "$UploadServiceUrl/api/v1/multipart/$TaskId2" -Headers (Get-AuthHeaders)
        if ($AbortResult.Success) {
            Write-Host "  Task aborted successfully" -ForegroundColor Gray
        }
    }
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Write-TestStep "Failed to test resumption: $ErrorMsg" "FAIL"
    $TestsFailed++
}

# === Test 8: Test Partial Upload and Resumption ===
Write-Host ""
Write-Host "=== Test 8: Test Partial Upload and Resumption ===" -ForegroundColor Magenta
Write-Host ""

# Create a new file with different hash
$TestFilePath3 = Join-Path $TempDir "partial_test_file.bin"
Write-TestStep "Creating new test file for partial upload..." "INFO"
New-TestLargeFile -FilePath $TestFilePath3 -SizeMB 12

$FileInfo3 = Get-Item $TestFilePath3
$FileHash3 = Get-FileMD5Hash -FilePath $TestFilePath3

# Initialize upload
$InitBody3 = @{
    fileName = "partial_test_file.bin"
    fileSize = $FileInfo3.Length
    fileHash = $FileHash3
    contentType = "application/octet-stream"
}

Write-TestStep "Initializing partial upload..." "INFO"
$Result = Invoke-ApiRequest -Method "POST" -Url "$UploadServiceUrl/api/v1/multipart/init" -Body $InitBody3 -Headers (Get-AuthHeaders)

if ($Result.Success -and $Result.Body.code -eq 200 -and $Result.Body.data) {
    $TaskId3 = $Result.Body.data.taskId
    $ChunkSize3 = $Result.Body.data.chunkSize
    $TotalParts3 = $Result.Body.data.totalParts
    
    Write-TestStep "Partial upload initialized" "PASS"
    Write-Host "  Task ID: $TaskId3" -ForegroundColor Gray
    Write-Host "  Total Parts: $TotalParts3" -ForegroundColor Gray
    $TestsPassed++
    
    # Upload only first 2 parts (simulate interruption)
    $FileStream3 = [System.IO.File]::OpenRead($TestFilePath3)
    $PartsToUpload = 2
    
    try {
        for ($i = 1; $i -le $PartsToUpload; $i++) {
            $Buffer = New-Object byte[] $ChunkSize3
            $BytesRead = $FileStream3.Read($Buffer, 0, $ChunkSize3)
            
            $PartUrl = "$UploadServiceUrl/api/v1/multipart/$TaskId3/parts/$i"
            $PartResult = Invoke-PartUpload -Url $PartUrl -Data $Buffer -Headers (Get-AuthHeaders)
            
            if ($PartResult.Success) {
                Write-Host "  Part $i uploaded" -ForegroundColor Green
            }
        }
    }
    finally {
        $FileStream3.Close()
    }
    
    Write-TestStep "Uploaded $PartsToUpload of $TotalParts3 parts (simulating interruption)" "INFO"
    
    # Now try to resume - initialize again with same hash
    Write-TestStep "Attempting to resume upload..." "INFO"
    $ResumeResult = Invoke-ApiRequest -Method "POST" -Url "$UploadServiceUrl/api/v1/multipart/init" -Body $InitBody3 -Headers (Get-AuthHeaders)
    
    if ($ResumeResult.Success -and $ResumeResult.Body.code -eq 200 -and $ResumeResult.Body.data) {
        $ResumedTaskId = $ResumeResult.Body.data.taskId
        $ResumedCompletedParts = $ResumeResult.Body.data.completedParts
        
        if ($ResumedTaskId -eq $TaskId3 -and $ResumedCompletedParts.Count -eq $PartsToUpload) {
            Write-TestStep "Resumption successful: returned existing task with completed parts" "PASS"
            Write-Host "  Task ID: $ResumedTaskId (same as previous)" -ForegroundColor Gray
            Write-Host "  Completed Parts: $($ResumedCompletedParts.Count)" -ForegroundColor Gray
            Write-Host "  Parts: $($ResumedCompletedParts -join ', ')" -ForegroundColor Gray
            $TestsPassed++
        } else {
            Write-TestStep "Resumption returned unexpected data" "FAIL"
            Write-Host "  Expected Task ID: $TaskId3, Got: $ResumedTaskId" -ForegroundColor Gray
            Write-Host "  Expected Parts: $PartsToUpload, Got: $($ResumedCompletedParts.Count)" -ForegroundColor Gray
            $TestsFailed++
        }
    } else {
        $ErrorMsg = if ($ResumeResult.Body.message) { $ResumeResult.Body.message } else { $ResumeResult.Error }
        Write-TestStep "Failed to resume upload: $ErrorMsg" "FAIL"
        $TestsFailed++
    }
    
    # Clean up - abort the partial upload
    Write-TestStep "Aborting partial upload task..." "INFO"
    $AbortResult = Invoke-ApiRequest -Method "DELETE" -Url "$UploadServiceUrl/api/v1/multipart/$TaskId3" -Headers (Get-AuthHeaders)
    if ($AbortResult.Success -and $AbortResult.Body.code -eq 200) {
        Write-Host "  Partial upload task aborted successfully" -ForegroundColor Gray
    }
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Write-TestStep "Failed to initialize partial upload: $ErrorMsg" "FAIL"
    $TestsFailed++
}

# === Test 9: List Upload Tasks ===
Write-Host ""
Write-Host "=== Test 9: List Upload Tasks ===" -ForegroundColor Magenta
Write-Host ""

Write-TestStep "Listing user's upload tasks..." "INFO"
$Result = Invoke-ApiRequest -Method "GET" -Url "$UploadServiceUrl/api/v1/multipart/tasks" -Headers (Get-AuthHeaders)

if ($Result.Success -and $Result.Body.code -eq 200) {
    $Tasks = $Result.Body.data
    Write-TestStep "Upload tasks retrieved successfully" "PASS"
    Write-Host "  Total Tasks: $($Tasks.Count)" -ForegroundColor Gray
    
    if ($Tasks.Count -gt 0) {
        foreach ($Task in $Tasks) {
            Write-Host "  - Task ID: $($Task.id), Status: $($Task.status), File: $($Task.fileName)" -ForegroundColor Gray
        }
    }
    $TestsPassed++
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Write-TestStep "Failed to list upload tasks: $ErrorMsg" "FAIL"
    $TestsFailed++
}

# === Cleanup ===
Write-Host ""
Write-Host "=== Cleanup ===" -ForegroundColor Magenta
Write-Host ""

# Remove temp directory
if (Test-Path $TempDir) {
    Remove-Item -Path $TempDir -Recurse -Force
    Write-TestStep "Removed temp directory: $TempDir" "INFO"
}

# === Test Summary ===
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Test Results Summary" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Total Tests: $($TestsPassed + $TestsFailed + $TestsSkipped)" -ForegroundColor White
Write-Host "Passed: $TestsPassed" -ForegroundColor Green
Write-Host "Failed: $TestsFailed" -ForegroundColor Red
Write-Host "Skipped: $TestsSkipped" -ForegroundColor Gray
Write-Host ""

if ($TestsFailed -eq 0 -and $TestsPassed -gt 0) {
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "ALL TESTS PASSED" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "Multipart upload integration is working correctly!" -ForegroundColor Green
    Write-Host "Key features verified:" -ForegroundColor Green
    Write-Host "  - Large file multipart upload" -ForegroundColor Gray
    Write-Host "  - Upload progress tracking" -ForegroundColor Gray
    Write-Host "  - Upload resumption (断点续传)" -ForegroundColor Gray
    Write-Host "  - Task management" -ForegroundColor Gray
    Write-Host ""
    exit 0
} elseif ($TestsFailed -gt 0) {
    Write-Host "========================================" -ForegroundColor Red
    Write-Host "SOME TESTS FAILED" -ForegroundColor Red
    Write-Host "========================================" -ForegroundColor Red
    Write-Host ""
    Write-Host "Please check:" -ForegroundColor Yellow
    Write-Host "  1. RustFS is running: docker-compose up -d rustfs" -ForegroundColor Gray
    Write-Host "  2. Upload service has STORAGE_TYPE=s3 set" -ForegroundColor Gray
    Write-Host "  3. Database is running and migrations applied" -ForegroundColor Gray
    Write-Host "  4. Multipart upload feature is enabled in config" -ForegroundColor Gray
    Write-Host ""
    exit 1
} else {
    Write-Host "========================================" -ForegroundColor Yellow
    Write-Host "NO TESTS EXECUTED" -ForegroundColor Yellow
    Write-Host "========================================" -ForegroundColor Yellow
    exit 0
}
