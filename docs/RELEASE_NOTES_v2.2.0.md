# Notas de Lançamento — ControleGitHub v2.2.0

**Versão:** 2.2.0 (`versionCode = 103`)

**Data:** 25 de outubro de 2026

**Distribuição:** [GitHub Releases](https://github.com/playertwo1/controlegithub/releases)

## O que mudou nesta versão

- **Issues e Pull Requests por repositório (`C26.1`):**
  - Novo botão **"Issues e PRs"** diretamente na tela de Detalhe do Repositório (`GitHubRepositoryDetailScreen`), ao lado de **"Releases e APKs"**.
  - Nova tela `GitHubRepositoryWorkScreen` permitindo alternar entre as abas **"Issues"** (`/repos/{owner}/{repo}/issues`) e **"Pull requests"** (`/repos/{owner}/{repo}/pulls`) exclusivas daquele `owner/repo`.
  - Filtros rápidos por estado (**"Abertas"**, **"Fechadas"**, **"Todas"**), paginação com **"Carregar mais"**, suporte a cache offline (`OfflineCacheBadge`) e botão **"Nova issue"** no cabeçalho.
  - Toque em qualquer issue ou pull request abre o detalhe completo existente (`GitHubIssueDetailScreen` / `GitHubPullRequestDetailScreen`) com leitura/edição Markdown, comentários, revisões/diffs e retorno previsível preservando a lista e os filtros.
- **Novo ícone adaptativo "Cyber Octocat & Git":**
  - Arte vetorial de lançador refinada (`@drawable/ic_launcher_foreground`, `@drawable/ic_launcher_background` e `@drawable/ic_launcher`) com silhueta geométrica Octocat, olhos ciano luminosos e grafo Git integrado dentro da safe zone de 66x66dp do Android Adaptive Icon.

## Compatibilidade

Esta versão utiliza o mesmo certificado de assinatura das versões 2.0.0 e 2.1.0 (`c622fc491da08aee1b9e196db0cc7ba92784eefc02c047c387049042ad0aa2ec`), permitindo atualização direta (`adb install -r` ou instalador do Android) sem perder sessão ou dados locais para quem já utiliza a série 2.x.

## Informações do APK

| Item | Valor |
|---|---|
| Arquivo | `app-release.apk` |
| Pacote | `com.playertwo.controlegithub` |
| `versionCode` / `versionName` | `103` / `2.2.0` |
| SDK mínimo / alvo | API 26 / API 36 |
| Tamanho | 12.659.543 bytes |
| Assinatura | APK Signature Scheme v2, verificada por `apksigner` |
| SHA-256 do certificado | `c622fc491da08aee1b9e196db0cc7ba92784eefc02c047c387049042ad0aa2ec` |
| SHA-256 do APK | `95BED54C6DE8A595E7477C63EA8570404D13FD0E7B3EF8D26B7180AEE437212D` |
