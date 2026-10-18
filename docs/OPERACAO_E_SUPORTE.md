# Guia de Operação e Suporte — ControleGitHub v1.0.0

Este documento define os procedimentos de governança operacional, canais de suporte técnico, gerenciamento de credenciais e rotinas de contingência e rollback para a versão de produção **1.0.0** do aplicativo **ControleGitHub**.

---

## 1. Princípios Operacionais e Arquitetura

O **ControleGitHub** opera sob o paradigma **local-first** (prioridade ao cliente local):
- **Comunicação Direta:** O aplicativo estabelece conexões HTTPS diretas e exclusivas entre o cliente Android e a API REST oficial do GitHub (`https://api.github.com`).
- **Sem Backend Intermediário:** Não existem servidores próprios, bancos de dados em nuvem ou relés de telemetria operados pelo projeto.
- **Isolamento de Contas:** Todas as informações de sessão, tokens de acesso, caches de dados e quadros de produtividade são isolados por conta e armazenados no sandbox protegido do sistema operacional Android.

---

## 2. Canais de Atendimento e Suporte Técnico

### 2.1 Suporte Geral e Relato de Defeitos
- **Canal Oficial:** Registro de demandas na aba de [Issues do Repositório GitHub](https://github.com/playertwo1/controlegithub/issues).
- **Procedimento para Relato de Bugs:**
  1. Verifique se o problema persiste após tocar no botão de atualizar ou reiniciar o aplicativo.
  2. Forneça o modelo do dispositivo, versão do Android e passos detalhados para reproduzir o comportamento observado.
  3. **Atenção:** Jamais inclua tokens de acesso, prints contendo dados confidenciais de clientes ou segredos de repositórios privados em relatos públicos.

### 2.2 Relato de Vulnerabilidades e Incidentes de Segurança
- Relatos que envolvam segurança de dados, permissões ou exposição de credenciais devem seguir estritamente o procedimento estabelecido em [`SECURITY.md`](file:///c:/Users/notefael/projetos/controlegithub/SECURITY.md).
- Utilize a funcionalidade de **GitHub Security Advisory** privado no repositório para envio confidencial de informações.

### 2.3 Diagnóstico de Falhas de Rede e Status do GitHub
Em caso de falha de conexão persistente com exibição de erros 5xx ou 403:
- Consulte a página de status dos serviços oficiais do GitHub em [githubstatus.com](https://www.githubstatus.com).
- Caso a API esteja em manutenção ou com degradação temporária, o ControleGitHub exibirá o conteúdo consultado previamente com o selo de dados em cache offline, preservando a utilidade do aplicativo sem interrupção.

---

## 3. Gestão e Revogação Imediata de Credenciais

### 3.1 Revogação Local no Dispositivo (Logout)
- O usuário pode encerrar sua sessão a qualquer momento através do botão **Sair** no menu do aplicativo.
- O procedimento de logout executa:
  1. Expurgo imediato e irreversível do token de acesso e credenciais de autorização cifradas via `AndroidKeyStore`.
  2. Exclusão física completa da base de dados local de cache (`controlegithub_cache.db`).
  3. Cancelamento imediato de todos os agendamentos de segundo plano no `JobScheduler`.
  4. Remoção de todos os alertas ativos no painel de notificações do sistema.

### 3.2 Revogação Remota pelo Portal Web do GitHub
Caso o dispositivo do usuário seja extraviado, danificado ou se deseje invalidar o acesso remotamente:
1. Acesse o portal do GitHub pelo navegador web: [github.com/settings/applications](https://github.com/settings/applications).
2. Na aba **Authorized OAuth Apps**, localize o aplicativo **ControleGitHub**.
3. Clique nas reticências (...) ou no nome da aplicação e selecione **Revoke** (Revogar acesso).
4. O GitHub invalidará o token OAuth instantaneamente nos servidores.
5. Na próxima tentativa de sincronização pelo dispositivo móvel, a API retornará HTTP `401 Unauthorized` e o aplicativo transicionará automaticamente para a tela de login inicial, impedindo qualquer acesso posterior.

---

## 4. Atualizações, Contingência e Procedimento de Rollback

### 4.1 Ciclo de Atualizações (Upgrades)
- Ao instalar uma versão mais recente (ex: 1.0.1) sobre a 1.0.0 via APK assinado com a mesma chave:
  - O sistema Android preserva integralmente o sandbox da aplicação.
  - Credenciais do usuário continuam ativas no `AndroidKeyStore`.
  - Preferências e cartões do Kanban persistem no Preferences DataStore.

### 4.2 Procedimento de Retorno à Versão Anterior (Rollback)
Caso seja necessário reverter uma atualização para restaurar a versão 1.0.0 a partir de um pacote futuro:
1. **Compatibilidade dos Bancos de Dados:**
   - O cache de leituras (`controlegithub_cache.db`) é estritamente descartável e opera na versão de esquema `1`. Em situações de downgrade de código, o gerenciador SQLite descarta os dados defasados e recria as tabelas de cache limpas automaticamente sem falhas ou exceções.
   - O quadro Kanban e as preferências utilizam Preferences DataStore com tipos de dados primitivos resilientes (strings e booleanos), suportando versões anteriores sem quebra de serialização.
2. **Procedimento Recomendado para Reversão Limpa:**
   - Se uma regressão crítica exigir desinstalação e reinstalação da versão 1.0.0:
     - Acesse **Configurações do Android** > **Aplicativos** > **ControleGitHub** > **Armazenamento** e toque em **Limpar Dados**.
     - Instale o APK oficial da versão 1.0.0 (`app-release.apk`) e execute o fluxo de autenticação normalmente.

---

## 5. Rotação de Chaves de Assinatura e Governança

- O binário oficial de release é assinado de acordo com as instruções documentadas em [`RELEASE.md`](RELEASE.md).
- As credenciais de assinatura de release (`*.keystore`, senhas de chaveiro) residem exclusivamente fora do controle de versão (Git) em ambiente controlado e protegido por variáveis de ambiente.
- Qualquer alteração futura de chaveiro de assinatura deve prever migração via esquema APK Signature Scheme v3 com rotação de certificados (`lineage`), garantindo continuidade de atualização sem necessidade de desinstalação para a base de usuários.
