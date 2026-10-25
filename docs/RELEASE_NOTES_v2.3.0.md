# Notas de Lançamento — ControleGitHub v2.3.0

**Versão:** 2.3.0 (`versionCode = 104`)

**Data:** 25 de outubro de 2026

**Distribuição:** [GitHub Releases](https://github.com/playertwo1/controlegithub/releases)

## O que mudou nesta versão

- **Histórico recente local de repositórios visitados (`C27.1` / `OH-08`):**
  - Ao abrir o detalhe de um repositório (`GitHubRepositoryDetailScreen`), o aplicativo agora registra automaticamente a visita no histórico recente local da conta autenticada (`RecentRepositoriesStore`).
  - Retenção máxima de **10 repositórios por conta** em ordem MRU (do mais recente ao mais antigo), sem duplicar `owner/repo`.
  - Nova seção **"Visitados recentemente"** exibida no Painel Inicial (`GitHubHomeScreen`) e na aba `Repositórios` (`GitHubRepositoriesScreen`), permitindo reabrir qualquer repositório visitado com **1 toque** e limpar o histórico a qualquer momento com o botão **"Limpar histórico"**.
  - **Privacidade e isolamento:** o histórico é estritamente isolado por conta autenticada, não altera stars/watch no GitHub e é automaticamente expurgado no logout ou na expiração da sessão.

## Compatibilidade

Esta versão utiliza o mesmo certificado de assinatura das versões 2.0.0, 2.1.0 e 2.2.0 (`c622fc491da08aee1b9e196db0cc7ba92784eefc02c047c387049042ad0aa2ec`), permitindo atualização direta (`adb install -r` ou instalador do Android) sem perder sessão ou dados locais.

## Informações do APK

| Item | Valor |
|---|---|
| Arquivo | `app-release.apk` |
| Pacote | `com.playertwo.controlegithub` |
| `versionCode` / `versionName` | `104` / `2.3.0` |
| SDK mínimo / alvo | API 26 / API 36 |
| Tamanho | 12.675.927 bytes |
| Assinatura | APK Signature Scheme v2, verificada por `apksigner` |
| SHA-256 do certificado | `c622fc491da08aee1b9e196db0cc7ba92784eefc02c047c387049042ad0aa2ec` |
| SHA-256 do APK | `771D120946328D548BF4207D00E9E20870516E3F865A47FA27D133A0990B4D82` |
