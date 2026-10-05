# Configuração de acesso ao GitHub

O app implementa login GitHub por Device Flow e usa a sessão cifrada no aparelho
para os fluxos reais descritos em [PROJECT_STATE.md](../PROJECT_STATE.md). O
modo de demonstração continua disponível sem sessão e seus dados são fictícios e
identificados como demonstração. Áreas ainda não integradas não devem aparentar
ser dados da conta.

O OAuth App do projeto já foi registrado na conta `playertwo1`, com Device Flow
ativado e expiração de tokens habilitada. [Abrir configurações do app](https://github.com/settings/applications/3900445).

## Configurar o OAuth App

1. No GitHub, abra **Settings → Developer settings → OAuth Apps → New OAuth App**.
2. Use `ControleGitHub Android` como nome e
   `https://github.com/playertwo1/controlegithub` como homepage pública.
3. Para o campo obrigatório de callback, use a mesma URL do repositório. O Device
   Flow não usa callback; não habilite wildcard matching.
4. Habilite **Enable Device Flow** e mantenha tokens com expiração habilitados.
5. Registre o app e copie o **Client ID**. Nunca copie o Client Secret para o
   Android, para este repositório ou para a conversa. Device Flow não exige
   client secret.
6. Configure somente o Client ID na propriedade Gradle local
   `GITHUB_OAUTH_CLIENT_ID`, por exemplo em
   `%USERPROFILE%\.gradle\gradle.properties`:

   ```properties
   GITHUB_OAUTH_CLIENT_ID=Iv1.seu_client_id
   ```

   A propriedade é pública e pode compor o APK. Não adicione `gradle.properties`
   pessoal ao Git.

Sem a propriedade, a tela de entrada informa que a autorização está
indisponível; ela ainda permite abrir a demonstração. Um Client ID configurado
habilita o pedido de código, mas não concede acesso por si só: o usuário ainda
precisa autorizar o app no GitHub. Não use um ID inventado como se fosse
integração.

## Escopos planejados

| Escopo | Motivo no produto 1.0 | Limite que o usuário deve conhecer |
|---|---|---|
| `read:user` | Identificar e exibir o perfil da conta conectada. | Permite leitura do perfil; não é necessário para ler informação pública sem autenticação. |
| `repo` | Repositórios privados e operações em issues/PRs previstas para C12/C13. | Concede acesso amplo de leitura e escrita a repositórios públicos e privados visíveis ao usuário, incluindo código e webhooks; também permite gerir projetos pessoais e recursos de organizações, como projetos, convites, associações a equipes e webhooks. OAuth Apps não oferecem permissões granulares por operação. |
| `notifications` | Ler a caixa de notificações no C09. | Também permite marcar threads como lidas e gerir inscrições/watch de repositórios. |

Não pedir `admin:org`, `delete_repo`, `gist` ou escopos de webhooks: não há
funcionalidade 1.0 que os justifique. Sem `repo`, conteúdo privado e issues/PRs
restritos podem falhar; políticas da organização e SAML também podem negar
acesso mesmo após autorização. O app deverá mostrar a funcionalidade indisponível
e explicar a permissão necessária, sem repetir autorização automaticamente.

O GitHub recomenda GitHub Apps em vez de OAuth Apps quando possível, pois usam
permissões granulares; também recomenda PKCE para clientes nativos. A opção
Device Flow está mantida por ser a decisão atual do roadmap, mas o GitHub alerta
que este fluxo pode facilitar phishing e recomenda habilitá-lo apenas em
ambientes restritos (CLI/IoT/headless). Reavaliar essa decisão antes de expandir
o login para usuários além do proprietário.

## Revogação

O logout do app remove a sessão e os tokens locais. A concessão OAuth permanece
ativa no GitHub até que o usuário a revogue em **Settings → Applications →
Authorized OAuth Apps**. O endpoint REST para revogar uma autorização exige
`client_secret`; esse segredo não pode ser distribuído num app Android nativo.
C03.2 oferece um atalho para as configurações oficiais, sem fingir revogação
remota. Se a política da organização, SAML ou permissões negarem acesso,
informar isso sem expor tokens ou dados privados em logs.

## Referências oficiais

- [Criar um OAuth App](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/creating-an-oauth-app)
- [Autorizar OAuth Apps e Device Flow](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/authorizing-oauth-apps)
- [Escopos de OAuth Apps](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/scopes-for-oauth-apps)
- [Boas práticas para criar OAuth Apps](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/best-practices-for-creating-an-oauth-app)
- [Revogar autorizações OAuth](https://docs.github.com/en/rest/apps/oauth-applications)
