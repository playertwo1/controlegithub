# PROJECT — ControleGitHub

Contrato de visão. Atualize quando o projeto nasce e quando a visão muda; o resto (marcos, estado) fica em `ROADMAP.md` e `STATE.md`. Resumo do `README.md` e do `ROADMAP.md` (objetivo e escopo da 1.0).

## Problema
Acompanhar repositórios, issues, pull requests, notificações e Actions do GitHub pelo celular é fragmentado; falta um painel Android nativo que mostre o trabalho real da conta com segurança e permita as ações do dia a dia.

## Para quem
O dono da conta GitHub (uma conta por sessão), usando um celular Android (API 26+), em português.

## MVP — até três fluxos que entregam valor
1. Conectar a conta por OAuth Device Flow e ver perfil, repositórios (busca/filtros) e detalhe/README reais (M1).
2. Triagem diária em leitura: issues, PRs (arquivos, diff, checks), notificações e Actions/logs, com cache e recuperação (M2).
3. Produtividade: criar/editar/comentar issues, revisar PRs, favoritos, Kanban local e resumo diário (M3).

## Fora do MVP (escrito para não voltar escondido)
- GitLab/Forgejo, múltiplas contas simultâneas, editor de código, clone/push Git.
- Merge de PR, disparo de workflows, dashboards avançados e IA.
- Layout dedicado para tablet (tablet só recebe comportamento utilizável).

## Restrições
- Plataforma/ambiente: Android nativo, Kotlin + Jetpack Compose, API 26+, JDK 17, SDK 36; tema claro/escuro/sistema, inspiração One UI com menu flutuante.
- Dados sensíveis e privacidade: sessão cifrada no aparelho; nunca versionar tokens, chaves de assinatura ou configuração da máquina; dados de demonstração sempre rotulados.
- Custos, licenças, integrações obrigatórias: GitHub REST API via OAuth App (Device Flow, sem client secret; ver `docs/AUTH.md`). Inspirado no GitDeck.

## Como saberemos que funcionou
M4 — Versão 1.0 operante: release assinada, beta com uso real validado e operação documentada (checkpoint C20).

## Autoridade de produto
Usuário/proprietário (Rafael / playertwo1). Documentação não amplia autorização.

## Alternativas consideradas
[GitDeck](https://github.com/debba/gitdeck) como inspiração de proposta; interface própria para celular.
