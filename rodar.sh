#!/bin/bash
# ============================================================
#  Versao Linux/Mac do rodar.bat.
#  Rode a partir da raiz do repositorio: os caminhos de
#  sprites/ e config/ sao relativos a pasta atual.
# ============================================================

cd "$(dirname "$0")" || exit 1

echo "[1/2] Compilando..."

mkdir -p out

if ! javac -encoding UTF-8 -d out $(find src -name "*.java"); then
    echo
    echo "ERRO DE COMPILACAO. Corrija os erros acima e rode de novo."
    exit 1
fi

echo "[2/2] Rodando..."
echo

java -cp out src.Main
