# Tentativa conectada 16 — ambiente observado

Data: 2026-10-22 (America/Sao_Paulo)
Commit testado: `29fa2e97d151dfcb0e2bf82d1efffe0928d8b43b` (`main`).

## Emulador

- AVD: `Medium_Phone` (Google SDK gphone64 x86_64), Android 17 / API 37.
- Inicialização fria desta sessão: `emulator.exe -avd Medium_Phone -no-window -no-audio -no-boot-anim -memory 3072 -gpu host -no-snapshot-load -no-snapshot-save`.
- A configuração persistente do AVD não foi alterada; os argumentos de memória, GPU e snapshot foram usados somente nesta sessão.
- Propriedades observadas na execução: fingerprint `google/sdk_gphone64_x86_64/emu64xa:17/CE2A.260420.051.A2/16418371:user/release-keys`; memória reportada pelo sistema: 4.006.176 kB.
- A tentativa 16 usou o emulador já iniciado a frio e manteve-se na mesma sessão.

## Comando e resultado

- JDK: `C:\Program Files\Android\Android Studio\jbr`.
- Dispositivo ADB: `emulator-5554`.
- Comando: `.\gradlew.bat connectedDebugAndroidTest --no-configuration-cache --console=plain`.
- Log: `connected-attempt-20261022-16-medium-phone-coldboot.log`; estado de saída: `connected-attempt-20261022-16-medium-phone-exit-code.txt`.
- Resultado: `BUILD SUCCESSFUL`; 207/207 testes concluídos, zero falhas, erros ou casos ignorados. XML JUnit e resultado binário acompanham este registro.
- SHA-256 do APK debug: `A2D96DF19258487D70295B75AD7A86B2512A1E6B54EFE8F93960DFD50B69A828`.
