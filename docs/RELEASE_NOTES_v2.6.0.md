# Notas de Lançamento — ControleGitHub v2.6.0

Data de lançamento: 4 de novembro de 2026
Canal oficial: https://github.com/playertwo1/controlegithub/releases/tag/v2.6.0

## O que mudou

- READMEs de outros repositórios agora mostram texto de HTML comum em vez de imprimir as tags. Imagens em caminhos relativos, como `docs/images/...`, podem ser carregadas sob demanda a partir da branch padrão.
- **Explorar GitHub** ganhou períodos Hoje, Esta semana e Este mês. Os resultados são repositórios com push no período, ordenados pelo total atual de estrelas; a API pública não fornece ganho de estrelas por período.

## Arquivos

- Renderização e links de imagens em `GitHubMarkdown.kt`, `GitHubMarkdownContent.kt` e `GitHubRepositoryDetailScreen.kt`.
- Filtros de busca global em `GitHubGlobalSearch.kt` e `GitHubRepositoriesScreen.kt`.
- Cobertura unitária e instrumentada para imagens e períodos de busca.

## Artefatos e verificação de integridade

- **Pacote:** `com.playertwo.controlegithub`
- **Versão:** `2.6.0` (`versionCode = 108`)
- **Requisitos:** Android 8.0 (API 26) ou superior
- **Arquivo:** `app-release.apk` (12.817.627 bytes)
- **SHA-256:** `C92A2B0CEF32D42C3BF75F8C25C5253F39944CED99BD3954C07EB0986B28FA97`
- **Certificado de assinatura:** `c622fc491da08aee1b9e196db0cc7ba92784eefc02c047c387049042ad0aa2ec` (APK Signature Scheme v2)

## Validação

- `assembleDebug`, `testDebugUnitTest` e `lintDebug`: PASS.
- 223 testes unitários passaram; `compileDebugAndroidTestKotlin` também passou.
- `clean assembleRelease`, `apksigner` e `aapt`: PASS.
- Testes instrumentados: NOT_RUN nesta sessão; você vai verificar o APK no seu aparelho.
