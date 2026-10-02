# Estado do projeto

Atualizado em 2026-10-02. Fase F0, **checkpoint ativo C00 — EM EXECUÇÃO**.
Base implementada e verificada localmente; CI remoto e auditoria inicial pendentes.

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

Evidências: `docs/VERIFICATION.md`. A correção de CI define explicitamente
`packages: platform-tools`; reexecução remota ainda precisa ser confirmada.
O resultado local não substitui o remoto.

## Checkpoints

C00: EM EXECUÇÃO; C01: PARCIAL (protótipo e referências prontos, aceite completo
de navegação/acessibilidade pendente); C02–C20: PLANEJADOS. Marcos M0–M4 ainda
não aceitos. Critérios e dependências: `ROADMAP.md`.

## Próxima ação

Confirmar CI corrigido e realizar auditoria da fundação para fechar C00.
Depois consolidar C01 e configurar OAuth em C02; C04 pode avançar com testes HTTP
independentemente da configuração da conta. C03 depende do OAuth App do proprietário.
