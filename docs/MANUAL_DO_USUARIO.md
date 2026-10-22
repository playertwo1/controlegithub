# Manual do Usuário — ControleGitHub v1.0.0

Bem-vindo ao **ControleGitHub**, sua ferramenta nativa Android para acompanhamento ágil, gerenciamento de demandas e monitoramento de atividades no GitHub com total controle e privacidade local.

---

## 1. Pré-requisitos

- Dispositivo físico ou emulador com **Android 8.0 (API 26)** ou superior.
- Uma conta ativa na plataforma [GitHub](https://github.com).
- Conexão à internet ativa para autenticação inicial e sincronização de dados.

---

## 2. Instalação e Verificação

1. Acesse o canal oficial de distribuição em [GitHub Releases](https://github.com/playertwo1/controlegithub/releases).
2. Baixe o pacote oficial `app-release.apk` e o arquivo de assinaturas `SHA256SUMS.txt`.
3. (Opcional, mas recomendado) Verifique a integridade do arquivo através de um utilitário de checksum no seu computador ou terminal móvel:
   ```bash
   sha256sum app-release.apk
   ```
   O valor deve corresponder rigorosamente ao hash oficial:
   `F2A51D35AD2C4603B5DCF7A29D2A4F65DC00734DAD79CFE4DB2E9B40FAC04F71`
4. No seu dispositivo Android, toque no arquivo baixado para iniciar o instalador do pacote. Se solicitado, permita a instalação a partir do navegador ou gerenciador de arquivos ("Permitir desta fonte").
5. Conclua a instalação tocando em **Instalar** (ou **Atualizar**, caso já possua uma versão prévia).

---

## 3. Primeiro Acesso e Autenticação Segura (OAuth Device Flow)

O ControleGitHub adota o padrão de autorização **Device Flow**, garantindo que suas credenciais e senhas jamais passem pelo aplicativo:

1. Ao abrir o aplicativo, toque no botão **Entrar com GitHub**.
2. O aplicativo exibirá um código alfanumérico único de 8 caracteres (por exemplo: `WXYZ-5678`) e uma contagem regressiva de expiração.
3. Toque em **Abrir página de autorização** (ou acesse [github.com/login/device](https://github.com/login/device) no seu navegador).
4. Insira o código exibido e confirme a concessão das permissões de leitura/escrita necessárias no portal seguro do GitHub.
5. Volte ao aplicativo: a autorização será concluída automaticamente e você será conduzido ao **Painel Principal**.

> [!NOTE]
> Suas credenciais são armazenadas com criptografia de ponta a ponta no hardware seguro do dispositivo (`AndroidKeyStore`). Nenhum token é compartilhado com terceiros.

---

## 4. Navegação e Recursos Principais

A interface utiliza uma doca flutuante inferior para alternar de forma imediata entre as seções:

### 4.1 Painel Principal (Início)
- **Métricas em Tempo Real:** Exibe o volume consolidado de repositórios acessíveis, issues abertas e notificações não lidas.
- **Resumo Diário (Últimas 24h):** Apresenta uma síntese cronológica organizada por repositório de todas as atividades do dia (novas issues, PRs e execuções de CI).
- **Exportar Resumo:** Toque no botão de cópia para transferir o resumo formatado em Markdown para sua área de transferência, ideal para compartilhar em relatórios ou canais de equipe.

### 4.2 Repositórios
- **Busca e Filtros:** Localize qualquer repositório pelo nome instantaneamente. Ordene por data de atualização recente, estrelas ou nome alfabético.
- **Detalhes Completos:** Toque em qualquer repositório para visualizar descrição, linguagem predominante, contagem de forks/estrelas e atalhos diretos para issues e pull requests vinculados.
- **Releases e APKs:** No detalhe do repositório, abra **Releases e APKs** para consultar releases e assets.

### 4.3 Download e instalação manual de APKs
- Toque em **Baixar** em um asset APK para acompanhar o progresso ou cancelar. Android 10+ salva em Downloads; no Android 8–9, escolha Downloads no seletor do sistema.
- O app compara SHA-256 com o digest publicado pelo GitHub ou, quando ele não existe, com a entrada correspondente em `SHA256SUMS.txt`. Divergência descarta o arquivo; releases sem hash mostram **sem hash publicado**.
- Depois de baixar, toque em **Instalar**. O Android pode pedir autorização para instalar apps iniciados pelo ControleGitHub e sempre apresenta a confirmação final. O app não instala nem atualiza APKs silenciosamente.
- Notificações de progresso e conclusão são opcionais. Se forem negadas, o estado continua visível na tela Releases.

### 4.4 Trabalho e Demandas (Issues e Pull Requests)
- **Visualização em Lista ou Kanban:** Alterne a visualização através do seletor superior.
  - **Modo Lista:** Visualização clássica com filtros de estado (aberto/fechado) e busca rápida.
  - **Modo Quadro (Kanban Local):** Organize suas issues em 4 colunas (*A fazer*, *Em andamento*, *Em revisão* e *Concluído*). A movimentação de cartões é 100% local e pessoal — ela não modifica os dados do repositório no GitHub!
- **Gestão de Issues:** Abra novas issues com título e descrição, comente em discussões existentes, altere labels e atribua responsáveis.
- **Revisão de Pull Requests:** Inspecione detalhes da proposta, arquivos modificados, envie comentários de revisão ou emita pareceres formais (*Aprovar*, *Comentar* ou *Solicitar alterações*).

### 4.5 Notificações
- Central de notificações com agrupamento por repositório e motivo.
- Toque em uma notificação para navegar diretamente ao item correspondente ou marque-a como lida individualmente.

---

## 5. Modo Offline e Resiliência de Conexão

O ControleGitHub foi construído para funcionar perfeitamente mesmo sem conexão:
- Todas as consultas já realizadas são armazenadas em um banco de dados local seguro (SQLite).
- Caso o sinal caia ou o dispositivo entre em modo avião, um indicador visual discreto informará: `Consulta offline realizada em [data e hora]`.
- Quando a conectividade for restaurada, basta puxar a lista para baixo ou tocar em **Recarregar**: o aplicativo consultará o GitHub de forma eficiente via cabeçalhos HTTP ETag, transferindo apenas o que tiver mudado.

---

## 6. Configurações, Preferências e Acessibilidade

Na tela de preferências você pode ajustar:
- **Tema Visual:** Selecione entre Tema Claro, Tema Escuro ou Sincronizar com as configurações do Sistema.
- **Sincronização em Segundo Plano:** Ative a verificação automática periódica e defina se a sincronização deve ocorrer apenas sob conexão Wi-Fi e nível seguro de bateria.
- **Alertas Locais:** Configure o recebimento de notificações no Android filtradas estritamente para quando você for mencionado, atribuído ou solicitado para revisão.
- **Acessibilidade:** Suporte total a leitores de tela (TalkBack) com descrições semânticas em todos os botões e alvos de toque generosos (mínimo de 48x48 dp).

---

## 7. Desconexão (Logout) e Desinstalação

- **Sair da Conta:** Toque no botão **Sair** no menu superior ou de preferências. Isso executará uma limpeza atômica imediata: as chaves criptográficas no KeyStore, as credenciais de sessão, o banco de cache SQLite e as preferências locais do Kanban serão completamente excluídos da memória do dispositivo.
- **Desinstalação:** Como o aplicativo não armazena dados em servidores externos e possui o backup em nuvem estritamente desabilitado por segurança, desinstalar o aplicativo do sistema Android remove 100% de quaisquer dados locais residuais.
