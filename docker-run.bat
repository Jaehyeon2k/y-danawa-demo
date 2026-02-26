@echo off
chcp 65001 >nul
title Y-DANAWA Docker 실행

echo.
echo ========================================
echo   🐳 Y-DANAWA Docker 환경 시작
echo ========================================
echo.

REM Docker 설치 확인
where docker >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ Docker가 설치되어 있지 않습니다.
    echo.
    echo Docker Desktop 설치:
    echo   1. https://www.docker.com/products/docker-desktop 방문
    echo   2. Windows용 Docker Desktop 다운로드 및 설치
    echo   3. 설치 후 Docker Desktop 실행
    echo   4. 이 파일 다시 실행
    echo.
    pause
    exit /b 1
)

echo ✅ Docker 설치 확인
echo.

REM Docker 실행 확인
docker ps >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ Docker Desktop이 실행되지 않았습니다.
    echo    Docker Desktop을 실행한 후 다시 시도하세요.
    echo.
    pause
    exit /b 1
)

echo ✅ Docker 실행 중
echo.

REM 기존 컨테이너 정리
echo 기존 컨테이너 정리 중...
docker-compose down >nul 2>&1
echo ✅ 정리 완료
echo.

REM 백엔드 빌드
echo 백엔드 빌드 중... (잠시만 기다려주세요)
call gradlew.bat clean bootJar -x test --console=plain --quiet
if %errorlevel% neq 0 (
    echo ❌ 백엔드 빌드 실패
    pause
    exit /b 1
)
echo ✅ 백엔드 빌드 완료
echo.

REM Docker Compose 실행
echo Docker 이미지 빌드 및 컨테이너 실행 중...
echo (처음 실행 시 시간이 오래 걸릴 수 있습니다)
echo.
docker-compose up --build -d

if %errorlevel% equ 0 (
    echo.
    echo ========================================
    echo   ✅ Docker 컨테이너 실행 완료!
    echo ========================================
    echo.
    echo 📍 접속 정보:
    echo   🌐 프론트엔드: http://localhost
    echo   🔌 백엔드 API: http://localhost:8080/api
    echo   🗄️  데이터베이스: localhost:5432
    echo.
    echo 📊 유용한 명령어:
    echo   컨테이너 상태: docker-compose ps
    echo   로그 확인: docker-compose logs -f
    echo   중지: docker-compose down
    echo.
    echo ⏳ 백엔드가 완전히 시작될 때까지 약 20초 기다려주세요...
    echo.

    REM 컨테이너 상태 표시
    docker-compose ps
    echo.
    echo 웹 브라우저를 열려면 아무 키나 누르세요...
    pause >nul
    start http://localhost
) else (
    echo.
    echo ❌ Docker 컨테이너 실행 실패
    echo    로그 확인: docker-compose logs
    pause
    exit /b 1
)

