# Arquitetura inicial

## Decisões

Android nativo em Kotlin e Jetpack Compose, módulo único `app`, minSdk 26.
AGP 9.1.1, Gradle 9.3.1, JDK 17 e plugin Compose 2.2.10 seguem a matriz
[oficial de compatibilidade](https://developer.android.com/build/releases/agp-9-1-0-release-notes).
As versões são fixadas para reprodução, sem dependências dinâmicas.

O módulo único `app` contém a navegação Compose, telas e integração Android.
`DemoData` alimenta somente a demonstração identificada como tal. Fluxos reais
usam cliente REST GitHub, armazenamento cifrado da sessão protegido pelo Android
Keystore e estado de tela separado conforme necessário. A implementação continua
deliberadamente sem backend próprio, banco de cache ou framework de injeção de
dependências.

## Integração atual e limites

Autenticação usa o [Device Flow documentado pelo GitHub](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/authorizing-oauth-apps#device-flow).
O Client ID é público; client secret não é embutido no aplicativo. O token fica
protegido no dispositivo e não é enviado a um backend próprio. Perfil,
repositórios, detalhe, lista de issues e partes de Actions já consultam a API em
modo somente leitura. O estado por tela, as limitações e as verificações estão
em [PROJECT_STATE.md](../PROJECT_STATE.md) e `docs/evidence/`.

Escrita no GitHub e cache persistente permanecem fora do estado implementado;
dependências e decisões futuras seguem o roadmap. A caixa de notificações está
bloqueada no backlog até haver compatibilidade de API com a decisão de OAuth
documentada em `plan/contracts/C09.1.md`.

## Relação com GitDeck

Inspiração funcional, sem copiar código, identidade ou imagens. O GitDeck usa
frontend web e servidor Node; este projeto começa com implementação Android própria.
