# Docker 로그 확인 스크립트
# PowerShell에서 실행: .\docker-logs.ps1

param(
    [string]$Service = "all",
    [switch]$Follow = $false
)

Write-Host "📋 Y-DANAWA Docker 로그 확인" -ForegroundColor Cyan
Write-Host ""

if ($Service -eq "all") {
    if ($Follow) {
        Write-Host "실시간 로그 확인 중... (Ctrl+C로 종료)" -ForegroundColor Yellow
        docker-compose logs -f
    } else {
        docker-compose logs --tail=50
    }
} else {
    if ($Follow) {
        Write-Host "[$Service] 실시간 로그 확인 중... (Ctrl+C로 종료)" -ForegroundColor Yellow
        docker-compose logs -f $Service
    } else {
        docker-compose logs --tail=50 $Service
    }
}

Write-Host ""
Write-Host "💡 사용법:" -ForegroundColor Yellow
Write-Host "  전체 로그: .\docker-logs.ps1" -ForegroundColor Cyan
Write-Host "  특정 서비스: .\docker-logs.ps1 -Service backend" -ForegroundColor Cyan
Write-Host "  실시간 모드: .\docker-logs.ps1 -Follow" -ForegroundColor Cyan
Write-Host ""
Write-Host "  서비스 목록: frontend, backend, db" -ForegroundColor Green

