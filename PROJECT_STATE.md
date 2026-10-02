# Estado do projeto

Atualizado em 2026-10-02. Fase F0; integração GitHub ainda não implementada.
Baseline C00.1 verificada; próxima tarefa READY: C00.2, auditoria independente.

## Entrega atual

Protótipo Android nativo com dados locais fictícios. Sem autenticação, rede,
sincronização ou operações no GitHub. As telas demonstram o fluxo do produto.
Menu inferior flutuante; sete pranchas conceituais (visão geral + 36 telas/estados)
e seis capturas reais. Origem Gold fixada no lock e adaptação documentada.

## Verificações

| Verificação | Resultado |
|---|---|
| Build debug | PASS |
| Testes unitários | PASS — 3 testes |
| Lint | PASS — sem erros; avisos de versões/target documentados |
| Teste de navegação no emulador | PASS — 1 teste, Android API 37 |
| Check Ideias Standard | PASS — manifest, lock e contexto |
| Capturas e links da documentação | PASS |
| Auditoria independente C00.2 | READY — contrato e pacote preparados; parecer NOT_RUN |
| Primeiro CI remoto (`69a02f1`) | FAIL — setup do SDK solicitou pacote obsoleto `tools` |

Evidências: docs/VERIFICATION.md. CI PASS no SHA 643757b, run 37050937095;
APK desse run instalado e iniciado no Pixel 9/API 37. Auditoria independente ainda
NOT_RUN; não fechar C00 nem M0 até seu parecer PASS.

## Planejamento e retomada

Estados canônicos: plan/tasks.json. Roadmap mantém 21 checkpoints/5 marcos;
backlog divide em 42 entregas. Nenhum marco foi aceito. Funções API não existem.
Contrato C00.2 pronto para auditor distinto do autor; C01.1 pode avançar após a
baseline, mas M0 não fecha antes do aceite C00.2. Para retomar, executar
python scripts/check_plan.py e seguir docs/EXECUTION.md.
