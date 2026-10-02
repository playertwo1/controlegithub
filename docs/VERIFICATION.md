# Verificação da fundação — 2026-10-02

## Ambiente

Windows, JDK 17, Gradle 9.3.1, AGP 9.1.1, SDK 36. Emulador Pixel 9 com
Android 17 (API 37). Dados demonstrativos, nenhuma credencial conectada ao app.

## Comando executado

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug connectedDebugAndroidTest --console=plain
```

Resultado final: **BUILD SUCCESSFUL**. Três testes unitários de busca e um teste
instrumentado de entrada → repositórios → busca por linguagem → detalhe → voltar,
sem falhas ou testes ignorados. Menu flutuante incluído na execução final.

Relatórios locais, gerados pelo Gradle e não versionados:

- `app/build/test-results/testDebugUnitTest/`;
- `app/build/outputs/androidTest-results/connected/debug/`;
- `app/build/reports/lint-results-debug.html`.

Lint não apresentou erros. Avisos remanescentes: target API 36 em vez de 37 e
versões fixadas de Compose BOM/Activity com atualizações disponíveis. Não foram
suprimidos. Atualizações devem receber validação antes do beta.

Durante a preparação, foram corrigidos o escape do caminho SDK no Windows,
compatibilidade do Espresso com API 37 e o seletor do item de navegação.
Essas execuções iniciais falharam; o resultado acima corresponde à execução final.

## Standard e documentação

Com o Ideias Standard na revisão `f546a2a128956e87c7ae89c103ca67aba4054cc4`,
Python 3.13.14, PyYAML 6.0.3 e jsonschema 4.26.0:

```sh
python ../ideias_standard/scripts/check.py . --details
python ../ideias_standard/scripts/check.py . --kind standard-lock --details
python ../ideias_standard/scripts/check.py . --kind context-manifest --details
```

Três resultados PASS. Links locais do Markdown e assinaturas dos PNGs foram
verificados; sete pranchas finais e seis capturas reais estão presentes.
As capturas foram inspecionadas visualmente: entrada, painel, repositórios,
detalhe, trabalho e notificações. A busca tem teste de estado vazio.

Imagens conceituais foram geradas e revisadas com `image_gen`; não são evidência
de funcionalidades de API. Prompts e reparos estão em `docs/design/`.

## Limites da evidência

Autenticação, API GitHub e mutações: NOT_RUN, ainda não implementadas.
API 26, aparelhos físicos, tema claro, tablets e TalkBack: NOT_RUN.
Auditoria independente: NOT_RUN; a revisão do autor não substitui auditoria.
O PASS estrutural do Standard não constitui certificação Gold.
O [primeiro CI remoto](https://github.com/playertwo1/controlegithub/actions/runs/37036444089)
falhou no setup do SDK, antes do build, ao solicitar o pacote obsoleto `tools`.
Correção: configurar `packages: platform-tools`, conforme a
[documentação do action](https://github.com/android-actions/setup-android#additional-packages).
A [execução corrigida](https://github.com/playertwo1/controlegithub/actions/runs/37037178446)
foi confirmada completed/success em 2026-10-02, SHA 03fe0cd.
