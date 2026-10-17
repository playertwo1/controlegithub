# Ideias e referências de evolução

Atualizado em 2026-10-17. Este registro organiza propostas; não contém estados
de execução. IDs, dependências e estados continuam em [tasks.json](../plan/tasks.json).
Uma ideia registrada não autoriza implementação, cópia de fonte, publicação
ou alteração de autenticação. O usuário decide o produto.

## Referência OpenHub

Origem: [ThirtyDegreesRay/OpenHub](https://github.com/ThirtyDegreesRay/OpenHub).
Pesquisa: [relatório](../reports/OpenHub%20para%20o%20ControleGitHub.md).
Detalhamento: [especificação](superpowers/specs/2026-10-17-openhub-aproveitamento-design.md).
Sequência: [plano](superpowers/plans/2026-10-17-openhub-aproveitamento.md).
Handoff: [prompt para Claude](prompts/CLAUDE_OPENHUB.md).

| Ideia | Benefício e fronteira | Encaminhamento |
|---|---|---|
| OH-01 — Cache privado de leituras | Consultar conteúdo já carregado após reinício e sem rede, com origem e horário; nunca mostrar demo como cache | Concluído em C11.1; base estabelecida para C11.2 |
| OH-02 — Refresh e reconexão | Preservar conteúdo útil no erro, revalidar sem duplicar e respeitar rate limit | Concluído em C11.2; conclui M2 (Checkpoint C11) |
| OH-03 — Markdown de leitura | Formatar README, descrições e comentários com links controlados e fallback textual | Proposta de evolução dos fluxos de leitura; não reabrir C06/C07/C08 automaticamente |
| OH-04 — Editor e prévia local | Escrever issues/comentários com atalhos e prévia sem transmitir rascunho para renderização | C12.1/C12.2/C13.1; revisão posicional permanece em C13.2 |
| OH-05 — Links para destinos nativos | Reaproveitar navegação existente para repo, issue e PR; fallback explícito | C09.1 apenas no escopo contratado; roteador geral exige refinamento próprio |
| OH-06 — Ergonomia dos diffs | Conferir arquivos renomeados/removidos, patch ausente/truncado e mudança de SHA | Checklist de regressão de C08.2/C13.2, sem presumir defeito existente |
| OH-07 — Navegador de código por branch | Consultar árvore do repositório, breadcrumbs, diretórios e arquivos | Ideia futura sem tarefa; não confundir com arquivos alterados de um PR em C08.2 |
| OH-08 — Histórico recente local | Retomar repositórios visitados, sem alterar stars/watch no GitHub | Futuro; definir retenção, limpeza no logout e decisão de produto antes de criar tarefa |
| OH-09 — Economia de dados | Controlar imagens remotas e preferência de rede | Avaliar com C15.2/C16; não carregar imagens de Markdown automaticamente |
| OH-10 — Releases e downloads | Consultar versões e baixar um artefato escolhido | Futuro; requer limite de tamanho, armazenamento e tratamento seguro de links/downloads |
| OH-11 — Descoberta pública | Busca remota de repos/usuários, perfis, organizações, tópicos e trending | Futuro; não confundir busca local C05.2 com busca global; scraping não aprovado |
| OH-12 — Multiaccount | Alternar contas com isolamento completo | Pós-1.0 conforme roadmap; cache por conta não autoriza seletor de contas |
| OH-13 — Ações sociais | Star, watch, fork, follow/unfollow | Futuro; escrita remota distinta dos favoritos locais C14.1 |
| OH-14 — Atalhos Android | Abrir destinos frequentes pelo launcher | Futuro; validar sessão e destino, sem vazar metadados privados no launcher |
| OH-15 — Temas e favoritos | Comparar acessibilidade e persistência | Já existem C16.1/C14.1; manter, sem nova entrega duplicada |

## Regras de triagem

1. Priorizar a retomada da tarefa ativa conforme EXECUTION; a numeração OH
   identifica ideias, não altera a ordem das tarefas Cxx.
2. Para cada ideia selecionada, registrar usuário beneficiado, cenário de uso,
   escopo, tarefas afetadas, dados persistidos, falhas e prova observável.
3. Não transformar todas as ideias em tarefas READY. Refinar somente o próximo
   grupo executável e preservar os IDs existentes.
4. Não copiar código, recursos visuais ou traduções do OpenHub sem decisão
   específica de licença e atribuições. Comportamentos serão implementados
   independentemente na arquitetura Kotlin/Compose existente.
5. Kanban, resumo diário, Actions e logs pertencem ao roadmap próprio; esta
   pesquisa não comprova que o OpenHub os ofereça ou os resolva.
