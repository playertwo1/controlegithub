# Roadmap operacional — versão 1.0 e evolução pós-release

Objetivo: um aplicativo Android que permita conectar uma conta GitHub, acompanhar
repositórios e trabalho, executar as ações previstas e usar versões assinadas
com recuperação de erros, privacidade e evidência de funcionamento real. C00–C20
compõem a versão 1.0; C21–C27 registram a evolução posterior.

Este documento define o aceite agregado. A execução usa as tarefas de
[plan/tasks.json](plan/tasks.json) e o [protocolo de execução](docs/EXECUTION.md).
O estado de execução fica em [PROJECT_STATE.md](PROJECT_STATE.md). Referências das 36 telas:
[catálogo visual](docs/design/README.md); regras One UI/menu flutuante:
[contrato visual](docs/DESIGN.md).

## Baseline original (C00.1)

A base existe: Kotlin/Compose, seis telas demonstrativas, busca, menu flutuante,
wrapper, documentação Gold e referências visuais. Build e testes locais passaram.
Este é o retrato registrado no início do projeto, antes das integrações. Não
descreve o estado atual: autenticação e alguns fluxos de leitura já foram
implementados. Consulte [PROJECT_STATE.md](PROJECT_STATE.md) e os registros em
`docs/evidence/` para o estado e as limitações atuais. A verificação original e
a auditoria da fundação estão em `docs/VERIFICATION.md` e
`docs/evidence/C00.2-review.md`.

## Escopo da primeira versão totalmente operante

Uma conta GitHub por sessão; repositórios públicos e privados permitidos pela
conta; issues, PRs, notificações e Actions; criação/edição/comentário de issues
e revisão de PRs; favoritos, Kanban local, resumo diário, cache e preferências.
Android API 26 ou superior, português, tema claro/escuro/sistema e menu flutuante.

GitLab/Forgejo, múltiplas contas simultâneas, editor de código, clone/push Git,
merge de PR, disparo de workflows, dashboards avançados e IA ficam depois da 1.0.
Tablet recebe comportamento utilizável; um layout dedicado fica para evolução.

### Referências e propostas de refinamento

O [registro de ideias OpenHub](docs/IDEIAS.md) e seu
[detalhamento](docs/superpowers/specs/2026-10-17-openhub-aproveitamento-design.md)
orientam refinamento futuro de C11 (cache/refresh), C12/C13 (composição) e
eventuais evoluções de leitura/links. A sequência está no
[plano de aproveitamento](docs/superpowers/plans/2026-10-17-openhub-aproveitamento.md).
Ideias OH não alteram dependências, estados ou aceites agregados sem uma tarefa
aprovada. C22–C24 registram as expansões já incorporadas: leitura Markdown,
edição Markdown e navegador de código. C08.2 continua tratando arquivos alterados
do PR, não a árvore geral do repositório.

## Marcos de entrega

| Marco | Checkpoints exigidos | Resultado demonstrável | Status |
|---|---|---|---|
| M0 — Fundação confiável | C00 + C01 | Protótipo reproduzível, CI verde, navegação validada e auditoria inicial aceita | ✅ ACEITO |
| M1 — Primeiro uso real | C06 | Login, repositórios e painel com dados da conta | ✅ CONCLUÍDO (2026-10-08) |
| M2 — Triagem diária | C11 | Issues, PRs, avisos e Actions com cache e recuperação | ✅ CONCLUÍDO (2026-10-17) |
| M3 — Produtividade completa | C15 | Escrita, revisão, Kanban, favoritos e resumo reais | ✅ CONCLUÍDO (2026-10-17) |
| M4 — Versão 1.0 operante | C20 | Release assinada, beta validado e operação documentada | ✅ CONCLUÍDO (2026-10-18) |

Cada marco fecha quando todos os checkpoints listados e seus aceites passam.
Dependências transitivas desses checkpoints também precisam estar aceitas.
Concluir o último checkpoint citado, sozinho, não fecha o marco.

