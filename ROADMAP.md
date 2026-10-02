# Roadmap

## F0 — Fundação e experiência Android

- [x] Kotlin, Compose, Gradle Wrapper e CI.
- [x] Entrada, painel, lista/busca, detalhe, trabalho e notificações demonstrativos.
- [x] README, estado, padrão Gold e imagens das telas.
- [x] Catálogo de 36 telas/estados finais, inspiração One UI e menu flutuante.
- [ ] Auditoria independente da fundação.

Aceite: APK compila; busca e navegação passam nos testes; lint sem erros;
capturas correspondem ao app; demonstração claramente identificada.

## F1 — Conta GitHub e repositórios reais

- [ ] Registrar OAuth App com Device Flow; Client ID configurável.
- [ ] Autorização, polling conforme intervalo, expiração, cancelamento e logout.
- [ ] Token protegido com Android Keystore; remoção ao sair; sem token em logs.
- [ ] Cliente HTTPS GitHub, paginação, rate limit, erro e carregamento.
- [ ] Perfil e repositórios reais; busca e detalhe.

Aceite: conta conecta e desconecta; nenhum segredo no APK ou repositório;
erros de rede e autorização possuem estados visíveis; testes do fluxo HTTP.
Não solicitar acesso de escrita sem uma funcionalidade que justifique isso.

## F2 — Triagem diária

- [ ] Issues e PRs reais, filtros por estado, autor e repositório.
- [ ] Detalhes, atividade do repositório e arquivos do PR em leitura.
- [ ] Caixa de notificações, leitura e abertura do item.
- [ ] Actions e últimas execuções por repositório.
- [ ] Detalhe de execução e leitura de logs.
- [ ] Cache local e indicação explícita de dados desatualizados.

Aceite: paginação sem duplicações; refresh consistente; rate limit respeitado;
contas e dados em cache isolados; testes de falha e modo offline.

## F3 — Ações e produtividade

- [ ] Criação, comentários, edição/fechamento de issues e revisão de PRs com escopos proporcionais.
- [ ] Confirmação contextual para operações irreversíveis.
- [ ] Kanban local e resumo diário com origem rastreável; exportação Markdown.
- [ ] Favoritos e sincronização em segundo plano quando útil.

Aceite: mutações auditáveis, estados de falha recuperáveis e testes de regressão.

## F4 — Beta e distribuição

- [ ] Revisão de acessibilidade, tema claro, tablets e idiomas.
- [ ] Testes em API 26 e versão recente; desempenho e consumo de rede.
- [ ] Política de privacidade, assinatura e pacote de publicação.
- [ ] Distribuição beta e feedback antes da versão 1.0.

Aceite: auditoria independente, build release e processo de assinatura documentado.
Publicação em loja exige uma decisão específica do proprietário.

## Depois do MVP

GitLab, Forgejo, múltiplas contas, tráfego, segurança e insights serão avaliados
após uso real. A paridade completa com o GitDeck não é requisito do MVP.
