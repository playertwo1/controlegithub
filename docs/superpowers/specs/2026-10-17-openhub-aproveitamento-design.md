# Aproveitamento do OpenHub no ControleGitHub

Data: 2026-10-17. Natureza: detalhamento de propostas e orientação para refinamento.
Pedido: detalhar a análise, atualizar documentos/ideias e preparar trabalho para
Claude. Esta entrega não implementa funcionalidades nem promove tarefas.

## 1. Intenção e fronteira

Aproveitar comportamentos úteis do OpenHub para tornar o ControleGitHub mais
útil na triagem diária e, depois, na escrita. Preservar Android nativo,
Kotlin/Compose, português, linguagem visual One UI/menu flutuante, Device Flow,
sessão protegida e uma conta por sessão. Não adotar o OpenHub como fork/base.

As propostas técnicas abaixo são recomendações deste planejamento, não decisões
de produto atribuídas ao usuário. Antes da execução, o contrato selecionado
resolve seus limites; mudanças de escopo exigem decisão do proprietário.

Não inclui copiar fonte/assets/traduções, trocar autenticação, adicionar PAT,
backend, conta simultânea, clone/push, merge, editor de código ou executar
workflows. Não inclui enviar mensagens ao Claude ou publicar qualquer artefato.

## 2. O que foi observado

Fonte consultada: OpenHub no commit
`fcb9b422e8013169e2328a6e09c675ce11a27ad6`, de 2021-03-21; release v3.2.1 de
2021-03-23. Datas de push de outras referências não provam manutenção de master.
Código inspecionado, sem executar APK ou validar suas telas no dispositivo.

| Evidência de fonte | Aprendizado | Limite |
|---|---|---|
| AppRetrofit: cache por login, FORCE_CACHE e FORCE_NETWORK | Separar identidade, consulta offline e atualização | Não copiar singleton/token mutável nem forçar Cache-Control public |
| AppOpener: reconhecimento de URLs e fallback | Destinos internos consistentes | Rotas não suportadas não devem ser inventadas |
| MarkdownEditor/Preview: atalhos e prévia | Composição compreensível | Há FIXME sobre clique em link; prévia existente não foi validada |
| RepoFilesPresenter: branch, caminho e breadcrumbs | Navegação contextual | É navegador de código, distinto de arquivos alterados em PR |
| HtmlHelper: Markdown/código/diff e links relativos | Casos de compatibilidade | HTML/regex antigos e atribuição a Kosh exigem cautela |
| Gradle: Java/MVP, Android Support, ButterKnife, GreenDAO, RxJava | Referência de comportamento | Não usar como recomendação de dependências atuais |

