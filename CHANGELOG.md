# Changelog — ControleGitHub

Todas as mudanças notáveis deste projeto serão documentadas neste arquivo.

O formato é baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.0.0/),
e este projeto adere ao [Versionamento Semântico](https://semver.org/lang/pt-BR/).

---

## [1.0.0] — 2026-10-18

Lançamento oficial da versão **1.0.0** do ControleGitHub para Android. Primeira versão estável e operante, cobrindo os Marcos M0, M1, M2, M3 e M4 do roadmap.

### Adicionado

#### Autenticação, Sessão e Painel Executivo (M1)
- Autenticação segura via **OAuth Device Flow** (`https://github.com/login/device`), sem necessidade de digitação de senhas no app.
- Armazenamento seguro de credenciais com criptografia AES-256-GCM via `AndroidKeyStore` em `SecureSessionStore.kt`.
- Painel Executivo (Dashboard) com métricas consolidadas em tempo real: volume de repositórios, issues abertas e notificações.
- Exploração de Repositórios com busca por nome, ordenação por atualização/estrelas/nome e filtros dinâmicos.
- Detalhes do repositório com linguagem predominante, contagem de estrelas/forks e atalhos rápidos.

#### Triagem Diária e Resiliência Offline (M2)
- Visualização detalhada de **Issues** com metadados, labels, responsáveis e timeline de comentários.
- Inspeção rica de **Pull Requests**, incluindo arquivos modificados, status de validação de CI e revisões existentes.
- Central de **Notificações** com marcação individual de leitura (PATCH HTTP) e paginação transparente.
- Integração com **GitHub Actions**: listagem de fluxos de trabalho, histórico de execuções, jobs e visualizador otimizado de logs de compilação.
- **Cache Local SQLite** nativo (`controlegithub_cache.db`) com revalidação condicional via HTTP ETag (`304 Not Modified`), permitindo uso offline integral com badge visual de data/hora.

#### Ações e Produtividade (M3)
- Gestão ativa de Issues: criação rápida com proteção anti-duplo clique, edição de título/corpo, comentários e alternância de estado (abrir/fechar).
- Revisão colaborativa de Pull Requests: submissão de comentários e envio de pareceres (*Aprovar*, *Comentar*, *Solicitar alterações* com justificativa obrigatória) com bloqueio nativo de auto-revisão.
- Sistema de **Favoritos** locais persistido no Preferences DataStore.
- **Quadro Kanban Local de 4 Colunas** (*A fazer*, *Em andamento*, *Em revisão*, *Concluído*) com movimentação instantânea de cartões sem alterar dados no repositório remoto do GitHub.
- **Resumo Diário (Últimas 24h)**: janela temporal estrita com fuso horário local e botão de exportação em Markdown para a área de transferência.
- **Sincronização em Segundo Plano**: agendamento via `JobScheduler` com restrições de Wi-Fi e bateria, revalidação ETag e expurgo atômico de jobs no logout.
- **Alertas Locais Configuráveis**: canal de notificação `controlegithub_alerts` com deduplicação de avisos e filtro opcional para menções e atribuições.
- Suporte a temas Claro, Escuro e Sistema, contraste WCAG AA e acessibilidade completa (TalkBack e fontes ampliadas até 200%).

#### Robustez, Qualidade e Lançamento (M4)
- Matriz de compatibilidade Android minSdk 26 (Android 8.0 Oreo) a compileSdk/targetSdk 36 (Android 16+).
- Layouts responsivos para orientações Retrato e Paisagem, e telas compactas (< 600 dp) e expandidas (≥ 600 dp).
- Orçamento de carregamento do painel e cache medido em ~8 ms (limite de 2.000 ms) e zero ANRs sob ausência prolongada de conectividade.
- Pacote assinado de produção com esquema APK Signature Scheme v2 verificado via `apksigner`.
- Arquivo canônico de integridade `SHA256SUMS.txt`.
- Documentação completa de lançamento: Notas de Versão, Manual do Usuário e Guia de Operação, Suporte e Rollback.

### Segurança e Privacidade
- Arquitetura estritamente **local-first**: conexão direta exclusiva com `https://api.github.com`, sem servidores intermediários ou telemetria de terceiros.
- Backup em nuvem estritamente desabilitado (`android:allowBackup="false"`).
- Regras de extração de dados excluindo todos os domínios locais (`data_extraction_rules.xml`).
- Bloqueio proativo de armazenamento de tokens ou endpoints de autenticação no cache SQLite.
- Expurgo atômico de credenciais, chaves criptográficas e dados locais no logout.
- Licença MIT formal ([LICENSE](LICENSE)) e política de segurança documentada ([SECURITY.md](SECURITY.md)).
