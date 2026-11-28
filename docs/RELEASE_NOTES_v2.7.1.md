# Notas de lançamento — ControleGitHub v2.7.1

Canal oficial: https://github.com/playertwo1/controlegithub/releases/tag/v2.7.1

## O que mudou

- Um aviso individual abre a issue, o pull request, o repositório ou a lista de releases correspondente. Vários avisos chegam separados: cada um abre o próprio item e o resumo abre a caixa **Avisos**. Se o destino não puder ser aberto, a caixa avisa e a navegação não marca o aviso como lido.
- O README mostra as imagens hospedadas pelo GitHub quando elas entram na tela. O toque amplia. Se a imagem falhar, o texto alternativo permanece e dá para tentar de novo. SVG não é exibido. Imagens de outros sites continuam por toque.

## Artefato

- **Arquivo:** ControleGitHub-v2.7.1.apk (12.850.395 bytes)
- **Pacote:** com.playertwo.controlegithub
- **Versão:** 2.7.1 (versionCode = 111)
- **Requisitos:** Android 8.0 (API 26) ou superior
- **SHA-256:** FBB262B7E1BD5186ED75BD2F3C659679CBE0641903EFA6D93E5812529E184FC2
- **Certificado de assinatura:** c622fc491da08aee1b9e196db0cc7ba92784eefc02c047c387049042ad0aa2ec (APK Signature Scheme v2)

## Validação

- `clean assembleRelease` com o JDK do Android Studio. Assinatura v2. O certificado SHA-256 é o mesmo da v2.7.0.
- `aapt` confirmou o pacote `com.playertwo.controlegithub`, versionCode 111 e versionName 2.7.1.
- `testDebugUnitTest`: 241 testes, 0 falhas e 2 ignorados. `lintDebug` passou.
- Testes no emulador e no aparelho físico não foram executados nesta publicação.