M1 já entrega valor; não esperar concluir todas as telas para usar dados reais.
Cada marco produz um APK de teste. M4 produz APK/AAB de release conforme o canal
escolhido pelo proprietário. Publicação na loja não é requisito para operar.

## Mapa de checkpoints

O estado canônico de cada tarefa está somente em `plan/tasks.json`; não inferir
estado pelo número do checkpoint ou por esta tabela. Checkpoint concluído exige
aceite agregado e evidências; desenho, commit ou build isolado não bastam.

| ID | Fase | Entrega | Depende de |
|---|---|---|---|
| C00 | F0 | Ambiente, CI e baseline verificável | — |
| C01 | F0 | Navegação e contrato visual estáveis | C00 |
| C02 | F1 | OAuth App e configuração de acesso | C00 |
| C03 | F1 | Login, sessão e logout protegidos | C01, C02 |
| C04 | F1 | Cliente GitHub e estados de rede | C00; integrar a C03 |
| C05 | F1 | Repositórios reais, busca e paginação | C03, C04 |
| C06 | F1 | Detalhe e painel reais — M1 | C05 |
| C07 | F2 | Issues em leitura | C06 |
| C08 | F2 | PRs, arquivos e revisão em leitura | C06 |
| C09 | F2 | Notificações e abertura do item | C07, C08 |
| C10 | F2 | Actions, execuções e logs | C06 |
| C11 | F2 | Cache, offline e refresh — M2 | C07–C10 |
| C12 | F3 | Criar, editar, comentar e fechar issues | C07, C11 |
| C13 | F3 | Comentários e revisões de PR | C08, C11 |
| C14 | F3 | Favoritos e Kanban local | C07, C11 |
| C15 | F3 | Resumo e sincronização — M3 | C09, C10, C12–C14 |
| C16 | F4 | Preferências, temas e acessibilidade | C15 |
| C17 | F4 | Robustez em dispositivos e desempenho | C16 |
| C18 | F4 | Privacidade e release assinada | C17 |
| C19 | F4 | Beta com uso real | C18 |
| C20 | F4 | Aceite final e operação — M4 | C19 |
| C21 | F5 | Releases e download/instalação manual de APK | C20 |
| C22 | F5 | Leitura Markdown | C21 |
| C23 | F5 | Edição Markdown | C22 |
| C24 | F5 | Navegação de código | C23 |
| C25 | F5 | Avisos → repositório → recursos existentes e APK | C09, tarefa C24.1 |
| C26 | F5 | Issues e Pull Requests por repositório | C07, C08, C25 |
| C27 | F5 | Histórico recente local de repositórios visitados (OH-08) | C06, C14, C26 |

As dependências desta tabela são gates de aceite/fechamento dos checkpoints.
Uma célula pode apontar um checkpoint inteiro ou, quando identificada como
“tarefa”, somente aquela entrega. `depends_on` no backlog controla quando uma
tarefa individual pode começar.
Uma tarefa pode avançar antes de seu checkpoint predecessor fechar se suas
dependências próprias estiverem concluídas. Por isso C01.1 pode avançar após
C00.1 enquanto a auditoria C00.2 está pendente. Isso permite preparação, não
fecha C00 nem M0. M0 só fecha depois de C00.1, C00.2 e todo C01 aceitos.
Favoritos não esperam issues; preferências podem avançar cedo.

C21.3 acompanha o aceite de download em aparelho físico e permanece separado.
Por decisão do proprietário, esse teste pode ocorrer depois. C25.1 depende
diretamente da tarefa já concluída C21.2, e C25 não depende do fechamento do
checkpoint C21. C25 usa C24.1 como dependência de tarefa, sem aguardar o
fechamento agregado de C24; C21.3 só é gate para fechar C21.

A ordem numérica é uma ordem segura de entrega, não uma exigência de serializar
tudo: C04 pode avançar com respostas HTTP de teste enquanto o OAuth é configurado;
C07/C08/C10 são independentes após C06. Não manter várias mudanças incompletas
na mesma área. Cada integração deve manter a branch principal verificável.

