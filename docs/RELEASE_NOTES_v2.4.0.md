# Notas de Lançamento — ControleGitHub v2.4.0

Data de lançamento: 25 de outubro de 2026
Canal oficial: [https://github.com/playertwo1/controlegithub/releases/tag/v2.4.0](https://github.com/playertwo1/controlegithub/releases/tag/v2.4.0)

## O que mudou na versão 2.4.0

1. **Ações sociais no repositório: Star / Unstar e Watch (`C28.1` — `OH-13`):**
   - No detalhe de qualquer repositório (`GitHubRepositoryDetailScreen`), o app consulta em tempo real se a sua conta já deu estrela (`GET /user/starred/{owner}/{repo}`) ou acompanha o projeto (`GET /repos/{owner}/{repo}/subscription`).
   - Adicionados botões compactos **"Estrela no GitHub"** / **"Com estrela no GitHub"** e **"Acompanhar (Watch)"** / **"Acompanhando (Watch)"** para marcar ou desmarcar Star e Watch remotamente no GitHub com confirmação HTTP e atualização imediata do contador de estrelas, mantendo separação clara dos favoritos locais do aparelho.

2. **Busca e descoberta global no GitHub (`C29.1` — `OH-11`):**
   - Novo modo **"Explorar GitHub"** na aba `Repositórios` (`GitHubRepositoriesScreen`), permitindo pesquisar repositórios públicos de qualquer pessoa ou organização no GitHub (`GET /search/repositories`).
   - Ao tocar em qualquer repositório público encontrado, você abre o detalhe completo no app: **README**, **Código**, **GitHub Actions**, **Issues e PRs**, **Nova issue** e **Releases e APKs**.

3. **Atalhos rápidos no Launcher do Android / App Shortcuts (`C30.1` — `OH-14`):**
   - Ao segurar o ícone do ControleGitHub na tela inicial do Android, agora aparecem 4 atalhos rápidos em português: **Resumo Diário**, **Repositórios**, **Demandas** e **Avisos**, levando direto para a aba escolhida quando autenticado sem expor dados privados no sistema.

4. **Carregamento sob demanda de imagens em Markdown / Economia de dados (`C31.1` — `OH-09`):**
   - Imagens externas em READMEs, issues e PRs continuam bloqueadas por padrão para economizar dados e proteger sua privacidade.
   - Ao tocar em `"Imagem: ..."`, o diálogo agora oferece o botão **"Carregar imagem no app"**, que baixa e exibe a imagem sob demanda via HTTPS seguro (limite de 5 MB e sem envio de token OAuth para servidores externos).

## Artefatos e Verificação de Integridade

- **Pacote:** `com.playertwo.controlegithub`
- **Versão:** `2.4.0` (`versionCode = 105`)
- **Requisitos:** Android 8.0 Oreo (API 26) a Android 16 (API 36)
- **Arquivo:** `app-release.apk` (12.712.827 bytes)
- **SHA-256 (`app-release.apk`):** `D2CF5199030D19DEEABF20D7D6B76FA3B27922BD4749A99696CFCE2FE58E8704`
- **Certificado SHA-256 (APK Signature Scheme v2):** `c622fc491da08aee1b9e196db0cc7ba92784eefc02c047c387049042ad0aa2ec`

Para validar a integridade no PowerShell:

```powershell
Get-FileHash -Algorithm SHA256 .\app-release.apk
```
