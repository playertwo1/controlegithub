# Abrir o destino dos avisos e exibir imagens do README no ControleGitHub

Status: pedido de implementação para o Codex. Relato do Rafael, conferido por leitura da main em `bfe0025` (README anuncia v2.7.0); não reproduzido em aparelho por este PR. O PR #2 trata da caixa de avisos; este pedido foca no toque na notificação do sistema e na renderização do README.

## 1. Toque no aviso do Android deve abrir o item certo

- **Hoje:** ao tocar num aviso do sistema, o app abre na tela inicial. Em [LocalAlerts.kt](../../app/src/main/java/com/playertwo/controlegithub/LocalAlerts.kt#L105), `notifyNewEvents` cria um `PendingIntent` para `MainActivity` sem identificar o aviso ou seu destino. [MainActivity.kt](../../app/src/main/java/com/playertwo/controlegithub/MainActivity.kt#L69) já lê atalhos na abertura e em novos intents.
- **Esperado:** um aviso individual de PR ou issue abre diretamente o detalhe daquele item, sem passar pelo painel ou exigir outro toque. Para um tipo com destino de repositório válido, abra o repositório. Reuse a validação de destino de [GitHubNotifications.kt](../../app/src/main/java/com/playertwo/controlegithub/GitHubNotifications.kt#L81) e a navegação já usada em [GitHubNotificationsScreen.kt](../../app/src/main/java/com/playertwo/controlegithub/GitHubNotificationsScreen.kt#L300).
- **Agrupados e exceções:** o resumo de vários avisos abre **Avisos**, sem escolher um item arbitrário. Se houver avisos individuais no grupo, cada um abre seu próprio destino. Para destino indisponível, avise e abra **Avisos** (se o detalhe do aviso estiver disponível, pode focá-lo). Na abertura a frio, aguarde a restauração da sessão; sem acesso, mantenha o fluxo de login/erro. Não marque como lido nem faça outra escrita só para navegar.
- **Aceite:** em emulador com dados fictícios, toque em um PR e numa issue de repositórios diferentes, com app fechado e já aberto; toque num segundo aviso e confira que não reabre o primeiro. Teste o toque no resumo, falha de acesso e Voltar, sem regressão nos atalhos do launcher ou reabrir o mesmo destino após rotação.

## 2. Mostrar as imagens no corpo do README sem toque extra

- **Hoje:** o detalhe do repositório chama [GitHubMarkdownContent](../../app/src/main/java/com/playertwo/controlegithub/GitHubRepositoryDetailScreen.kt#L454) com contexto do repositório e branch. [GitHubMarkdownContent.kt](../../app/src/main/java/com/playertwo/controlegithub/GitHubMarkdownContent.kt#L113) usa o toque para ativar a prévia, que carrega a imagem no diálogo.
- **Esperado:** as imagens suportadas e permitidas aparecem no corpo do README quando chegam à área visível, sem tocar nelas. O toque continua disponível para ampliar, sem abrir diálogo sozinho. Preserve proporções e texto alternativo; mostre estado de carregamento e, em erro, alternativa/retry sem esconder o resto do README. Não baixe tudo de uma vez nem deixe o scroll travar.
- **Aceite:** usando respostas fictícias, inclua imagem Markdown com caminho relativo, imagem HTML já suportada pelo parser (como banner/screenshot), endereço HTTPS permitido, imagem quebrada e README sem imagens. Use o contexto correto do arquivo/branch. Preserve as restrições existentes de origem, credenciais, tipos e tamanho; não envie token para hosts externos nem relaxe proteções para ativar download automático. Se um tipo, como SVG, não tiver decodificação segura hoje, trate o fallback e deixe a limitação explícita em vez de mostrar a imagem como carregada.
- Limite a mudança ao README no detalhe do repositório; não altere o comportamento de imagens em comentários, issues ou outros leitores por efeito colateral.

## Entrega

Adicione testes de regressão nas duas frentes, rode `./gradlew testDebugUnitTest lintDebug` e valide os dois fluxos no emulador com dados fictícios, se disponível. Anexe resultado dos testes e informe separadamente o que não foi validado no aparelho. Não use tokens ou notificações reais para os testes e não publique APK nem faça merge por este rascunho.