## F0 — Fundação

### C00 — Ambiente, CI e baseline

**Entregar:** build reproduzível a partir de um clone limpo, versões fixadas,
SDK/JDK documentados, APK debug no CI, contratos Gold e relatório inicial.
Corrigir a falha de setup do SDK e revisar a fundação pelo delta.

**Aceite:** build, testes unitários e lint passam localmente e no GitHub Actions;
wrapper validado; SDK local/segredos não versionados; links e imagens presentes;
APK gerado pelo CI instalado. Auditoria independente registra PASS sem findings
ou PASS após findings corrigidos e nova revisão. Findings abertos mantêm C00
incompleto.
**Evidência:** SHA, link da execução, APK e relatório. Teste não executado = NOT_RUN.

### C01 — Navegação e contrato visual

**Entregar:** consolidar as seis telas atuais, componentes visuais compartilhados
quando já repetidos, rotas necessárias, estado de tela e menu flutuante.
Adicionar cabeçalho que se adapta à rolagem; manter imagens como referência.

**Aceite:** quatro destinos consistentes; Voltar correto; estado preservado na
rotação; teclado e menu sem conflito; nenhum conteúdo ou botão oculto pelo menu;
busca vazia e texto ampliado utilizáveis; modo demo claramente identificado.
**Evidência:** teste instrumentado e capturas atuais. Fecha M0.

## F1 — Conta e primeiro uso real

### C02 — OAuth App e acesso

**Entregar:** OAuth App com Device Flow habilitado; Client ID configurável;
instruções de configuração e revogação; matriz funcionalidade → permissão.
O proprietário registra/configura o app; o Client ID não é um client secret.

**Aceite:** solicitar código funciona na configuração real; nenhuma senha ou
client secret no app; permissões justificadas. Documentar a abrangência dos
escopos disponíveis, sem prometer acesso somente leitura quando forem mais amplos.
**Evidência:** configuração sem segredos e teste da solicitação de código.

### C03 — Autorização e ciclo de sessão

**Entregar:** gerar/copiar código, abrir GitHub, polling com intervalo informado,
expiração, cancelamento, reconexão, perfil da sessão e logout.
Token cifrado no aparelho com chave protegida pelo Android Keystore.

**Aceite:** login real persiste após reinício; código inválido/expirado e acesso
revogado se recuperam; cancelar interrompe polling; logout remove token, cache e
trabalho pendente da sessão. Nenhum token em logs, backups ou capturas.
**Evidência:** testes determinísticos do fluxo e demonstração real sem credenciais.

### C04 — Cliente GitHub e rede

**Entregar:** cliente HTTPS com timeouts, cancelamento, paginação e tratamento de
autorização, permissão, recurso removido, erro de servidor e rate limit.
Separar dados, estado e interface apenas conforme a integração exigir.

**Aceite:** testes HTTP para sucesso e 401/403/404/429/5xx, timeout e falha de rede;
respeitar cabeçalhos de retry/reset e cancelamento; não repetir escrita automaticamente;
erro traduzido em estado visível. Carregamento não exibe números inventados.
**Evidência:** testes com servidor/respostas controladas e teste de contrato real.

### C05 — Repositórios reais

**Entregar:** listagem da conta, públicos/privados conforme acesso, busca por nome
e linguagem, filtros, ordenação, refresh, paginação e vazio.

**Aceite:** dados conferidos com o GitHub; conjunto maior que uma página sem
duplicações; busca/filtro explicam seu alcance (itens carregados ou busca remota);
trocar filtro não mistura páginas; recurso sem acesso não quebra a lista.
**Evidência:** teste de paginação/filtros e comparação real com a conta de teste.

### C06 — Detalhe e painel real

**Entregar:** informações do repositório, branch, visibilidade, contagens e
atividade; painel com atalhos e atenção necessária a partir de dados reais.
As informações ainda não disponíveis devem ser explicitadas, sem contagens demo.

