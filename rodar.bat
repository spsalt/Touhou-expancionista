@echo off
REM ============================================================
REM  Compila e roda o jogo. Rode com duplo clique OU pelo cmd,
REM  mas SEMPRE a partir da raiz do repositorio: os caminhos de
REM  sprites/ e config/ sao relativos a pasta atual.
REM ============================================================

cd /d "%~dp0"

echo [1/2] Compilando...

if not exist out mkdir out

dir /s /b src\*.java > out\fontes.txt
javac -encoding UTF-8 -d out @out\fontes.txt

if errorlevel 1 (
    echo.
    echo ERRO DE COMPILACAO. Corrija os erros acima e rode de novo.
    pause
    exit /b 1
)

echo [2/2] Rodando...
echo.

java -cp out src.Main

pause
