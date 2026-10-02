# Estado do projeto

Atualizado em 2026-10-02. Fase F0; integração GitHub ainda não implementada.
Base verificada localmente e no CI remoto; auditoria independente pendente.

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
| Auditoria independente | NOT_RUN |
| Primeiro CI remoto (`69a02f1`) | FAIL — setup do SDK solicitou pacote obsoleto `tools` |

Evidências: docs/VERIFICATION.md. CI corrigido PASS no SHA 03fe0cd,
run 37037178446. Auditoria da fundação ainda NOT_RUN.

## Planejamento e retomada

Estados canônicos: plan/tasks.json. Roadmap mantém 21 checkpoints/5 marcos;
backlog divide em 42 entregas. Nenhum marco foi aceito. Funções API não existem.
Contratos iniciais delimitam navegação e configuração OAuth. Auditoria pendente
impede M0, mas não preparação independente. Para retomar, executar
python scripts/check_plan.py e seguir docs/EXECUTION.md.
