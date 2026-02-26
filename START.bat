@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul

echo.
echo ========================================
echo   Y-DANAWA Docker Build and Run
echo ========================================
echo.

set "ROOT=%~dp0"
cd /d "%ROOT%"
if not defined GRADLE_USER_HOME set "GRADLE_USER_HOME=%ROOT%.gradle-user"

echo [0/3] Checking Docker engine...
where docker >nul 2>&1
if errorlevel 1 (
    echo [ERROR] docker command not found.
    echo         Please install Docker Desktop.
    pause
    exit /b 1
)

docker compose version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] docker compose is not available.
    echo         Please update Docker Desktop.
    pause
    exit /b 1
)

docker info >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Cannot connect to Docker daemon.
    echo.
    echo [How to fix]
    echo   1^) Start Docker Desktop first.
    echo   2^) Run terminal as Administrator.
    echo   3^) Ensure docker-users group permission:
    echo      net localgroup docker-users %USERNAME% /add
    echo   4^) Sign out and sign in after step 3.
    echo.
    pause
    exit /b 1
)
echo [OK] Docker engine reachable
echo.

echo [1/3] Building backend...
call "%ROOT%gradlew.bat" bootJar -x test
if errorlevel 1 (
    echo [ERROR] Build failed
    pause
    exit /b 1
)
echo [OK] Build complete
echo.

echo [1.5/3] Rebuilding library-scraper image with latest local code...
docker compose build library-scraper
if errorlevel 1 (
    echo [ERROR] library-scraper image build failed
    pause
    exit /b 1
)
echo [OK] library-scraper image updated
echo.

echo [2/3] Running docker compose (force-recreate for code changes)...
docker compose up --build --force-recreate -d
if errorlevel 1 (
    echo [ERROR] docker compose up failed
    pause
    exit /b 1
)
echo [OK] Docker compose complete
echo.

echo [2.1/3] Ensuring library-scraper container is recreated with latest code...
docker compose up -d --build --force-recreate --no-deps library-scraper
if errorlevel 1 (
    echo [ERROR] Failed to recreate library-scraper
    pause
    exit /b 1
)
echo [OK] library-scraper recreated
echo.

echo [2.2/3] Verifying scraper response quickly...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$body = @{isbn='9788998139766'} | ConvertTo-Json -Compress; " ^
  "try { " ^
  "  $res = Invoke-RestMethod -Method Post -Uri 'http://localhost:8090/check-library' -ContentType 'application/json' -Body $body -TimeoutSec 35; " ^
  "  Write-Host ('[SCRAPER] status=' + $res.status_text + ' status_ko=' + $res.status_text_ko + ' available=' + $res.available + ' found=' + $res.found); " ^
  "} catch { " ^
  "  Write-Host ('[WARN] scraper verification failed: ' + $_.Exception.Message); " ^
  "}"
echo.

echo [2.5/3] Running image crawler (optional)...
echo.

rem 1) Detect crawler script path first
set "CRAWLER_DIR="
set "CRAWLER_SCRIPT="
if exist "%ROOT%image-crawler\ydanawa_cli.py" (
    set "CRAWLER_DIR=%ROOT%image-crawler"
    set "CRAWLER_SCRIPT=%ROOT%image-crawler\ydanawa_cli.py"
) else if exist "%ROOT%image-crawler\image-crawler\ydanawa_cli.py" (
    set "CRAWLER_DIR=%ROOT%image-crawler\image-crawler"
    set "CRAWLER_SCRIPT=%ROOT%image-crawler\image-crawler\ydanawa_cli.py"
)
if not defined CRAWLER_SCRIPT (
    echo [WARN] Crawler script not found. Skip crawler.
    goto after_crawler
)
echo [INFO] Crawler script: %CRAWLER_SCRIPT%
echo [INFO] Crawler dir   : %CRAWLER_DIR%

rem 2) Detect Python: project .venv -> crawler .venv -> system python
set "PY="
if exist "%ROOT%.venv\Scripts\python.exe" set "PY=%ROOT%.venv\Scripts\python.exe"
if not defined PY if exist "%CRAWLER_DIR%\.venv\Scripts\python.exe" set "PY=%CRAWLER_DIR%\.venv\Scripts\python.exe"
if not defined PY (
    where python >nul 2>&1
    if errorlevel 1 (
        echo [WARN] Python not found. Skip crawler.
        goto after_crawler
    )
    set "PY=python"
)

