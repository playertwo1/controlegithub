# Estado do projeto

Atualizado em 2026-10-02. Integração GitHub ainda não implementada.
Baseline C00.1 verificada; C00.2 e C01.1 aguardam auditoria independente; C02.1
aguarda registro do OAuth App e Client ID pelo proprietário.

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
| Navegação C01.1 (4 cenários) | PASS — Pixel 9, API 37; dock dentro da área segura por gestos |
| Check Ideias Standard | PASS — manifest, lock e contexto |
| Capturas e links da documentação | PASS |
| Auditoria independente C00.2 | BLOCKED — revisor independente não disponível; parecer NOT_RUN |
| Auditoria independente C01.1 | BLOCKED — implementação aprovada nos testes locais; parecer NOT_RUN |
| CI remoto (`3e6945c`) | PASS — run 37058742858; plano, build, unit tests e lint |
| Configuração C02.1 sem Client ID | PASS — BuildConfig vazio e indisponibilidade visível no protótipo |
| Requisição real de Device Flow | BLOCKED — Client ID/App ainda não fornecido; resposta real NOT_RUN |
| Primeiro CI remoto (`69a02f1`) | FAIL — setup do SDK solicitou pacote obsoleto `tools` |

Evidências: docs/VERIFICATION.md. CI PASS no SHA 643757b, run 37050937095;
APK desse run instalado e iniciado no Pixel 9/API 37. Auditoria independente ainda
NOT_RUN; não fechar C00 nem M0 até seu parecer PASS.

## Planejamento e retomada

Estados canônicos: plan/tasks.json. Roadmap mantém 21 checkpoints/5 marcos;
backlog divide em 42 entregas. Nenhum marco foi aceito. Funções API não existem.
Contrato C00.2 pronto para auditor distinto do autor. C01.1 implementado e
verificado localmente; evidência em docs/evidence/C01.1.md. Ambos aguardam parecer
independente, então M0 continua aberto. A próxima tarefa elegível por dependência
individual deve ser selecionada pelo validador do backlog. C02.1 tem guia,
propriedade local Gradle e estado sem configuração explícito; falta o proprietário
registrar OAuth App com Device Flow e fornecer o Client ID público para testar o
endpoint. Veja docs/AUTH.md e docs/evidence/C02.1.md. Login não foi implementado.
