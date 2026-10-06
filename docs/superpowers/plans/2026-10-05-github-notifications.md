# Caixa de notificações do GitHub — plano de implementação

> **Para agentes de execução:** SUB-SKILL OBRIGATÓRIA: use `superpowers:subagent-driven-development` (recomendado) ou `superpowers:executing-plans` para executar cada tarefa. Acompanhe o progresso com caixas `- [ ]`.

**Objetivo:** Implementar e validar C09.1 com notificações paginadas, filtros, detalhe e navegação segura, tratando rejeição OAuth como falha explícita em vez de caixa vazia.

**Arquitetura:** Acrescentar parser, destinos validados e pager ao padrão REST já existente; compor lista/detalhe/origem em uma pilha local à aba Avisos e reutilizar as telas atuais de issue, pull request e repositório. O token OAuth Device Flow atual é testado antes de considerar o fluxo funcional; `403` deve ser explícito e não pode parecer uma caixa vazia.

**Tecnologias:** Kotlin, Jetpack Compose, `org.json`, `GitHubHttpClient`, `GitHubPaginator`, coroutines, JUnit e testes Compose instrumentados existentes.

**Especificação:** `docs/superpowers/specs/2026-10-06-github-notifications-design.md`

## Restrições globais

- Android nativo em Kotlin e Jetpack Compose; interface e mensagens em português.
- Usar o token do OAuth App obtido por Device Flow, solicitado com escopo `notifications`; não adicionar PAT, segredo ou nova dependência.
- Fazer somente GET em C09.1; marcar lida permanece em C09.2.
- Nunca tratar falha ou resposta malformada como lista vazia, nunca seguir URL arbitrária recebida da API e nunca registrar token ou conteúdo privado.
- Dados de teste e inspeção Maestro são sintéticos e identificados como fixtures; não apresentar dados de demonstração como dados reais.
- `401` usa o tratamento compartilhado de sessão expirada; `403` informa que OAuth não foi aceito e mantém ativa a sessão do restante do app.
- Em logout ou mudança de conta, limpar estado local da conta anterior.
- Antes de concluir: `python scripts/check_plan.py`, `git diff --check`, `./gradlew.bat assembleDebug testDebugUnitTest lintDebug --console=plain`; testar navegação com `./gradlew.bat connectedDebugAndroidTest --console=plain` no Pixel 9/API 37.
- Atualizar contrato, backlog, estado e evidência; executar auditoria independente do diff e registrar SHA e resultados. NOT_RUN não é PASS.

## Foco de revisão

- URL hostil, malformada ou inconsistente com `repository.full_name`: destino indisponível; nenhum link externo/API arbitrário é seguido.
- JSON incompleto, item inválido ou tipo desconhecido: rejeitar resposta malformada ou mostrar fallback “Outro” conforme a validade do item, sem crash nem dados parciais.
- `403` OAuth versus `429` rate limit: mensagem correta para cada caso, sem estado vazio enganoso e com sessão preservada.
- Troca de conta ou logout enquanto detalhes estão abertos: conteúdo da conta anterior não permanece visível.
- Duas páginas com o mesmo ID, filtros combinados e retorno do detalhe: sem duplicatas e com filtros, itens e posição da lista preservados.

---

## Mapa de arquivos

