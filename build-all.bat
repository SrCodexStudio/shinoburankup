@echo off
echo ============================================================
echo Building ShinobuRankup - ALL VERSIONS (OBFUSCATED)
echo ============================================================
echo.

:: Create output directory
if not exist "build\libs" mkdir "build\libs"

:: Build FREE version
echo [1/3] Building FREE version (obfuscated)...
echo       (Max 10 ranks, English only)
call gradlew.bat clean setFree proguardFree --no-daemon --no-build-cache -q
if errorlevel 1 (
    echo ERROR: FREE build failed!
    pause
    exit /b 1
)
echo       FREE version created!

:: Backup FREE jar before building PREMIUM
copy /Y "build\libs\ShinobuRankup-Free.jar" "build\libs\ShinobuRankup-Free.jar.tmp" >nul

:: Build PREMIUM version
echo.
echo [2/3] Building PREMIUM version (obfuscated)...
echo       (Unlimited ranks, Full language support)
call gradlew.bat clean setPremium proguardPremium --no-daemon --no-build-cache -q
if errorlevel 1 (
    echo ERROR: PREMIUM build failed!
    pause
    exit /b 1
)
echo       PREMIUM version created!

:: Restore FREE jar
move /Y "build\libs\ShinobuRankup-Free.jar.tmp" "build\libs\ShinobuRankup-Free.jar" >nul

:: Reset to FREE (default state)
echo.
echo [3/3] Resetting to FREE (default state)...
call gradlew.bat setFree --no-daemon -q

:: Show results
echo.
echo ============================================================
echo BUILD COMPLETE! (Both versions OBFUSCATED with ProGuard)
echo ============================================================
echo.
echo Generated JARs in build\libs\:
for %%F in ("build\libs\ShinobuRankup-Free.jar") do echo   - ShinobuRankup-Free.jar    (%%~zF bytes) - Max 10 ranks, English only
for %%F in ("build\libs\ShinobuRankup-Premium.jar") do echo   - ShinobuRankup-Premium.jar (%%~zF bytes) - Unlimited ranks, Full languages
echo.
echo ============================================================
pause
