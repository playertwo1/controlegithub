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
repositórios, detalhe/README, issues/comentários, PRs/arquivos/diffs/checks e
Actions/jobs/logs têm integrações de leitura. Favoritos são locais, por conta.
O estado por tela, as limitações e as verificações estão
em [PROJECT_STATE.md](../PROJECT_STATE.md) e `docs/evidence/`.

Escrita no GitHub e cache persistente permanecem fora do estado implementado;
dependências e decisões futuras seguem o roadmap. C09.1 (caixa de notificações)
está concluído e comprovado com OAuth Device Flow real e testes instrumentados;
`GET /notifications` foi validado empiricamente retornando 200 OK com token OAuth
clássico (escopos `repo` e `notifications`), resolvendo a divergência histórica de
documentação. C09.2 (marcar como lida) é a próxima etapa de mutação.

## Notificações e Autenticação (C09.1)

A caixa de notificações integra a aba autenticada "Avisos" com a API do GitHub (`GET /notifications`). A autenticação utiliza o token OAuth App obtido via Device Flow com escopos `read:user repo notifications`.

### Fluxo de Autenticação e Consulta de Notificações

```mermaid
sequenceDiagram
    autonumber
    actor User as Usuário
    participant UI as ControleGitHub UI (Avisos)
    participant Client as GitHubHttpClient
    participant API as GitHub REST API (/notifications)
    participant Origin as Telas de Origem (Issue / PR / Repo)

    User->>UI: Abre aba "Avisos"
    UI->>Client: GET /notifications?all=true&per_page=50
    Note over Client,API: Header Authorization: Bearer &lt;oauth_token&gt;
    Client->>API: HTTP GET /notifications
    alt 200 OK (Lista retornada)
        API-->>Client: JSON com notificações (threads)
        Client-->>UI: Coleção deduplicada e tipada
        UI->>User: Exibe lista com filtros locais (Todas / Não lidas)
    else 403 Forbidden (Escopo insuficiente)
        API-->>Client: 403 Forbidden
        Client-->>UI: Erro tipado (OAuth rejeitado)
        UI->>User: Exibe aviso explicativo; sessão mantida ativa
    else 401 / 429 / Rede
        API-->>Client: Erro HTTP / timeout
        Client-->>UI: Estado de erro específico
        UI->>User: Mensagem amigável com opção de tentar novamente
    end

    opt Navegação para Origem
        User->>UI: Clica em card de notificação
        UI->>UI: Valida subject URL contra domínios confiáveis
        alt Origem suportada (Issue / PR / Repo)
            UI->>Origin: Abre detalhe da Issue / PR / Repo
            User->>Origin: Clica em "Voltar aos avisos"
            Origin-->>UI: Retorna à lista com posição e filtros preservados
        else Origem desconhecida / não suportada
            UI->>User: Exibe card de detalhe com fallback textual acessível
        end
    end
```

### Arquitetura de Componentes da Caixa de Notificações

```mermaid
flowchart TD
    subgraph UI ["Camada de Interface (Jetpack Compose)"]
        NavHost["Navegação Principal (Dock)"]
        Screen["GitHubNotificationsScreen<br/>(Aba Avisos)"]
        FilterRow["Filtros Locais<br/>(Não lidas / Todas)"]
        Card["NotificationCard<br/>(Item da lista)"]
        Detail["NotificationDetailScreen<br/>(Fallback / Origem)"]
    end

    subgraph Domain ["Modelos e Mapeamento"]
        Model["GitHubNotification<br/>(id, reason, subject, unread, repository)"]
        Subject["GitHubNotificationSubject<br/>(title, type, url)"]
        Destination["NotificationDestination<br/>(Issue / PullRequest / Repository / Unsupported)"]
    end

    subgraph Data ["Cliente HTTP e Rede"]
        Pager["GitHubNotificationPager<br/>(Paginação e deduplicação)"]
        HttpClient["GitHubHttpClient<br/>(Transporte seguro HttpURLConnection)"]
        Session["GitHubSessionManager<br/>(Keystore cifrado)"]
    end

    NavHost --> Screen
    Screen --> FilterRow
    Screen --> Card
    Card --> Detail
    Screen --> Pager
    Pager --> HttpClient
    HttpClient --> Session
    Pager --> Model
    Model --> Subject
    Subject --> Destination
    Detail --> Destination
```

## Relação com GitDeck

Inspiração funcional, sem copiar código, identidade ou imagens. O GitDeck usa
frontend web e servidor Node; este projeto começa com implementação Android própria.

## Referência OpenHub e propostas futuras

Referência de comportamentos, sem importação de fonte/assets/traduções. OpenHub
usa Java/MVP e componentes antigos; não se propõe migrar a base Kotlin/Compose
nem o transporte HttpURLConnection. Sua GPL-3.0 e atribuições precisam ser
avaliadas antes de qualquer incorporação de material protegido.

O [detalhamento](superpowers/specs/2026-10-17-openhub-aproveitamento-design.md)
propõe cache privado cifrado, chave por conta/autorização/recurso/consulta e
gravação atômica; formato, limites e cobertura serão fixados no contrato C11.1.
Reinício offline exige tratar a restauração de sessão, que hoje consulta perfil
na rede. Logs Actions e credenciais ficam fora do cache de conteúdo.
ETag/Last-Modified/304 exigem extensão explícita do transporte se selecionados;
não são suporte já implementado.

Markdown formatado e prévia local segura são propostas; o conteúdo atual é
inerte. Roteador reutilizável deve preservar destinos existentes, validar URLs
e usar fallback sem encaminhar credencial. Não introduzir WebView ativa,
dependência nova, Android App Links ou escrita offline incidentalmente.
Decisões finais e aceites pertencem aos contratos refinados, não a esta seção.
