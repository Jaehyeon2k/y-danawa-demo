# Docker 환경 실행 스크립트
# PowerShell에서 실행: .\docker-run.ps1

Write-Host "🐳 Y-DANAWA Docker 환경 시작" -ForegroundColor Green
Write-Host ""

# Docker 설치 확인
Write-Host "1. Docker 설치 확인 중..." -ForegroundColor Cyan
$dockerInstalled = Get-Command docker -ErrorAction SilentlyContinue
if (-not $dockerInstalled) {
    Write-Host "❌ Docker가 설치되어 있지 않습니다." -ForegroundColor Red
    Write-Host ""
    Write-Host "Docker Desktop 설치:" -ForegroundColor Yellow
    Write-Host "  1. https://www.docker.com/products/docker-desktop 방문"
    Write-Host "  2. Windows용 Docker Desktop 다운로드 및 설치"
    Write-Host "  3. 설치 후 Docker Desktop 실행"
    Write-Host "  4. 이 스크립트 다시 실행"
    Write-Host ""
    exit 1
}
Write-Host "✅ Docker 설치 확인됨" -ForegroundColor Green

# Docker 실행 확인
Write-Host "2. Docker 실행 상태 확인 중..." -ForegroundColor Cyan
try {
    docker ps | Out-Null
    Write-Host "✅ Docker가 실행 중입니다" -ForegroundColor Green
} catch {
    Write-Host "❌ Docker Desktop이 실행되지 않았습니다." -ForegroundColor Red
    Write-Host "   Docker Desktop을 실행한 후 다시 시도하세요." -ForegroundColor Yellow
    exit 1
}

# 기존 컨테이너 중지
Write-Host ""
Write-Host "3. 기존 컨테이너 정리 중..." -ForegroundColor Cyan
docker-compose down 2>&1 | Out-Null
Write-Host "✅ 정리 완료" -ForegroundColor Green

# 백엔드 빌드
Write-Host ""
Write-Host "4. 백엔드 빌드 중..." -ForegroundColor Cyan
.\gradlew.bat clean bootJar -x test --console=plain --quiet
if ($LASTEXITCODE -eq 0) {
    Write-Host "✅ 백엔드 빌드 완료" -ForegroundColor Green
} else {
    Write-Host "❌ 백엔드 빌드 실패" -ForegroundColor Red
    exit 1
}

# Docker Compose 빌드 및 실행
Write-Host ""
Write-Host "5. Docker 이미지 빌드 및 컨테이너 실행 중..." -ForegroundColor Cyan
Write-Host "   (처음 실행 시 시간이 오래 걸릴 수 있습니다)" -ForegroundColor Yellow
Write-Host ""

docker-compose up --build -d

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "✅ Docker 컨테이너 실행 완료!" -ForegroundColor Green
    Write-Host ""
    Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
    Write-Host "📍 접속 정보" -ForegroundColor Yellow
    Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
    Write-Host "  🌐 프론트엔드: " -NoNewline; Write-Host "http://localhost" -ForegroundColor Green
    Write-Host "  🔌 백엔드 API: " -NoNewline; Write-Host "http://localhost:8080/api" -ForegroundColor Green
    Write-Host "  🗄️  데이터베이스: " -NoNewline; Write-Host "localhost:5432" -ForegroundColor Green
    Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "📊 컨테이너 상태 확인: " -NoNewline; Write-Host "docker-compose ps" -ForegroundColor Cyan
    Write-Host "📋 로그 확인: " -NoNewline; Write-Host "docker-compose logs -f" -ForegroundColor Cyan
    Write-Host "🛑 중지: " -NoNewline; Write-Host "docker-compose down" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "⏳ 백엔드가 완전히 시작될 때까지 약 20초 정도 기다려주세요..." -ForegroundColor Yellow
    Write-Host ""

    # 컨테이너 상태 표시
    Write-Host "현재 실행 중인 컨테이너:" -ForegroundColor Cyan
    docker-compose ps

} else {
    Write-Host ""
    Write-Host "❌ Docker 컨테이너 실행 실패" -ForegroundColor Red
    Write-Host "   로그 확인: docker-compose logs" -ForegroundColor Yellow
    exit 1
}

