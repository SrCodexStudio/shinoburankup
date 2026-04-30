@echo off
echo ============================================================
echo Building ShinobuRankup FREE Version (OBFUSCATED)
echo Limitations: Max 10 ranks, Language locked to English
echo ============================================================
echo.

:: Clean, set FREE version, compile and obfuscate
call gradlew.bat clean setFree proguardFree --no-daemon --no-build-cache

echo.
echo ============================================================
echo FREE build complete!
echo Output: build\libs\ShinobuRankup-Free.jar (Obfuscated)
echo ============================================================
pause