**Aceite:** entrada → login → repos → detalhe → painel funciona em instalação
limpa; atualizar reflete alteração feita no GitHub; dado indisponível não vira zero;
escopo das contagens documentado e consistente com a API.
**Evidência:** fluxo ponta a ponta com conta real. Fecha M1.

## F2 — Triagem diária

### C07 — Issues em leitura

**Entregar:** listas por repo/conta, filtros aberta/fechada, autoria/atribuição,
detalhe, descrição Markdown, labels e comentários paginados.

**Aceite:** links/Markdown tratados com segurança; conteúdo longo acessível;
paginação e filtros consistentes; resultados respeitam acesso; links internos
abrem o item correto. PRs não aparecem como issues por engano.
**Evidência agregada:** PASS por aceitação do proprietário em 2026-10-12.
C07.1 e C07.2 estão `DONE` com build, testes, lint, auditoria independente e CI.
A comparação autenticada com issue real com comentários ficou `NOT_RUN` porque a
conta de teste não tinha issue adequada; o proprietário (Rafael / playertwo1)
aceitou o checkpoint agregado C07 dispensando essa evidência pendente. Estados e
evidências individuais em [`plan/tasks.json`](plan/tasks.json),
[`PROJECT_STATE.md`](PROJECT_STATE.md),
[`docs/evidence/C07.1.md`](docs/evidence/C07.1.md) e
[`docs/evidence/C07.2.md`](docs/evidence/C07.2.md).

### C08 — Pull requests em leitura

**Entregar:** listas, detalhe, branches, estados, comentários, revisões, checks
e arquivos alterados; indicar quando o diff for grande ou não fornecido.

**Aceite:** aberto/fechado/mesclado e revisão representados corretamente;
diff grande não trava a UI; ausência/truncamento explicado; nenhuma edição
ou merge disponibilizados acidentalmente.
**Evidência agregada:** PASS por aceitação do proprietário em 2026-10-12.
C08.1 e C08.2 estão `DONE` com build, testes, lint, auditoria independente e CI.
A comparação autenticada com PR real (arquivos, estados e reviews) ficou
`NOT_RUN` porque a conta de teste não tinha PR adequado; o proprietário
(Rafael / playertwo1) aceitou o checkpoint agregado C08 dispensando essa
evidência pendente. Estados e evidências individuais em
[`plan/tasks.json`](plan/tasks.json), [`PROJECT_STATE.md`](PROJECT_STATE.md),
[`docs/evidence/C08.1.md`](docs/evidence/C08.1.md) e
[`docs/evidence/C08.2.md`](docs/evidence/C08.2.md).

### C09 — Notificações

**Entregar:** caixa lidas/não lidas, categorias, detalhe, marcar como lida e
abrir issue/PR/repositório de origem com retorno à caixa.

**Aceite:** estado lido converge com o GitHub; falha não registra sucesso local;
item removido ou sem acesso recebe tratamento; atualização não duplica notificações.
**Evidência:** testes de sincronização e fluxo real de leitura/abertura.

### C10 — Actions e logs

**Entregar:** workflows, execuções, jobs, status e logs por repositório, em
somente leitura. Explicar logs ainda indisponíveis, expirados ou sem permissão.

**Aceite:** execução em andamento/sucesso/falha corresponde ao GitHub; log grande
carrega sem congelar; não expor credenciais nos logs do próprio app;
não confundir ler uma execução com disparar workflow.
**Evidência agregada:** PASS em 2026-10-05. No Pixel 9, Maestro mostrou o run
real #49 como sucesso e o #51 como falha; os metadados corresponderam ao GitHub
CLI. O job `build` do #51 estava cancelado sem etapas; ao abrir seus logs, o app
indicou link expirado ou logs indisponíveis, sem confundi-los com log vazio. A
fixture 404 de C10.2 também verificou o erro de indisponibilidade. Para o #49,
o app abriu a tela real de logs e mostrou o aviso de sensibilidade. Nenhum corpo
de log foi copiado ou persistido. C10.1 e C10.2 estão `DONE` e o aceite
agregado C10 está concluído. Evidências individuais em
[`plan/tasks.json`](plan/tasks.json), [`PROJECT_STATE.md`](PROJECT_STATE.md),
[`docs/evidence/C10.1.md`](docs/evidence/C10.1.md) e
[`docs/evidence/C10.2.md`](docs/evidence/C10.2.md).