- Criar `app/src/main/java/com/playertwo/controlegithub/GitHubNotifications.kt`: modelos, decodificação JSON, validação do destino e pager que compõe `GitHubPaginator`.
- Criar `app/src/main/java/com/playertwo/controlegithub/GitHubNotificationsScreen.kt`: estados da caixa, filtros, detalhe da notificação, roteamento local e reutilização das telas existentes.
- Modificar `app/src/main/java/com/playertwo/controlegithub/MainActivity.kt`: substituir o placeholder autenticado de Avisos pela nova tela, preservando a navegação principal.
- Criar `app/src/test/java/com/playertwo/controlegithub/GitHubNotificationsTest.kt`: parser, tipos/motivos, validação e construção do destino.
- Criar `app/src/androidTest/java/com/playertwo/controlegithub/GitHubNotificationsParserTest.kt`: parser executado com o `org.json` do Android.
- Criar `app/src/androidTest/java/com/playertwo/controlegithub/GitHubNotificationPagerTest.kt`: endpoint inicial, paginação, deduplicação e respostas de falha por servidor local.
- Criar `app/src/androidTest/java/com/playertwo/controlegithub/GitHubNotificationsScreenTest.kt`: filtros, estados visíveis, stack local, retorno e limites de sessão usando fixture HTTP local sintética.
- Modificar `plan/contracts/C09.1.md`, `plan/tasks.json`, `PROJECT_STATE.md` e criar `docs/evidence/C09.1.md` ao executar e fechar a entrega. Remover o bloqueio anterior somente após registrar a decisão do proprietário e atualizar o contrato; marcar DONE apenas com todos os gates comprovados.

## Tarefas

### Task 0: Ativar o contrato antes de iniciar implementação

**Arquivos:** modificar `plan/contracts/C09.1.md`, `plan/tasks.json`, `PROJECT_STATE.md`.

**Interfaces:** manter o ID `C09.1` e seus critérios de escopo. O consentimento já registrado pelo proprietário substitui o bloqueio anterior que exigia compatibilidade OAuth documentada: o plano aprovado agora testa empiricamente o token OAuth atual e para com erro explícito se receber `403`.

- [x] **Passo 1: revisar os textos atuais do contrato e do bloqueio.** Confirmar que somente a antiga exigência de compatibilidade oficial está sendo removida; manter o escopo somente leitura e a regra contra exibir falha como lista vazia.
- [x] **Passo 2: atualizar contrato e backlog para início autorizado.** Reescrever pré-condições/verificação/aceite em `plan/contracts/C09.1.md`; alterar C09.1 de `BLOCKED` para `IN_PROGRESS` e limpar `blocked_reason` em `plan/tasks.json` porque a decisão foi revista e este plano já terá aprovação do proprietário.

```json
{
  "id": "C09.1",
  "status": "IN_PROGRESS",
  "blocked_reason": null,
  "contract": "plan/contracts/C09.1.md"
}
```

- [x] **Passo 3: registrar a retomada no estado.** Atualizar a seção C09.1 de `PROJECT_STATE.md` para citar o plano aprovado e a tarefa ativa; não registrar testes ainda não executados como PASS.
- [x] **Passo 4: validar contrato e backlog antes do código.** Rode `python scripts/check_plan.py` e `git diff --check`. Esperado: estrutura do backlog PASS e somente C09.1 IN_PROGRESS.
- [x] **Passo 5: commit.** `git add plan/contracts/C09.1.md plan/tasks.json PROJECT_STATE.md; git commit -m "docs: resume GitHub notifications task"`.

### Task 1: Modelos, parser e destino seguro

**Arquivos:** criar `GitHubNotifications.kt`; criar `GitHubNotificationsTest.kt` e `GitHubNotificationsParserTest.kt`.

**Interfaces:** `parseGitHubNotifications(json: String): List<GitHubNotification>`; `notificationDestination(item: GitHubNotification): GitHubNotificationDestination`; `notificationTypeLabel(item: GitHubNotification): String`; `notificationReasonLabel(reason: String): String`. `GitHubNotification` contém `id: String`, `unread: Boolean`, `reason: String`, `title: String?`, `subjectType: String?`, `repository: GitHubRepository`, `updatedAt: Instant?` e `subjectApiUrl: String?`. Reuse `parseGitHubRepositories` para validar e mapear o objeto `repository`; não duplique seu parser. O destino é selado: `Issue(repository: String, number: Int)`, `PullRequest(repository: String, number: Int)`, `Repository(repository: String)` ou `Unavailable`. `Repository` é usado quando `subject.type` é `Repository` ou ausente; tipos desconhecidos levam a `Unavailable`.

