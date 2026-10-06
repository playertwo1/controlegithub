# Estado do projeto

Atualizado em 2026-10-05. C03.1, C03.2, C05.1, C05.2, C06.1, C06.2, C07.1, C10.1 e C16.1 estão concluídas.
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

O checkpoint agregado C07 segue sem aceite até ser possível comparar uma issue
real com comentários, conforme `ROADMAP.md`; as tarefas C07.1 e C07.2 estão
concluídas individualmente. C08.1 está `DONE`: contrato de PRs em
`plan/contracts/C08.1.md`, com busca global de PRs que envolvem a conta, estados
aberto/fechado/mesclado e detalhe/comentários/reviews somente leitura já
implementados. Build/lint, suíte Pixel 9 69/69, auditoria independente e CI
remoto passaram no commit `4d2c972`. A comparação autenticada real ficou
`NOT_RUN`; C08 agregado segue pendente do PR real com arquivos, estados e
reviews, além de C08.2. Contrato e evidência em `plan/contracts/C08.1.md` e
`docs/evidence/C08.1.md`.

## Planejamento e retomada

Estados canônicos: plan/tasks.json. Roadmap mantém 21 checkpoints/5 marcos;
backlog divide em 42 entregas. Nenhum marco foi aceito. Funções API não existem.
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
cancelamento C07.2 que passou na repetição. A comparação autenticada com PR
real está `NOT_RUN` até haver PR adequado; C08 agregado segue pendente do aceite
real descrito no roadmap. Evidência em `docs/evidence/C08.2.md`.

Em C09.1, Tasks 0–3 ativaram o contrato autorizado, implementaram parsing,
validação de destinos, paginação e a caixa autenticada com filtros, detalhe e
navegação local. A auditoria independente inicial apontou data inválida aceita
e ausência de estado vazio para filtros; ambos foram corrigidos e ganharam
testes. Testes focados no Pixel 9/API 37: parser 5/5, caixa 7/7, navegação 5/5,
Home 8/8 e regressões selecionadas de detalhes passaram. `assembleDebug
testDebugUnitTest lintDebug` passou. O teste real OAuth na aba Avisos retornou
HTTP 200, com itens presentes e renderizados; nenhum conteúdo privado foi
registrado. A suíte conectada completa parou em 84/110 com `IndexOutOfBounds`
em teste existente de detalhe de repositório e queda do runner; a repetição
isolada passou 1/1, mas a suíte completa não conta como PASS. Maestro visualizou
a demonstração sintética; a fixture de Avisos ainda não pode ser carregada na
tela instalada sem adicionar um modo de teste. Reauditoria independente do
código passou sem achados no range `abee9e5..c90879d`. Task 4 segue ativa para
resolver/verificar os gates de dispositivo e registrar o estado final. CI remoto
está `NOT_RUN`.
Plano: `docs/superpowers/plans/2026-10-05-github-notifications.md`; desenho:
`docs/superpowers/specs/2026-10-06-github-notifications-design.md`.
