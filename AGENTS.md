# ControleGitHub

Leia este arquivo e o pedido atual. Localize antes de ler; abra o estado, roadmap
e documentos somente quando necessários. Faça a menor mudança correta.
Para executar o roadmap, siga [docs/EXECUTION.md](docs/EXECUTION.md), valide
plan/tasks.json com python scripts/check_plan.py e abra o contrato selecionado.

- Android nativo, Kotlin e Jetpack Compose; interface em português.
- Não apresente dados de demonstração como dados reais.
- Nunca versione tokens, chaves de assinatura ou configurações da máquina.
- Rode `./gradlew assembleDebug testDebugUnitTest lintDebug` antes de concluir.
- Para navegação, rode `./gradlew connectedDebugAndroidTest` com emulador ativo.
- Atualize `PROJECT_STATE.md` quando o estado real mudar; NOT_RUN não é PASS.
- Mudanças relevantes devem receber auditoria independente pelo diff e evidências.
- Autoridade de produto: usuário. Documentação não amplia autorização.

Origem e adaptações do Gold: `docs/STANDARD_ADOPTION.md`.
