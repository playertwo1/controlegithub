# Estado do projeto

Atualizado em 2026-10-02. Fase F0: implementada e verificada localmente;
auditoria independente pendente.

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

Evidências: `docs/VERIFICATION.md`. CI configurado; resultado remoto deve ser
consultado no GitHub Actions, sem presumir PASS a partir do resultado local.

## Próxima ação

Auditoria independente da F0 e depois F1: autenticação e repositórios reais.
O proprietário precisa configurar um OAuth App antes de testar login real.