Helper unitário: `fixtureNotification(id: String = "1", type: String? = "Issue", url: String? = "https://api.github.com/repos/acme/app/issues/12"): GitHubNotification`; os demais campos usam `unread=true`, `reason="assign"`, `title="Fixture title"`, `repository=GitHubRepository(1, "app", "acme/app", "acme", false, null, null, 0)` e `updatedAt=Instant.parse("2026-10-01T12:00:00Z")`. Em `GitHubNotificationsParserTest`, definir `fixtureThreads(vararg notificationJson: String): String` para agrupar JSON sintético em array; IDs de fixture seguem o exemplo oficial, como string numérica; testar o parser no Android instrumentado porque a implementação usa o `org.json` do sistema.

- [x] **Passo 1: escrever testes que falham.** Em `GitHubNotificationsTest`, cobrir rótulo de tipo/motivo e destinos válidos/inválidos. Em `GitHubNotificationsParserTest`, cobrir campos da thread, array vazio, JSON/item inválido e tipos desconhecidos usando JSON literal sintético. Casos de URL inválida devem incluir host diferente, HTTP, porta, credenciais, query, fragmento, repo divergente, número zero e caminho fora de `/issues/{n}` ou `/pulls/{n}`.

```kotlin
@Test fun pullRequestDestinationAcceptsOnlyValidatedApiPath() {
    val item = fixtureNotification(type = "PullRequest", url = "https://api.github.com/repos/acme/app/pulls/12")
    assertEquals(GitHubNotificationDestination.PullRequest("acme/app", 12), notificationDestination(item))
}

@Test fun destinationRejectsDifferentHostAndRepository() {
    assertEquals(GitHubNotificationDestination.Unavailable, notificationDestination(fixtureNotification(url = "https://example.com/repos/acme/app/issues/12")))
    assertEquals(GitHubNotificationDestination.Unavailable, notificationDestination(fixtureNotification(url = "https://api.github.com/repos/other/app/issues/12")))
}
```

- [x] **Passo 2: rodar testes novos antes da implementação.** Rode `./gradlew.bat testDebugUnitTest --tests 'com.playertwo.controlegithub.GitHubNotificationsTest' --console=plain` e `./gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.playertwo.controlegithub.GitHubNotificationsParserTest' --console=plain` no Pixel 9/API 37. Esperado: ambos falham na compilação por faltar o modelo/parser.
- [x] **Passo 3: implementar modelos, parser e validação.** Parsear cada thread e seus campos obrigatórios sem retornar metadados parciais; manter strings `reason`/`subject.type` desconhecidas para fallback de UI. Validar URI HTTPS, host exatamente `api.github.com`, porta ausente, sem credenciais/query/fragmento, repo idêntico ao `repository.full_name` sem diferença de caixa e número decimal positivo dentro de `Int`.

```kotlin
internal sealed interface GitHubNotificationDestination {
    data class Issue(val repository: String, val number: Int) : GitHubNotificationDestination
    data class PullRequest(val repository: String, val number: Int) : GitHubNotificationDestination
    data class Repository(val repository: String) : GitHubNotificationDestination
    data object Unavailable : GitHubNotificationDestination
}
```

- [x] **Passo 4: rodar testes unitários e parser Android.** Rode o comando JVM acima e `./gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.playertwo.controlegithub.GitHubNotificationsParserTest' --console=plain` com Pixel 9/API 37. Esperado: destinos/labels e parsing instrumentado passam.
- [x] **Passo 5: commit.** `git add app/src/main/java/com/playertwo/controlegithub/GitHubNotifications.kt app/src/test/java/com/playertwo/controlegithub/GitHubNotificationsTest.kt app/src/androidTest/java/com/playertwo/controlegithub/GitHubNotificationsParserTest.kt; git commit -m "feat: parse GitHub notifications safely"`.

