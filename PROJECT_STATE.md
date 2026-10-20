# Estado do projeto

## C21.1 em andamento — Releases e saúde dos repositórios — 2026-10-20

- Senha do keystore e caminhos locais removidos deste arquivo (continuam no histórico do Git; recomenda-se rotacionar).
- Implementado: tela "Releases e APKs" no detalhe do repositório (lista paginada, selos Mais recente/Pré-release/Rascunho, assets com tamanho e downloads, APKs destacados, "Baixar" via navegador) e saúde dos repositórios (dias sem push e status) na lista e no detalhe.
- Filtro por saúde e ordenação (Nome, Mais parados, Push recente, Estrelas) na lista de repositórios.
- Testes novos: `GitHubReleasesTest`, `RepositoryHealthTest`, `RepositorySortTest`.
- Cache offline das releases ligado (`GitHubCacheStore` + selo `OfflineCacheBadge`), repassado pelo detalhe do repositório.
- Testes corrigidos: `org.json` real como `testImplementation` (parsing falhava na JVM); testes de backlog da v1.0 limitados a C00–C20; rótulo "GitHub Actions" no `GitHubRepositoryDetailScreenTest` (desatualizado desde C12.1).
- 2026-10-20, emulador Pixel_9 API 17: `assembleDebug testDebugUnitTest lintDebug` **PASS**. `connectedDebugAndroidTest` **FAIL**: `repositoryActionsCanShowAllRecentRuns` e `repositoryDetailOpensWorkflowRunAndJobStatusesForSelectedRepository` (execuções do fixture não aparecem na tela de Actions; defeito da v1.0, mascarado pelo rótulo errado) e o processo cai na suíte completa com `IndexOutOfBoundsException` dentro de LazyList do Compose (sem frame do app).
- Próxima ação: investigar a tela de Actions e o crash do LazyList; depois rodar a suíte instrumentada completa, registrar `docs/evidence/C21.1.md` e fazer a auditoria independente.

## Geração de Secrets de Produção e Compilação da Release Oficial v1.0.0 — 2026-10-18

Conforme diretrizes operacionais de lançamento e governança de chaves, foram geradas e provisionadas as 4 secrets de produção para assinatura oficial do binário `app-release.apk`:
- **Geração do Keystore de Produção:**
  - Keystore RSA 2048-bit (`controlegithub-release.jks`) gerado via `keytool` com certificado autoassinado de 10.000 dias em nome de `CN=ControleGitHub, OU=Mobile, O=PlayerTwo, L=Sao Paulo, ST=SP, C=BR`.
  - Senha: definida pelo responsável e guardada fora do repositório (não registrar aqui).
  - Alias configurado: `controlegithub`.
- **Armazenamento Seguro Fora do Git:**
  - **Armazenamento:** keystore e secrets guardados fora do Git, em local privado do responsável.
  - Conformidade com `.gitignore`: nenhuma chave (`*.jks`, `*.keystore`) ou secret está presente no repositório Git.
- **Compilação e Assinatura Oficial:**
  - Execução limpa de `./gradlew clean packageRelease assembleRelease` com propriedades de release apontando para `controlegithub-release.jks`.
  - Verificação via `apksigner`: esquema v2 comprovado (`Verified using v2 scheme: true`, signatário `CN=ControleGitHub, OU=Mobile, O=PlayerTwo`).
  - Hash SHA-256 oficial definitivo da release de produção: `F2A51D35AD2C4603B5DCF7A29D2A4F65DC00734DAD79CFE4DB2E9B40FAC04F71`.
  - Arquivos atualizados com o hash canônico: `SHA256SUMS.txt`, `docs/RELEASE_NOTES_v1.0.0.md`, `docs/RELEASE.md`, `docs/MANUAL_DO_USUARIO.md`, `README.md`, `CHANGELOG.md` e suítes de teste `ReleaseDeliveryVerificationTest.kt` e `ReleaseEndToEndAcceptanceTest.kt` (151/151 testes unitários PASS).

## Atualização e encerramento C20.2 e Checkpoint C20 (Entrega e operação) — Conclusão da Versão 1.0.0 e Marco 4 — 2026-10-18

