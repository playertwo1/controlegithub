# Como executar o roadmap

O ROADMAP define o produto e o aceite dos checkpoints C00–C20. O backlog
`plan/tasks.json` é a única fonte de IDs, dependências e estados das tarefas.
PROJECT_STATE registra evidências e limitações observadas, sem duplicar estados.
Os checkpoints agregam tarefas; não são a unidade de implementação.

## Seleção e contexto

1. Leia AGENTS e PROJECT_STATE; rode `python scripts/check_plan.py`.
2. Se o usuário indicou uma tarefa, use-a. Caso contrário, retome IN_PROGRESS;
   na ausência dela, escolha a primeira READY na ordem do backlog cujas
   dependências estejam DONE. Não escolha por número de checkpoint.
3. Leia somente a seção Cxx correspondente no ROADMAP, o contrato da tarefa
   e os arquivos necessários. Localize código antes de abrir documentos extras.
4. PLANNED é uma entrega futura, não uma instrução pronta para implementar.
   Antes de promovê-la, escreva seu contrato usando o modelo abaixo. Investigue
   o código e a documentação oficial atual da API. Não invente decisões externas.
5. Se a tarefa selecionada depender de informação ausente, registre BLOCKED
   com o impedimento e a pergunta concreta; continue outra READY independente.

## Estados e contrato

PLANNED → READY → IN_PROGRESS → DONE. BLOCKED pode ocorrer após refinamento
ou execução; retorne a READY quando o impedimento estiver resolvido.
No máximo uma tarefa IN_PROGRESS nesta cópia de trabalho. DONE exige critérios
comprovados, evidência versionada e revisão independente quando AGENTS exigir.
Código parcialmente implementado continua IN_PROGRESS ou BLOCKED.

Cada READY deve apontar `contract` para um arquivo com estas seções:

```markdown
# ID — resultado
## Escopo
Comportamento incluído e exclusões que delimitam esta entrega.
## Pré-condições e decisões
Entradas disponíveis, permissões necessárias e decisões resolvidas.
## Aceite
Cenários numerados: dado [estado], quando [ação], então [resultado observável].
Incluir a falha relevante à entrega; não repetir testes sem risco novo.
## Verificação
Comandos exatos, ambiente e procedimento de uso real necessário.
## Encerramento
Artefatos esperados e revisão exigida, com local de registro.
```

O campo `acceptance` no backlog resume a fronteira; o contrato detalha os
cenários somente quando a tarefa estiver próxima da execução. Se houver
conflito, corrija os documentos antes de implementar. Critério novo não pode
reduzir o aceite do checkpoint nem ampliar o escopo autorizado.

## Conclusão e retomada

- Implemente uma entrega completa através das camadas necessárias. Não crie
  tarefas separadas para cada classe, tela, endpoint ou teste.
- Aplique acessibilidade, proteção de credenciais e estados de erro durante
  cada entrega. C16/C17 verificam o conjunto; não adiam esses requisitos.
- Verifique os critérios, execute os checks exigidos por AGENTS e registre
  comando, resultado, ambiente, SHA testado e links de artefatos em
  `docs/evidence/ID.md`. Não copie tokens, dados privados ou chaves.
- Uma tarefa DONE aponta `evidence` para esse registro. Resultado NOT_RUN
  mantém pendente o critério correspondente; um build não prova integração.
- Antes de encerrar a sessão, registre no estado o que mudou, falhas e próxima
  ação. Preserve mudanças parciais e descreva como retomá-las. Não marque DONE
  apenas porque a sessão terminou.
- Um checkpoint fecha quando suas tarefas e o aceite agregado passam. Cada
  dependência de checkpoint no ROADMAP é gate para fechar o checkpoint. Ela não
  impede começar tarefas cujas dependências individuais já passaram. Cada marco
  fecha somente quando todos os checkpoints listados para ele no ROADMAP e suas
  dependências transitivas passam. Use os gates explícitos do ROADMAP;
  ser dependência da última tarefa não substitui o aceite do checkpoint ou do
  marco. No M0, C01.1 pode avançar após C00.1, mas M0 exige C00.1, C00.2
  (auditoria aceita) e C01 concluídos.
- Faça refinamento apenas do próximo grupo executável. Divida uma tarefa quando
  houver resultados independentes ou quando não couber em uma mudança coerente;
  não divida somente para aumentar a contagem. Mantenha IDs existentes e registre
  substituições. Não crie um mecanismo de execução automática para este backlog.

## Por que essa estrutura

A revisão encontrou checkpoints com comportamentos independentes (C14/C15),
dependências expressas apenas por agrupamentos e critérios futuros sem ambiente
definido. Mantemos os 21 checkpoints para rastreabilidade e refinamos as entregas
sem transformar cada detalhe técnico em uma etapa.

- [INVEST e SMART, Bill Wake](https://xp123.com/invest-in-good-stories-and-smart-tasks/):
  pequenas entregas de valor, fronteiras sem sobreposição e critérios testáveis.
  Aplicação: dividir por comportamento completo, com aceite observável.
- [Anthropic: agentes em trabalhos longos](https://www.anthropic.com/engineering/effective-harnesses-for-long-running-agents):
  progresso incremental, lista estruturada e evidência antes de declarar sucesso.
  Aplicação: backlog JSON, uma tarefa ativa e registro de retomada.
- [OpenAI: harness engineering](https://openai.com/index/harness-engineering/):
  entrada curta, conhecimento versionado e contexto progressivo.
  Aplicação: AGENTS aponta para contratos; critérios e estados não são copiados.

Estas são adaptações ao nosso projeto, não uma certificação ou garantia de que
qualquer modelo agirá corretamente. O validador detecta inconsistências
estruturais; a revisão e os testes avaliam o significado e o funcionamento.