rem 3) Defaults (can be overridden by env vars)
if not defined CRAWLER_TOP set "CRAWLER_TOP=50"
if not defined CRAWLER_CONCURRENCY set "CRAWLER_CONCURRENCY=8"
if not defined CRAWLER_OUT set "CRAWLER_OUT=%CRAWLER_DIR%\images"
if not defined CRAWLER_INGEST set "CRAWLER_INGEST=1"
if not defined CRAWLER_DB_URL set "CRAWLER_DB_URL=postgresql://root:0910@localhost:5433/ydanawa_db"
if not defined CRAWLER_SOURCE set "CRAWLER_SOURCE=pipeline"
if not defined CRAWLER_USE_PLAYWRIGHT set "CRAWLER_USE_PLAYWRIGHT=1"
if not defined CRAWLER_HEADLESS set "CRAWLER_HEADLESS=true"
if not defined CRAWLER_MAX_RETRIES set "CRAWLER_MAX_RETRIES=3"
if not defined CRAWLER_TIMEOUT set "CRAWLER_TIMEOUT=20"
if not defined CRAWLER_DELAY_MIN set "CRAWLER_DELAY_MIN=0.05"
if not defined CRAWLER_DELAY_MAX set "CRAWLER_DELAY_MAX=0.25"
if not defined CRAWLER_MIN_BYTES set "CRAWLER_MIN_BYTES=1024"
if not defined CRAWLER_MAX_CANDIDATES_PROBE set "CRAWLER_MAX_CANDIDATES_PROBE=40"
if not defined CRAWLER_AUTO_CSV set "CRAWLER_AUTO_CSV=1"

set "RUN_INGEST=1"
if /I "%CRAWLER_INGEST%"=="0" set "RUN_INGEST=0"
if /I "%CRAWLER_INGEST%"=="false" set "RUN_INGEST=0"
if /I "%CRAWLER_INGEST%"=="no" set "RUN_INGEST=0"

set "RUN_PLAYWRIGHT=0"
if /I "%CRAWLER_USE_PLAYWRIGHT%"=="1" set "RUN_PLAYWRIGHT=1"
if /I "%CRAWLER_USE_PLAYWRIGHT%"=="true" set "RUN_PLAYWRIGHT=1"
if /I "%CRAWLER_USE_PLAYWRIGHT%"=="yes" set "RUN_PLAYWRIGHT=1"

rem 4) Check/install crawler dependencies when needed
set "CRAWLER_REQUIREMENTS="
if exist "%CRAWLER_DIR%\requirements.txt" set "CRAWLER_REQUIREMENTS=%CRAWLER_DIR%\requirements.txt"
if not defined CRAWLER_REQUIREMENTS if exist "%ROOT%image-crawler\image-crawler\requirements.txt" set "CRAWLER_REQUIREMENTS=%ROOT%image-crawler\image-crawler\requirements.txt"
if not defined CRAWLER_REQUIREMENTS if exist "%ROOT%image-crawler\requirements.txt" set "CRAWLER_REQUIREMENTS=%ROOT%image-crawler\requirements.txt"

if "%RUN_INGEST%"=="1" (
    if "%RUN_PLAYWRIGHT%"=="1" (
        "%PY%" -c "import aiohttp, bs4, PIL, psycopg, playwright" >nul 2>&1
    ) else (
        "%PY%" -c "import aiohttp, bs4, PIL, psycopg" >nul 2>&1
    )
) else (
    if "%RUN_PLAYWRIGHT%"=="1" (
        "%PY%" -c "import aiohttp, bs4, PIL, playwright" >nul 2>&1
    ) else (
        "%PY%" -c "import aiohttp, bs4, PIL" >nul 2>&1
    )
)
if errorlevel 1 (
    if defined CRAWLER_REQUIREMENTS (
        echo [INFO] Installing crawler dependencies...
        "%PY%" -m pip install -r "%CRAWLER_REQUIREMENTS%"
        if errorlevel 1 (
            echo [WARN] Dependency install failed. Skip crawler.
            goto after_crawler
        )
    ) else (
        echo [WARN] requirements.txt not found. Skip crawler.
        goto after_crawler
    )
)

