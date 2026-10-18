# Guia de Release e Distribuição — ControleGitHub 1.0

Última atualização: 18 de outubro de 2026

Este documento orienta o processo de geração, assinatura, verificação de integridade e distribuição oficial de versões do aplicativo **ControleGitHub**.

---

## 1. Identificação da Versão 1.0

- **Nome do Pacote (`applicationId`):** `com.playertwo.controlegithub`
- **Versão de Lançamento (`versionName`):** `1.0.0`
- **Código de Versão (`versionCode`):** `100`
- **SDK Mínimo (`minSdk`):** `26` (Android 8.0 Oreo)
- **SDK Alvo (`targetSdk`):** `36` (Android 17)

---

## 2. Processo de Compilação e Assinatura

### 2.1 Compilação Local / CI com Assinatura Padrão
Para gerar o pacote de release em ambiente local ou integração contínua sem credenciais privadas de produção:

```powershell
.\gradlew.bat assembleRelease
```
*O Gradle detecta a ausência de chaves de produção externas e aplica automaticamente a assinatura controlada de teste/desenvolvimento.*

### 2.2 Compilação de Produção com Chave Privada
Para gerar a release assinada oficial com o keystore de produção (mantido estritamente fora do controle de versão):

Defina as variáveis de ambiente ou passe as propriedades via Gradle:
```powershell
$env:RELEASE_STORE_FILE = "C:\caminho\seguro\controlegithub-release.keystore"
$env:RELEASE_STORE_PASSWORD = "senha_do_keystore"
$env:RELEASE_KEY_ALIAS = "controlegithub"
$env:RELEASE_KEY_PASSWORD = "senha_da_chave"

.\gradlew.bat assembleRelease
```

O artefato assinado e alinhado é gerado em:
`app/build/outputs/apk/release/app-release.apk`

---

## 3. Verificação de Integridade (SHA-256)

Antes de distribuir o APK, gere e publique o checksum SHA-256 correspondente:

### No Windows (PowerShell):
```powershell
Get-FileHash -Algorithm SHA256 .\app\build\outputs\apk\release\app-release.apk
```

### No Linux / macOS:
```bash
sha256sum app/build/outputs/apk/release/app-release.apk
```

### Checksum Oficial da Versão 1.0.0
- **Algoritmo:** SHA-256
- **Hash:** `F2A51D35AD2C4603B5DCF7A29D2A4F65DC00734DAD79CFE4DB2E9B40FAC04F71`

O hash gerado deve acompanhar as notas de lançamento para que o usuário final possa auditar a autenticidade do binário baixado.

---

## 4. Canal Oficial de Distribuição

O canal principal de distribuição da versão 1.0 é o **GitHub Releases** no repositório oficial:
- **Repositório:** `https://github.com/playertwo1/controlegithub/releases`
- **Tag canônica:** `v1.0.0`
- **Artefatos disponibilizados:**
  1. `ControleGitHub-v1.0.0.apk` (cópia de `app-release.apk`)
  2. `SHA256SUMS.txt` (arquivo contendo o hash SHA-256 do APK)
  3. Notas de versão detalhando as novidades, correções e requisitos do sistema.

---

## 5. Procedimento de Instalação e Atualização

### 5.1 Primeira Instalação
O usuário transfere o APK para o dispositivo ou faz o download direto pelo navegador móvel e autoriza a instalação de fontes desconhecidas para aquele aplicativo.

### 5.2 Atualização de Versão Existente
Para atualizar uma versão previamente instalada sem perda de dados:
```bash
adb install -r app-release.apk
```
*Nota: A assinatura da atualização deve utilizar a mesma chave criptográfica da versão anterior para que o Android preserve o sandbox privado (`AndroidKeyStore`, bancos de dados SQLite e preferências).*