### C11 — Cache, offline e recuperação

**Entregar:** persistência dos dados de leitura, horário de atualização, refresh
consistente, offline com cache e estados de falha sem cache.

**Aceite:** fechar/abrir sem rede permite consultar dados já obtidos; voltar à
rede atualiza; cache isolado por sessão/recurso/filtros; logout limpa dados;
nenhuma tentativa infinita após rate limit; dados antigos claramente sinalizados.
**Evidência:** teste de reinício/offline/reconexão e fluxo completo de triagem.
Fecha M2: aplicativo útil para acompanhar trabalho diariamente.

## F3 — Ações e produtividade

### C12 — Escrita em issues

**Entregar:** criar, editar, comentar, atribuir/rotular quando permitido,
fechar/reabrir; confirmação contextual e revisão das permissões necessárias.

**Aceite:** ação comprovada no GitHub; estados enviando/sucesso/falha; toque duplo
não duplica envio; timeout com resultado incerto reconcilia antes de repetir;
rascunho preservado em falha; acesso negado explicado. Offline não envia escrita.
**Evidência:** testes HTTP de falha/duplicação e ciclo real em repo de teste.

### C13 — Comentários e revisão de PR

**Entregar:** comentar, aprovar e solicitar alterações conforme permissão;
mostrar diff/commit correspondente antes de enviar. Merge fica fora da 1.0.

**Aceite:** revisão aparece no PR correto; diff desatualizado não recebe comentário
posicional silenciosamente; limitações de arquivo/linha e do autor explicadas;
falha conserva rascunho; envio duplicado prevenido como em C12.
**Evidência:** PR de teste revisado com segunda conta e cenários de erro controlados.

### C14 — Favoritos e Kanban local

**Entregar:** favoritos persistentes e quadro com A fazer/Em andamento/Em revisão/
Concluído; cartões ligados a issues reais e alteração de coluna local.

**Aceite:** organização persiste após reinício; item removido não quebra quadro;
logout limpa dados; mover coluna não fecha/edita a issue no GitHub;
alternativa acessível ao arrastar disponível.
**Evidência:** teste de persistência e conferência de que a issue remota não mudou.

### C15 — Resumo diário e sincronização

**Entregar:** resumo de eventos reais e exportação Markdown; sincronização
periódica configurável, filtros de notificações e preferência de rede.
Usar agendamento Android que respeita sistema, bateria e conectividade.

**Aceite:** contagens têm origem, período e fuso explícitos; evento não é contado
duas vezes; cobertura parcial indicada; exportação confere com dados;
logout/cancelamento encerram tarefas; preferência Wi-Fi respeitada; nenhum horário
exato de background prometido. Permissão de notificação negada não impede usar o app.
**Evidência:** testes de resumo/agendamento e comparação com eventos reais.
Fecha M3: todas as funções de produto da 1.0 operam.

## F4 — Qualidade, distribuição e operação

### C16 — Preferências e acessibilidade

**Entregar:** tema claro/escuro/sistema, conta, permissões, privacidade e Sobre;
fonte ampliada, contraste, TalkBack, rótulos e foco; estado persistente das opções.

**Aceite:** cada ID 01–36 do catálogo visual recebe exatamente um destino:
implementado na rota indicada; coberto por uma rota/estado equivalente indicado;
ou excluído da 1.0 com motivo e decisão do proprietário. “Equivalente” significa
mesma ação e resultado observável, ainda que apresentação ou rota difira; aparência
da imagem, por si só, não é requisito de implementação. Exclusão não pode retirar
uma função incluída no escopo 1.0. Além do mapeamento, o dock respeita teclado e
gestos; TalkBack e fonte a 200% completam o fluxo principal; temas têm contraste
legível e controles acionáveis.
**Evidência:** matriz ID → rota/estado ou motivo de exclusão → critério aplicável;
capturas claras/escuras e teste de preferências.