if "%RUN_PLAYWRIGHT%"=="1" (
    echo [INFO] Ensuring Playwright browser: chromium
    "%PY%" -m playwright install chromium >nul 2>&1
    if errorlevel 1 (
        echo [WARN] Playwright browser install failed. Continue without Playwright.
        set "RUN_PLAYWRIGHT=0"
    )
)

rem 5) Auto input: CSV from DB (isbn,title,category,detail_url)
if defined CRAWLER_ISBN_CSV if not exist "%CRAWLER_ISBN_CSV%" (
    echo [WARN] CRAWLER_ISBN_CSV not found: %CRAWLER_ISBN_CSV%
    set "CRAWLER_ISBN_CSV="
)

if not defined CRAWLER_ISBN_CSV if not defined CRAWLER_KEYWORD (
    if /I "%CRAWLER_AUTO_CSV%"=="1" (
        set "AUTO_CSV=%CRAWLER_DIR%\books_auto.csv"
        echo [INFO] Generating crawler CSV from DB: !AUTO_CSV!
        docker compose exec -T db psql -U root -d ydanawa_db -c "COPY (SELECT isbn, COALESCE(title,'') AS title, '' AS category, '' AS detail_url FROM books WHERE isbn IS NOT NULL AND isbn <> '' ORDER BY isbn) TO STDOUT WITH CSV HEADER" > "!AUTO_CSV!"
        if errorlevel 1 (
            echo [WARN] Auto CSV generation failed. Fallback keyword: java
            set "CRAWLER_KEYWORD=java"
        ) else (
            set "CRAWLER_ISBN_CSV=!AUTO_CSV!"
            echo [OK] Auto CSV generated.
        )
    ) else (
        set "CRAWLER_KEYWORD=java"
        echo [INFO] CRAWLER_KEYWORD not set. Using default keyword: java
    )
)

pushd "%CRAWLER_DIR%"

rem 6) Run crawler fetch
set "FETCH_OK=0"
if defined CRAWLER_ISBN_CSV (
    if "%RUN_PLAYWRIGHT%"=="1" (
        "%PY%" "%CRAWLER_SCRIPT%" fetch --isbn-csv "%CRAWLER_ISBN_CSV%" --top %CRAWLER_TOP% --concurrency %CRAWLER_CONCURRENCY% --out "%CRAWLER_OUT%" --max-retries %CRAWLER_MAX_RETRIES% --timeout %CRAWLER_TIMEOUT% --delay-min %CRAWLER_DELAY_MIN% --delay-max %CRAWLER_DELAY_MAX% --min-bytes %CRAWLER_MIN_BYTES% --max-candidates-probe %CRAWLER_MAX_CANDIDATES_PROBE% --prefer-playwright --headless %CRAWLER_HEADLESS%
    ) else (
        "%PY%" "%CRAWLER_SCRIPT%" fetch --isbn-csv "%CRAWLER_ISBN_CSV%" --top %CRAWLER_TOP% --concurrency %CRAWLER_CONCURRENCY% --out "%CRAWLER_OUT%" --max-retries %CRAWLER_MAX_RETRIES% --timeout %CRAWLER_TIMEOUT% --delay-min %CRAWLER_DELAY_MIN% --delay-max %CRAWLER_DELAY_MAX% --min-bytes %CRAWLER_MIN_BYTES% --max-candidates-probe %CRAWLER_MAX_CANDIDATES_PROBE%
    )
    if errorlevel 1 (
        echo [WARN] Crawler fetch --isbn-csv failed. Check errors.log
    ) else (
        echo [OK] Crawler fetch complete --isbn-csv
        set "FETCH_OK=1"
    )
) else if defined CRAWLER_KEYWORD (
    if "%CRAWLER_KEYWORD%"=="" (
        echo [INFO] Empty keyword. Skip crawler.
    ) else (
        if "%RUN_PLAYWRIGHT%"=="1" (
            "%PY%" "%CRAWLER_SCRIPT%" fetch --keyword "%CRAWLER_KEYWORD%" --top %CRAWLER_TOP% --concurrency %CRAWLER_CONCURRENCY% --out "%CRAWLER_OUT%" --max-retries %CRAWLER_MAX_RETRIES% --timeout %CRAWLER_TIMEOUT% --delay-min %CRAWLER_DELAY_MIN% --delay-max %CRAWLER_DELAY_MAX% --min-bytes %CRAWLER_MIN_BYTES% --max-candidates-probe %CRAWLER_MAX_CANDIDATES_PROBE% --prefer-playwright --headless %CRAWLER_HEADLESS%
        ) else (
            "%PY%" "%CRAWLER_SCRIPT%" fetch --keyword "%CRAWLER_KEYWORD%" --top %CRAWLER_TOP% --concurrency %CRAWLER_CONCURRENCY% --out "%CRAWLER_OUT%" --max-retries %CRAWLER_MAX_RETRIES% --timeout %CRAWLER_TIMEOUT% --delay-min %CRAWLER_DELAY_MIN% --delay-max %CRAWLER_DELAY_MAX% --min-bytes %CRAWLER_MIN_BYTES% --max-candidates-probe %CRAWLER_MAX_CANDIDATES_PROBE%
        )
        if errorlevel 1 (
            echo [WARN] Crawler fetch --keyword failed. Check errors.log
        ) else (
            echo [OK] Crawler fetch complete --keyword=%CRAWLER_KEYWORD%
            set "FETCH_OK=1"
        )
    )
) else (
    echo [INFO] No crawler input. Skip crawler.
)

