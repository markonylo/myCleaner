@echo off
chcp 65001 >nul
title ADB Čistač - Sistemski bloatware
color 0A
echo.
echo  ╔══════════════════════════════════════════════════════╗
echo  ║          ADB Čistač - Sistemski bloatware           ║
echo  ║         Autor: Elingpro  │  Marko Lovrić           ║
echo  ╚══════════════════════════════════════════════════════╝
echo.

REM Provjera ADB-a
where adb >nul 2>&1
if errorlevel 1 (
    echo [GREŠKA] ADB nije pronađen u PATH-u.
    echo Preuzmite Platform Tools: https://developer.android.com/studio/releases/platform-tools
    echo Raspakujte i dodajte folder u Windows PATH, zatim ponovo pokrenite skriptu.
    pause
    exit /b 1
)

REM Provjera uređaja
echo [1/5] Tražim priključeni telefon...
adb devices -l
adb get-state >nul 2>&1
if errorlevel 1 (
    echo.
    echo [GREŠKA] Telefon nije priključen ili USB debagovanje nije uključeno.
    echo   1. Na telefonu: Podešavanja → O telefonu → kucni 7x na "Broj verzije"
    echo   2. Programerske opcije → USB debagovanje → UKLJUČI
    echo   3. Priključi kablom i odaberi "Prenos fajlova" na telefonu
    echo   4. Na telefonu prihvati RSA ključ za ovaj računar
    pause
    exit /b 1
)
echo [OK] Telefon pronađen.
echo.

REM Meni
:MENU
echo ══════════════════════════════════════════
echo  Šta hoćeš da uradiš?
echo ══════════════════════════════════════════
echo  [1] Izlistaj sve instalirane pakete (svi)
echo  [2] Izlistaj samo sistemske (bloatware)
echo  [3] Deinstaliraj pakete iz paketi.txt
echo  [4] Vrati (reinstaliraj) paket za korisnika
echo  [5] Obriši keš SVIH aplikacija (automatski, bez tapkanja)
echo  [6] Obriši keš iz paketi.txt (selektivno)
echo  [7] Obriši temp fajlove sa SD kartice
echo  [8] Prikaži top 10 po veličini u /data
echo  [9] ADB Shell (direktna komanda)
echo  [0] Izlaz
echo ══════════════════════════════════════════
set /p CHOICE="Tvoj izbor: "

if "%CHOICE%"=="1" goto LIST_ALL
if "%CHOICE%"=="2" goto LIST_SYS
if "%CHOICE%"=="3" goto UNINSTALL_FILE
if "%CHOICE%"=="4" goto RESTORE
if "%CHOICE%"=="5" goto CLEAR_ALL_CACHE
if "%CHOICE%"=="6" goto CLEAR_CACHE_FILE
if "%CHOICE%"=="7" goto CLEAR_TEMP
if "%CHOICE%"=="8" goto TOP10
if "%CHOICE%"=="9" goto SHELL
if "%CHOICE%"=="0" goto END
echo Pogrešan izbor.
goto MENU

:LIST_ALL
echo.
echo Sve instalirane aplikacije:
adb shell pm list packages -f | sort
echo.
echo Lista sačuvana u: svi_paketi.txt
adb shell pm list packages > svi_paketi.txt
pause
goto MENU

:LIST_SYS
echo.
echo Sistemske aplikacije (kandidati za uklanjanje):
adb shell pm list packages -s | sort
adb shell pm list packages -s > sistemski_paketi.txt
echo.
echo Lista sačuvana u: sistemski_paketi.txt
echo.
echo [NAPOMENA] "pm uninstall -k --user 0 PAKET" uklanja samo za korisnika
echo bez brisanja sa sistema. Može se oporaviti sa opcijom [4].
pause
goto MENU

:UNINSTALL_FILE
echo.
if not exist paketi.txt (
    echo [GREŠKA] paketi.txt ne postoji!
    echo Napravi ga sa jednim paketom po redu, npr:
    echo   com.facebook.katana
    echo   com.microsoft.teams
    echo Ili koristi "Kopiraj za ADB" dugme u Android aplikaciji.
    pause
    goto MENU
)
echo Sadržaj paketi.txt:
type paketi.txt
echo.
echo [UPOZORENJE] Deinstalirace se svi paketi iz gornje liste.
echo Za sistemske: pm uninstall -k --user 0 (može se oporaviti)
echo Za korisničke: pm uninstall (trajno)
echo.
set /p CONFIRM="Nastavljaš? (da/ne): "
if /i not "%CONFIRM%"=="da" goto MENU

