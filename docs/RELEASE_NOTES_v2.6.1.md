# Notas de Lançamento — ControleGitHub v2.6.1

Data de lançamento: 5 de novembro de 2026
Canal oficial: https://github.com/playertwo1/controlegithub/releases/tag/v2.6.1

## O que mudou

- Tocar em uma imagem do README abre a prévia dentro do app e carrega a imagem a partir do endereço seguro resolvido para o repositório.
- Links para páginas externas continuam pedindo confirmação antes de abrir no navegador.

## Artefato

- **Pacote:** `com.playertwo.controlegithub`
- **Versão:** `2.6.1` (`versionCode = 109`)
- **Requisitos:** Android 8.0 (API 26) ou superior
- **Arquivo:** `app-release.apk` (12.817.627 bytes)
- **SHA-256:** `C4B809DB5A8251A0A440A743A6871BDCE99549F86437DB0C631C6885EEA69599`
- **Certificado de assinatura:** `c622fc491da08aee1b9e196db0cc7ba92784eefc02c047c387049042ad0aa2ec` (APK Signature Scheme v2)

## Verificação

- `clean assembleRelease`, assinatura v2, `aapt` e SHA-256: **PASS**.
- `compileDebugAndroidTestKotlin`: **PASS**; testes instrumentados **NOT_RUN** nesta sessão para validação posterior pelo usuário.
- `assembleDebug`, `testDebugUnitTest` (223/223) e `lintDebug`: **PASS**.
