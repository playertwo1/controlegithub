# ControleGitHub

Seu GitHub, sob controle. Um aplicativo Android nativo em Kotlin e Jetpack
Compose, inspirado na proposta do [GitDeck](https://github.com/debba/gitdeck) e
com interface própria para celular.

**Estado atual:** autenticação GitHub e alguns fluxos de leitura usam dados reais
da conta conectada. As áreas ainda não integradas são identificadas no app; o
modo de demonstração usa dados fictícios rotulados como demonstração. O produto
continua em desenvolvimento e ainda não oferece operações de escrita no GitHub.
O estado detalhado e as limitações verificadas estão em
[PROJECT_STATE.md](PROJECT_STATE.md).

## O que funciona hoje

- Autorização por Device Flow, restauração de sessão cifrada no aparelho e logout.
- Perfil da conta, lista de repositórios e busca/filtros locais dos itens carregados.
- Detalhe do repositório e lista/filtros de issues em leitura.
- Fluxos de Actions para workflows, execuções, jobs e uma tela de logs em
  implementação. C10.2 ainda aguarda inspeção visual da tela de logs e comparação
  com um job real; veja [roadmap](ROADMAP.md) e [evidência C10.2](docs/evidence/C10.2.md).
- Modo de demonstração para áreas ainda não conectadas, sem apresentar amostras
  como dados da conta.

## Design

[Catálogo das 36 telas e estados conceituais](docs/design/README.md): inspiração
One UI, tema escuro e menu inferior flutuante. As imagens mostram o produto
planejado; não comprovam a implementação das telas.

![Referência One UI com menu flutuante](docs/design/00-overview-oneui-v2.png)

## Capturas

As imagens abaixo documentam capturas do baseline visual inicial no emulador.
Elas antecedem as integrações reais e mostram dados fictícios; consulte o estado
atual em [PROJECT_STATE.md](PROJECT_STATE.md).

| Entrada | Painel | Repositórios |
|---|---|---|
| ![Entrada](docs/screens/01-welcome.png) | ![Painel](docs/screens/02-dashboard.png) | ![Repositórios](docs/screens/03-repositories.png) |

| Detalhe | Trabalho | Notificações |
|---|---|---|
| ![Detalhe](docs/screens/04-detail.png) | ![Trabalho](docs/screens/05-work.png) | ![Notificações](docs/screens/06-notifications.png) |

## Executar

1. Abra a pasta no Android Studio e aguarde a sincronização Gradle.
2. Use JDK 17, SDK Android 36 e um dispositivo Android 8.0 (API 26) ou superior.
3. Configure o SDK no Android Studio ou em `local.properties` (não versionado).
4. Para habilitar o login local, configure `GITHUB_OAUTH_CLIENT_ID` conforme
   [docs/AUTH.md](docs/AUTH.md). Sem essa propriedade, o app informa que a
   autorização está indisponível; a demonstração permanece acessível.
5. Execute o módulo `app`.

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
# Com emulador ou dispositivo conectado:
./gradlew connectedDebugAndroidTest
```

Windows: use `.\gradlew.bat`.
APK local: `app/build/outputs/apk/debug/app-debug.apk`.

## Roadmap e documentos

O [roadmap](ROADMAP.md) define os 21 checkpoints e cinco marcos. O
[backlog](plan/tasks.json) contém as 42 tarefas e seus estados canônicos; rode
`python scripts/check_plan.py` antes de selecionar trabalho. O
[protocolo de execução](docs/EXECUTION.md) define como continuar uma tarefa; cada
entrega READY tem um contrato em `plan/contracts/`. O
[estado atual](PROJECT_STATE.md) e as evidências em `docs/evidence/` registram o
que foi verificado e o que segue pendente. Consulte também as
[decisões de arquitetura](docs/ARCHITECTURE.md), o [contrato visual](docs/DESIGN.md),
o [guia OAuth](docs/AUTH.md) e a [verificação histórica da fundação](docs/VERIFICATION.md).

A base Gold foi adaptada de [Ideias Standard](https://github.com/playertwo1/ideias_standard);
veja [origem e adaptações](docs/STANDARD_ADOPTION.md).

## Contribuir

Abra uma issue descrevendo o problema, altere o mínimo necessário, execute os
checks e envie um PR com evidência. Nunca inclua credenciais nas capturas ou logs.
Veja [CONTRIBUTING.md](CONTRIBUTING.md) e [SECURITY.md](SECURITY.md).
teste do appp
