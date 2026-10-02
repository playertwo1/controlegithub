# Arquitetura inicial

## Decisões

Android nativo em Kotlin e Jetpack Compose, módulo único `app`, minSdk 26.
AGP 9.1.1, Gradle 9.3.1, JDK 17 e plugin Compose 2.2.10 seguem a matriz
[oficial de compatibilidade](https://developer.android.com/build/releases/agp-9-1-0-release-notes).
As versões são fixadas para reprodução, sem dependências dinâmicas.

`MainActivity` contém a navegação e as telas. `DemoData` fornece o conjunto
demonstrativo e a busca. Estado de interface usa `rememberSaveable`; não há
banco, injeção de dependências ou backend nesta fase.

## Integração prevista

Na F1, separar cliente GitHub, armazenamento de sessão e estado de tela conforme
as necessidades reais. A autenticação deverá usar o
[Device Flow documentado pelo GitHub](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/authorizing-oauth-apps#device-flow).
O Client ID é público; client secret não deve ser embutido no aplicativo.
O token fica protegido no dispositivo e não é enviado a um backend próprio.

Cache e múltiplos provedores entram apenas nas fases que necessitarem deles.

## Relação com GitDeck

Inspiração funcional, sem copiar código, identidade ou imagens. O GitDeck usa
frontend web e servidor Node; este projeto começa com implementação Android própria.
