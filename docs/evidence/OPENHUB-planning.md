# Aproveitamento OpenHub — evidência documental

Data: 2026-10-17. Escopo: detalhar propostas, atualizar documentos/ideias e criar
prompt para Claude; sem implementar funcionalidades ou modificar plan/tasks.json.

## Alterações desta entrega

- docs/IDEIAS.md: 15 ideias OH, vínculo com checkpoints ou classificação futura.
- docs/superpowers/specs/2026-10-17-openhub-aproveitamento-design.md: comportamento,
  dados, integração, cenários CACHE/REFRESH/MD/WRITE/LINK e decisões de refinamento.
- docs/superpowers/plans/2026-10-17-openhub-aproveitamento.md: sequência dependente,
  arquivos, checklist, saídas e gates por entrega.
- docs/prompts/CLAUDE_OPENHUB.md: prompt copiável de implementação de uma entrega,
  com retomada canônica, limites de autenticação e evidências obrigatórias.
- README, ROADMAP, PROJECT_STATE e docs/ARCHITECTURE: referências e correção de
  descrições desatualizadas de Actions/notificações, sem alterar aceite agregado.
- Relatório anterior: links para o detalhamento e distinção entre diffs de PR
  existentes e proposta de navegador geral de código.

## Preservação e limites

Preexistentes: alteração em docs/evidence/C06-real-comparison.md e capturas C06
não rastreadas; não editadas nesta entrega. reports/ e research_notes/ já existiam
como não rastreados da pesquisa anterior. Nenhum arquivo de app, configuração de
build, autenticação ou backlog foi alterado por este trabalho.
Sem commit, push, publicação, nova sessão OAuth ou mutação no GitHub.

## Validação

Ambiente: Windows/PowerShell, JDK 17. Branch `main`, HEAD
`7aaab346d0afd2e8c63423e9e49927b3239e988a`; o conjunto documental está no
working tree, não em commit isolado.

| Comando/checagem | Resultado |
|---|---|
| `python scripts/check_plan.py` | PASS — 42 tarefas; eligible: none; C09.1 permanece IN_PROGRESS |
| `git diff --check` | PASS — sem erros de whitespace; avisos de conversão CRLF/LF em PROJECT_STATE/ROADMAP |
| `.\gradlew.bat assembleDebug testDebugUnitTest lintDebug --console=plain` | PASS — BUILD SUCCESSFUL em 29 s; 53 tarefas, 1 executada e 52 UP-TO-DATE. A tarefa unitária estava UP-TO-DATE; não houve nova execução dos testes |
| `adb devices` | Nenhum dispositivo/emulador conectado |
| `connectedDebugAndroidTest` | NOT_RUN — nenhum dispositivo ativo; alteração somente documental, sem mudança de navegação |
| Runtime/inspeção visual do OpenHub | NOT_RUN — análise de código e documentação |
| Integrações futuras/cache/Markdown | NOT_RUN — propostas, sem código implementado |
| Links locais dos documentos alterados/novos | PASS — 63 referências locais verificadas em 10 documentos; nenhum destino ausente ou whitespace final |
| Auditoria independente pelo diff e documentos novos | PASS documental — sem achados acionáveis; não constitui aprovação de funcionalidades futuras |

## Fontes e rastreabilidade

OpenHub: commit fcb9b422e8013169e2328a6e09c675ce11a27ad6 e referências fixadas nas
[notas](../../research_notes/OpenHub%20para%20o%20ControleGitHub/openhub.md).
Fontes oficiais consultadas em 2026-10-17:
[boas práticas REST](https://docs.github.com/en/rest/using-the-rest-api/best-practices-for-using-the-rest-api),
[Markdown](https://docs.github.com/en/rest/markdown/markdown) e
[notificações](https://docs.github.com/en/rest/activity/notifications).
Documentação de execução deverá ser revalidada na sessão do implementador.

## Auditoria e encerramento

Revisor independente `audit_openhub_docs`, em leitura, examinou diff e documentos
novos, backlog/contrato C09.1 e pontos relevantes do código. Não encontrou
achados acionáveis de escopo, dependências, autenticação/licença ou precisão do
handoff. Confirmou a distinção entre C08.2 e navegador geral de código.
Também executou check_plan, diff --check e conferência independente dos 63
links locais, com os mesmos resultados acima. Não repetiu build nem alterou arquivos.

A checagem de viabilidade para o implementador não encontrou perguntas
importantes além das decisões explicitamente reservadas ao refinamento de cada
contrato: formato/limites/cobertura/validade offline do cache, biblioteca Markdown
e eventual ampliação de escopo do roteador/leitura. Essas decisões não foram
apresentadas como resolvidas ou autorizadas. O plano é de refinamento e sequência,
não um contrato READY de C11.

Esta evidência não fecha tarefas Cxx nem marcos M0–M4. O resultado esperado
desta entrega foi obtido: pacote documental navegável e revisado, com prompt
copiável para o Claude. Os gates de runtime das futuras funcionalidades continuam
pendentes nas respectivas entregas.