Links fixados e evidências: [notas da pesquisa](../../../research_notes/OpenHub%20para%20o%20ControleGitHub/openhub.md).
O README do OpenHub declara GPL-3.0 e trechos provenientes de FastHub/Kosh.
A análise não decide a licença do ControleGitHub. Tradução de código não elimina
obrigações de obra derivada. Consultar [LICENSE](https://github.com/ThirtyDegreesRay/OpenHub/blob/fcb9b422e8013169e2328a6e09c675ce11a27ad6/LICENSE)
antes de qualquer proposta de incorporação; neste plano a implementação é própria.

## 3. Base local e dependências

Retrato consultado em 2026-10-17; confirmar novamente ao executar:

- C08.2 já entrega arquivos alterados, diffs inertes e checks/status por SHA.
  Não entrega navegação geral da árvore do repositório ou troca de branch.
- C14.1 entrega favoritos locais; C16.1 entrega claro/escuro/sistema.
- C09.1 é a tarefa ativa; C09.2 ainda está planejada. A validação OAuth real
  da caixa continua pendente conforme contrato/evidência; não presumir sucesso.
- C11.1 depende de C07.2, C08.2, C09.2 e C10.2. C11.2 depende de C11.1.
- C12.1 depende de C07.2/C11.2; C12.2 de C12.1; C12.3 de C12.2.
- C13.1 depende de C08.2/C11.2; C13.2 de C13.1.

O transporte atual é HttpURLConnection, não Retrofit/OkHttp. GitHubHttpClient
oferece GET cancelável, origem restrita e rate limit; GitHubResponseHeaders não
contém ETag/Last-Modified. GitHubSessionManager restaura sessão consultando perfil
na rede: cache offline após reinício exige resolver também a restauração offline,
sem fingir que uma sessão foi revalidada online. Sessão cifrada não significa
que todo futuro cache estará cifrado automaticamente.

## 4. OH-01 — Cache privado de leituras (C11.1)

### Resultado e dados

Consultar a última representação válida de recursos já obtidos, inclusive após
reiniciar o processo sem rede. A UI identifica dados salvos, horário da última
validação e cobertura parcial. Cache não representa alterações feitas no servidor
durante o período offline e não autoriza escrita.

Recomendação para o contrato: cache privado em armazenamento interno excluído de
backup, cifrado com chave protegida pelo Keystore; gravação atômica e versão de
schema. Evitar banco/framework novo sem demonstrar necessidade. O executor escolhe
arquivo cifrado ou banco após medir formatos/tamanhos e documenta a decisão.

Chave lógica: conta por ID estável + geração de autorização + recurso + parâmetros
normalizados + página/cursor + representação/versão de API. Login pode mudar;
token nunca entra na chave, nome de arquivo ou log. Rotação normal do token na
mesma autorização não é troca de conta; logout/revogação invalida a geração.

Entrada: payload validado, identidade do recurso, instante de obtenção,
instante de revalidação, versão de schema, validators HTTP se suportados,
próxima página validada e indicador de cobertura. Corpo inválido/erro não
substitui conteúdo válido. O contrato fixa limites numéricos de bytes por entrada,
total em disco e política de descarte antes de READY; não usar retenção ilimitada.

### Cobertura a fechar no contrato

Mapear explicitamente repos/detalhe/README, issues/comentários, PRs/comentários/
reviews/arquivos/checks, notificações e metadados Actions. Registrar quais páginas
foram obtidas e quais não existem offline. Logs Actions, credenciais, URLs
temporárias de download e respostas OAuth ficam fora do cache de conteúdo.
Não reduzir silenciosamente o alcance do checkpoint C11 a apenas repositórios.

### Cenários de aceite propostos

- CACHE-01: carregou resposta válida; reiniciou sem rede; abre o recurso salvo
  com conteúdo idêntico, horário e indicação offline, sem iniciar novo login.
- CACHE-02: recurso nunca carregado; offline; informa ausência de dados salvos,
  sem spinner infinito, lista falsamente vazia ou dados de demonstração.
- CACHE-03: muda filtro, página ou recurso; não reutiliza representação de outra
  consulta; avisa quando só parte dos itens está disponível.
- CACHE-04: encerra sessão A e autentica B; nenhum dado, validator, favorito,
  rascunho ou resultado tardio de A aparece em B.
- CACHE-05: logout ou revogação conhecida apaga/invalida cache e resultados em
  voo; falha de limpeza mantém barreira de acesso e oferece recuperação.
- CACHE-06: gravação interrompida, schema incompatível ou entrada corrompida;
  descarta a entrada inválida e preserva as demais, sem crash ou segredo no log.
- CACHE-07: autorização local ainda utilizável, rede indisponível; permite modo
  somente leitura sem alegar revalidação remota. Credencial expirada ou identidade
  não verificável bloqueia a leitura até reautenticar; o contrato define o prazo
  máximo de acesso offline e testa a transição. Não ampliar validade do token.
- CACHE-08: resposta nova indica 401; trata recuperação da sessão existente;
  revogação confirmada encerra acesso. 403/404 não provam recurso removido nem
  permitem apresentar cache privado como se a permissão tivesse sido confirmada.

### Arquivos de integração

Investigar GitHubHttpClient.kt, GitHubPagination.kt, GitHubSessionManager.kt,
SecureSessionStore.kt, RepositoryFavorites.kt e modelos/telas de cada recurso.
Criar componente de cache com responsabilidade própria; não embutir serialização
de todos os recursos em MainActivity. Nomes e assinaturas finais são decisão do
contrato após leitura do código; não representam APIs já existentes.

## 5. OH-02 — Atualização e reconexão (C11.2)

Atualizar mantém a tela e o conteúdo salvo até a resposta válida. Sucesso troca
a representação e atualiza horário; erro conserva o conteúdo com aviso explícito.
Reconexão dispara no máximo uma revalidação por consulta visível em voo, respeita
cancelamento e não inaugura sincronização periódica (C15.2).

- REFRESH-01: resposta 200 válida atualiza dados sem duplicar IDs/páginas; filtro
  e posição se conservam quando o item continua na lista.
- REFRESH-02: timeout, rede ou 5xx mantém cache, identifica falha e permite retry;
  nenhum erro vira lista vazia válida.
- REFRESH-03: 429/403 com rate limit respeita o gate atual; sem loop ou retry
  imediato causado por recomposição ou alternância de conectividade.
- REFRESH-04: sair, logout ou trocar consulta cancela/desconsidera a resposta
  anterior; nada repovoa sessão encerrada.
- REFRESH-05: se validators forem adotados, 304 preserva corpo/paginação e muda
  apenas metadados de revalidação; 304 sem entrada correspondente é recuperado
  com uma consulta sem condição, nunca interpretado como JSON vazio.
- REFRESH-06: nova primeira página inaugura nova geração da coleção; não mistura
  páginas antigas com uma ordem nova sem reconciliar IDs e next links.

ETag/Last-Modified são otimização recomendada, condicionada ao endpoint e ao
contrato. Acrescentar suporte tipado ao transporte caso selecionados, sem alterar
a restrição de origem ou seguir redirects autenticados arbitrários. Usar os
links de paginação já validados, não gerar URLs de páginas por concatenação.
Fonte: [boas práticas REST GitHub](https://docs.github.com/en/rest/using-the-rest-api/best-practices-for-using-the-rest-api).

## 6. OH-03/OH-04 — Markdown e composição

### Leitura (proposta de evolução)

README, descrição e comentários terão renderização local de títulos, parágrafos,
ênfase, listas, citações e código, preservando cópia de texto e fallback legível.
Sem execução de HTML/JavaScript, formulários, iframes, file/content/data URLs ou
carregamento automático de imagens remotas. Uma biblioteca deve ser escolhida
pela compatibilidade Compose, licença, manutenção e acessibilidade; nenhuma foi
aprovada por este documento. Não substituir o Markdown inerte atual incidentalmente.

Links relativos só podem ser resolvidos com owner/repo/ref/caminho conhecido.
Sem contexto suficiente, manter texto/fallback, sem adivinhar branch. Links
externos passam pela política OH-05. Destacar sintaxe é melhoria opcional,
não critério para concluir a primeira integração.

### Escrita (C12/C13)

Campos mínimos conforme ação: repo/destino inequívoco, título quando criar issue,
corpo, atalhos de formatação e alternância Editar/Prévia. Prévia local usa a mesma
política de leitura e não chama POST /markdown nem transmite o rascunho. A API
oferece renderização remota, mas esta proposta escolhe local para privacidade e
uso offline: [referência oficial](https://docs.github.com/en/rest/markdown/markdown).

Rascunho é separado de cache: conta + ação + repo/issue/PR + SHA quando relevante.
O contrato define se sobrevive a reinício; recomendação é persistência privada
cifrada, exclusão de backup e limpeza no logout, com confirmação ao descartar.
Editar/navegar não envia. Confirmar mostra destino e ação. Offline bloqueia envio;
prévia e edição continuam. Não criar fila de escrita automática.

- MD-01: texto longo, Unicode e bloco de código permanecem legíveis com fonte
  ampliada, TalkBack e teclado; foco/seleção se conservam nos atalhos.
- MD-02: abrir prévia ou link não executa script nem faz chamada autenticada a
  terceiros; URLs inválidas recebem tratamento explícito.
- WRITE-01: toque repetido durante envio produz uma única requisição mutativa.
- WRITE-02: falha de rede/403/validação mantém rascunho e mensagem útil.
- WRITE-03: timeout após envio tem resultado incerto; reconciliar com servidor
  antes de permitir repetição. Não prometer idempotência que a API não oferece.
- WRITE-04: revisão posicional verifica head SHA e arquivo/linha atuais antes de
  enviar; mudança de SHA exige reavaliar o diff (C13.2).

## 7. OH-05 — Links e destinos

Reaproveitar GitHubNotificationDestination e navegação existente primeiro.
No escopo C09.1, preservar apenas destinos contratados e fallback; um roteador
reutilizável para links de Markdown/repositórios é refinamento separado.

Política proposta: aceitar HTTPS com host github.com exato, sem userinfo ou porta
inesperada. Classificar repo/issue/PR apenas quando o formato for inequívoco;
perfis, releases, commits e âncoras sem suporte nativo recebem navegador ou aviso
claro. Usar URI estruturada e campos de identidade da API sempre que disponíveis.
Não enviar Authorization ao navegador nem seguir URL de API como URL de usuário.

- LINK-01: issue/PR válido abre destino correto e Voltar restaura origem/filtros.
- LINK-02: github.com.evil.test, userinfo, esquemas javascript/file/content/data,
  escapes malformados e número inválido não entram como rota GitHub.
- LINK-03: caminho desconhecido e domínio HTTPS externo abrem somente fallback
  explícito por ação do usuário, sem fetch autenticado e sem crash.
- LINK-04: link privado sem acesso respeita erro e sessão; link não concede acesso.

Receber URLs por Android App Links/intent filters fica fora do primeiro roteador;
exige contrato, validação do manifesto e testes próprios de entrada externa.

## 8. OH-06/OH-07 e demais ideias

Revisar diffs de C08.2 com fixtures para adição, exclusão, rename, binário/patch
ausente, truncamento, Unicode e head SHA alterado. Registrar defeito reproduzido
antes de corrigir; não refatorar o parser por mera semelhança com HtmlHelper.

Um navegador completo de código exige outro resultado: escolha de ref, árvore,
breadcrumbs, voltar ao diretório pai e leitura limitada de arquivo. Não é C08.2.
Branch com barra, caminho profundo, ref removida e arquivo grande devem entrar
no futuro contrato. Não adicionar ao roadmap v1 sem decisão de produto.

Histórico local, downloads/releases, busca global/usuários/organizações/trending,
ações sociais e atalhos ficam no [registro de ideias](../../IDEIAS.md).
Multiaccount é pós-1.0. Preferência de rede pode ser avaliada com C15.2; não
importar scraping ou bibliotecas antigas para obter trending.

## 9. Entrega, provas e decisões antes de READY

Cada entrega selecionada precisa de contrato conforme EXECUTION, fontes oficiais
atuais, arquivos afetados, aceites observáveis e evidência própria. Nenhuma tarefa
foi promovida por este documento. Decisões que cabem ao executor no refinamento:

1. Formato/limites/criptação do cache e inventário de recursos cobertos.
2. Restauração offline: identidade persistida mínima, validade local e limpeza;
   mudança material no modelo de acesso volta ao proprietário.
3. Validators por endpoint e paginação consistente em nova geração.
4. Biblioteca e subconjunto Markdown, retenção de rascunhos e integração de links.
5. Se as propostas de leitura/roteador geral cabem no contrato escolhido ou
   necessitam aprovação e tarefa própria; não usar C09.1 para embuti-las.

Provas: testes de parser/cache/transporte com fixtures sanitizadas; emulador para
reinício/offline/logout/navegação/IME/acessibilidade; inspeção visual e comparação
real autorizada; auditoria independente pelo diff e evidências. Rodar check_plan,
diff --check e assembleDebug/testDebugUnitTest/lintDebug. Navegação exige
connectedDebugAndroidTest com emulador ativo. CI/aceite humano conforme contrato.
NOT_RUN não fecha critério; não iniciar login novo nem mutação real por inferência.
