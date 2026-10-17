# Aproveitamento OpenHub — plano de refinamento e execução

> **Para agentes:** executar uma entrega por vez, seguindo docs/EXECUTION.md.
> Skills de implementação, quando disponíveis: superpowers:executing-plans ou
> superpowers:subagent-driven-development. A segunda opção depende de autorização
> e disponibilidade do ambiente. Este plano não inicia implementação por si só.

**Objetivo:** incorporar os comportamentos selecionados do OpenHub à base própria,
sem duplicar entregas existentes nem contornar dependências.

**Arquitetura:** módulo Android existente, Kotlin/Compose, HttpURLConnection e
sessão protegida. Cache privado e Markdown local são propostas de componentes
separados; APIs e dependências finais pertencem ao contrato refinado.

**Stack:** configuração existente; Android API 26+, target 36, JDK 17, português.

**Spec:** [design detalhado](../specs/2026-10-17-openhub-aproveitamento-design.md).

## Restrições globais

- Uma tarefa IN_PROGRESS; states/deps em plan/tasks.json, sem segunda fila OH.
- Uma conta por sessão, Device Flow e identidade visual própria.
- Não copiar fonte/assets/traduções nem adotar a stack do OpenHub.
- Preservar alterações concorrentes; sem commit/push/release/login/mutação real
  por mera instrução deste documento. Confirmar autorização na sessão executora.
- Conteúdo privado, tokens e logs Actions não entram em evidência ou prompts.
- Documentar propostas não equivale a READY/DONE nem aceite de marco.

## Foco da revisão

1. Reinício offline pode falhar na restauração antes de chegar ao cache.
2. Resposta tardia pode repovoar dados de sessão encerrada.
3. Filtro/página/ETag incompatível pode reutilizar conteúdo de outra consulta.
4. Timeout mutativo pode produzir duplicação se tratado como retry de leitura.
5. URL de conteúdo/Markdown pode escapar da navegação segura ou vazar credencial.

Cada risco deve ter cenário de teste no contrato que o implementar.

## 0. Preparação obrigatória

- [ ] Ler AGENTS, PROJECT_STATE e EXECUTION; registrar branch/HEAD e git status.
- [ ] Rodar `python scripts/check_plan.py`; consultar estados/deps atuais.
- [ ] Localizar a tarefa ativa. No retrato de 2026-10-17 é C09.1; se mudou,
  atualizar a seleção a partir do backlog, não da data deste plano.
- [ ] Ler contrato/evidência da tarefa e arquivos específicos antes de editar.
- [ ] Classificar o pedido de execução: implementação explicitamente autorizada
  ou apenas refinamento documental. Não converter o segundo no primeiro.

**Prova:** seleção justificada por ID/dependências e lista de arquivos sujos
preservados, sem conteúdo privado.

## 1. Resolver a sequência existente C09

**Arquivos:** plan/contracts/C09.1.md, docs/evidence/C09.1.md,
GitHubNotifications.kt, GitHubNotificationsScreen.kt e testes correspondentes
em app/src/{main,test,androidTest}/java/com/playertwo/controlegithub/.

**Consome:** sessão OAuth existente; transporte GET e destino de notificações.
**Produz:** somente os comportamentos contratados da caixa, com evidência real
ou bloqueio explícito. Não produz o roteador geral de Markdown.

- [ ] Verificar pendências atuais, incluindo suíte instável e comparação real.
- [ ] Com sessão existente e autorização aplicável, testar acesso conforme
  contrato; caso indisponível, registrar NOT_RUN/bloqueio sem obter PAT ou ler
  credenciais por meios externos ao app.
- [ ] Corrigir apenas defeitos reproduzidos; executar testes relevantes e gates.
- [ ] Encerrar C09.1 somente com provas exigidas e auditoria; se não for possível,
  registrar retomada e manter seu estado correto.
- [ ] Só depois de C09.1 DONE, refinar C09.2 em contrato próprio: confirmação
  remota de leitura, erro sem falso sucesso, timeout incerto sem reenvio cego.
  Confirmar documentação e autorização antes de qualquer PATCH real.

**Saída:** dependência C09.2 efetivamente satisfeita ou impedimento concreto.
Não alterar dependências de C11 para contornar a Notifications API.

## 2. Refinar e entregar C11.1 (OH-01)

**Arquivos de partida:** GitHubHttpClient.kt, GitHubPagination.kt,
GitHubSessionManager.kt, SecureSessionStore.kt, RepositoryFavorites.kt,
modelos/telas dos recursos. Todos sob app/src/main/java/com/playertwo/controlegithub/.
**Documentos:** criar plan/contracts/C11.1.md apenas quando elegível para
refinamento; atualizar seu apontamento no backlog, evidência e PROJECT_STATE
conforme execução. Não editar contratos DONE para ocultar nova obrigação.

**Consome:** respostas válidas dos leitores existentes e identidade da sessão.
**Produz:** persistência isolada e leitura offline; contrato deve fixar interfaces
de leitura/gravação/invalidação e metadados antes de qualquer consumidor novo.

- [ ] Confirmar C07.2/C08.2/C09.2/C10.2 DONE; se não, não promover C11.1.
- [ ] Inventariar recursos/páginas; definir escopo de cobertura de C11 sem reduzir
  o checkpoint a uma tela. Registrar exclusão de logs e credenciais.
- [ ] Fechar formato, limites numéricos de disco/entrada, schema, criptografia,
  exclusão de backup e atomicidade. Documentar decisão proporcional à base.
