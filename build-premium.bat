@echo off
echo ============================================================
echo Building ShinobuRankup PREMIUM Version (OBFUSCATED)
echo Features: Unlimited ranks, Full language support
echo ============================================================
echo.

:: Clean, set PREMIUM version, compile and obfuscate
call gradlew.bat clean setPremium proguardPremium --no-daemon --no-build-cache

:: Reset to FREE for safety (default state)
call gradlew.bat setFree --no-daemon -q

echo.
echo ============================================================
echo PREMIUM build complete!
echo Output: build\libs\ShinobuRankup-Premium.jar (Obfuscated)
echo ============================================================
pause
