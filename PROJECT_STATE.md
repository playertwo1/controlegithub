# Estado do projeto

Atualizado em 2026-10-04. C03.1, C03.2, C05.1, C05.2 e C16.1 estão concluídas.
C05.2 passou build, 40 testes unitários, 18 instrumentados, lint, inspeção
Maestro com fixture, auditoria independente e CI no SHA `992d781` (run
37200219568). C05.1 passou build,
36 testes unitários, 15 instrumentados, lint, comparação manual sanitizada, CI
no SHA `5064cd0` e auditoria independente. C03.2
foi publicada em `04077f9`; seu CI passou no run 37194260058. C00.2, C01.1, C01.2,
C02.1, C04.1 e C04.2 também estão concluídos. M0 — Fundação confiável — foi aceito.
C06.1 foi concluída no SHA `e4a218a`: build, lint, 40 testes unitários, 31 instrumentados, inspeção Maestro, reauditoria independente e CI remoto (run 37205228834) passaram. C06.2 é a tarefa ativa; implementação, validação local e reauditoria independente passaram; CI remoto pendente; evidência em docs/evidence/C06.2.md.

## Entrega atual

Protótipo Android nativo com dados locais fictícios identificados como
demonstração. C03.1 já solicita código, faz polling cancelável e confirma perfil
real; C03.2 persiste a sessão cifrada. C05.1 lista repositórios reais e C05.2
adiciona busca e filtros locais à aba Repos; demais operações seguem marcadas
como demonstração. As telas demonstram o produto.
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
| C06.2 — Painel real da conta conectada | EM ANDAMENTO — build, lint, 40 unitários, suíte conectada 41/41 e 8 testes focados após último ajuste PASS; Maestro e auditoria independente PASS; CI remoto pendente; evidência em docs/evidence/C06.2.md |
| C16.1 — Preferência de tema | CONCLUÍDO — build, 25 unitários, lint, 11 instrumentados, persistência após reinício do processo, capturas Sistema/Claro/Escuro, CI remoto (run 37138619727) e revisão independente PASS |
| Transporte GitHub C04.1 | PASS — auditoria e CI remotos passaram em `2a4e689` |
| Paginação e rate limit C04.2 | PASS — implementação, auditoria e CI remoto no SHA `bd02490` |
| Primeiro CI remoto (`69a02f1`) | FAIL — setup do SDK solicitou pacote obsoleto `tools` |

Evidências: docs/VERIFICATION.md e docs/evidence/C00.2-review.md. C00.2 passou
em revisão independente; APK e instrumentação constam como evidência histórica,
e novas execuções ficaram NOT_RUN sem dispositivo conectado. C01.1 e C01.2 também
passaram em auditoria; M0 foi aceito.

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

C06.1 foi concluída: o detalhe consulta metadados atuais e README da identidade selecionada na lista real; branch, contagens e último push têm semânticas documentadas, e o README é texto Markdown inerte. Build/lint, 40 testes unitários, 31 instrumentados no Pixel 9, inspeção Maestro, reauditoria independente e CI remoto passaram. A comparação real permanece como parte do aceite agregado C06. C06.2 implementa o perfil real da conta conectada, o refresh e placeholders honestos; build/lint, 40 unitários, suíte conectada de 41 e 8 testes C06.2 focados após o último ajuste passaram no Pixel 9. Maestro confirmou a hierarquia da fixture, e a auditoria independente passou sem findings acionáveis. CI remoto pendente; evidência em docs/evidence/C06.2.md. Contratos e evidências anteriores em plan/contracts/C06.1.md e docs/evidence/C06.1.md.

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
