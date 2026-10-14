# Acoustic Direction Lab (v0.1)

Aplicativo Android de diagnóstico do acesso aos microfones físicos, canais de áudio PCM e atrasos entre canais do Samsung Galaxy S25.

**Esta é a versão de diagnóstico:** ainda não produz câmera acústica, localização 3D ou beamforming. Identificar microfones físicos não garante que o Android entregue seus sinais independentemente.

## Instalação direta no Galaxy S25 (sem computador)

1. Abra https://github.com/joaogabrielgn/Mictest/actions
2. Toque na execução mais recente **Build Android APK** com símbolo verde.
3. Em **Artifacts**, baixe **AcousticDirectionLab-APK**. É necessário entrar na conta GitHub.
4. Pelo aplicativo **Meus Arquivos** no S25, extraia o ZIP baixado.
5. Abra o arquivo **app-debug.apk** e autorize a instalação dessa fonte quando solicitado.

O APK é assinado com chave de debug do Android. O app pede apenas permissão de microfone, sem internet, câmera ou localização.

## Experimentos

- Toque **Listar microfones** para obter o inventário e posições.
- Escolha **UNPROCESSED**, **2 canais**, **48000 Hz**; teste som no topo e na base.
- Teste **3 canais** e **4 canais**, sabendo que podem falhar legitimamente.
- Repita em **CAMCORDER** e **VOICE_RECOGNITION**.
- Toque **Copiar relatório** e cole os resultados na conversa com o ChatGPT.
- **Exportar WAV** salva a última gravação para análise posterior.

A medição de atraso é exploratória por correlação cruzada. Não é estimativa espacial calibrada.

## Desenvolvimento

Java nativo + Android AudioRecord + API MicrophoneInfo. GitHub Actions compila automaticamente o APK de debug no push e em execução manual, com Java 17 / Gradle 8.9 / Android SDK 35.
