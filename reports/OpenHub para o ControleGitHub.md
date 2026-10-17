# OpenHub orienta melhorias concretas no ControleGitHub

Atualização documental de 2026-10-17: consulte o [registro de ideias](../docs/IDEIAS.md),
o [detalhamento](../docs/superpowers/specs/2026-10-17-openhub-aproveitamento-design.md),
o [plano](../docs/superpowers/plans/2026-10-17-openhub-aproveitamento.md) e o
[prompt para Claude](../docs/prompts/CLAUDE_OPENHUB.md).

O OpenHub é uma boa referência para **cache offline, navegação por links GitHub e edição de Markdown** no ControleGitHub. O maior ganho está nos comportamentos e nos casos de validação que seu código torna visíveis. Nossa base já usa Kotlin, Jetpack Compose, Device Flow e sessão criptografada; aproveitar o OpenHub não exige trocar essa estrutura. Sua implementação Java/MVP e suas dependências antigas reduzem a vantagem de importar componentes diretamente. Além disso, a licença GPL-3.0 exige avaliar as condições aplicáveis antes de incorporar fonte ou assets. A recomendação é estudar os fluxos e implementá-los na arquitetura atual, priorizando os contratos já previstos no roadmap.

## Cache e atualização oferecem o melhor aproveitamento imediato

O cliente HTTP do OpenHub distingue cache por login, força leitura de cache offline e permite forçar a rede durante uma atualização. Isso demonstra uma estratégia implementada, embora seu funcionamento não tenha sido executado nesta pesquisa. O código também reescreve políticas de cache, detalhe que não deve ser transportado sem revisão para respostas autenticadas. ([AppRetrofit.java](https://github.com/ThirtyDegreesRay/OpenHub/blob/fcb9b422e8013169e2328a6e09c675ce11a27ad6/app/src/main/java/com/thirtydegreesray/openhub/http/core/AppRetrofit.java))

No ControleGitHub, **C11.1, cache, e C11.2, atualização, continuam planejados**. O aproveitamento concreto é transformar essa referência em critérios: identificar conteúdo desatualizado, manter dados úteis quando a rede falhar, buscar dados novos ao atualizar e impedir que uma sessão veja conteúdo privado de outra. Logout e troca de credenciais precisam de comportamento explícito para o cache. Isso cabe na sessão única da v1; não depende de adicionar múltiplas contas.

## Links e Markdown completam os fluxos de leitura e ação

O `AppOpener` reconhece URLs de perfis, repositórios, issues, releases e commits, abrindo telas nativas e usando navegador como alternativa. Essa abordagem serve para links vindos de notificações e conteúdo Markdown, com validação do host e fallback para rotas desconhecidas. ([AppOpener.java](https://github.com/ThirtyDegreesRay/OpenHub/blob/fcb9b422e8013169e2328a6e09c675ce11a27ad6/app/src/main/java/com/thirtydegreesray/openhub/util/AppOpener.java))

O editor oferece atalhos de formatação e prévia. Existe, porém, um `FIXME` sobre exceção ao clicar em links da prévia, razão suficiente para tratar o código como referência a revisar. ([Editor](https://github.com/ThirtyDegreesRay/OpenHub/blob/fcb9b422e8013169e2328a6e09c675ce11a27ad6/app/src/main/java/com/thirtydegreesray/openhub/ui/fragment/MarkdownEditorFragment.java), [prévia](https://github.com/ThirtyDegreesRay/OpenHub/blob/fcb9b422e8013169e2328a6e09c675ce11a27ad6/app/src/main/java/com/thirtydegreesray/openhub/ui/fragment/MarkdownPreviewFragment.java))

Hoje, o Markdown de issues permanece inerte e o README do repositório aparece como texto simples no projeto local. Uma renderização com links controlados melhoraria a leitura. Para os contratos de escrita C12/C13, ainda planejados, vale adotar composição com prévia local, preservação de rascunho e envio explícito. A caixa de notificações C09.1 está em andamento, e marcar como lida C09.2 permanece planejado; esses estados delimitam a sequência sem ampliar automaticamente o escopo.

## Arquivos e diff servem como revisão de ergonomia

O navegador de arquivos mantém branch e caminho, oferece breadcrumbs, volta ao diretório pai e reinicia o caminho ao trocar de branch. Também ordena diretórios antes de arquivos. ([RepoFilesPresenter.java](https://github.com/ThirtyDegreesRay/OpenHub/blob/fcb9b422e8013169e2328a6e09c675ce11a27ad6/app/src/main/java/com/thirtydegreesray/openhub/mvp/presenter/RepoFilesPresenter.java)) O gerador de HTML trata links relativos e numeração de diff, mas declara reaproveitamento de código do Kosh. ([HtmlHelper.java](https://github.com/ThirtyDegreesRay/OpenHub/blob/fcb9b422e8013169e2328a6e09c675ce11a27ad6/app/src/main/java/com/thirtydegreesray/openhub/ui/widget/webview/HtmlHelper.java))

**C08.2, arquivos/diff/checks de PR, já está concluído**, assim como favoritos locais C14.1 e temas C16.1. Arquivo removido/renomeado, patch ausente e mudança de SHA servem como casos de regressão dos diffs existentes. Branch contendo `/`, caminhos profundos e troca de branch pertencem à ideia separada de navegador geral de código, que C08.2 não implementa. Esses exemplos não comprovam falhas atuais. O estado foi conferido no backlog e no contrato; o README foi atualizado nesta rodada documental.

## A idade técnica e a licença favorecem implementação própria

O código consultado usa Android Support, Java 8, ButterKnife, GreenDAO, RxJava e SDK 29. A raiz usa AGP 4.1.0 e jcenter. Isso contrasta com a base local Kotlin/Compose, SDK mínimo 26, target 36 e Java 17. ([Dependências](https://github.com/ThirtyDegreesRay/OpenHub/blob/fcb9b422e8013169e2328a6e09c675ce11a27ad6/app/build.gradle), [configuração](https://github.com/ThirtyDegreesRay/OpenHub/blob/fcb9b422e8013169e2328a6e09c675ce11a27ad6/gradle.properties), [build raiz](https://github.com/ThirtyDegreesRay/OpenHub/blob/fcb9b422e8013169e2328a6e09c675ce11a27ad6/build.gradle)) A última release consultada é v3.2.1, de março de 2021; isso não prova incompatibilidade funcional, mas limita seu valor como base moderna. ([Release](https://github.com/ThirtyDegreesRay/OpenHub/releases/tag/v3.2.1))

O projeto declara **GPL-3.0** e menciona 13 arquivos com trechos de FastHub/Kosh. Copiar ou adaptar código requer avaliar licença e atribuições; traduzir Java para Kotlin não resolve essa questão. Estudar comportamentos não equivale, por si só, a incorporar código ao ControleGitHub. Esta análise não determina a compatibilidade jurídica de uma eventual incorporação. ([Licença](https://github.com/ThirtyDegreesRay/OpenHub/blob/master/LICENSE), [atribuições](https://github.com/ThirtyDegreesRay/OpenHub#license))

## Conclusão

A decisão mais útil é usar o OpenHub para tornar C11 verificável e orientar a futura experiência de Markdown, respeitando as dependências C09.1/C09.2 antes do cache. Uma comparação posterior no emulador pode revelar detalhes de interação que a leitura de fonte não captura. **Execução do OpenHub e comparação visual: NOT_RUN.** Este relatório registra análise de fonte, sem importar código. A rodada posterior atualizou documentação e referências do roadmap; verificações locais estão em [OPENHUB-planning](../docs/evidence/OPENHUB-planning.md), sem promover tarefas.
