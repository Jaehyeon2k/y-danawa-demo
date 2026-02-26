@echo off
chcp 65001 >nul
title Y-DANAWA Docker 중지

echo.
echo ========================================
echo   🛑 Y-DANAWA Docker 환경 중지
echo ========================================
echo.

docker-compose down

if %errorlevel% equ 0 (
    echo.
    echo ✅ 컨테이너가 중지되었습니다.
    echo    (데이터베이스 데이터는 보존됨)
    echo.
    echo 💡 데이터베이스까지 삭제하려면:
    echo    docker-compose down -v
) else (
    echo.
    echo ❌ 중지 실패
)

echo.
pause

