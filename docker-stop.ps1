# Docker 환경 중지 스크립트
# PowerShell에서 실행: .\docker-stop.ps1

param(
    [switch]$RemoveVolumes = $false
)

Write-Host "🛑 Y-DANAWA Docker 환경 중지" -ForegroundColor Yellow
Write-Host ""

if ($RemoveVolumes) {
    Write-Host "⚠️  데이터베이스 볼륨도 함께 삭제됩니다!" -ForegroundColor Red
    Write-Host ""
    docker-compose down -v
    Write-Host ""
    Write-Host "✅ 모든 컨테이너와 볼륨이 삭제되었습니다." -ForegroundColor Green
} else {
    docker-compose down
    Write-Host ""
    Write-Host "✅ 컨테이너가 중지되었습니다." -ForegroundColor Green
    Write-Host "   (데이터베이스 데이터는 보존됨)" -ForegroundColor Cyan
}

Write-Host ""
Write-Host "💡 데이터베이스까지 삭제하려면:" -ForegroundColor Yellow
Write-Host "   .\docker-stop.ps1 -RemoveVolumes" -ForegroundColor Cyan