### Task 2: Pager de threads

**Arquivos:** modificar `GitHubNotifications.kt`; criar `app/src/androidTest/java/com/playertwo/controlegithub/GitHubNotificationPagerTest.kt`.

**Interfaces:** `GitHubNotificationPager(client: GitHubHttpClient, accessToken: String)`; `loadNext(): GitHubPageResult<GitHubNotification>`; `cancel()`. Compor `GitHubPaginator` usando `GET /notifications?all=true&per_page=50`, `parseGitHubNotifications` e `GitHubNotification::id`. Helper instrumentado: `NotificationApi(initialStatus: Int = 200, pages: List<String>, nextLink: String? = null): AutoCloseable`, com `baseUri: URI` e `requests: List<String>`; implementar com `ServerSocket` local e fechar servidor/thread em `close()`.

- [x] **Passo 1: escrever testes instrumentados com servidor HTTP local.** Verificar caminho e bearer fictício, primeira página, `Link` para página seguinte, deduplicação por ID, `401`/`403`/erro de servidor como `GitHubPageResult.Failed`, `429` como `RateLimited` e JSON inválido como falha de decode com mensagem explícita. Não incluir token real nem dados de conta.

```kotlin
@Test fun pagerRequestsAllNotificationsAndDeduplicatesAcrossPages() {
    NotificationApi(pages = listOf(fixtureThreads(fixtureNotificationJson()), fixtureThreads(fixtureNotificationJson())), nextLink = "<page-2>; rel=\"next\"").use { api ->
        val pager = GitHubNotificationPager(GitHubHttpClient(api.baseUri), "fixture-token")
        assertEquals(1, pager.loadNext().items.size)
        assertEquals(1, pager.loadNext().items.size)
        assertTrue(api.requests.first().startsWith("GET /notifications?all=true&per_page=50"))
    }
}
```

- [x] **Passo 2: executar o teste instrumentado novo para confirmar que falha.** Rode `./gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.playertwo.controlegithub.GitHubNotificationPagerTest' --console=plain` no Pixel 9/API 37. Esperado: FAIL de compilação antes do pager.
- [x] **Passo 3: implementar pager sobre `GitHubPaginator`.** Não criar parser de paginação próprio; propagar `GitHubPageResult` existente e cancelar chamada ativa ao sair da tela.
- [x] **Passo 4: executar o pager instrumentado.** Rode `./gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.playertwo.controlegithub.GitHubNotificationPagerTest' --console=plain` no Pixel 9/API 37. Esperado: PASS, incluindo falha/retry e nenhuma duplicata.
- [x] **Passo 5: commit.** `git add app/src/main/java/com/playertwo/controlegithub/GitHubNotifications.kt app/src/androidTest/java/com/playertwo/controlegithub/GitHubNotificationPagerTest.kt; git commit -m "feat: page GitHub notifications"`.

### Task 3: Caixa e pilha local de navegação

**Arquivos:** criar `GitHubNotificationsScreen.kt`; modificar `MainActivity.kt`; criar `GitHubNotificationsScreenTest.kt`; ajustar `GitHubHomeScreenTest.kt` para a caixa autenticada.

**Interfaces:** `GitHubNotificationsScreen(modifier: Modifier, client: GitHubHttpClient, session: GitHubSession?, sessionRestoring: Boolean, onSessionExpired: (GitHubSession) -> Unit, onAppearance: () -> Unit)`. A composição recebe o estado da sessão como as telas existentes. Estado de lista/pager é lembrado pelo login/token; manter a lista composta sob uma camada de detalhe/origem para conservar pager, filtros e posição. Mudar de conta descarta o estado anterior. Para repositório, usar `notification.repository` já validado; para issue/PR, passar ao detalhe existente somente identidade validada (repo e número), sem renderizar campos de estado/descrição inventados.

