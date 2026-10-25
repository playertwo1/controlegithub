# Guia de Release e Distribuição — ControleGitHub

Última atualização: 25 de outubro de 2026

## Release atual: v2.3.0

- Pacote: `com.playertwo.controlegithub`
- Versão: `2.3.0` (`versionCode = 104`)
- Compatibilidade: Android 8.0 (API 26) ou superior
- Arquivo: `app-release.apk`
- SHA-256: `771D120946328D548BF4207D00E9E20870516E3F865A47FA27D133A0990B4D82`
- Certificado SHA-256: `c622fc491da08aee1b9e196db0cc7ba92784eefc02c047c387049042ad0aa2ec`
- Assinatura: APK Signature Scheme v2

## Assinatura de produção local

O Gradle lê os valores `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS` e `RELEASE_KEY_PASSWORD` de `.secrets/signing.properties`, além de aceitar propriedades Gradle ou variáveis de ambiente. O arquivo e o keystore ficam no diretório `.secrets/`, que é ignorado pelo Git. Nunca adicione esses arquivos ao índice ou ao histórico.

O caminho do keystore é resolvido a partir da raiz do projeto. Se uma tarefa de release não encontrar os quatro valores e o arquivo de chave, a compilação falha; ela não publica APK assinado com a chave debug.

Para compilar pelo Android Studio, abra o projeto, sincronize o Gradle e execute a variante `release`. Na linha de comando do Windows, usando o JDK e SDK do Android Studio:

```powershell
$env:JAVA_HOME = "$env:ProgramFiles\Android\Android Studio\jbr"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
.\gradlew.bat clean assembleRelease --no-configuration-cache --console=plain
```

O APK é gerado em `app/build/outputs/apk/release/app-release.apk`.

## Verificar o APK

No PowerShell, defina `$BT` como a pasta `build-tools\36.0.0` do SDK e execute:

```powershell
& "$BT\apksigner.bat" verify --verbose --print-certs .\app\build\outputs\apk\release\app-release.apk
& "$BT\aapt.exe" dump badging .\app\build\outputs\apk\release\app-release.apk
Get-FileHash -Algorithm SHA256 .\app\build\outputs\apk\release\app-release.apk
```

O certificado deve corresponder ao fingerprint acima, a verificação v2 precisa retornar `true`, e o SHA-256 deve coincidir com `SHA256SUMS.txt`.

## Distribuição e compatibilidade

A distribuição oficial é feita em [GitHub Releases](https://github.com/playertwo1/controlegithub/releases), com a tag `v2.0.0`, o APK, `SHA256SUMS.txt` e as [notas de lançamento](RELEASE_NOTES_v2.0.0.md).

A chave v2.0.0 é diferente da chave da v1.0.0. O Android não aceita a v2.0.0 como atualização direta; é necessário desinstalar a versão anterior e instalar a nova. A desinstalação apaga os dados locais do aplicativo. As releases v2.x seguintes devem reutilizar a chave v2.0.0 para permitir atualização normal.

Entre versões assinadas com a mesma chave, atualize pelo ADB com:

```bash
adb install -r app-release.apk
```
