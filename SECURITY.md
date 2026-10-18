# Política de Segurança — ControleGitHub

Última atualização: 18 de outubro de 2026

O **ControleGitHub** segue padrões rigorosos de segurança e privacidade para proteger as credenciais e os dados de seus usuários:

1. **Armazenamento Seguro:**
   - Tokens de autorização OAuth e credenciais de sessão são armazenados com criptografia AES-256-GCM utilizando chaves gerenciadas pelo `AndroidKeyStore`.
   - As credenciais nunca são persistidas em texto plano.

2. **Bloqueio de Extração e Backup:**
   - Backups automáticos em nuvem e transferências entre dispositivos estão desativados no manifesto (`android:allowBackup="false"` e `@xml/data_extraction_rules`).

3. **Prevenção de Vazamento em Logs e Cache:**
   - Nenhuma credencial pessoal, segredo de cliente ou token de autorização é registrado em logs do sistema (`Log.d`, `Log.i`, etc.).
   - O cache SQLite local bloqueia ativamente o armazenamento de payloads de autenticação ou que contenham chaves sensíveis.

4. **Comunicação Segura:**
   - O aplicativo comunica-se exclusivamente com a API oficial do GitHub (`https://api.github.com`) via HTTPS/TLS.

## Reporte Responsável de Vulnerabilidades

Caso encontre alguma falha de segurança ou comportamento vulnerável no aplicativo:
1. Utilize o canal privado de Security Advisories do repositório no GitHub.
2. Não abra issues públicas contendo detalhes de exploração ou amostras de dados sensíveis.
3. Forneça passos claros para reprodução e tempo hábil para correção antes de qualquer divulgação pública.
