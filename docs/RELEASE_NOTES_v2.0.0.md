# Notas de Lançamento — ControleGitHub v2.0.0

**Versão:** 2.0.0 (`versionCode = 101`)

**Data:** 23 de outubro de 2026

**Distribuição:** [GitHub Releases](https://github.com/playertwo1/controlegithub/releases)

## O que mudou nesta fase

- **C22.1 — Leitura Markdown:** renderer CommonMark/GFM, tabelas, fallback textual e tratamento seguro de links. Links HTTPS externos pedem confirmação.
- **C23.1 — Editor Markdown:** formatação, abas Escrever/Prévia, rascunhos locais por conta e item, e sugestões `@` de participantes carregados.
- **C24.1 — Navegador de código:** navegação por pastas e breadcrumb, arquivos de texto com linhas e destaque local; binários e arquivos acima de 1 MB exibem aviso.

## Instalação e compatibilidade

Esta versão usa uma nova chave de assinatura porque o keystore da v1.0.0 não estava disponível. Android não permite atualizar a v1.0.0 diretamente com esta chave. Para uma instalação limpa, é necessário desinstalar a versão anterior antes de instalar a v2.0.0; isso apaga os dados locais do aplicativo. As próximas versões devem reutilizar a chave v2.0.0.

## Informações do APK

| Item | Valor |
|---|---|
| Arquivo | `app-release.apk` |
| Pacote | `com.playertwo.controlegithub` |
| `versionCode` / `versionName` | `101` / `2.0.0` |
| SDK mínimo / alvo | API 26 / API 36 |
| Tamanho | 12.602.147 bytes |
| Assinatura | APK Signature Scheme v2, verificada por `apksigner` |
| SHA-256 do certificado | `c622fc491da08aee1b9e196db0cc7ba92784eefc02c047c387049042ad0aa2ec` |
| SHA-256 do APK | `64A3549593E6EC7CC10D70F6E49B56FD3FBA6E4BB65FF11015CA1F46575AF8D8` |

## Validação

- `clean assembleRelease` **PASS** com JDK/SDK do Android Studio e secrets locais ignoradas pelo Git.
- `apksigner verify --verbose --print-certs` **PASS**; assinatura v2 verificada e certificado diferente do APK v1.0.0.
- `aapt dump badging` confirmou pacote, `versionCode` e `versionName` acima.
- `assembleDebug testDebugUnitTest lintDebug` **PASS** (193/193 testes JVM).
- `python scripts/check_plan.py` **PASS** (48 tarefas; elegíveis: nenhuma) e `git diff --check` **PASS**.
- Instalação em aparelho físico: **NOT_RUN**.
