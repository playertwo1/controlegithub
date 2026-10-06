# C09.1 — Design da caixa de notificações

**Estado:** desenho aprovado em conversa em 2026-10-06; aguardando revisão desta
especificação antes do plano de implementação.

## Objetivo

Entregar a aba Avisos para a conta GitHub conectada. A pessoa pode consultar
notificações lidas e não lidas, distinguir seus tipos, abrir o detalhe e navegar
à issue, pull request ou repositório relacionado. Ao voltar, a caixa preserva
filtros, página e posição da lista.

## Restrições aprovadas

- Usar inicialmente o token do OAuth App obtido por Device Flow, já solicitado
  com o escopo `notifications`. Não introduzir PAT nem segredo no app.
- Tentar a Notifications REST API com esse token. A referência REST atual diz
  que os endpoints aceitam apenas PAT clássico, enquanto a referência de
  escopos OAuth atribui leitura de notificações ao escopo `notifications`.
  Essa divergência será resolvida empiricamente com a conta autorizada antes de
  considerar a integração funcional.
- Se o GitHub negar acesso, mostrar indisponibilidade explícita; nunca mostrar
  uma caixa vazia como se a leitura tivesse funcionado. Registrar somente
  status e resultado sanitizado, sem token nem conteúdo privado. Parar o fluxo
  OAuth dessa funcionalidade e rever a alternativa de autenticação em uma
  mudança subsequente.
- C09.1 é somente leitura. Marcar notificações como lidas pertence a C09.2.
- Interface e mensagens ficam em português; fixtures e testes locais são
  sintéticos e identificados como tais.

## Abordagens avaliadas

1. **Pilha de navegação local à aba Avisos — escolhida.** A tela mantém lista,
   detalhe da notificação e detalhe da origem; reaproveita os Composables
   existentes para issue, pull request e repositório. O retorno conserva o
   estado da caixa e mantém a alteração de navegação restrita a C09.
2. **Pilha global em `MainActivity`.** Facilita deep links futuros, mas altera a
   navegação principal e espalha a rota de notificações pelo app antes de haver
   outro consumidor.
3. **Abrir cada origem no navegador.** Reduz código local, mas tira a pessoa do
   fluxo do app e não preserva a experiência aprovada de detalhes existentes.

## Arquitetura e fluxo

1. `GitHubNotifications` define os modelos tipados, valida e decodifica a
   resposta da REST API e pagina com o `GitHubPaginator` existente. A identidade
   de deduplicação é o ID da thread. O cliente usa os caminhos relativos e o
   `GitHubHttpClient` existente; não segue URLs arbitrárias vindas do JSON.
2. `GitHubNotificationsScreen` substitui o placeholder autenticado em Avisos.
   A lista solicita `GET /notifications?all=true&per_page=50`, representa o
   campo `unread` e o `reason`, e oferece filtros combináveis de estado
   (`Todas`, `Não lidas`, `Lidas`) e tipo (`Todos`, `Issue`, `Pull request`,
   `Repositório`, `Outro`). Tipos desconhecidos recebem o rótulo “Outro”, sem
   descartar o item.
3. Tocar num cartão abre detalhe com título, repositório, tipo, motivo, data de
   atualização e estado lido/não lido. A tela pode consultar
   `GET /notifications/threads/{id}`; esse GET não marca a thread como lida.
4. A ação para abrir a origem valida `repository.full_name`, `subject.type` e
   `subject.url` antes de selecionar a rota tipada existente. Só aceita URL
   HTTPS de `api.github.com`, sem porta, credenciais, query ou fragmento, com
   caminho do repositório igual ao `full_name` e número positivo. `Issue` abre
   a rota de issue; `PullRequest` abre a rota de pull request; `Repository` ou
   assunto ausente abre o repositório. Para pull request, aceita o endpoint
   validado `/issues/{n}` ou `/pulls/{n}` retornado pelo GitHub e constrói a
   chamada de origem pelo cliente interno. Outros tipos ou inconsistências
   mostram que a origem não está disponível e nunca abrem um endereço
   arbitrário.
5. O botão Voltar ou o gesto do Android retorna primeiro da origem ao detalhe e
   depois à caixa, preservando seu estado. Logout ou troca de conta descarta o
   estado associado à sessão anterior.

## Estados observáveis

- Restauração de sessão, carregamento inicial e carregamento de próxima página.
- Lista vazia somente após resposta válida `200` sem itens.
- Erro de rede, limite de taxa e resposta malformada com tentativa de novo.
- `401` segue o tratamento compartilhado de sessão expirada.
- `403` mostra que o acesso OAuth às notificações não foi aceito e não oferece
  “caixa vazia”; a sessão permanece ativa para o restante do app.
- Detalhe removido ou inacessível explica indisponibilidade e mantém retorno à
  caixa.
- Tipo ou destino desconhecido recebe fallback sem seguir a URL recebida.

## Verificação prevista

- Testes unitários de parser, validação de URLs/caminhos, mapeamento de tipo e
  motivo, paginação, deduplicação, itens lidos/não lidos e estados de erro.
- Testes Compose de lista vazia válida, lista com tipos conhecidos/desconhecidos,
  filtros, abrir detalhe, retorno preservando lista, logout/troca de conta,
  retry e `403` explícito.
- Pixel 9/API 37: `./gradlew.bat connectedDebugAndroidTest --console=plain`.
- Maestro com fixture sintética para lista/detalhe/retorno; nenhuma notificação
  real deve ser capturada, copiada ou anexada à evidência.
- Prova OAuth real controlada pela interface do app: registrar apenas status
  HTTP, presença/ausência de itens e resultado de renderização; descartar o
  corpo e não registrar login, título, repositório, ID, URL ou credenciais.
- `python scripts/check_plan.py`, `git diff --check` e
  `./gradlew.bat assembleDebug testDebugUnitTest lintDebug --console=plain`.

## Limites desta entrega

C09.1 não altera estado no GitHub, não faz polling em segundo plano, não cria
notificações do Android, não gere subscriptions e não troca o método de
autenticação. Uma rejeição do OAuth é evidência para interromper esta integração
e rever a autenticação separadamente. C09.2 continua responsável por marcar
threads como lidas.

## Referências oficiais

- [REST API de Notifications](https://docs.github.com/en/rest/activity/notifications?apiVersion=2022-11-28)
- [Escopos de OAuth Apps](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/scopes-for-oauth-apps)
- [Autorização OAuth e Device Flow](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/authorizing-oauth-apps)