### C17 — Robustez e desempenho

**Entregar:** validar API 26 e versão recente, aparelho físico, tela pequena e
tablet, rotação, processo encerrado, rede lenta e conjuntos grandes de dados.
Investigar requisições repetidas, travamentos, memória e uso de bateria.

**Aceite:** fluxos essenciais sem crash/ANR; paginação/diffs grandes responsivos;
recuperação após processo encerrado; nenhuma requisição extra por mera recomposição.
Orçamento do painel com cache: até 2 s; antes de READY, o contrato identifica
aparelho, versão Android, volume da fixture, início/fim medidos e número de
repetições. Medir abertura até conteúdo acionável; rede não entra nessa meta.
**Evidência:** matriz dispositivo → cenário → resultado e medições reproduzíveis.

### C18 — Release, privacidade e atualização

**Entregar:** política baseada no comportamento real, licença escolhida pelo
proprietário, assinatura protegida, build release, versionamento e instruções
de instalação/atualização/revogação. Revisar tokens, backups e dependências.

**Aceite:** APK assinado instala; atualização preserva sessão/dados permitidos;
nenhum segredo no Git/APK/logs; release passa lint/testes e fluxo principal;
chave não versionada; artefato vinculado a SHA e checksum. Auditoria independente
de segurança/sessão/escrita e da entrega registra PASS ou findings resolvidos.
**Evidência:** artefatos, checksum, teste de atualização e relatório de auditoria.

### C19 — Beta em uso real

**Entregar:** distribuir pelo canal autorizado a um grupo pequeno e registrar
problemas com reprodução, impacto e versão. Sem telemetria obrigatória.

**Aceite:** cobrir login, leitura, notificação, escrita, revisão, offline,
reinício e atualização em contas/repos de teste. Não encerrar com perda de dados,
exposição de credenciais, envio duplicado, crash que impeça o fluxo ou função
essencial inacessível. Outros defeitos precisam de impacto e decisão registrados.
Dois testers e sete dias são sugestões para recrutar/observar uso, sem espera
artificial após cobertura concluída. O contrato define a matriz antes de READY.
**Evidência:** relatos, matriz de cenários e regressões verificadas nas correções.

### C20 — Aplicativo totalmente operante

**Entregar:** versão 1.0 assinada, release notes, manual curto e procedimento
de suporte/correção/retorno à versão anterior compatível com o modelo de dados.
Publicar no canal aprovado; loja somente por decisão específica do proprietário.

**Aceite final:** em instalação limpa, login real → repos → issue/PR → avisos →
Actions → escrita/revisão → Kanban/resumo → reinício/offline/reconexão → logout;
executar também atualização da versão beta. Todos os checkpoints CONCLUÍDOS,
CI verde no SHA da release, auditorias resolvidas, artefatos acessíveis e nenhuma
dependência de modo demo para uma função essencial. Fecha M4.
**Evidência:** relatório ponta a ponta, SHA, release, APK/checksum e limitações aceitas.

## F5 — Evolução pós-release

### C21–C24 — Releases, APK, Markdown e código

Esses checkpoints já entregam lista/detalhe de releases, download verificado e
instalação manual de APK, leitura e edição Markdown e navegação da árvore de
código. Os contratos C21.1–C24.1 detalham limites e evidências. C21.3 ainda
acompanha a validação física, marcada `PLANNED`/`NOT_RUN`.

### C25 — Acesso do aviso ao repositório e APK

**Entregar:** para notificações sem destino de issue ou PR, abrir o repositório
de origem quando sua identidade validada estiver disponível. De lá, alcançar os
recursos já presentes no app — resumo/README, Código, Actions, Nova issue e
Releases/APKs — e usar o download manual verificado de C21.2. Notificações de
issue e PR mantêm seus destinos específicos.

