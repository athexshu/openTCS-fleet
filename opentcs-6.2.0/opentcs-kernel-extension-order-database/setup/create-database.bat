@echo off
rem Creates the PostgreSQL login and database "fms_orders" for the openTCS order database.
rem Asks for a new password for the login fms_orders, then for the password of the
rem PostgreSQL superuser "postgres". Run it once per PC.
setlocal

set "PSQL=C:\Program Files\PostgreSQL\16\bin\psql.exe"
if not exist "%PSQL%" (
  for %%P in (psql.exe) do set "PSQL=%%~$PATH:P"
)
if not exist "%PSQL%" (
  echo ERROR: psql.exe not found. Edit the PSQL line at the top of this file.
  goto :fail
)

echo Choose a password for the new database login fms_orders.
echo Use letters, digits and - _ . @ only.
set /p "PW=Password: "
if "%PW%"=="" (
  echo ERROR: the password must not be empty.
  goto :fail
)

echo.
echo Now enter the password of the PostgreSQL user postgres.
"%PSQL%" -h localhost -U postgres -d postgres -v ON_ERROR_STOP=1 -v "pw=%PW%" -f "%~dp0create-database.sql"
if errorlevel 1 (
  echo ERROR: psql failed, see the message above. If fms_orders already exists, nothing is needed.
  goto :fail
)

echo.
echo Done. Now add these two lines to the kernel's config\opentcs-kernel.properties:
echo   orderdatabase.enable = true
echo   orderdatabase.password = ^<the password you just chose^>
echo Then restart the kernel.
pause
exit /b 0

:fail
pause
exit /b 1
