# Política de Privacidade — ControleGitHub

Última atualização: 20 de outubro de 2026

O **ControleGitHub** é um aplicativo Android nativo de código aberto projetado com princípios de privacidade local (*local-first*) e transparência absoluta. Esta política descreve de forma fiel como os dados são tratados no aplicativo.

---

## 1. Coleta e Transmissão de Dados

- **Sem servidores intermediários:** O ControleGitHub não possui servidores próprios, banco de dados na nuvem, serviços de telemetria ou rastreamento analítico.
- **Comunicação direta com o GitHub:** Consultas usam `https://api.github.com`; downloads de assets de release seguem somente o redirect HTTPS devolvido pelo GitHub, sem encaminhar o token de sessão ao host do arquivo.
- **Nenhum dado é compartilhado com terceiros:** Nenhuma informação de navegação, perfil, repositório ou atividade é compartilhada, monetizada ou enviada para qualquer entidade externa.

---

## 2. Armazenamento Seguro de Credenciais

- **Criptografia por Chave de Hardware:** Seus tokens de autorização pessoal e credenciais de sessão são armazenados no dispositivo utilizando o **AndroidKeyStore** com criptografia padrão AES-256-GCM.
- **Bloqueio de Backup em Nuvem:** O backup automático em nuvem do Android e a transferência direta entre dispositivos estão explicitamente desativados para todos os dados do aplicativo (`android:allowBackup="false"` e `data_extraction_rules.xml`), garantindo que seus tokens nunca sejam extraídos em backups não criptografados pelo sistema operacional.

---

## 3. Armazenamento Local e Cache

- **Cache de Leitura SQLite:** O aplicativo armazena temporariamente informações de consulta (repositórios, issues, pull requests e notificações) em um banco de dados SQLite local protegido no sandbox privado do aplicativo (`controlegithub_cache.db`), com isolamento estrito entre contas.
- **Proteção contra Armazenamento Indevido de Segredos:** O mecanismo de cache rejeita e bloqueia ativamente o armazenamento de qualquer rota de autenticação (`/login`, `/oauth`, `/device`, `/tokens`), corpos de logs do Actions (`/logs`) ou payloads que contenham chaves de acesso (`access_token`, `client_secret`).
- **Limpeza Total no Encerramento de Sessão:** Ao realizar logout no aplicativo, todas as credenciais do Keystore, bancos de dados SQLite de cache, preferências locais e tarefas agendadas em segundo plano são excluídas imediatamente.
- **Downloads de APK:** O APK é preparado em arquivo temporário privado, validado por SHA-256 quando há digest publicado e então salvo em Downloads (ou no destino escolhido no seletor do Android em versões antigas). O app não solicita acesso amplo ao armazenamento.

---

## 4. Permissões Solicitadas

O ControleGitHub solicita as permissões necessárias para suas funções:
1. `INTERNET`: Necessária para comunicação segura com a API oficial do GitHub via HTTPS.
2. `ACCESS_NETWORK_STATE`: Utilizada para verificar a conectividade do dispositivo e respeitar preferências de sincronização (ex.: apenas Wi-Fi).
3. `POST_NOTIFICATIONS`: Opcional; usada para notificações locais de menções, eventos e downloads. Downloads continuam disponíveis se for negada; o estado fica na tela Releases.
4. `FOREGROUND_SERVICE` e `FOREGROUND_SERVICE_DATA_SYNC`: Permitem que um download iniciado por você continue em segundo plano com notificação de andamento.
5. `REQUEST_INSTALL_PACKAGES`: Permite abrir o instalador do Android depois que você toca em “Instalar”. O sistema pode pedir que você autorize esta fonte; cada instalação continua dependendo da confirmação no instalador.

O aplicativo não solicita permissões para:
- Localização geográfica (GPS/Rede);
- Contatos, telefone ou agenda;
- Câmera, microfone ou fotos;
- Acesso amplo ao armazenamento compartilhado.

---

## 5. Controle e Revogação

O usuário mantém controle total a qualquer momento:
- **No Aplicativo:** Você pode encerrar sua sessão a qualquer momento no perfil, limpando todos os dados locais.
- **No GitHub:** Você pode revogar imediatamente a autorização do dispositivo acessando [GitHub Settings > Applications > Authorized OAuth Apps](https://github.com/settings/applications).
- **No Sistema Android:** As permissões concedidas podem ser revogadas a qualquer momento em *Configurações do Android > Aplicativos > ControleGitHub*.