echo.
echo Obrađujem...
set FAIL=0
set OK=0
for /f "usebackq tokens=*" %%P in ("paketi.txt") do (
    if not "%%P"=="" (
        echo   → %%P
        REM Probaj kao sistemski (--user 0)
        adb shell pm uninstall -k --user 0 %%P >nul 2>&1
        if errorlevel 1 (
            REM Probaj kao korisnički
            adb shell pm uninstall %%P >nul 2>&1
            if errorlevel 1 (
                echo     [NIJE USPELO] %%P
                set /a FAIL+=1
            ) else (
                echo     [OK - korisnički] %%P
                set /a OK+=1
            )
        ) else (
            echo     [OK - sistemski] %%P
            set /a OK+=1
        )
    )
)
echo.
echo Rezultat: %OK% uspešno, %FAIL% nije uspelo.
pause
goto MENU

:RESTORE
echo.
set /p PKG="Upiši naziv paketa za vraćanje (npr. com.facebook.katana): "
echo Vracam %PKG%...
adb shell cmd package install-existing %PKG%
if errorlevel 1 (
    echo [GREŠKA] Paket nije pronađen ili nije sistemski.
)
pause
goto MENU

:CLEAR_ALL_CACHE
echo.
echo Brišem keš SVIH korisničkih aplikacija automatski...
echo Ovo može potrajati 1-3 minuta (bez tapkanja na telefonu).
echo.
set CACHE_OK=0
set CACHE_FAIL=0
for /f "tokens=*" %%P in ('adb shell pm list packages ^| findstr "package:"') do (
    set "LINE=%%P"
    setlocal enabledelayedexpansion
    set "PKGNAME=!LINE:package:=!"
    set "PKGNAME=!PKGNAME: =!"
    adb shell pm clear-cache-dirs !PKGNAME! >nul 2>&1
    if !errorlevel! equ 0 (
        echo   [OK] !PKGNAME!
        set /a CACHE_OK+=1
    ) else (
        REM Fallback: pm clear (briše i podatke — samo za keš koristimo clear-cache-dirs)
        adb shell pm clear-cache-dirs !PKGNAME! >nul 2>&1
        set /a CACHE_FAIL+=1
    )
    endlocal
)
echo.
echo [REZULTAT] Keš obrisan za %CACHE_OK% aplikacija.
echo.
echo Slobodan prostor posle brisanja:
adb shell df -h /data
adb shell df -h /sdcard
pause
goto MENU

:CLEAR_CACHE_FILE
echo.
if not exist paketi.txt (
    echo [GREŠKA] paketi.txt ne postoji!
    echo Napravi ga sa jednim paketom po redu, ili koristi dugme "ADB komande" u Android aplikaciji.
    pause
    goto MENU
)
echo Brišem keš aplikacija iz paketi.txt...
echo.
set CACHE_OK=0
set CACHE_FAIL=0
for /f "usebackq eol=# tokens=*" %%P in ("paketi.txt") do (
    if not "%%P"=="" (
        adb shell pm clear-cache-dirs %%P >nul 2>&1
        if errorlevel 1 (
            echo   [FAIL] %%P
            set /a CACHE_FAIL+=1
        ) else (
            echo   [OK]   %%P
            set /a CACHE_OK+=1
        )
    )
)
echo.
echo [REZULTAT] Keš obrisan: %CACHE_OK%  Neuspelo: %CACHE_FAIL%
echo.
echo Slobodan prostor:
adb shell df -h /data
pause
goto MENU

:CLEAR_TEMP
echo.
echo Brišem temp i log fajlove sa SD kartice...
adb shell "find /sdcard -name '*.tmp' -o -name '*.temp' -o -name '*.log' -o -name '*.bak' 2>/dev/null" > temp_lista.txt
type temp_lista.txt
echo.
set /p CONFIRM2="Obrisati sve gornje fajlove? (da/ne): "
if /i not "%CONFIRM2%"=="da" goto MENU
adb shell "find /sdcard -name '*.tmp' -delete; find /sdcard -name '*.temp' -delete; find /sdcard -name '*.log' -delete; find /sdcard -name '*.bak' -delete 2>/dev/null"
adb shell "rm -rf /sdcard/.thumbnails/* /sdcard/DCIM/.thumbnails/* 2>/dev/null"
echo [OK] Temp fajlovi obrisani.
pause
goto MENU

:TOP10
echo.
echo Top 10 aplikacija po veličini podataka (/data/data):
adb shell "du -sh /data/data/* 2>/dev/null | sort -rh | head -10"
echo.
pause
goto MENU

:SHELL
echo.
echo Direktan ADB shell. Kucaj 'exit' da se vratiš u meni.
adb shell
goto MENU

:END
echo Doviđenja!
pause