Helpers instrumentados: `fixtureSession(login: String = "fixture-user"): GitHubSession`; `NotificationsScreenApi(initialStatus: Int = 200, notificationsJson: String, originResponses: Map<String, String> = emptyMap()): AutoCloseable`, expondo `baseUri: URI`, `requests: List<String>`, `responseStatus: Int` e `notificationsJson: String` mutáveis para testes de retry/troca de conta; `showNotifications(api: NotificationsScreenApi, session: GitHubSession = fixtureSession(), onSessionExpired: (GitHubSession) -> Unit = {})` instala a tela em `createComposeRule()` usando `GitHubHttpClient(api.baseUri)`. JSON de lista deve ser produzido por `fixtureThreads(fixtureNotificationJson(...))`, helper da Tarefa 1.

- [x] **Passo 1: escrever testes Compose contra fixture HTTP local.** Testar lista vazia somente após HTTP 200 válido, lista com lida/não lida e tipos conhecidos/desconhecidos, filtros combinados, retry, `403` explícito, `401` compartilhado, detalhe, abrir origem, voltar origem → detalhe → lista e logout/troca de usuário sem conteúdo antigo. Validar IDs semânticos de controles da mesma forma que telas vizinhas.

```kotlin
@Test fun forbiddenOAuthIsExplicitAndNeverRendersEmptyState() {
    NotificationsScreenApi(initialStatus = 403, notificationsJson = "[]").use { api ->
        showNotifications(api)
        compose.waitUntil(10_000) { api.requests.size == 1 && compose.onAllNodesWithText("O GitHub não aceitou o acesso OAuth às notificações.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Nenhuma notificação.").assertDoesNotExist()
    }
}
```

- [x] **Passo 2: rodar o teste Compose de `403` antes da tela.** Rode `./gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.playertwo.controlegithub.GitHubNotificationsScreenTest' --console=plain` com Pixel 9/API 37 ativo. Esperado: a tela/teste ainda não compila.
- [x] **Passo 3: implementar lista e detalhe da notificação.** Usar `rememberLazyListState`, filtros locais combináveis (estado: Todas/Não lidas/Lidas; tipo: Todos/Issue/Pull request/Repositório/Outro), paginação explícita, estados de carregamento/vazio válido/erro/retry. Mostrar `reason` em português quando conhecido e rótulo neutro se desconhecido.
- [x] **Passo 4: implementar destinos com pilha local.** Abrir as telas de detalhe existentes. Para issue/PR, criar o modelo mínimo da origem a partir de campos já validados e deixar o detalhe existente buscar metadados atuais; para repositório, usar `repositoryId` e nome/owner validados. Se o tipo/dados não permitem construir identidade segura, exibir indisponibilidade. Configurar `BackHandler` para voltar à tela anterior sem reset da lista.
- [x] **Passo 5: substituir o placeholder autenticado de Avisos.** Em `MainActivity.kt`, no ramo autenticado de `page == 3`, passar `apiClient`, sessão, flag de restauração e callbacks existentes. Manter o texto/fluxo sem sessão existente e não mexer na pilha global ou no dock.
- [x] **Passo 6: executar testes focados e regressão de navegação.** Rode o comando instrumentado focado acima e `./gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.playertwo.controlegithub.NavigationTest' --console=plain`. Os 7 testes da caixa e 5 testes de navegação passaram individualmente no Pixel 9/API 37. A suíte conectada completa foi tentada três vezes; a execução em retrato parou em 84/110 com `IndexOutOfBoundsException` num teste existente de detalhe de repositório e queda do runner. A repetição isolada passou 1/1; a suíte completa não é registrada como PASS.
- [x] **Passo 7: commit.** A tela e seus testes entraram em `bfe62c0` (`feat: add GitHub notifications inbox`); a correção independente de validação de data entra em commit separado.

### Task 4: Verificação real, documentação e aceite

**Arquivos:** modificar `plan/contracts/C09.1.md`, `plan/tasks.json`, `PROJECT_STATE.md`; criar `docs/evidence/C09.1.md`.

