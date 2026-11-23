# Notas de Lançamento — ControleGitHub v2.7.0

Preparada em 23 de novembro de 2026. Publicação pendente de autorização.
Canal oficial: https://github.com/playertwo1/controlegithub/releases

## O que mudou

- Seletor de 1, 2 ou 3 repositórios por linha.
- Ordenação e filtro por saúde. O estado **Vazio** é exibido quando o GitHub confirma a condição com HTTP 409.
- Ajustes no cabeçalho, no alinhamento do seletor e na validação do layout a 412 dp.

## Artefato

- **Pacote:** com.playertwo.controlegithub
- **Versão:** 2.7.0 (versionCode = 110)
- **Requisitos:** Android 8.0 (API 26) ou superior
- **Arquivo:** ControleGitHub-v2.7.0.apk (12.850.395 bytes)
- **SHA-256:** 9DBD77FFB54A646FAA132D25E0182E3C389234987CA81A0CE7E9F68BE77F3217
- **Certificado de assinatura:** c622fc491da08aee1b9e196db0cc7ba92784eefc02c047c387049042ad0aa2ec (APK Signature Scheme v2)

## Verificação

- `assembleRelease`, `apksigner verify` com assinatura v2, metadados do pacote e SHA-256 do APK versionado validados.
- O certificado de produção coincide com o do APK publicado na v2.6.1.
- O teste `releaseApkMatchesSha256Sums` executou com o APK versionado presente e passou.