- [ ] Definir restauração offline com perfil mínimo persistido e validade local;
  revogação conhecida e expiração não podem ser mascaradas pelo cache.
- [ ] Escrever contrato com CACHE-01…08, arquivos/interfaces finais e provas.
- [ ] Na execução autorizada: demonstrar falhas dos cenários novos com fixtures,
  implementar o mínimo e comprovar persistência, reinício offline, isolamento,
  cleanup e resposta tardia. Não criar teste que apenas espelhe serialização.
- [ ] Rodar os gates e registrar docs/evidence/C11.1.md, auditoria e estado real.

**Gate:** contrato completo e dependências DONE antes de READY; funcionamento
offline após reinício e ausência de vazamento antes de DONE.

## 3. Refinar e entregar C11.2 (OH-02)

**Arquivos:** transporte/paginação/cache introduzido em C11.1, telas afetadas,
testes; plan/contracts/C11.2.md e docs/evidence/C11.2.md na etapa apropriada.
**Consome:** representações persistidas e identidade/geração de consulta.
**Produz:** atualização consistente, resultado preservado no erro e reconexão
limitada. Validators HTTP são tipados se adotados, nunca tokens em query/cache.

- [ ] Confirmar C11.1 DONE e definir REFRESH-01…06 no contrato.
- [ ] Verificar suporte atual dos endpoints a ETag/Last-Modified; se adotado,
  definir cabeçalhos de request/response e 304 sem corpo, com teste sem cache.
- [ ] Demonstrar falhas de reconexão duplicada, páginas misturadas, erro com cache,
  rate limit, cancelamento e resposta tardia antes da implementação.
- [ ] Implementar e validar atualização/reconexão, preservando scroll/filtros
  quando coerente; não iniciar agendamento em background de C15.2.
- [ ] Executar reinício → offline → reconexão → logout com dados de teste,
  inspeção visual e gates; registrar evidência e auditoria.

**Gate agregado:** M2 exige também aceites C07/C08/C09/C10, não apenas C11 DONE.

## 4. Markdown e links: definir a entrega antes de integrar

**Arquivos de leitura:** GitHubRepositoryDetailScreen.kt,
GitHubIssueDetailScreen.kt, GitHubPullRequestsScreen.kt, MainActivity.kt e
destinos de GitHubNotifications.kt, apenas conforme escopo escolhido.

- [ ] Escolher se OH-03/OH-05 serão evolução separada ou requisito de uma próxima
  entrega; solicitar decisão se isso ampliar o contrato. Não embutir no C09 ativo.
- [ ] Confirmar biblioteca/licença, subset de formatação, limites e fallback
  textual. Fixar MD-01/02 e LINK-01…04 em critérios verificáveis.
- [ ] Definir contrato compartilhado de conteúdo (texto + contexto opcional do
  repo/ref/caminho) e roteamento (URI → destino suportado/fallback/rejeição).
- [ ] Testar host falso, userinfo, escapes inválidos, esquema perigoso, links
  relativos sem contexto, Unicode/texto longo, acessibilidade e Voltar.
- [ ] Implementar componente reutilizável somente na entrega autorizada;
  sem HTML ativo, imagens remotas automáticas, fetch autenticado externo ou
  Android App Links incidental.

**Saída:** leitura segura com fallback e evidência de navegação; nenhuma mutação.

## 5. Escrita em C12/C13 (OH-04)

**Arquivos:** os leitores/telas de issue/PR existentes, novo componente de
composição e transporte mutativo a definir em contrato, testes e evidências.
**Consome:** destino/identidade válidos, C11.2, Markdown/links se selecionados.
**Produz:** uma ação remota por entrega, com rascunho, confirmação e reconciliação.

- [ ] Selecionar C12.1 ou C13.1 pela ordem canônica e pré-requisitos; não fazer
  editor, criação, comentário, labels e revisão numa única alteração.
- [ ] Fixar destino, campos, retenção de rascunho, descarte/logout, preview local,
  estados de envio e permissões usando documentação oficial atual.
- [ ] Cobrir WRITE-01…03 com fixture; incluir WRITE-04 em C13.2.
- [ ] Implementar ação selecionada sem fila offline ou retry mutativo automático.
- [ ] Validar remotamente apenas em repo/issue/PR de teste explicitamente
  autorizado; confirmar o resultado no GitHub sem persistir dados privados.
- [ ] Registrar testes/inspeção, auditoria, CI e aceite conforme contrato.

## 6. Revisão opcional e ideias futuras

- [ ] Usar OH-06 como checklist de regressão de diffs; reproduzir um defeito
  antes de alterar C08.2, que já está DONE.
- [ ] Para OH-07…14, manter no registro de ideias até seleção pelo proprietário;
  não criar navegador completo, downloads, social ou multiaccount incidentalmente.
- [ ] Manter favoritos locais distintos de star e busca local distinta de global.

## Comandos e encerramento de cada entrega

```powershell
python scripts/check_plan.py
git diff --check
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug --console=plain
# Com emulador ativo e para mudanças de navegação:
.\gradlew.bat connectedDebugAndroidTest --console=plain
```

Evidência registra comando, resultado, ambiente, HEAD + diff não commitado,
cenários exercidos e limitações. Não declarar CI, comparação real, inspeção visual
ou aceite humano por inferência. Se um gate não rodou, escrever NOT_RUN e a
próxima ação. Parar no término de uma entrega ou no impedimento que dependa do
usuário; não iniciar várias tarefas incompletas. Commit/push não são passos
automáticos deste plano.
