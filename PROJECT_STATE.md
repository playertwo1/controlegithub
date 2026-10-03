# Estado do projeto

Atualizado em 2026-10-03. C03.1 está bloqueado somente pela autorização real do
proprietário, explicitamente adiada por ele; implementação, testes, auditoria e
CI passaram. C16.1 está em execução para tema claro/escuro/sistema, independente
da autorização. C00.2,
C01.1, C01.2, C02.1, C04.1 e C04.2 estão concluídos. M0 — Fundação confiável —
foi aceito.

## Entrega atual

Protótipo Android nativo com dados locais fictícios. C03.1 já solicita código,
faz polling cancelável e confirma perfil real; repositórios e operações ainda
usam demonstração, sem persistência de sessão. As telas demonstram o produto.
Menu inferior flutuante; sete pranchas conceituais (visão geral + 36 telas/estados)
e seis capturas reais. Origem Gold fixada no lock e adaptação documentada.

## Verificações

| Verificação | Resultado |
|---|---|
| Build debug | PASS |
| Testes unitários | PASS — 25 testes, incluindo fixtures locais OAuth e orquestração |
| Lint | PASS — sem erros; avisos de versões/target documentados |
| Teste instrumentado no emulador | PASS — 7 testes, Android API 37; inclui perfil OAuth |
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
| C03.1 — Device Flow e perfil | BLOQUEADO — implementação, 25 unitários, 7 instrumentados, build/lint, auditoria e CI remoto (run 37082537911) passaram; autorização real adiada pelo proprietário |
| C16.1 — Preferência de tema | EM EXECUÇÃO — build, 25 unitários, lint e 11 instrumentados passaram no Pixel 9/API 37; captura visual e auditoria independente pendentes |
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
docs/evidence/C01.2.md; C00 e C01 fecham M0. C03.1 está bloqueada pela
autorização real que o proprietário decidiu deixar para depois; auditoria
independente, testes determinísticos e CI já passaram. Contrato e evidência
parcial em plan/contracts/C03.1.md e docs/evidence/C03.1.md. Retomar o aceite
real quando o proprietário autorizar.
C02.1 está concluído:
OAuth App criado, Client ID somente local, escopos documentados, endpoint real
testado e auditoria independente PASS. Veja docs/AUTH.md e
docs/evidence/C02.1.md. Nenhum usuário concedeu acesso ao app antes de C03.1;
a prova real desta entrega ainda não ocorreu.

C04.1 pode avançar sem login: transporte REST nativo limitado a `api.github.com`
em HTTPS, mensagens de erro tipadas e fixtures locais. Evidência em
docs/evidence/C04.1.md; integrado com o teste CI remoto 37070881713. Integrar
token real fica para C03/C05.

C04.2 implementa o consumo explícito de uma página por vez, deduplicação por
identificador e bloqueio compartilhado do cliente HTTP até o prazo de rate
limit. Fontes e testes em plan/contracts/C04.2.md e docs/evidence/C04.2.md.

C16.1 é a tarefa ativa, com contrato em plan/contracts/C16.1.md. C01.2 já
concluída é sua única pré-condição; C03.1 fica pendente sem impedir esta entrega.
Implementação e verificações locais estão prontas. Ainda faltam captura visual
confiável do emulador e auditoria independente; a tarefa continua IN_PROGRESS.
