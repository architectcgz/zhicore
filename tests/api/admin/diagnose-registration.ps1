# Diagnose User Registration Issue
# This script tests user registration with detailed error output

param(
    [string]$ConfigPath = "../../config/test-env.json"
)

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ConfigFullPath = Join-Path $ScriptDir $ConfigPath
$Config = Get-Content $ConfigFullPath | ConvertFrom-Json
$UserServiceUrl = $Config.user_service_url

$Timestamp = Get-Date -Format "yyyyMMddHHmmss"
$TestUsername = "diagtest_$Timestamp"
$TestEmail = "diagtest_$Timestamp@example.com"
$TestPassword = "Test123456!"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "User Registration Diagnosis" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "Configuration:" -ForegroundColor Yellow
Write-Host "  User Service URL: $UserServiceUrl" -ForegroundColor White
Write-Host "  Test Username: $TestUsername" -ForegroundColor White
Write-Host "  Test Email: $TestEmail" -ForegroundColor White
Write-Host ""

# Test 1: Check if user service is accessible
Write-Host "[TEST 1] Checking user service accessibility..." -ForegroundColor Yellow
try {
    $Response = Invoke-WebRequest -Uri "$UserServiceUrl/actuator/health" -Method GET -ErrorAction Stop
    Write-Host "  SUCCESS - User service is accessible" -ForegroundColor Green
    Write-Host "  Status Code: $($Response.StatusCode)" -ForegroundColor White
} catch {
    Write-Host "  FAILED - Cannot reach user service" -ForegroundColor Red
    Write-Host "  Error: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
Write-Host ""

# Test 2: Attempt registration with detailed error capture
Write-Host "[TEST 2] Attempting user registration..." -ForegroundColor Yellow
$RegisterBody = @{
    userName = $TestUsername
    email = $TestEmail
    password = $TestPassword
}

Write-Host "  Request Body:" -ForegroundColor White
Write-Host "  $($RegisterBody | ConvertTo-Json)" -ForegroundColor Gray
Write-Host ""

try {
    $RequestParams = @{
        Method = "POST"
        Uri = "$UserServiceUrl/api/v1/auth/register"
        ContentType = "application/json"
        Body = ($RegisterBody | ConvertTo-Json -Depth 10)
        ErrorAction = "Stop"
    }
    
    $Response = Invoke-WebRequest @RequestParams
    $ResponseBody = $Response.Content | ConvertFrom-Json
    
    Write-Host "  SUCCESS - Registration completed" -ForegroundColor Green
    Write-Host "  Status Code: $($Response.StatusCode)" -ForegroundColor White
    Write-Host "  Response Body:" -ForegroundColor White
    Write-Host "  $($Response.Content)" -ForegroundColor Gray
    Write-Host ""
    
    if ($ResponseBody.code -eq 200) {
        Write-Host "  User ID: $($ResponseBody.data)" -ForegroundColor Green
    } else {
        Write-Host "  WARNING - Response code is not 200" -ForegroundColor Yellow
        Write-Host "  Code: $($ResponseBody.code)" -ForegroundColor Yellow
        Write-Host "  Message: $($ResponseBody.message)" -ForegroundColor Yellow
    }
    
} catch {
    Write-Host "  FAILED - Registration failed" -ForegroundColor Red
    Write-Host ""
    Write-Host "  Exception Type: $($_.Exception.GetType().FullName)" -ForegroundColor Red
    Write-Host "  Exception Message: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host ""
    
    if ($_.Exception.Response) {
        $StatusCode = [int]$_.Exception.Response.StatusCode
        Write-Host "  HTTP Status Code: $StatusCode" -ForegroundColor Red
        
        try {
            $StreamReader = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
            $ErrorBody = $StreamReader.ReadToEnd()
            $StreamReader.Close()
            
            Write-Host "  Response Body:" -ForegroundColor Red
            Write-Host "  $ErrorBody" -ForegroundColor Gray
            Write-Host ""
            
            try {
                $ErrorJson = $ErrorBody | ConvertFrom-Json
                Write-Host "  Parsed Error:" -ForegroundColor Red
                Write-Host "  Code: $($ErrorJson.code)" -ForegroundColor Gray
                Write-Host "  Message: $($ErrorJson.message)" -ForegroundColor Gray
                if ($ErrorJson.data) {
                    Write-Host "  Data: $($ErrorJson.data)" -ForegroundColor Gray
                }
            } catch {
                Write-Host "  Could not parse error response as JSON" -ForegroundColor Yellow
            }
        } catch {
            Write-Host "  Could not read error response body" -ForegroundColor Yellow
        }
    } else {
        Write-Host "  No HTTP response available (network error?)" -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Diagnosis Complete" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