**Aceite agregado:** resolver repositórios dinamicamente pelos dados da
notificação, sem lista fixa; respeitar a sessão OAuth e as permissões concedidas;
mostrar explicitamente falha ou falta de acesso; preservar a caixa de avisos ao
voltar e anunciar o destino correto no botão Voltar. O APK só baixa após ação
explícita e segue o fluxo C21.2 de progresso, checksum e instalação manual. Não
acrescentar permissões, escopos OAuth ou operações administrativas.

**Evidência:** contrato e registro de C25.1 em `plan/contracts/C25.1.md` e
`docs/evidence/C25.1.md`. Até o proprietário ampliar o escopo, “acessar tudo”
significa usar as funções existentes no app com as permissões da sessão. O
checkpoint C25 fecha com C25.1, por instrução do proprietário em 2026-10-23, com a suíte integral registrada como **NOT PASS**. C21.3 continua `PLANNED`/`NOT_RUN` e não entra nesse fechamento.

### C26 — Issues e Pull Requests do repositório

**Entregar:** a partir do detalhe de um repositório (`GitHubRepositoryDetailScreen`),
permitir consultar as Issues e os Pull Requests específicos daquele `owner/repo`,
filtrar por estado, paginar com segurança, abrir o detalhe existente de cada
issue (`GitHubIssueDetailScreen`) ou PR (`GitHubPullRequestDetailScreen`) e
acionar a criação de nova issue, além de refinar a arte vetorial do ícone
adaptativo do aplicativo.

**Aceite agregado:** listas de issues e PRs consultam apenas o repositório
selecionado; itens do endpoint de issues que sejam pull requests não se
misturam na aba de issues; abrir issue ou PR preserva filtros e posição ao
voltar; falhas de rede/acesso negado e estados vazios são explícitos sem dados
fictícios.

**Evidência:** contrato e registro de C26.1 em `plan/contracts/C26.1.md` e
`docs/evidence/C26.1.md`.

### C27 — Histórico recente local de repositórios visitados (OH-08)

**Entregar:** registrar localmente, por conta autenticada, os últimos repositórios
visitados no aplicativo (até 10 itens, ordenados do mais recente ao mais antigo,
sem duplicar `owner/repo`), exibir a seção **"Visitados recentemente"** no painel
inicial (`GitHubHomeScreen`) e na aba `Repositórios` (`GitHubRepositoriesScreen`)
para retomada rápida com 1 toque e botão **"Limpar histórico"**, além de expurgar
automaticamente o histórico no logout ou expiração da sessão.

**Aceite agregado:** abrir o detalhe de um repositório válido (`GitHubRepositoryDetailScreen`)
registra a visita apenas para a conta ativa; repositórios privados ou públicos
visitados nunca vazam entre contas diferentes; limpar histórico ou fazer logout
remove os registros persistidos; tocar em um item recente abre
`GitHubRepositoryDetailScreen` diretamente com retorno previsível; não altera
stars/watch no GitHub nem cria dados de demonstração.

**Evidência:** contrato e registro de C27.1 em `plan/contracts/C27.1.md` e
`docs/evidence/C27.1.md`.

## Execução e refinamento

O [backlog](plan/tasks.json) é a fonte única das entregas verificáveis e inclui
as evoluções pós-v1.0.
O [protocolo](docs/EXECUTION.md) define seleção, estados, contratos e evidências.
Não duplicar status nesta tabela nem criar uma segunda fila.

PLANNED indica entrega futura, ainda não pronta para implementar. Definir no
contrato ambiente, métricas, janela do resumo, política de alertas e canal antes
de READY. Sugestões de duração/amostra do beta não substituem cobertura de riscos.
Contratos iniciais: C01.1 e C02.1. Refinar próximos grupos com código e API atual,
sem remover aceite ou ampliar o produto para fechar tarefas.
