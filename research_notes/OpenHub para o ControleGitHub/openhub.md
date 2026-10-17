# OpenHub como referência para ControleGitHub

## O que aproveitar primeiro?

### Takeaway
Aproveitar comportamentos e casos de teste: cache offline por conta/recurso, roteamento de links GitHub e composição Markdown. Não importar a arquitetura Java/MVP para o app Kotlin/Compose.

### Cited Findings
- `AppRetrofit.BaseInterceptor` diferencia cache por login (`uniqueLoginId`), usa `FORCE_CACHE` offline e `FORCE_NETWORK` ao atualizar. `NetworkBaseInterceptor` reescreve Cache-Control quando existe `forceNetWork`. É evidência de implementação, não de funcionamento testado. — [AppRetrofit.java](https://github.com/ThirtyDegreesRay/OpenHub/blob/fcb9b422e8013169e2328a6e09c675ce11a27ad6/app/src/main/java/com/thirtydegreesray/openhub/http/core/AppRetrofit.java)
- `AppOpener.launchUrl` abre perfil/repositório/issue/releases/commit em telas nativas e usa Custom Tabs/browser quando não reconhece a rota. — [AppOpener.java](https://github.com/ThirtyDegreesRay/OpenHub/blob/fcb9b422e8013169e2328a6e09c675ce11a27ad6/app/src/main/java/com/thirtydegreesray/openhub/util/AppOpener.java)
- Editor oferece títulos, negrito, itálico, citação, código, link, lista e menção; preview renderiza Markdown, mas contém `FIXME click link cause exception`. — [MarkdownEditorFragment.java](https://github.com/ThirtyDegreesRay/OpenHub/blob/fcb9b422e8013169e2328a6e09c675ce11a27ad6/app/src/main/java/com/thirtydegreesray/openhub/ui/fragment/MarkdownEditorFragment.java); [MarkdownPreviewFragment.java](https://github.com/ThirtyDegreesRay/OpenHub/blob/fcb9b422e8013169e2328a6e09c675ce11a27ad6/app/src/main/java/com/thirtydegreesray/openhub/ui/fragment/MarkdownPreviewFragment.java)

### Inferences
- C11: implementar cache próprio no desenho existente; provar isolamento de sessão, conteúdo antigo identificado, atualizar com rede, falha de rede preservando conteúdo e logout limpando dados sensíveis. Não copiar o singleton com token mutável nem a reescrita para Cache-Control `public`.
- C09/C12/C13: inspiração mais relevante é texto + prévia + atalhos e preservação do rascunho; reimplementar em Compose com renderização segura e links controlados. Preview não deve enviar texto ao GitHub.
- Links: útil ao transformar links de Markdown/notificações em navegação nativa; manter fallback e testes para host inválido, URL externa e rota desconhecida. Não é necessário ampliar escopo para multiaccount.

### Gaps
- Nenhum APK/build/fluxo OpenHub executado; offline e preview não receberam validação funcional nesta pesquisa.

## Arquivos, branches e diff trazem algo novo?

### Takeaway
Servem para comparar ergonomia e testes; não tratar como lacunas novas do ControleGitHub, pois o coordenador verificou C08.2 DONE.

### Cited Findings
- `RepoFilesPresenter` guarda branch/caminho, usa branch padrão, gera breadcrumb, volta ao diretório pai, reinicia o caminho ao trocar branch, ordena diretórios antes dos arquivos e tem cache em memória pela combinação branch/caminho. — [RepoFilesPresenter.java](https://github.com/ThirtyDegreesRay/OpenHub/blob/fcb9b422e8013169e2328a6e09c675ce11a27ad6/app/src/main/java/com/thirtydegreesray/openhub/mvp/presenter/RepoFilesPresenter.java)
- `HtmlHelper` gera Markdown/código/diff em HTML, oferece quebra de linha e numeração antes/depois, corrige links relativos e imagens usando contexto do repositório. O cabeçalho declara código copiado de Kosh e modificado. — [HtmlHelper.java](https://github.com/ThirtyDegreesRay/OpenHub/blob/fcb9b422e8013169e2328a6e09c675ce11a27ad6/app/src/main/java/com/thirtydegreesray/openhub/ui/widget/webview/HtmlHelper.java)

### Inferences
- Usar como checklist de regressão: branch com `/`, caminho profundo, breadcrumbs, voltar, mudança de branch, arquivo removido/renomeado, patch ausente e hunk sem quantidade explícita.
- Não transportar o parser HTML/regex antigo. A semântica de links relativos e números de diff é interessante; a implementação exige validação independente.

### Gaps
- Não comparado pixel a pixel nem executado em dispositivo. Casos sugeridos não são provas de bug no projeto atual.

## Arquitetura, manutenção e licença permitem copiar código?

### Takeaway
É uma referência funcional antiga, com GPL-3.0 declarada. Adoção de fonte/assets pede decisão explícita sobre licença e atribuições; reimplementação independente dos comportamentos é o caminho recomendado.

### Cited Findings
- `app/build.gradle` usa Android Support, Java 8, ButterKnife, GreenDAO, RxJava, Retrofit e Dagger; `gradle.properties` fixa compile/target SDK 29, RxJava 1.1.0 e Retrofit 2.1.0; raiz usa AGP 4.1.0 e jcenter. — [app/build.gradle](https://github.com/ThirtyDegreesRay/OpenHub/blob/fcb9b422e8013169e2328a6e09c675ce11a27ad6/app/build.gradle); [gradle.properties](https://github.com/ThirtyDegreesRay/OpenHub/blob/fcb9b422e8013169e2328a6e09c675ce11a27ad6/gradle.properties); [build.gradle](https://github.com/ThirtyDegreesRay/OpenHub/blob/fcb9b422e8013169e2328a6e09c675ce11a27ad6/build.gradle)
- Consulta em 2026-10-17: último commit retornado para master é fcb9b42, de 2021-03-21 (`Remove Bugly.`); última release é v3.2.1, publicada em 2021-03-23. A API indica archived=false e pushed_at=2024-05-30, o que não demonstra atualização recente de master. — [commit](https://github.com/ThirtyDegreesRay/OpenHub/commit/fcb9b422e8013169e2328a6e09c675ce11a27ad6); [release](https://github.com/ThirtyDegreesRay/OpenHub/releases/tag/v3.2.1); [API metadata](https://api.github.com/repos/ThirtyDegreesRay/OpenHub)
- README declara GPL-3.0 e informa 13 arquivos com trechos vindos do FastHub/Kosh. — [README License](https://github.com/ThirtyDegreesRay/OpenHub#license); [LICENSE](https://github.com/ThirtyDegreesRay/OpenHub/blob/master/LICENSE)

### Inferences
- Não recomendar fork como base técnica nem migrar autenticação Device Flow existente. A idade observada reduz o valor das dependências como escolha atual; não prova que o app deixou de funcionar.
- Não assumir que tradução Java→Kotlin elimina obrigações de código derivado. Esta pesquisa não decide a licença do ControleGitHub.

### Gaps
- Compatibilidade jurídica do projeto local não avaliada; não foi feita auditoria de cada um dos 13 arquivos citados pelo README.