- [x] **Passo 1: concluir o contrato de aceite.** Depois da Tarefa 0, completar no contrato os resultados comprovados, negativos e ainda não executados. O resultado OAuth `200` e os gates ainda pendentes estão documentados; C09.1 continua `IN_PROGRESS`.
- [x] **Passo 2: rodar verificações completas locais.** `python scripts/check_plan.py`, `git diff --check` e `./gradlew.bat assembleDebug testDebugUnitTest lintDebug --console=plain`. Verificações passaram em 2026-10-06; a primeira tentativa unitária teve uma falha intermitente no teste compartilhado de cancelamento HTTP, e a repetição passou. Registrar resultados por comando, sem promover NOT_RUN a PASS.
- [ ] **Passo 3: concluir a verificação no Pixel 9/API 37.** A inspeção sintética da lista, detalhe, origem e retorno com `.maestro/C09.1-notifications-fixture.yaml` passou; capturas em `docs/evidence/C09.1-maestro-*.png`. A suíte `./gradlew.bat connectedDebugAndroidTest --console=plain` ainda falha em `GitHubRepositoriesFilterScreenTest.searchByLanguageAppliesToNextPageAndRefreshKeepsCriteria` (1/110; a classe isolada também falhou nesse teste). Manter este passo aberto até resolver ou adjudicar a falha. Nenhuma notificação real foi capturada.
- [x] **Passo 4: testar OAuth real com a conta autorizada.** Em 2026-10-06, a sessão autorizada consultou Avisos uma vez no Pixel 9/API 37: HTTP 200, havia itens e a tela renderizou. Registramos somente esses três fatos; corpo e títulos não foram capturados nem gravados. Se receber `403`, registrar acesso negado sanitizado, manter a sessão útil para demais telas e parar; não marcar integração como funcional, não pedir PAT nem implementar outro método dentro de C09.1. Reportar evidência e propor revisão de autenticação como decisão separada.
- [x] **Passo 5: fechar evidência e estado.** `docs/evidence/C09.1.md` e `PROJECT_STATE.md` registram o Maestro PASS, capturas sintéticas, OAuth sanitizado e a falha instrumentada atual. C09.1 permanece `IN_PROGRESS` enquanto esse gate não passar ou for adjudicado.
  - [x] **Passo 6: validar o diff completo, commit e CI.** `python scripts/check_plan.py` e `git diff --check` passaram; auditoria independente sem achados acionáveis contra `3bd0ad9`; fixture enviada no commit `d2642ec7048bf8d7a0835c3c45f3caee3fc919bb`; CI passou no run `37483593333`. C09.1 permanece `IN_PROGRESS` enquanto o gate instrumentado estiver aberto.

## Autorrevisão do plano

- Cobertura da especificação: token OAuth e `403` explícito (Tarefas 2 e 4); leitura/paginação/deduplicação/modelagem (Tarefas 1 e 2); filtros e tipos desconhecidos (Tarefa 3); detalhe, destinos validados, fallback e retorno que preserva estado (Tarefas 1 e 3); expiração, logout e troca de conta (Tarefa 3); Maestro sintético, smoke test real sanitizado, testes exigidos, auditoria, evidência e estado (Tarefa 4).
- Busca de placeholders: não há marcadores de trabalho futuro, instruções genéricas nem chamada a função fora das interfaces deste plano. Helpers de parser, fixture HTTP e tela usados pelos trechos de teste têm assinatura e arquivo definidos nas tarefas correspondentes.
- Consistência de tipos: o pager reutiliza `GitHubPageResult<GitHubNotification>` e usa `GitHubNotification::id` como chave de deduplicação; destinos selados correspondem às rotas de issue, pull request e repositório já existentes.
- Foco de revisão: os cinco riscos desta seção estão vinculados a testes nas Tarefas 1 a 3. O fluxo real OAuth e privacidade de evidência pertencem à Tarefa 4.