rem 7) Optional ingest after successful fetch
if "%RUN_INGEST%"=="1" (
    if "%FETCH_OK%"=="1" (
        echo.
        echo [2.6/3] Ingesting fetched images...
        "%PY%" "%CRAWLER_SCRIPT%" ingest --dir "%CRAWLER_OUT%" --db-url "%CRAWLER_DB_URL%" --source "%CRAWLER_SOURCE%"
        if errorlevel 1 (
            echo [WARN] Crawler ingest failed.
        ) else (
            echo [OK] Crawler ingest complete
        )
    ) else (
        echo [INFO] Skip ingest because fetch was skipped or failed.
    )
) else (
    echo [INFO] Crawler ingest disabled by CRAWLER_INGEST=%CRAWLER_INGEST%.
)
echo [INFO] Crawler logs: %CRAWLER_OUT%\crawl_results_*.csv / failed_*.csv / errors.log
popd

:after_crawler
echo.
echo [2.7/3] Running library loan crawler (optional)...
set "LOAN_CRAWLER_DIR=%ROOT%library-loan-crawler"
set "LOAN_CRAWLER_SCRIPT=%LOAN_CRAWLER_DIR%\main.py"
if not defined LOAN_CRAWLER_RUN set "LOAN_CRAWLER_RUN=1"
if /I "%LOAN_CRAWLER_RUN%"=="0" goto after_loan_crawler
if /I "%LOAN_CRAWLER_RUN%"=="false" goto after_loan_crawler
if /I "%LOAN_CRAWLER_RUN%"=="no" goto after_loan_crawler

if not exist "%LOAN_CRAWLER_SCRIPT%" (
    echo [WARN] Loan crawler script not found. Skip loan crawler.
    goto after_loan_crawler
)

set "LOAN_PY="
if exist "%ROOT%.venv\Scripts\python.exe" set "LOAN_PY=%ROOT%.venv\Scripts\python.exe"
if not defined LOAN_PY (
    where python >nul 2>&1
    if errorlevel 1 (
        echo [WARN] Python not found. Skip loan crawler.
        goto after_loan_crawler
    )
    set "LOAN_PY=python"
)

if not exist "%LOAN_CRAWLER_DIR%\.env" (
    echo [WARN] %LOAN_CRAWLER_DIR%\.env not found. Skip loan crawler.
    goto after_loan_crawler
)

pushd "%LOAN_CRAWLER_DIR%"
"%LOAN_PY%" "%LOAN_CRAWLER_SCRIPT%"
if errorlevel 1 (
    echo [WARN] Loan crawler failed.
) else (
    echo [OK] Loan crawler complete
)
popd

:after_loan_crawler
echo.
echo [3/3] Done!
echo.
echo ========================================
echo   All tasks completed
echo ========================================
echo.
echo Open: http://localhost
echo.
echo Status: docker compose ps
echo Logs  : docker compose logs -f
echo Stop  : docker compose down
echo.

cmd /c exit /b 0
pause
endlocal
