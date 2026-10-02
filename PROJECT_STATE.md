# Estado do projeto

Atualizado em 2026-10-02. Login e uso da API ainda não implementados.
Baseline C00.1 verificada; C00.2, C01.1 e C02.1 aguardam auditoria independente.

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
| OAuth App C02.1 | PASS — registrado por playertwo1; Device Flow e expiração ativos; sem secret |
| Requisição real de Device Flow | PASS — HTTP 200; códigos de dispositivo/usuário sanitizados e descartados |
| Auditoria independente C02.1 | BLOCKED — evidência funcional completa; parecer NOT_RUN |
| CI C02.1 (`1deaac5`) | PASS — run 37065527035; plano, build, testes unitários e lint |
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
individual deve ser selecionada pelo validador do backlog. C02.1 foi configurado:
OAuth App criado, Client ID somente local, escopos documentados e endpoint real
testado. A tarefa aguarda auditoria independente antes de DONE; login pertence a
C03. Veja docs/AUTH.md e docs/evidence/C02.1.md. Nenhum usuário concedeu acesso
ao app e nenhuma sessão autenticada foi criada.