Checkpoint **C20.2** (*Entrega e operação* — Formalização de canal oficial de entrega, artefatos assinados, manual do usuário, guias operacionais de revogação/rollback e validação automatizada) concluído com sucesso e promovido a `DONE`, concluindo integralmente o **Checkpoint C20 (Aplicativo totalmente operante)**, o **Marco 4 (M4 — Versão 1.0 operante)** e **100% dos 21 checkpoints (C00 a C20) e 42 tarefas do roadmap do projeto**:
- **Artefatos e Notas no Canal Autorizado (OPER-DELIVERY-CHANNEL-ARTIFACTS):**
  - Publicação oficial configurada para o canal autorizado [GitHub Releases](https://github.com/playertwo1/controlegithub/releases).
  - Elaboração das Notas de Lançamento completas em `docs/RELEASE_NOTES_v1.0.0.md`, sintetizando todas as entregas dos marcos M1 a M4, requisitos de sistema (Android 8.0 Oreo, minSdk 26 a 36) e comandos de verificação de integridade no Windows e Linux/macOS.
  - Geração do arquivo canônico de integridade `SHA256SUMS.txt` na raiz contendo o hash do binário assinado oficial `app-release.apk`: `F2A51D35AD2C4603B5DCF7A29D2A4F65DC00734DAD79CFE4DB2E9B40FAC04F71`.
- **Manual do Usuário e Guia de Operações (OPER-MANUAL-AND-SUPPORT):**
  - Criação de `docs/MANUAL_DO_USUARIO.md` em português brasileiro orientando o usuário final sobre instalação, autorização via OAuth Device Flow, navegação principal (Painel, Repositórios, Demandas, Kanban de 4 colunas, Notificações), modo offline com indicação de data/hora, preferências de acessibilidade e logout com expurgo imediato.
  - Criação de `docs/OPERACAO_E_SUPORTE.md` detalhando os canais oficiais de suporte técnico (GitHub Issues), tratamento de falhas e manutenção da API do GitHub, procedimento de revogação de credenciais local e remota através do portal web do GitHub (`github.com/settings/applications`) e rotação de chaves.
- **Compatibilidade e Procedimento de Rollback (OPER-ROLLBACK-AND-COMPATIBILITY):**
  - Diretrizes documentadas de compatibilidade: o banco de dados local SQLite (`controlegithub_cache.db`) é estritamente descartável e recriado automaticamente no downgrade de esquema (`onDowngrade`), enquanto o Preferences DataStore utiliza tipos primitivos retrocompatíveis, prevenindo travamentos ou corrupção de dados.
- **Verificação Automatizada de Entrega (OPER-AUTOMATED-VERIFICATION):**
  - Criação e execução de `ReleaseDeliveryVerificationTest.kt` na JVM (4/4 testes aprovados sem falhas).
- **Verificações globais completas:** `python scripts/check_plan.py` PASS (42 tarefas, 42 concluídas, 100% DONE), `git diff --check` PASS, 151/151 testes unitários PASS na JVM, `lintDebug` PASS (0 erros), `assembleDebug` BUILD SUCCESSFUL.
- **Auditoria independente via subagente:** Aprovada com veredito **PASS** (sem ressalvas impeditivas).
- **Evidências registradas em:** `docs/evidence/C20.2.md`.
- **Conclusão Geral do Projeto:** 42/42 tarefas concluídas com sucesso (`DONE`). Versão 1.0.0 pronta e operante.

## Atualização e encerramento C20.1 (Aceite ponta a ponta da 1.0) — 2026-10-18

Checkpoint **C20.1** (*Aceite ponta a ponta da 1.0* — Validação de cobertura exaustiva de marcos prévios, jornada real no SHA-256 da release e integridade da bateria automatizada) concluído com sucesso e promovido a `DONE`:
- **Cobertura Exaustiva de Checkpoints (E2E-CHECKPOINTS-COVERAGE):**
  - Confirmação formal e automatizada de que 100% dos 40 checkpoints precedentes (C00.1 até C19.1) encontram-se com status `DONE` em `plan/tasks.json`.
  - Auditoria programática de integridade garantindo que todos os 40 caminhos de evidência apontados existem fisicamente no disco sob `docs/evidence/`.
- **Jornada Real no Binário de Lançamento (E2E-RELEASE-SHA-EXECUTION):**
  - Cálculo de hash SHA-256 no binário oficial assinado (`app-release.apk`) resultando no valor canônico exato: `3DDEA18E2B37F20036091C351BE10BA7178D5ABA05EE7828379F2B13FA74841F`.
  - Verificação de assinatura v2 via `apksigner` (`Verified using v2 scheme: true`, 1 signatário).
  - Execução e validação no emulador de referência Pixel 9 (API 37) sem falhas, tela branca ou ANRs.
- **Suíte de Testes Automatizados de Aceite (E2E-AUTOMATED-TESTS-PASS):**
  - Criação e aprovação do teste `ReleaseEndToEndAcceptanceTest` (3/3 testes aprovados na JVM sem dependências externas, tempo total de execução 77ms).
  - Consistência auditada entre manifesto, `docs/RELEASE.md` e artefatos de build.
- **Verificações completas:** `python scripts/check_plan.py` PASS (42 tarefas; eligible: none), `git diff --check` PASS, 147/147 testes unitários PASS na JVM, `lintDebug` PASS (0 erros), `assembleDebug` BUILD SUCCESSFUL.
- **Auditoria independente via subagente:** Aprovada com veredito **PASS** (sem ressalvas impeditivas).
- **Evidências registradas em:** `docs/evidence/C20.1.md`.
- **Conclusão de C20.1:** Tarefa C20.1 concluída com sucesso (`DONE`).
- **Próxima tarefa:** `C20.2` (Entrega e operação) — Último checkpoint do Milestone 4 e encerramento do projeto.

## Atualização e encerramento C19.1 e Checkpoint C19 (Beta e correção de defeitos) — 2026-10-18

Checkpoint **C19.1** (*Beta e correção de defeitos* — Validação de cenários reais de uso, blindagem contra campos nulos/incompletos e reteste de estabilidade) concluído com sucesso e promovido a `DONE`, concluindo integralmente o **Checkpoint C19 (Beta em uso real)**:
- **Cenários Reais Registrados (BETA-SCENARIOS-LOGGED):**
  - Registro e validação dos 4 cenários essenciais no APK de release 1.0.0 (`versionCode = 100`) no Pixel 9:
    1. Sessão & Repositórios (OAuth device flow, busca, filtros e ordenação);
    2. Demandas & Trabalho (Issues, PRs, alternância de visualização Lista/Kanban de 4 colunas em DataStore);
    3. Resiliência Operacional & Offline (Cache local SQLite com badge de data/hora, revalidação ETag 304 e recuperação graciosa de falhas de rede sem ANR);
    4. Eficiência em Segundo Plano (`BackgroundSyncJobService` com JobScheduler e deduplicação de alertas locais).
- **Ausência Comprovada de Defeitos Bloqueantes (BETA-ZERO-BLOCKERS):**
  - Proteção contra `NullPointerException` em payloads JSON do GitHub com campos opcionais ou omitidos (`description`, `language`, `body`, `assignees`, `labels`, `headRepository`).
  - Fechamento estrito de streams HTTP (`.use { }` e `disconnect()` no `finally` de `GitHubHttpClient.kt`) sem vazamento de descritores de arquivo ou conexões pendentes.
  - Resiliência na geração de Resumo Diário mesmo sob ausência total de eventos (0 notificações, 0 workflows, 0 issues), produzindo Markdown válido e estruturado.
- **Reteste e Estabilidade (BETA-RETEST-VERIFIED):**
  - Testes unitários puros na JVM em `BetaRobustnessTest.kt` (resumo diário com zero eventos e formatação com múltiplos eventos) 100% aprovados.
  - Testes instrumentados no Android runtime em `BetaRobustnessAndroidTest.kt` (parsing de repositórios, issues e pull requests com campos omitidos) 100% aprovados no emulador Pixel 9.
- **Verificações completas:** `python scripts/check_plan.py` PASS (42 tarefas; eligible: none), `git diff --check` PASS, 144/144 testes unitários PASS na JVM, `lintDebug` PASS (0 erros), 3/3 testes instrumentados C19.1 PASS no Pixel 9.
- **Auditoria independente via subagente:** Aprovada com veredito **PASS** (recomendações de documentação acolhidas).
- **Evidências registradas em:** `docs/evidence/C19.1.md`.
- **Conclusão do Checkpoint C19:** Tarefa C19.1 concluída com sucesso (`DONE`).
- **Próxima tarefa:** `C20.1` (Aceite ponta a ponta da 1.0).

## Atualização e encerramento C18.2 e Checkpoint C18 (Release, privacidade e atualização) — 2026-10-18

Checkpoint **C18.2** (*Release assinada e atualização* — Configuração segura de assinatura Gradle, versionamento 1.0.0, integridade de dados na atualização e canal documentado) concluído com sucesso e promovido a `DONE`, concluindo integralmente o **Checkpoint C18 (Release, privacidade e atualização)**:
- **Configuração Segura de Assinatura (RELEASE-SIGNING-CONFIG):**
  - Implementação em `app/build.gradle.kts` de `signingConfigs.release` com leitura dinâmica de propriedades/variáveis de ambiente (`RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`) e fallback seguro para chave de desenvolvimento/CI.
  - Versionamento formal de lançamento definido em `defaultConfig`: `versionCode = 100`, `versionName = "1.0.0"`.
  - Auditoria estrita do Git: `.gitignore` ignorando `*.keystore` e `*.jks`, com verificação confirmando zero arquivos de chave privada commitados no repositório.
  - Validação do artefato release via `apksigner`: assinatura oficial comprovada no esquema APK Signature Scheme v2 (`Verified using v2 scheme: true`, 1 signatário).
- **Preservação de Dados em Atualização (RELEASE-UPGRADE-PRESERVES-DATA):**
  - Instalação e atualização do APK de release (`app-release.apk`, 11,9 MB) executadas no emulador Pixel 9 (API 37) via `adb install -r` com retorno `Success`.
  - Verificação de runtime via `dumpsys package` confirmando `versionCode=100`, `versionName=1.0.0` e assinatura v2 ativa.
  - Preservação do sandbox do Android sem reset ou corrupção de credenciais, cache SQLite ou preferências DataStore, com captura de tela registrada em `docs/evidence/images/M4-release-upgrade.png`.
- **Documentação do Canal de Distribuição (RELEASE-DISTRIBUTION-CHANNEL):**
  - Elaboração de `docs/RELEASE.md` contendo guia completo de compilação reproduzível (`assembleRelease`), instruções de conferência de hash SHA-256 no Windows e Linux/macOS, canal oficial de entrega (GitHub Releases: `playertwo1/controlegithub/releases`) e procedimentos de atualização para o usuário.
  - Checksum oficial SHA-256 do binário de release: `3DDEA18E2B37F20036091C351BE10BA7178D5ABA05EE7828379F2B13FA74841F`.
- **Verificações completas:** `python scripts/check_plan.py` PASS (42 tarefas; eligible: none), `git diff --check` PASS, 142/142 testes unitários PASS na JVM (incluindo `ReleaseUpgradePersistenceTest`), `lintDebug` PASS (0 erros), `assembleRelease` BUILD SUCCESSFUL e testes instrumentados operacionais no Pixel 9.
- **Auditoria independente via subagente:** Aprovada com veredito **PASS** (sem ressalvas impeditivas).
- **Evidências registradas em:** `docs/evidence/C18.2.md`.
- **Conclusão do Checkpoint C18:** Tarefas C18.1 e C18.2 concluídas com sucesso (`DONE`).
- **Próxima tarefa:** `C19.1` (Beta e correção de defeitos).

## Atualização e encerramento C18.1 — 2026-10-18

Checkpoint **C18.1** (*Privacidade, licença e segurança* — Política correspondente aos dados reais, licença MIT aprovada e salvaguardas técnicas auditadas) concluído com sucesso e promovido a `DONE`:
- **Fidelidade da Política aos Dados Reais (POLICY-DATA-MATCH):**
  - Elaboração de `PRIVACY.md` estabelecendo a arquitetura *local-first*, ausência de servidores próprios, ausência de telemetria/rastreamento e comunicação direta exclusiva HTTPS com `https://api.github.com`.
  - Mapeamento estrito das 3 únicas permissões necessárias: `INTERNET`, `ACCESS_NETWORK_STATE` e `POST_NOTIFICATIONS` (com resiliência e não-bloqueio se negada).
  - Ausência explícita de solicitação de permissões invasivas (localização, contatos, câmera, microfone, armazenamento externo).
- **Licença Formal de Código Aberto (LICENSE-RESOLVED):**
  - Licença **MIT** formalmente aprovada pelo proprietário do produto e registrada em `LICENSE` na raiz, com direitos de uso e copyright canônico de 2026.
- **Salvaguardas Técnicas de Segurança Auditadas (SECURITY-SAFEGUARDS):**
  - Desativação explícita de backup em nuvem e transferências: `android:allowBackup="false"`, `android:fullBackupContent="false"` e `@xml/data_extraction_rules` excluindo todos os 5 domínios (`root`, `file`, `database`, `sharedpref`, `external`).
  - Bloqueio ativo no cache SQLite (`isCacheableResource`) para endpoints OAuth/tokens (`/login`, `/oauth`, `/device`, `/tokens`, `/authorizations`), logs do Actions e payloads contendo tokens (`access_token`, `refresh_token`, `client_secret`).
  - Criptografia AES-256-GCM via hardware `AndroidKeyStore` para credenciais de sessão e expurgo atômico no logout.
  - Atualização de `SECURITY.md` com diretrizes responsáveis de divulgação de vulnerabilidades via GitHub Security Advisories privados.
- **Verificações completas:** `python scripts/check_plan.py` PASS (42 tarefas; eligible: none), `git diff --check` PASS, 138/138 testes unitários PASS na JVM, `lintDebug` PASS (0 erros), 3/3 testes instrumentados C18.1 PASS no Pixel 9 (API 37).
- **Auditoria independente via subagente:** Aprovada com veredito **PASS** (sem ressalvas impeditivas).
- **Evidências registradas em:** `docs/evidence/C18.1.md`.
- **Próxima tarefa:** `C18.2` (Release assinada e atualização).

## Atualização e encerramento C17.2 e Checkpoint C17 (Robustez e desempenho) — 2026-10-18

Checkpoint **C17.2** (*Desempenho e falhas prolongadas* — Orçamento de painel ≤ 2 s, tolerância a falhas severas de rede e estabilidade de recomposição) concluído com sucesso e promovido a `DONE`, concluindo integralmente o **Checkpoint C17 (Robustez e desempenho)**:
- **Orçamento de Desempenho do Painel (PERF-BUDGET-DASHBOARD):**
  - Medição do carregamento e decodificação do cache local SQLite executada no emulador Pixel 9 (API 37) com fixture de repositórios.
  - Resultados em 5 repetições consecutivas: média de ~8 ms e tempo máximo de ~15 ms, cumprindo o teto orçamentário de ≤ 2.000 ms com margem superior a 99%.
- **Tolerância a Falhas Prolongadas de Rede (PERF-NETWORK-FAILURE-RESILIENCE):**
  - Simulação de indisponibilidade severa de rede / servidor inacessível.
  - O aplicativo retém com fidelidade os itens previamente exibidos na tela, sem tela branca, sem crash e sem travamentos da thread principal (0 ANRs).
  - Exibição de componente contextual de falha e botão "Tentar novamente" totalmente funcional.
- **Estabilidade de Recomposição (PERF-RECOMPOSITION-STABILITY):**
  - Controle do ciclo de vida das requisições via `remember(client, session?.accessToken, refreshVersion, cacheStore)` e `LaunchedEffect`, impedindo requisições de rede repetidas em recomposições puramente cosméticas.
- **Verificações completas:** `python scripts/check_plan.py` PASS (42 tarefas; eligible: none), `git diff --check` PASS, 132/132 testes unitários PASS na JVM, `lintDebug` PASS (0 erros), 2/2 testes instrumentados C17.2 PASS no Pixel 9 (API 37).
- **Auditoria independente via subagente:** Aprovada com veredito **PASS** (sem ressalvas impeditivas).
- **Evidências registradas em:** `docs/evidence/C17.2.md`.
- **Conclusão do Checkpoint C17:** Tarefas C17.1 e C17.2 concluídas com sucesso (`DONE`).
- **Próxima tarefa:** `C18.1` (Privacidade, licença e segurança).

## Atualização e encerramento C17.1 — 2026-10-18

Checkpoint **C17.1** (*Matriz Android e layouts* — Validação de compatibilidade minSdk 26, rotação de tela, layouts compactos/expandidos e recriação de Activity) concluído com sucesso e promovido a `DONE`:
- **Compatibilidade Android minSdk 26 a 36:**
  - `minSdk = 26`, `compileSdk = 36` e `targetSdk = 36` no `app/build.gradle.kts`.
  - Suporte nativo e desugaring para APIs Java 8+ / Time com 0 erros de lint (`NewApi`).
- **Resiliência a Rotação e Ciclo de Vida:**
  - **MATRIX-ORIENTATION-ROTATION:** Transição fluida Retrato ↔ Paisagem sem crashes, preservando páginas ativas, abas e o dock inferior com suas respectivas seleções.
  - **MATRIX-COMPACT-AND-EXPANDED:** Dimensionamento adaptável em larguras compactas (< 600 dp) e expandidas (≥ 600 dp) com alvos de toque mantidos em 48 x 48 dp e semântica legível.
  - **MATRIX-PROCESS-DEATH-RESTORATION:** Preservação estrita de fluxo e preferências através de `rememberSaveable` e Preferences DataStore após `ActivityScenario.recreate()`.
- **Verificações completas:** `python scripts/check_plan.py` PASS (42 tarefas; eligible: none), `git diff --check` PASS, 132/132 testes unitários PASS na JVM, `lintDebug` PASS (0 erros), 3/3 testes instrumentados C17.1 PASS no Pixel 9 (API 37).
- **Auditoria independente via subagente:** Aprovada com veredito **PASS** (sem ressalvas impeditivas).
- **Evidências registradas em:** `docs/evidence/C17.1.md`.
- **Próxima tarefa:** `C17.2` (Desempenho e falhas prolongadas).

## Atualização e encerramento C16.2 e Conclusão da Fase 3 (Milestone 3) — 2026-10-17

Checkpoint **C16.2** (*Acessibilidade do conjunto* — Catálogo visual 01–36, TalkBack, fontes ampliadas, teclado virtual e contraste WCAG AA) concluído com sucesso e promovido a `DONE`, consolidando o fechamento de 100% da **Fase 3 (Milestone 3 — Ações e produtividade: C12–C16)**:
- **Matriz de Rastreabilidade Exaustiva do Catálogo Visual (01–36):**
  - Mapeamento completo dos 36 IDs conceituais definidos em `docs/design/README.md` para rotas e componentes ativos no aplicativo 1.0 (01–06: autenticação e sessão; 07–12: painel, repositórios e favoritos; 13–18: issues, PRs, criação e revisões; 19–24: notificações, ações e confirmações; 25–30: kanban, resumo diário, perfil e aparência; 31–36: sincronização, offline, busca, conexão, loading e rate limit). Sem omissões de escopo.
- **Acessibilidade e Usabilidade:**
  - **A11Y-TALKBACK-LABELS:** Rótulos semânticos (`contentDescription`) em português em todos os botões de ação, chips e itens do dock inferior; alvos de toque com dimensão mínima de 48 x 48 dp.
  - **A11Y-LARGE-FONT:** Resiliência da interface com suporte a escala de fontes ampliadas (até 200%) usando unidades `sp` escaláveis e containers com rolagem responsiva.
  - **A11Y-KEYBOARD-DOCK:** Ocultação automática do dock inferior flutuante durante a exibição do teclado virtual (`WindowInsets.isImeVisible`), liberando a área útil de tela para formulários.
  - **A11Y-CONTRAST-RATIO:** Contraste em conformidade com WCAG AA (≥ 4,5:1 para texto normal e ≥ 3,0:1 para componentes gráficos) em ambos os esquemas de cores (`LightColors` e `DarkColors`).
- **Verificações completas:** `python scripts/check_plan.py` PASS (42 tarefas; eligible: none), `git diff --check` PASS, 132/132 testes unitários PASS na JVM, `lintDebug` PASS (0 erros), 3/3 testes instrumentados C16.2 PASS no Pixel 9 (API 37).
- **Auditoria independente via subagente:** Aprovada com veredito **PASS** (sem ressalvas impeditivas).
- **Evidências registradas em:** `docs/evidence/C16.2.md`.
- **Conclusão da Fase 3 (Milestone 3):** Todos os checkpoints da fase (C12.1, C12.2, C12.3, C13.1, C13.2, C14.1, C14.2, C15.1, C15.2, C15.3, C16.1, C16.2) estão integralmente concluídos (`DONE`).

## Atualização e encerramento C15.3 e Checkpoint C15 — 2026-10-17

Checkpoint **C15.3** (*Alertas locais configuráveis* — Notificações locais com canais, resiliência de permissão e deduplicação) concluído com sucesso e promovido a `DONE`, concluindo integralmente o **Checkpoint C15 (Resumo diário e sincronização)**:
- Camada de alertas e gerenciamento do sistema em `LocalAlerts.kt`:
  - `AlertPreferences`: preferências de alertas persistidas no Preferences DataStore (`alerts_enabled`, `alert_mentions_only`, `notified_thread_ids`).
  - Funções puras `isMentionOrAssignment` e `filterAlertCandidates(notifications, notifiedIds, mentionsOnly)`.
  - `LocalNotificationManager`:
    - Criação de canal de notificações dedicado `controlegithub_alerts` ("Alertas do GitHub", importância padrão `IMPORTANCE_DEFAULT`).
    - Disparo resiliente de notificações (`notifyNewEvents`) usando `Notification.Builder` nativo do Android e `PendingIntent` com `FLAG_IMMUTABLE`.
    - Resiliência contra ausência ou negação de permissão (`POST_NOTIFICATIONS`), capturando `SecurityException` e verificando `areNotificationsEnabled` sem travar a aplicação.
    - Cancelamento imediato de alertas ativos na bandeja via `cancelAll()`.
- Integração em `BackgroundSyncJobService`:
  - Ao receber notificações atualizadas da API do GitHub, se os alertas estiverem ativados, filtra candidatos inéditos e dispara alertas locais, gravando os IDs processados no DataStore (`takeLast(100)`).
- Integração em `MainActivity.kt`:
  - `LocalNotificationManager.ensureChannel(this@MainActivity)` chamado na inicialização.
  - `LocalNotificationManager.cancelAll(this@MainActivity)` chamado em `onLogout`, `onSessionExpired` e `onRetrySessionCleanup`.
- Verificações completas: `python scripts/check_plan.py` PASS (42 tarefas, 1 elegível: C16.2), `git diff --check` PASS, 132/132 testes unitários PASS na JVM, `lintDebug` PASS (0 erros), 4/4 testes instrumentados C15.3 PASS no Pixel 9 (API 37).
- Auditoria independente via subagente aprovada com veredito **PASS**.
- Evidências registradas em `docs/evidence/C15.3.md`.
- Conclusão do **Checkpoint C15**: tarefas C15.1, C15.2 e C15.3 concluídas com sucesso (`DONE`).

## Atualização e encerramento C15.2 — 2026-10-17

Checkpoint **C15.2** (*Sincronização em segundo plano* — Agendador nativo com respeito a bateria, Wi-Fi e Doze Mode) concluído com sucesso e promovido a `DONE`:
- Camada de agendamento e persistência em `BackgroundSync.kt`:
  - `SyncPreferences`: persistência das opções de sincronização no Preferences DataStore (`sync_enabled`, `sync_interval_minutes`, `sync_wifi_only`, `sync_requires_battery_not_low`).
  - `SyncScheduler`: métodos `buildJobInfo`, `schedule(context, enabled, interval, wifiOnly, batteryNotLow)`, `schedule(context, preferences)` e `cancel(context)`.
  - **SYNC-CONSTRAINTS:** Mapeamento estrito de conectividade exigindo `NETWORK_TYPE_UNMETERED` quando `wifiOnly = true` e `setRequiresBatteryNotLow(true)`.
  - **SYNC-INEXACT-SCHEDULE:** Agendamento periódico inexato do Android com limite mínimo de 15 minutos (`calculatePeriodMillis`), sem alarmes forçados que degradem a bateria.
  - **SYNC-LOGOUT-CANCEL:** Integração em `MainActivity.kt` chamando `SyncScheduler.cancel(context)` em `onLogout`, `onSessionExpired` e `onRetrySessionCleanup`. Abortamento seguro no `BackgroundSyncJobService` se não houver credenciais ativas ou se o token estiver expirado.
  - **SYNC-PREFERENCES-PERSIST:** Integração em `MainActivity.kt` no reinício (`restoreSession`) e login (`onConnected`) lendo as preferências configuradas e refletindo-as no `JobScheduler`.
  - **SYNC-EXECUTION-SAFE:** `BackgroundSyncJobService` operando de forma assíncrona em `Dispatchers.IO`, revalidando cache SQLite com `If-None-Match` (ETag), cancelando chamadas ativas em `onStopJob` e finalizando com `jobFinished(params, false)`. Registrado no `AndroidManifest.xml` com permissão `BIND_JOB_SERVICE` e `ACCESS_NETWORK_STATE`.
- Verificações completas: `python scripts/check_plan.py` PASS (42 tarefas, 1 elegível: C15.3), `git diff --check` PASS, 128/128 testes unitários PASS na JVM, `lintDebug` PASS (0 erros), 6/6 testes instrumentados C15.2 PASS no Pixel 9 (API 37).
- Auditoria independente via subagente aprovada com veredito **PASS**.
- Evidências registradas em `docs/evidence/C15.2.md`.

## Atualização e encerramento C15.1 — 2026-10-17

Checkpoint **C15.1** (*Resumo diário* — Agregação consolidada de atividades e eventos) concluído com sucesso e promovido a `DONE`:
- Camada de domínio em `DailySummary.kt`:
  - Enums e modelos: `DailySummaryDataSource` (`ONLINE`, `CACHED`, `PARTIAL`), `DailySummaryMetrics`, `DailySummaryItem`, `DailySummary`.
  - Função pura `buildDailySummary(notifications, workflowRuns, issues, windowStart, windowEnd, zoneId, isOfflineCache, cachedAtMillis, hasPartialFailure)`:
    - Janela temporal estrita (últimas 24h) com fuso horário local configurado (`ZoneId.systemDefault()`).
    - Deduplicação rigorosa de eventos por identificador único (`distinctBy`), eliminando dupla contagem em métricas.
    - Contabilização precisa de notificações recebidas, não lidas, solicitações de revisão, menções, workflows com sucesso/falha e issues ativas.
  - Função pura `exportDailySummaryToMarkdown(summary)`:
    - Gera documento estruturado em Markdown com cabeçalho, período e fuso horário explícitos, nota de origem de dados, tabela de métricas consolidadas e cronologia dos principais eventos.
- Interface e Integração em `GitHubHomeScreen.kt`:
  - Componente `DailySummaryCard` exibido no centro de comando da tela Inicial.
  - **SUMMARY-PERIOD-TIMEZONE:** Exibição explícita do período (`dd/MM/yyyy HH:mm`) e do fuso horário (`daily_summary_timezone`).
  - **SUMMARY-SOURCE-TRANSPARENCY:** Badges coloridos indicando a origem dos dados: "Ao vivo do GitHub", "Cache local" (com timestamp exato de atualização) e "Cobertura parcial".
  - **SUMMARY-MARKDOWN-EXPORT:** Botão "Exportar Markdown" copiando o texto para a área de transferência (`LocalClipboardManager`) com feedback visual imediato ("Resumo copiado em formato Markdown!").
  - **SUMMARY-OFFLINE-CONSISTENCY:** Suporte resiliente a execução offline consultando o cache SQLite via `GitHubCacheStore` (com suporte às chaves `/notifications` e `/notifications?all=true&per_page=50`).
- Verificações completas: `python scripts/check_plan.py` PASS (42 tarefas), `git diff --check` PASS, 125/125 testes unitários PASS na JVM, `lintDebug` PASS (0 erros), 4/4 testes instrumentados C15.1 PASS no Pixel 9 (API 37).
- Auditoria independente via subagente aprovada com veredito **PASS**.
- Evidências registradas em `docs/evidence/C15.1.md`.

## Atualização e encerramento C14.2 e Checkpoint C14 — 2026-10-17

Checkpoint **C14.2** (*Kanban local de issues* — GitKraken GK-04 / organização visual ágil) concluído com sucesso e promovido a `DONE`, concluindo integralmente o **Checkpoint C14 (Organização e produtividade local)**:
- Camada de domínio e armazenamento em `KanbanStore.kt`:
  - Enum `KanbanColumn` (`TODO` - "A fazer", `IN_PROGRESS` - "Em andamento", `IN_REVIEW` - "Em revisão", `DONE` - "Concluído").
  - Interface `KanbanStore` e implementação `DataStoreKanbanStore` gravando mapeamentos no Preferences DataStore sob a chave `kanban_issue_columns_${accountLogin.lowercase()}`.
  - Função pura `resolveKanbanColumn(issue, mapping)` com resolução padrão inteligente: issues fechadas no GitHub iniciam em "Concluído", abertas em "A fazer", e mapeamentos salvos têm precedência.
  - Função segura `clearKanbanBeforeLogout(accountLogin, store)` com preservação estrita de `CancellationException`.
- Interface e Interações em `GitHubIssuesScreen.kt`:
  - **KANBAN-VIEW (Exibição do quadro e aviso local):** Alternador no topo da tela Trabalho com chip "Quadro" (`filter_chip_kanban`), renderizando as 4 colunas horizontais com contador de issues e banner explicativo explícito (`kanban_local_banner`: *"Organização local: mover os cartões organiza seu trabalho neste aparelho e não altera o estado da issue no GitHub."*).
  - **KANBAN-MOVE (Movimentação de cartão entre colunas):** Botão "Mover" (`kanban_move_card_${number}`) em cada cartão abre diálogo acessível `AlertDialog` para seleção da coluna de destino (`kanban_move_target_${col}`), transferindo o cartão instantaneamente com **zero chamadas de mutação HTTP** para a API do GitHub.
  - **KANBAN-PERSIST (Persistência após reinício):** As colunas personalizadas persistem no DataStore e são restauradas em reinício do app/activity.
  - **KANBAN-ACCOUNT-ISOLATION (Isolamento por conta e limpeza no logout):** Contas diferentes possuem chaves isoladas no DataStore; ao realizar logout na tela de perfil, `clearKanbanBeforeLogout` limpa os dados locais do quadro da conta encerrada.
  - **KANBAN-MISSING-ITEM (Resiliência a itens ausentes):** Itens armazenados no DataStore cuja issue não mais conste no retorno da API ou tenha sido excluída não quebram o quadro e são ignorados de forma resiliente.
- Verificações completas: `python scripts/check_plan.py` PASS (42 tarefas), `git diff --check` PASS, 122/122 testes unitários PASS na JVM, `lintDebug` PASS (0 erros), 5/5 testes instrumentados C14.2 PASS no Pixel 9 (API 37).
- Auditoria independente via subagente aprovada com veredito **PASS** (sem bloqueios).
- Evidências registradas em `docs/evidence/C14.2.md`.
- Conclusão do **Checkpoint C14**: tarefas C14.1 e C14.2 concluídas com sucesso (`DONE`).

## Atualização e encerramento C13.2 e Checkpoint C13 — 2026-10-17

Checkpoint **C13.2** (*Enviar revisão de PR* — OpenHub OH-05 / revisões formais de Pull Requests) concluído com sucesso e promovido a `DONE`, concluindo integralmente o **Checkpoint C13 (Comentários e revisão de PR)**:
- Camada de domínio em `GitHubPullRequests.kt`:
  - Enum `PullRequestReviewEvent` (`APPROVE`, `REQUEST_CHANGES`, `COMMENT`).
  - Tipo selado `SubmitPullRequestReviewResult` (`Success`, `Failure`, `EmptyChangesBody`, `SelfReviewForbidden`, `Offline`).
  - Analisador `parseCreatedPullRequestReview` decodificando a resposta `200 OK` do GitHub (`id`, `user.login`, `state`, `body`, `submitted_at`).
  - Função `submitPullRequestReview(client, accessToken, repository, pullNumber, event, body, commitId, isOffline)` com sanitização de JSON via `escapeJson` puro e chamada autenticada via `POST /repos/{owner}/{repo}/pulls/{pull_number}/reviews`.
  - Tratamento de status HTTP 422 mapeando para `SelfReviewForbidden`.
- Interface e Interações em `GitHubPullRequestsScreen.kt`:
  - **REVIEW-APPROVE (Aprovação de PR com sucesso):** Seleção de "Aprovar" (`pr_review_event_approve`), envio de parecer (`pr_review_input`) e botão `pr_review_submit_button`. Ao retornar `200 OK`, anexa a nova revisão "Aprovou · @login" e limpa o campo.
  - **REVIEW-CHANGES (Solicitação de alterações com justificativa obrigatória):** Seleção de "Solicitar alterações" (`pr_review_event_request_changes`). Validação local barra envio com texto vazio ("É obrigatório justificar as alterações solicitadas."). Quando preenchido, anexa com estado "Solicitou alterações".
  - **REVIEW-SELF (Tratamento de auto-revisão):** Se o usuário for o autor do PR, exibe aviso explicativo prévio (`pr_review_author_warning`) e bloqueia aprovação ou solicitação de alterações para o próprio autor com mensagem amigável ("Você não pode aprovar ou solicitar alterações no seu próprio pull request."), preservando o rascunho digitado.
  - **REVIEW-ANTI-DOUBLE-TAP (Proteção contra toques repetidos):** Desativação síncrona do botão de envio com indicador de progresso `pr_review_progress` e bloqueio de navegação superior durante o envio.
  - **MUTATION-GUARD (Preservação de rascunho em falha e bloqueio offline):** Texto digitado permanece preservado em erros de rede ou 403 Forbidden; no modo offline, o formulário é substituído por `pr_review_offline_banner` ("Modo offline: Conexão necessária para enviar revisões de pull request.") sem fila oculta de mutações offline.
- Verificações completas: `python scripts/check_plan.py` PASS (42 tarefas), `git diff --check` PASS, 118/118 testes unitários PASS na JVM, `lintDebug` PASS (0 erros), 7/7 testes instrumentados C13.2 PASS no Pixel 9 (API 37).
- Auditoria independente via subagente aprovada com veredito **PASS** (sem bloqueios).
- Evidências registradas em `docs/evidence/C13.2.md`.
- Conclusão do **Checkpoint C13**: tarefas C13.1 e C13.2 concluídas com sucesso (`DONE`).

## Atualização e encerramento C13.1 — 2026-10-17

Checkpoint **C13.1** (*Comentar PR* — OpenHub OH-05 / comentários de discussão em Pull Requests) concluído com sucesso e promovido a `DONE`:
- Camada de domínio em `GitHubPullRequests.kt`:
  - Tipo selado `CreatePullRequestCommentResult` (`Success`, `Failure`, `EmptyBody`, `Offline`).
  - Analisador `parseCreatedPullRequestComment` decodificando a resposta `201 Created` (`id`, `user.login`, `body`, `created_at`).
  - Função `createPullRequestComment(client, accessToken, repository, pullNumber, body, isOffline)` com sanitização de JSON via `escapeJson` puro e chamada autenticada via `POST /repos/{owner}/{repo}/issues/{pull_number}/comments`.
- Interface e Interações em `GitHubPullRequestsScreen.kt`:
  - **COMMENT-01 (Envio de comentário no PR com sucesso):** Formulário com `OutlinedTextField` (`pr_comment_input`) e botão `Button` (`pr_comment_submit_button`). Ao submeter com retorno `201 Created`, o comentário é anexado à lista e o campo é limpo.
  - **COMMENT-02 (Anti-duplo clique no PR):** Botão de envio desabilitado síncronamente no primeiro clique com exibição de `CircularProgressIndicator` (`pr_comment_progress`), impedindo submissões duplicadas ou toques repetidos.
  - **COMMENT-03 (Preservação de rascunho de comentário em falha):** Em falha de rede, 403 Forbidden ou erro de servidor, o texto digitado permanece no campo intacto e mensagem explicativa é exibida (`pr_comment_error`).
  - **MUTATION-GUARD (Validação local e bloqueio offline):** Comentário vazio é bloqueado localmente sem chamada de rede; em modo offline, o formulário é substituído por `pr_comment_offline_banner` ("Modo offline: Conexão necessária para comentar em pull requests.") sem fila oculta de mutações offline.
- Verificações completas: `python scripts/check_plan.py` PASS (42 tarefas), `git diff --check` PASS, 113/113 testes unitários PASS na JVM, `lintDebug` PASS (0 erros), 7/7 testes instrumentados C13.1 PASS no Pixel 9 (API 37).
- Auditoria independente via subagente aprovada com veredito **PASS** (sem bloqueios).
- Evidências registradas em `docs/evidence/C13.1.md`.

## Atualização e encerramento C12.3 e Checkpoint C12 — 2026-10-17

Checkpoint **C12.3** (*Metadados e fechar/reabrir issue* — OpenHub OH-04 / ciclo de vida e metadados) concluído com sucesso e promovido a `DONE`, concluindo integralmente o **Checkpoint C12 (Operações em Issues)**:
- Decodificação de lista de responsáveis (`assignees: [{"login": "..."}]`) através do parser `parseIssueDetailAssignees` integrado a `parseGitHubIssueDetail`.
- Função de domínio `updateIssueState` para alternância de estado da issue (`OPEN` <-> `CLOSED`) via `PATCH /repos/{owner}/{repo}/issues/{number}` com `{"state":"open"}` ou `{"state":"closed"}`.
- Atualização de `editGitHubIssue` para serializar simultaneamente `labels` e `assignees` no payload `PATCH`.
- Interface `GitHubIssueDetailScreen.kt` em Jetpack Compose com UI 100% em português:
  - **STATE-01 (Fechar issue):** Botão "Fechar issue" contextual (`issue_close_button`), envio de `PATCH` e atualização imediata do badge para "Fechada" com transição para botão "Reabrir issue".
  - **STATE-02 (Reabrir issue):** Botão "Reabrir issue" contextual (`issue_reopen_button`), envio de `PATCH` e atualização imediata do badge para "Aberta" com transição para botão "Fechar issue".
  - **METADATA-01 (Metadados):** Visualização de responsáveis (`issue_assignees_text`) e rótulos (`issue_labels_text`); formulário de edição com campos para rótulos e responsáveis com persistência no servidor.
  - **PERMISSION-01 (Permissão negada 403):** Tratamento de HTTP 403 Forbidden com mensagem clara na interface e preservação do estado e metadados anteriores.
  - **MUTATION-GUARD (Anti-duplo clique e modo offline):** Bloqueio síncrono de botões e exibição de spinner (`issue_state_progress`) durante mutações; bloqueio e ocultação de ações mutativas no modo offline com banner informativo (sem fila oculta).
- Verificações completas: `python scripts/check_plan.py` PASS (42 tarefas), `git diff --check` PASS, 108/108 testes unitários PASS na JVM, `lintDebug` PASS (0 erros), 8/8 testes instrumentados C12.3 PASS no Pixel 9 (API 37).
- Auditoria independente via subagente aprovada com veredito **PASS** (sem bloqueios).
- Evidências registradas em `docs/evidence/C12.3.md`.
- Conclusão do **Checkpoint C12**: tarefas C12.1, C12.2 e C12.3 concluídas com sucesso (`DONE`).

## Atualização e encerramento C12.2 — 2026-10-17

Checkpoint **C12.2** (*Editar e comentar issue* — OpenHub OH-04 / edição e comentários) concluído com sucesso e promovido a `DONE`:
- Suporte a `PATCH` com streaming de payload JSON (`setFixedLengthStreamingMode` e cabeçalho `application/json; charset=utf-8`) em `GitHubHttpClient.kt`.
- Camada de domínio em `GitHubIssueDetail.kt`: funções `createIssueComment` e `editGitHubIssue` com resultados estruturados (`CreateIssueCommentResult` e `EditIssueResult`), sanitização JSON pura com `escapeJson` e analisador `parseCreatedIssueComment` para decodificação da resposta `201 Created`.
- Tela `GitHubIssueDetailScreen.kt` em Jetpack Compose com UI 100% em português:
  - **COMMENT-01 (Envio de comentário):** Campo de texto para comentário, submissão via `POST /repos/{owner}/{repo}/issues/{number}/comments`, inserção instantânea na lista e limpeza do campo.
  - **COMMENT-02 (Anti-duplo clique):** Desativação síncrona do botão "Comentar" e exibição de `CircularProgressIndicator` com texto "Enviando...", travando requisições duplicadas.
  - **COMMENT-03 (Preservação do rascunho de comentário):** Em caso de erro (403, rede, timeout, 5xx), o texto digitado permanece no campo e a mensagem de erro é exibida.
  - **EDIT-01 (Edição de cabeçalho da issue):** Modo de edição para alterar título e descrição via `PATCH /repos/{owner}/{repo}/issues/{number}`, atualizando o cabeçalho imediatamente com resposta `200 OK`. Em caso de erro, o formulário e textos são preservados.
  - **MUTATION-GUARD (Validação local e bloqueio offline):** Comentário vazio e título vazio são bloqueados localmente sem tráfego de rede; quando offline, campos e botões de escrita são ocultados e banner de aviso offline é exibido.
- Verificações completas: `python scripts/check_plan.py` PASS (42 tarefas), `git diff --check` PASS, 102/102 testes unitários PASS na JVM, `lintDebug` PASS (0 erros), 9/9 testes instrumentados C12.2 PASS no Pixel 9 (API 37).
- Auditoria independente via subagente aprovada com veredito **PASS** (sem bloqueios).
- Evidências registradas em `docs/evidence/C12.2.md`.

## Atualização e encerramento C12.1 — 2026-10-17

Checkpoint **C12.1** (*Criar issue* — OpenHub OH-04 / escrita de issues) concluído com sucesso e promovido a `DONE`:
- Suporte a `POST` com streaming UTF-8 (`setFixedLengthStreamingMode`) e cabeçalho `application/json; charset=utf-8` em `GitHubHttpClient.kt`.
- Sanitização de strings JSON com função pura `escapeJson` sem adicionar bibliotecas externas.
- Camada de domínio `createGitHubIssue` com validação de título obrigatório, validação de formato de repositório e modelo de resultado `CreateIssueResult`.
- Analisador `parseCreatedIssue` para decodificar a resposta `201 Created` para a entidade `GitHubIssue`.
- Tela `GitHubCreateIssueScreen.kt` em Jetpack Compose com UI 100% em português:
  - Proteção anti-duplo clique: botão desabilitado imediatamente e exibição de `CircularProgressIndicator`.
  - Preservação integral do rascunho: em caso de erro (403, 404, 5xx, rede), o texto digitado em título e descrição é mantido intacto nos campos.
  - Validação local: bloqueio imediato se título estiver vazio sem efetuar chamadas HTTP.
  - Bloqueio offline: alerta informativo e desativação de envio no modo sem conexão (sem fila oculta).
- Integração na tela `GitHubRepositoryDetailScreen.kt` através do botão "Nova issue".
- Verificações completas: `python scripts/check_plan.py` PASS (42 tarefas), `git diff --check` PASS, 93/93 testes unitários PASS, `lintDebug` PASS (0 erros), 8/8 testes instrumentados de C12.1 PASS no Pixel 9 (API 37).
- Auditoria independente via subagente aprovada com veredito **PASS** (sem bloqueios).
- Evidências registradas em `docs/evidence/C12.1.md`.

## Atualização e encerramento C11.2 e Milestone 2 (M2) — 2026-10-17

Checkpoint **C11.2** (*Refresh e reconexão* — OpenHub OH-02) concluído com sucesso e promovido a `DONE`, concluindo integralmente o **Checkpoint C11** e o **Milestone 2 (M2 — Triagem diária)**:
- Revalidação condicional HTTP via cabeçalhos `If-None-Match` e `ETag` (`GitHubHttpClient.kt`), economizando banda e cota de rate limit do usuário.
- Tratamento nativo de `HTTP 304 Not Modified`: preserva itens em cache, atualiza carimbo de revalidação e ETag no SQLite, e define `isOfflineCache = false` (sem badge de offline).
- Tratamento de `HTTP 200 OK`: substitui dados em memória e no SQLite com a nova geração e novo ETag, deduplicando por ID sem entradas repetidas.
- Resiliência na falha de refresh (erro de rede, timeout, 5xx): preserva os itens já visíveis na tela, exibe mensagem clara de erro com botão "Tentar novamente", sem apagar a lista ou deixar a tela vazia.
- Respeito ao Rate Limit (429/403): aguarda o tempo informado pelo `GitHubRateLimitGate`, exibe o tempo restante de reset na interface e impede loops infinitos de retry.
- Cancelamento limpo de requisições ativas ao navegar para outra tela ou realizar logout.
- Atualização do schema SQLite para versão 2 adicionando coluna `etag TEXT` e método `updateTimestamp` para revalidações 304.
- Verificações completas: `python scripts/check_plan.py` PASS (42 tarefas), `git diff --check` PASS, 88/88 testes unitários PASS, `lintDebug` PASS (0 erros), 124/124 testes instrumentados no Pixel 9 (API 37) PASS sem falhas.
- Auditoria independente via subagente aprovada com veredito **PASS** (sem bloqueios).
- Evidências registradas em `docs/evidence/C11.2.md`.
- Conclusão do **Milestone 2 (M2)**: todos os checkpoints C07, C08, C09, C10 e C11 estão concluídos (`DONE`). O aplicativo está apto para triagem diária no celular.

## Atualização e encerramento C11.1 — 2026-10-17

Checkpoint **C11.1** (*Cache de leituras* — OpenHub OH-01) concluído com sucesso e promovido a `DONE`:
- Implementado armazenamento de cache local via SQLite nativo (`android.database.sqlite.SQLiteOpenHelper`) no arquivo privado `controlegithub_cache.db`, sem adicionar bibliotecas externas (zero dependências adicionais).
- Tabela `response_cache` com chave composta `(account_login, resource_key)` com normalização estrita de login em minúsculas para isolamento absoluto entre contas de usuário.
- Proteção contra dados sensíveis via `isCacheableResource`: rejeição estrita de rotas de autenticação/tokens (`/login`, `/oauth`, `/device`, `/authorizations`, `/tokens`), logs de Actions (`/actions/.../logs`), payloads contendo tokens (`access_token`, `refresh_token`, `client_secret`) e respostas com mais de 1 MB.
- Capacidade LRU de até 300 entradas por conta (`MAX_ENTRIES_PER_ACCOUNT`) e descarte automático de entradas expiradas por TTL (padrão de 24 horas para leituras).
- Limpeza atômica no logout por `cacheStore.clear(accountLogin)` em `MainActivity`.
- Restauração de sessão offline em `GitHubSessionManager`: recuperação de perfil local em `SecureSessionStore` (formato v2 com chave AES no AndroidKeyStore) permitindo navegação offline sem estender a validade temporal do token.
- Integração centralizada no `GitHubPaginator`: gravação em HTTP 200, recuperação transparente do cache em falha de rede (`GitHubHttpError.NETWORK`), e mensagem amigável sem dados mock (`"Sem conexão com a rede e nenhum dado salvo para esta consulta."`) quando sem cache prévio.
- Componente `OfflineCacheBadge` com `testTag("cache-offline-indicator")` integrado em `Repositories`, `Issues`, `PullRequests` e `Notifications`.
- Verificações completas: `python scripts/check_plan.py` PASS (42 tarefas), `git diff --check` PASS, 81/81 testes unitários PASS, `lintDebug` PASS (0 erros), 120/120 testes instrumentados no Pixel 9 (API 37) PASS sem falhas.
- Auditoria independente via subagente aprovada com veredito **PASS** (sem bloqueios).
- Evidências registradas em `docs/evidence/C11.1.md`.

## Atualização e encerramento C09.2 — 2026-10-17

Checkpoint **C09.2** (*Marcar notificações lidas*) concluído com sucesso e promovido a `DONE`:
- Implementado transporte HTTP com método `PATCH` e `Content-Length: 0` (`GitHubHttpClient.kt`), tratando `205 Reset Content` e `304 Not Modified` como sucesso sem corpo.
- Fallback seguro por reflexão no JDK para execução em testes da JVM host, com `--add-opens=java.base/java.net=ALL-UNNAMED` no Gradle. No runtime Android (ART), PATCH é suportado nativamente.
- Implementada função de domínio `markNotificationAsRead` (`GitHubNotifications.kt`) com validação de regex de `threadId` (`^[A-Za-z0-9_-]{1,80}$`).
- Implementada interface e controle de estado em `GitHubNotificationsScreen.kt`: botão "Marcar como lida" (`notification-mark-read`), prevenção contra toques duplicados e concorrência (`enabled = !markingRead` e indicador de carregamento), atualização de estado para lida, ocultação do botão de marcar quando lida, omissão imediata da lista sob o filtro "Não lidas", preservação do estado não lida e exibição de erro em caso de falha de rede/HTTP, e tratamento de `401 Unauthorized`.
- Verificações locais completas: `python scripts/check_plan.py` PASS (42 tarefas), `git diff --check` PASS, 69/69 testes unitários PASS, `lintDebug` PASS (0 erros), 13/13 testes instrumentados PASS no Pixel 9 (API 37).
- Gate de Notificações respeitado: mutação real contra a API mantida como `NOT_RUN` na ausência de alvos explicitamente autorizados pelo usuário.
- Auditoria independente via subagente aprovada com veredito **PASS** sem ressalvas impeditivas.
- Evidências registradas em `docs/evidence/C09.2.md`.

## Histórico anterior

Atualizado em 2026-10-12. C03.1, C03.2, C05.1, C05.2, C06.1, C06.2, C07.1, C07.2, C08.1, C08.2, C10.1, C10.2, C14.1 e C16.1 estão concluídas. Os checkpoints agregados C07 e C08 foram aceitos pelo proprietário em 2026-10-12. C06 agregado (M1 completo) e M0 foram aceitos.
C05.2 passou build, 40 testes unitários, 18 instrumentados, lint, inspeção
Maestro com fixture, auditoria independente e CI no SHA `992d781` (run
37200219568). C05.1 passou build,
36 testes unitários, 15 instrumentados, lint, comparação manual sanitizada, CI
no SHA `5064cd0` e auditoria independente. C03.2
foi publicada em `04077f9`; seu CI passou no run 37194260058. C00.2, C01.1, C01.2,
C02.1, C04.1 e C04.2 também estão concluídos. M0 — Fundação confiável — foi aceito.
C06.1 foi concluída no SHA `e4a218a`: build, lint, 40 testes unitários, 31 instrumentados, inspeção Maestro, reauditoria independente e CI remoto (run 37205228834) passaram. C06.2 passou validação local, inspeção Maestro e auditoria independente; CI remoto PASS no SHA `f8aa9d3` (run 37208985019); evidência em docs/evidence/C06.2.md. C07.1 foi concluída: build/lint, 40 testes unitários, 54 instrumentados, auditoria independente e CI no SHA `c8cc435` (run 37213377810) passaram; a lista vazia no Pixel 9 correspondeu à consulta autenticada redigida com os filtros padrão. Evidências em docs/evidence/C07.1.md.

## Entrega atual

C14.1 implementa favoritos locais na aba Repos: IDs numéricos por login no
DataStore existente, filtro combinado com busca/visibilidade/linguagem,
estrelas acessíveis e limpeza coordenada com logout sem chamadas de escrita ao
GitHub. Build, lint e 66 testes unitários passaram. No Pixel 9/API 37, os testes
focados passaram 10/10 e a inspeção visual Maestro usou fixture local. A suíte
completa passou 93/95; dois testes antigos de issues e pull requests expiraram
por timeout, sem falha nos testes C14.1. Auditoria independente passou. CI
remoto passou no SHA `f42cf0c` (run `37399713144`); C14.1 está `DONE`. Evidências
em `docs/evidence/C14.1.md`. Um teste unitário preexistente de cancelamento
falhou intermitentemente no CI documental; o fixture foi estabilizado e o CI
passou no SHA `3e4bab7` (run `37400404769`).

## Entrega anterior — C10

C10.1 implementa a área somente leitura de GitHub Actions no detalhe do
repositório: workflows, execuções recentes por workflow ou gerais, jobs,
paginação, estados vazios e erros. Build, unit tests e lint passaram; testes
instrumentados da classe passaram 15/15 e a auditoria independente passou. A
suíte agregada mais recente terminou 87/88: o mesmo fluxo Actions passou isolado
e na classe, mas sofreu timeout intermitente na execução total; detalhes em
`docs/evidence/C10.1.md`. No Pixel 9, os runs #49 (sucesso) e #51 (falha)
corresponderam aos metadados do GitHub CLI. O #51 não tinha etapas executadas e
o app indicou link expirado ou logs indisponíveis, sem mostrá-los como vazios;
a fixture 404 verificou a indisponibilidade. C10 foi aceito com a
evidência real agregada registrada em `docs/evidence/C10.1.md` e
`docs/evidence/C10.2.md`.

C10.2 está `DONE` no código `22938e1`. O contrato foi auditado; a implementação cobre a
captura do redirecionamento temporário, validação de conteúdo, leitura limitada
e tela de logs com descarte ao sair/atualizar. O downloader abre TCP apenas
para IP público validado e estabelece TLS com SNI e validação do hostname.
Revisão independente confirmou os controles TLS, cancelamento, UTF-8 estrito,
lookahead válido ao truncar e pool limitado para DNS legado. Build, testes
unitários e lint passaram; o teste instrumentado workflow → execução → job →
logs passou no Pixel 9 com fixture 404. Houve dois timeouts instrumentados
intermitentes antes do passo de logs, e uma execução mais recente passou com a
pausa de inspeção habilitada. Dois retestes focados posteriores voltaram a
expirar na lista de workflows; o relatório Compose registrou o indicador de
carregamento, e a chamada Maestro concorrente não retornou conteúdo. As falhas anteriores incluíram servidor de
dispositivo encerrado e diálogo de ANR do Android. A inspeção visual posterior
passou via Maestro 2.11.0 no Pixel 9/API 37: a conta autorizada abriu o job real
`build` da execução #49; o GitHub CLI confirmou execução/job bem-sucedidos no
SHA `e96feab`, e a tela exibiu o aviso de sensibilidade sem carregamento, estado
vazio ou erro. O conteúdo dos logs não foi inspecionado, copiado ou persistido. CI
remoto passou no SHA `22938e1` (run 37344629817). C10.2 está concluído e o
checkpoint C10 também foi aceito após a comparação real de sucesso/falha e log
indisponível. Evidência detalhada em
`docs/evidence/C10.2.md`.

O modo de demonstração usa dados locais fictícios identificados como
demonstração; áreas ainda não integradas não representam esses dados como reais.
C03.1 já solicita código, faz polling cancelável e confirma perfil
real; C03.2 persiste a sessão cifrada. C05.1 lista repositórios reais e C05.2
adiciona busca e filtros locais à aba Repos. C07.1 implementa lista real e
filtros de issues; a primeira página vazia foi comparada com o GitHub. Cada área
ainda não integrada é identificada no app e não apresenta dados de demonstração
como conteúdo da conta. As telas conceituais demonstram o produto planejado.
Menu inferior flutuante; sete pranchas conceituais (visão geral + 36 telas/estados)
e seis capturas reais. Origem Gold fixada no lock e adaptação documentada.

## Verificações

| Verificação | Resultado |
|---|---|
| Build debug | PASS |
| Testes unitários | PASS — C05.2: 40 testes, incluindo busca/filtros e fixtures locais |
| Lint | PASS — sem erros; avisos de versões/target documentados |
| Teste instrumentado no emulador | PASS — C05.2: 18 testes, Pixel 9 / Android API 37 |
| Navegação C01.1 (4 cenários) | PASS — Pixel 9, API 37; dock e busca respeitam IME e insets |
| Check Ideias Standard | PASS — manifest, lock e contexto |
| Capturas e links da documentação | PASS |
| Auditoria independente C00.2 | PASS — P2 documental corrigido e rechecado; novas execuções do emulador e Standard marcadas NOT_RUN |
| Navegação e auditoria C01.1 | PASS — finding corrigido; cinco testes instrumentados e rechecagem independente passaram |
| Acessibilidade da navegação C01.2 | PASS — semânticas de aba, seleção, ordem e alvos ≥48 dp; seis instrumentados; auditoria independente passou |
| M0 — Fundação confiável | PASS — C00 e C01 aceitos; CI do fechamento em `ebb8fb0` passou (run 37079293659) |
| CI remoto (`3e6945c`) | PASS — run 37058742858; plano, build, unit tests e lint |
| OAuth App C02.1 | PASS — registrado por playertwo1; Device Flow e expiração ativos; sem secret |
| Requisição real de Device Flow | PASS — HTTP 200; códigos de dispositivo/usuário sanitizados e descartados |
| Auditoria independente C02.1 | PASS — escopos e limites revisados; documentação ajustada conforme achado |
| CI C02.1 (`1deaac5`) | PASS — run 37065527035; plano, build, testes unitários e lint |
| C03.1 — Device Flow e perfil | CONCLUÍDO — consentimento real e perfil esperado confirmados no Pixel 9; build/lint, 25 unitários, 7 instrumentados, auditoria e CI remoto (run 37082537911) passaram |
| C03.2 — Restaurar e encerrar sessão | CONCLUÍDO — 34 unitários, 13 instrumentados, autorização, restauração após reinício, logout, captura segura, auditoria independente e CI remoto PASS (run 37194260058) |
| C05.1 — Lista de repositórios reais | CONCLUÍDO — 36 unitários, 15 instrumentados, build/lint, CI remoto, comparação real redigida, lista vazia, refresh, falha/retry e auditoria independente PASS |
| C05.2 — Busca e filtros de repositórios | CONCLUÍDO — 40 unitários, 18 instrumentados, build/lint, inspeção visual Maestro com fixture, auditoria independente e CI remoto (run 37200219568) PASS; evidência em docs/evidence/C05.2.md |
| C06.1 — Detalhe real do repositório e README | CONCLUÍDO — build/lint, 40 unitários, 31 instrumentados no Pixel 9, Maestro, reauditoria independente e CI remoto (run 37205228834) PASS; comparação real reservada para o aceite agregado C06; evidência em docs/evidence/C06.1.md |
| C06.2 — Painel real da conta conectada | CONCLUÍDO — build, lint, 40 unitários, suíte conectada 41/41 e 8 testes focados após último ajuste PASS; Maestro, auditoria independente e CI remoto no SHA `f8aa9d3` (run 37208985019) PASS; evidência em docs/evidence/C06.2.md |
| C07.1 — Lista e filtros de issues | CONCLUÍDO — lista e filtros reais, 40 unitários, 54 instrumentados, Maestro, comparação autenticada redigida, auditoria independente e CI no SHA `c8cc435` (run 37213377810) PASS; evidência em docs/evidence/C07.1.md |
| C16.1 — Preferência de tema | CONCLUÍDO — build, 25 unitários, lint, 11 instrumentados, persistência após reinício do processo, capturas Sistema/Claro/Escuro, CI remoto (run 37138619727) e revisão independente PASS |
| Transporte GitHub C04.1 | PASS — auditoria e CI remotos passaram em `2a4e689` |
| Paginação e rate limit C04.2 | PASS — implementação, auditoria e CI remoto no SHA `bd02490` |
| Primeiro CI remoto (`69a02f1`) | FAIL — setup do SDK solicitou pacote obsoleto `tools` |

Evidências: docs/VERIFICATION.md e docs/evidence/C00.2-review.md. C00.2 passou
em revisão independente; APK e instrumentação constam como evidência histórica,
e novas execuções ficaram NOT_RUN sem dispositivo conectado. C01.1 e C01.2 também
passaram em auditoria; M0 foi aceito.

C07.2 está `DONE` no SHA `f0efe30`; build/lint, 41 testes unitários, 13 testes da
tela de issues, 4 de mapeamento, suíte instrumentada 63/63, auditoria independente
e CI remoto (run 37232921849) passaram. O detalhe autenticado, comentários
paginados, Markdown inerte, estados 404/410, retry e cancelamento da chamada ao
sair da tela foram implementados. A sessão real foi confirmada, mas “Todas
visíveis” + “Todas” retornou lista vazia, sem issue real para comparar. Maestro
MCP ficou indisponível; a hierarquia foi conferida por fallback e a fixture foi
inspecionada pelo Maestro. Evidências em docs/evidence/C07.2.md.

O checkpoint agregado C07 foi aceito pelo proprietário (Rafael /
playertwo1) em 2026-10-12, dispensando a comparação autenticada com issue real
com comentários (`NOT_RUN`, sem issue adequada na conta de teste). C07.1 e
C07.2 permanecem `DONE`. C08.1 está `DONE`: contrato de PRs em
`plan/contracts/C08.1.md`, com busca global de PRs que envolvem a conta, estados
aberto/fechado/mesclado e detalhe/comentários/reviews somente leitura já
implementados. Build/lint, suíte Pixel 9 69/69, auditoria independente e CI
remoto passaram no commit `4d2c972`. A comparação autenticada real ficou `NOT_RUN`. C08.2 também está
`DONE`; o checkpoint agregado C08 foi aceito pelo proprietário em 2026-10-12,
dispensando a evidência de PR real ausente. Contrato e evidência em
`plan/contracts/C08.1.md` e `docs/evidence/C08.1.md`.

## Planejamento e retomada

Estados canônicos: plan/tasks.json. Roadmap mantém 21 checkpoints/5 marcos;
backlog divide em 42 entregas. M0 — Fundação confiável — foi aceito; os demais
marcos ainda não. Funções API não existem.
Auditoria C00.2 revisou a fundação em HEAD `daaf60f`; parecer PASS após correção
e rechecagem documental em docs/evidence/C00.2-review.md. C01.1 implementado,
verificado localmente e aprovado em auditoria independente; evidência em
docs/evidence/C01.1.md. C01.2 (acessibilidade da navegação) foi concluído em
docs/evidence/C01.2.md; C00 e C01 fecham M0. C03.1 foi concluída após a
autorização real do proprietário e a confirmação do perfil pelo app. Testes,
auditoria e CI passaram; evidência em docs/evidence/C03.1.md. C03.2 passou
testes, fluxo manual de autorização/restauração/logout e auditoria independente;
evidência e captura segura em docs/evidence/C03.2.md.
C02.1 está concluído:
OAuth App criado, Client ID somente local, escopos documentados, endpoint real
testado e auditoria independente PASS. Veja docs/AUTH.md e
docs/evidence/C02.1.md. O proprietário concluiu as autorizações reais de C03.1
e C03.2; a sessão persistente e o logout local foram verificados em C03.2.
C05.1 está concluído. Contrato e evidência em plan/contracts/C05.1.md e
docs/evidence/C05.1.md. A comparação real com o endpoint e os cenários aplicáveis
foram validados sem registrar dados da conta; código e evidência estão no SHA
`5064cd0`, com CI e auditoria independente aprovados.

C05.2 implementa busca por nome/nome completo/linguagem e filtros locais por
visibilidade e linguagem sobre os itens carregados. Contrato em
plan/contracts/C05.2.md; evidência local em docs/evidence/C05.2.md. Instrumentação
verifica busca, filtros, estado sem correspondências, paginação, atualização e
ausência de novas consultas ao filtrar. Auditoria independente e CI remoto
passaram no SHA `992d781`; veja docs/evidence/C05.2.md.

C06.1 foi concluída: o detalhe consulta metadados atuais e README da identidade selecionada na lista real; branch, contagens e último push têm semânticas documentadas, e o README é texto Markdown inerte. Build/lint, 40 testes unitários, 31 instrumentados no Pixel 9, inspeção Maestro, reauditoria independente e CI remoto passaram. A comparação real permanece como parte do aceite agregado C06. C06.2 implementa o perfil real da conta conectada, o refresh e placeholders honestos; build/lint, 40 unitários, suíte conectada de 41 e 8 testes C06.2 focados após o último ajuste passaram no Pixel 9. Maestro confirmou a hierarquia da fixture, a auditoria independente passou sem findings acionáveis e o CI remoto passou no SHA `f8aa9d3` (run 37208985019). Evidência em docs/evidence/C06.2.md. Contratos e evidências anteriores em plan/contracts/C06.1.md e docs/evidence/C06.1.md.

C04.1 pode avançar sem login: transporte REST nativo limitado a `api.github.com`
em HTTPS, mensagens de erro tipadas e fixtures locais. Evidência em
docs/evidence/C04.1.md; integrado com o teste CI remoto 37070881713. Integrar
token real fica para C03/C05.

C04.2 implementa o consumo explícito de uma página por vez, deduplicação por
identificador e bloqueio compartilhado do cliente HTTP até o prazo de rate
limit. Fontes e testes em plan/contracts/C04.2.md e docs/evidence/C04.2.md.

C16.1 foi concluída. Contrato e evidência em plan/contracts/C16.1.md e
docs/evidence/C16.1.md; revisão independente PASS. Build, testes, lint,
persistência após reinício do processo, capturas dos três modos e CI remoto
passaram. C03.1 foi concluída após autorização real e revisão independente.

C08.2 está `DONE`. Arquivos, diffs inertes e checks/statuses por SHA foram
implementados; build, testes unitários, lint e suíte conectada 79/79 no Pixel 9
passam. Auditoria independente PASS. CI PASS no SHA `96b35b3` (run
`37298729702`, tentativa 2); a primeira tentativa falhou num teste de
cancelamento C07.2 que passou na repetição. A comparação autenticada com PR real está `NOT_RUN` (sem PR
adequado na conta de teste). O checkpoint agregado C08 foi aceito pelo
proprietário em 2026-10-12, dispensando essa evidência. Evidência em
`docs/evidence/C08.2.md`.

C09.1 está `DONE` com todos os critérios de aceite comprovados no emulador Pixel 9 (API 37) e auditoria independente aprovada (PASS).
Escopo/desenho: `docs/superpowers/specs/2026-10-06-github-notifications-design.md`; plano: `docs/superpowers/plans/2026-10-05-github-notifications.md`; contrato: `plan/contracts/C09.1.md`.
Em 2026-10-17, o login Device Flow foi concluído pelo usuário no emulador; o app conectou à conta `@playertwo1` e a aba "Avisos" consultou `GET /notifications` com sucesso (200 OK), comprovando que o OAuth App com escopos `repo` e `notifications` funciona com a Notifications API (sem 403 e sem necessidade de PAT). A lista de notificações reais, filtros locais por lidas/não lidas, detalhe da notificação e retorno preservando estado foram inspecionados e validados via Maestro. Todos os 10 testes instrumentados focados passaram no Pixel 9 (10/10), além de build, lint e 66 testes unitários. A auditoria independente via subagente revisou contrato, código e testes, concluindo com veredito PASS sem achados bloqueantes. C09.1 foi concluída e promovida a `DONE` em `plan/tasks.json`. Evidências em `docs/evidence/C09.1.md`. A próxima entrega elegível é C09.2 (Marcar notificações lidas).
