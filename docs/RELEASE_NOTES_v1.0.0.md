# Notas de Lançamento — ControleGitHub v1.0.0

**Versão:** 1.0.0 (`versionCode = 100`)  
**Data de Lançamento:** 18 de Outubro de 2026  
**Canal de Distribuição Oficial:** [GitHub Releases](https://github.com/playertwo1/controlegithub/releases)  
**Licença:** MIT ([LICENSE](file:///c:/Users/notefael/projetos/controlegithub/LICENSE))  

---

## 1. Visão Geral

O **ControleGitHub** chega à sua versão oficial **1.0.0**, consolidando-se como uma aplicação nativa Android em Kotlin e Jetpack Compose construída sobre uma arquitetura estritamente **local-first**, transparente e segura.

O aplicativo conecta-se diretamente à API oficial do GitHub (`https://api.github.com`), eliminando qualquer servidor intermediário, telemetria oculta ou coleta de dados privados. Todas as métricas, cartões de organização, históricos e credenciais residem e são processados exclusivamente no dispositivo do usuário com proteção criptográfica por hardware (`AndroidKeyStore`).

---

## 2. Destaques e Capacidades Entregues

### Marco 1 — Fundação e Consulta Essencial (M1)
- **Autenticação Segura via OAuth Device Flow:** Conexão sem inserção de senhas no app, utilizando o fluxo oficial do GitHub via navegador seguro.
- **Painel Executivo e Métricas Reais:** Indicadores consolidados de repositórios, issues abertas e notificações pendentes sem dados de demonstração.
- **Exploração Completa de Repositórios:** Listagem de repositórios próprios e colaborativos, busca instantânea, ordenação e filtros dinâmicos.
- **Navegação Fluida:** Arquitetura de tela única com doca flutuante Material 3 adaptável.

### Marco 2 — Inspeção e Resiliência Offline (M2)
- **Leitura Detalhada de Issues e Pull Requests:** Visualização rica de discussões, metadados, autores, labels, marcos e arquivos modificados.
- **Notificações Integradas:** Caixa de notificações com marcação individual de leitura (PATCH) e paginação transparente.
- **GitHub Actions & CI/CD:** Acompanhamento em tempo real de execuções de fluxos de trabalho, status de jobs e visualizador otimizado de logs de compilação.
- **Cache Local SQLite Inteligente:** Armazenamento local de consultas com revalidação condicional via HTTP ETag (`304 Not Modified`), permitindo uso completo em modo offline com indicação clara de data/hora da última sincronização.

### Marco 3 — Ações e Produtividade (M3)
- **Gestão Ativa de Issues:** Criação rápida com proteção contra duplo envio, edição de título e descrição, inclusão de comentários, atribuição de responsáveis/labels e alternância de estado (abrir/fechar).
- **Revisão Colaborativa de Pull Requests:** Envio de comentários de revisão e submissão formal de pareceres (*Aprovar*, *Comentar*, *Solicitar Alterações*) com proteção nativa contra auto-aprovação indevida.
- **Organização Local:** Sistema de favoritos rápidos e **Quadro Kanban de 4 colunas** (*A fazer*, *Em andamento*, *Em revisão*, *Concluído*) persistido via Preferences DataStore para organização pessoal sem poluir o repositório remoto.
- **Sincronização em Segundo Plano e Alertas:** Agendamento inexato e econômico via `JobScheduler`, geração de **Resumo Diário de 24 horas** com exportação em Markdown e notificações locais deduplicadas no canal `controlegithub_alerts`.
- **Acessibilidade e Personalização:** Suporte pleno aos temas Claro, Escuro e Sistema, contraste WCAG AA, alvos de toque normatizados (≥ 48 dp) e adaptação dinâmica a fontes ampliadas até 200%.

### Marco 4 — Robustez, Qualidade e Lançamento (M4)
- **Compatibilidade Ampla:** Suporte completo do Android 8.0 (API 26) até o Android 15/16/17 (API 36/37).
- **Desempenho Medido:** Carregamento do painel e cache em ~8 ms (orçamento limite de 2.000 ms) e zero ANRs (*Application Not Responding*) sob falhas severas ou ausência prolongada de conectividade.
- **Privacidade Rigorosa:** Backup em nuvem desativado (`android:allowBackup="false"`), exclusão de todos os domínios em regras de extração e ausência de SDKs de telemetria de terceiros.
- **Binário Oficial e Assinado:** Pacote de produção assinado pelo esquema moderno v2 (APK Signature Scheme v2).

---

## 3. Informações Técnicas do Binário de Release

| Parâmetro | Detalhe |
| :--- | :--- |
| **Arquivo** | `app-release.apk` |
| **Tamanho** | ~11,9 MB |
| **versionCode** | `100` |
| **versionName** | `1.0.0` |
| **minSdkVersion** | `26` (Android 8.0 Oreo) |
| **targetSdkVersion** | `36` (Android 16+) |
| **Algoritmo de Hash** | SHA-256 |
| **Checksum SHA-256** | `3DDEA18E2B37F20036091C351BE10BA7178D5ABA05EE7828379F2B13FA74841F` |
| **Esquema de Assinatura** | APK Signature Scheme v2 (Verificado via `apksigner`) |

---

## 4. Instruções de Verificação de Integridade

Antes de instalar o arquivo em seu dispositivo Android, confirme a autenticidade do pacote baixado calculando o hash SHA-256:

**No Windows (PowerShell):**
```powershell
Get-FileHash -Algorithm SHA256 app-release.apk
```

**No Linux ou macOS (Terminal):**
```bash
sha256sum app-release.apk
```

O resultado deve ser exatamente igual a:
```text
3DDEA18E2B37F20036091C351BE10BA7178D5ABA05EE7828379F2B13FA74841F
```

---

## 5. Documentação Complementar

- [Manual do Usuário](MANUAL_DO_USUARIO.md)
- [Guia de Operação e Suporte](OPERACAO_E_SUPORTE.md)
- [Diretrizes de Release e Compilação](RELEASE.md)
- [Política de Privacidade](file:///c:/Users/notefael/projetos/controlegithub/PRIVACY.md)
- [Política de Segurança](file:///c:/Users/notefael/projetos/controlegithub/SECURITY.md)
