# Roadmap operacional — do ponto zero à versão 1.0

Objetivo: um aplicativo Android que permita conectar uma conta GitHub, acompanhar
repositórios e trabalho, executar as ações previstas e usar uma versão assinada
com recuperação de erros, privacidade e evidência de funcionamento real.

Este documento define entregas e aceite. O estado de execução e o checkpoint
ativo ficam em [PROJECT_STATE.md](PROJECT_STATE.md). Referências das 36 telas:
[catálogo visual](docs/design/README.md); regras One UI/menu flutuante:
[contrato visual](docs/DESIGN.md).

## Ponto de partida

A base existe: Kotlin/Compose, seis telas demonstrativas, busca, menu flutuante,
wrapper, documentação Gold e referências visuais. Build e testes locais passaram.
**Isso ainda não é um app integrado:** não há login, API, cache ou ações reais.
O primeiro CI remoto falhou na instalação do SDK; a configuração está sendo
corrigida em C00. A auditoria independente inicial ainda não foi executada.

## Escopo da primeira versão totalmente operante

Uma conta GitHub por sessão; repositórios públicos e privados permitidos pela
conta; issues, PRs, notificações e Actions; criação/edição/comentário de issues
e revisão de PRs; favoritos, Kanban local, resumo diário, cache e preferências.
Android API 26 ou superior, português, tema claro/escuro/sistema e menu flutuante.

GitLab/Forgejo, múltiplas contas simultâneas, editor de código, clone/push Git,
merge de PR, disparo de workflows, dashboards avançados e IA ficam depois da 1.0.
Tablet recebe comportamento utilizável; um layout dedicado fica para evolução.

## Marcos de entrega

| Marco | Checkpoint | Resultado demonstrável |
|---|---|---|
| M0 — Fundação confiável | C01 | Protótipo reproduzível, CI verde e navegação validada |
| M1 — Primeiro uso real | C06 | Login, repositórios e painel com dados da conta |
| M2 — Triagem diária | C11 | Issues, PRs, avisos e Actions com cache e recuperação |
| M3 — Produtividade completa | C15 | Escrita, revisão, Kanban, favoritos e resumo reais |
| M4 — Versão 1.0 operante | C20 | Release assinada, beta validado e operação documentada |

M1 já entrega valor; não esperar concluir todas as telas para usar dados reais.
Cada marco produz um APK de teste. M4 produz APK/AAB de release conforme o canal
escolhido pelo proprietário. Publicação na loja não é requisito para operar.

## Mapa de checkpoints

Estados: **PARCIAL**, **PLANEJADO**, **EM EXECUÇÃO**, **BLOQUEADO** e **CONCLUÍDO**.
CONCLUÍDO exige aceite e evidências; desenho, commit ou build isolado não bastam.

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
resultado da auditoria independente registrado. Instalar APK gerado pelo CI.
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
**Evidência:** testes de mapeamento/paginação e issue real com comentários.

### C08 — Pull requests em leitura

**Entregar:** listas, detalhe, branches, estados, comentários, revisões, checks
e arquivos alterados; indicar quando o diff for grande ou não fornecido.

**Aceite:** aberto/fechado/mesclado e revisão representados corretamente;
diff grande não trava a UI; ausência/truncamento explicado; nenhuma edição
ou merge disponibilizados acidentalmente.
**Evidência:** PR real com arquivos e testes dos estados/revisões.

### C09 — Notificações

**Entregar:** caixa lidas/não lidas, categorias, detalhe, marcar como lida e
abrir issue/PR/repositório de origem com retorno à caixa.

**Aceite:** estado lido converge com o GitHub; falha não registra sucesso local;
item removido ou sem acesso recebe tratamento; atualização não duplica notificações.
**Evidência:** testes de sincronização e fluxo real de leitura/abertura.

### C10 — Actions e logs

**Entregar:** workflows, execuções, jobs, status e logs por repositório.
Explicar logs ainda indisponíveis, expirados ou sem permissão.

**Aceite:** execução em andamento/sucesso/falha corresponde ao GitHub; log grande
carrega sem congelar; não expor credenciais nos logs do próprio app;
não confundir ler uma execução com disparar workflow.
**Evidência:** testar uma execução passando e uma falhando, mais log indisponível.

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

**Aceite:** todas as 36 telas/estados mapeados para implementação ou variante
equivalente; dock respeita teclado/gestos e não cobre conteúdo; fluxo principal
concluído com TalkBack e fonte a 200%; temas legíveis e controles acionáveis.
**Evidência:** checklist por tela, capturas claras/escuras e teste de preferências.

### C17 — Robustez e desempenho

**Entregar:** validar API 26 e versão recente, aparelho físico, tela pequena e
tablet, rotação, processo encerrado, rede lenta e conjuntos grandes de dados.
Investigar requisições repetidas, travamentos, memória e uso de bateria.

**Aceite:** fluxos essenciais sem crash/ANR; paginação/diffs grandes responsivos;
recuperação após processo encerrado; nenhuma requisição extra por mera recomposição.
Meta inicial de abertura do painel com cache: até 2 s no aparelho de referência,
com medição e condições documentadas; rede não entra nessa meta.
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

**Aceite:** pelo menos dois usuários distintos completam login, leitura,
notificação e uma ação de escrita em repos de teste; acompanhar ao menos sete
dias de uso; nenhum bug crítico/alto aberto; demais limitações documentadas.
Esses números são metas do plano, não validações já realizadas.
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

## Regras para executar com eficiência

1. Um checkpoint ativo por área; entregar uma fatia funcional por PR/commit.
2. Implementar leitura antes de escrita; usar repositório de teste nas mutações.
3. Reutilizar estados/componentes já necessários; não criar módulos, backend ou
   abstração multiprovedor antes de haver uma demanda concreta.
4. Testar risco real: paginação, sessão, rate limit, envio duplicado e recuperação.
5. Build/testes/lint em toda mudança de código; instrumentados para navegação;
   validação de links/contratos em documentação. Inspecionar imagens quando mudarem.
6. Atualizar estado e evidência na conclusão. Revisão independente nos marcos e
   em mudanças de sessão/escrita; não tratar autorrevisão como auditoria.
7. Falha bloqueia o aceite afetado, não toda preparação independente. Dependência
   de OAuth, segunda conta, aparelho físico ou assinatura deve ser identificada cedo.
8. Não prometer datas antes de C06. Estimar esforço por checkpoint após os primeiros
   ciclos medidos; manter prazo, escopo e dependências explícitos.

## Registro mínimo por checkpoint

```text
Checkpoint: Cxx — título
Estado: PLANEJADO / EM EXECUÇÃO / BLOQUEADO / CONCLUÍDO
Entrega e critérios: link para a seção deste roadmap
Dependências e impedimento concreto:
SHA/PR e artefato:
Verificações: comando → PASS / FAIL / NOT_RUN
Evidência de uso real, quando aplicável:
Revisão independente, quando exigida:
Limitações e próxima ação:
```

Registrar no estado ou relatório do checkpoint; não copiar o roadmap inteiro.
Se o aceite mudar, documentar o motivo e a decisão do proprietário antes de
marcar CONCLUÍDO. Nunca remover um requisito apenas para obter PASS.
