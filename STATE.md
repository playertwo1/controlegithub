# STATE — ponto de retomada

Qualquer agente deve conseguir retomar só com este arquivo + a seção ativa do `ROADMAP.md`, sem a conversa anterior. Curto: o que não for estado atual sai daqui. Estados canônicos das tarefas: `plan/tasks.json`. Histórico detalhado de verificações: [`docs/archive/PROJECT_STATE.md`](docs/archive/PROJECT_STATE.md) e `docs/evidence/`.

**Atualizado:** 2026-10-17 · **Marco ativo:** M2 — Triagem diária · **Estado:** IN_PROGRESS

## O que funciona hoje (confirmado no código)
- Device Flow, sessão cifrada restaurada após reinício e logout (C03.1, C03.2).
- Perfil, repositórios reais com busca/filtros, detalhe/README e painel da conta (C05, C06).
- Issues e PRs em leitura, incluindo arquivos, diff e checks (C07, C08; aceitos pelo proprietário em 2026-10-12).
- Actions em leitura: workflows, execuções, jobs e logs (C10 aceito); favoritos locais (C14.1); preferência de tema (C16.1).
- `python scripts/check_plan.py` → `PASS: 42 tasks; eligible: C09.1` (rodado em 2026-10-17).

## Última atividade
- ID / commit: `53f6a8b` (2026-10-12) — decisões do proprietário registradas (C07/C08 aceitos; C09.1 desbloqueada).
- Resultado real: C09.1 passou a `READY`, com plano aprovado em `docs/superpowers/plans/2026-10-05-github-notifications.md`.
- Testes executados: `python scripts/check_plan.py` → PASS (2026-10-17). Gradle (`assembleDebug testDebugUnitTest lintDebug`) NOT_RUN nesta sessão; último CI registrado PASS no SHA `3e4bab7` (run `37400404769`).
- Evidência: `docs/evidence/C14.1.md`, `docs/archive/PROJECT_STATE.md`.

## Próxima ação
- C09.1 — caixa de notificações; começar por `plan/contracts/C09.1.md` e o plano `docs/superpowers/plans/2026-10-05-github-notifications.md`; promover a `IN_PROGRESS` em `plan/tasks.json` ao iniciar o código.

## Bloqueios e aprovações humanas
- Se a Notifications API devolver `403` com o token do Device Flow, registrar a falha e reabrir a decisão de autenticação em mudança separada.
- Aceite do marco M1 pelo proprietário não está registrado (todos os checkpoints C00–C06 estão `DONE`).

## Divergências entre roadmap e implementação
- Testes instrumentados têm timeouts intermitentes em suítes completas (C10.1, C10.2, C14.1), registrados em `docs/evidence/`.
- Comparações autenticadas com issue/PR real ficaram `NOT_RUN` (dispensadas pelo proprietário em 2026-10-12).
