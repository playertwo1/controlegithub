# Design — One UI e navegação flutuante

Direção escolhida pelo proprietário: inspiração One UI, tema escuro e menu
inferior flutuante. Catálogo do produto planejado: [36 telas](design/README.md).
No tema escuro, fundo `#080808`, superfícies `#1C1C1E` e texto `#F5F5F7`; o
tema claro usa fundo `#F7F8FA` e superfície branca. O azul tem papel semântico
por tema para manter contraste: escuro `#80B3FF`, claro `#0052A4`. Texto
secundário usa `onSurfaceVariant` do Material 3. Fonte do sistema, cards de
28 dp, espaçamento base 8 dp.

Referências oficiais: [princípios One UI](https://developer.samsung.com/one-ui/index.html),
[layout](https://developer.samsung.com/one-ui/layout/basic.html),
[app bar](https://developer.samsung.com/one-ui/comp/app-bar.html) e
[acessibilidade](https://developer.samsung.com/one-ui/accessibility/layout-and-typo.html).
Inspiração em hierarquia, legibilidade e alcance; sem afiliação com Samsung.

## Contrato do menu

Quatro destinos na mesma ordem: **Início, Repos, Trabalho, Avisos**. Não adicionar
quinta aba. Favoritos ficam em Repos, Kanban em Trabalho e resumo em Início;
conta/configurações abrem por um atalho no painel.

Cápsula de raio 32 dp, elevação 8 dp e borda discreta. Margem lateral 16–20 dp,
distância de 10–14 dp da área de gestos. Ícones com rótulos, seleção azul e
indicador arredondado; alvo de toque mínimo 48 dp. Reservar espaço no conteúdo.

Ocultar em autenticação, teclado, editores e painéis modais. Detalhes focados
podem usar Voltar e sua ação no rodapé. Comentar, Ver alterações, Ver logs e
Salvar nunca devem ser substituídos pelo menu. Adaptar com texto ampliado.

Imagens geradas por IA orientam aparência; este contrato governa comportamento.
Exemplos de avatares, nomes e contagens são fictícios. Expiração e intervalo
de autorização vêm da API, não da imagem. Senha não coletada; token protegido
no aparelho. Offline mostra cache e horário; limite da API respeita retry/reset.
Permissão insuficiente terá mensagem contextual e acesso à revisão de permissões.

## Fluxo

Entrada → Explorar demonstração → Início / Repos / Trabalho / Avisos.
Um card de repositório abre o detalhe; Voltar retorna à seção anterior.
A busca aceita nome ou linguagem e explica quando não encontra resultados.

## Imagens conceituais

`design/` contém pranchas para a versão 1.0 planejada, geradas com `image_gen`.
Não são capturas nem evidência de recursos implementados. Variantes de tema
claro usam a mesma estrutura; detalhes e espaçamento seguem este documento.

## Capturas

`screens/01-welcome.png` até `screens/06-notifications.png` são capturas do app,
produzidas no emulador Pixel 9. Não são propostas geradas por IA.
Para atualizar: instalar o APK, navegar até cada tela e usar
`adb shell screencap -p /sdcard/screen.png` seguido de `adb pull`.

Dados e conta são exemplificativos. A indicação de demonstração aparece em todas
as seções; a entrada não simula um login concluído.

## Evolução

A F1 adicionará autorização, carregamento, erro, vazio e sessão expirada.
Antes do beta: TalkBack, tamanho de fonte, contraste, tema claro e telas grandes.
