# Acoustic Direction Lab v0.4 — Rastreamento de som contínuo sem IA

Aplicativo experimental no GitHub [joaogabrielgn/Mictest](https://github.com/joaogabrielgn/Mictest). Foco: começar a analisar sons persistentes de motores, ventiladores, chiados, pessoas falando e ruídos ambientais, **não somente estalos**.

## Instalar APK pelo S25

Abra [Actions do Mictest](https://github.com/joaogabrielgn/Mictest/actions), entre no último build verde, baixe `AcousticDirectionLab-APK`, extraia `app-debug.apk` do ZIP e instale no telefone. Faça cópia/exportação dos CSV/WAV antigos antes de desinstalar alguma versão: o debug APK pode ter assinatura diferente entre compilações do GitHub.

## Modos

### SOM_CONTINUO (novo; selecionado por padrão)

- Microfone padrão: `CAMCORDER`, 2 canais, 48000 Hz.
- Processamento em fluxo de **janelas sobrepostas de 4096 frames** (85,3 ms @48 kHz) a cada **1024 frames** (21,3 ms).
- GCC-PHAT independente em três bandas: **GRAVE 180–950 Hz**, **MÉDIO 950–3000 Hz**, **AGUDO 3000–8500 Hz**. A janela tem *Hann* para reduzir vazamento espectral. A banda dominante é a de maior energia analisada, **não é uma fonte sonora individual identificada**.
- Rejeita ruído fraco, clipping, borda de busca e picos espectrais ambíguos. Em seguida requer **concordância de atraso em ao menos duas bandas** dentro de 105 µs, evitando interpretar um único pico de som tonal como direção precisa. Indica `MULTIBANDA_OK`, `SILENCIO`, `TONAL_AMBIGUO`, `FAIXA_UNICA_OU_DIVERGENTE` etc.
- Calcula atraso bruto e um valor suavizado pela mediana móvel + EMA, **apenas enquanto existem novas janelas confiáveis**. Com silêncio ou ambiguidade, marca o atraso apresentado como indeterminado, sem manter uma seta enganosa.
- Confronta o atraso com o perfil-piloto 1D de dois microfones do S25: inferior ~−434,7 µs, centro ~−2,8 µs, superior ~+472,1 µs. A informação é **somente um índice relativo entre extremidades físicas**, não uma direção 3D.
- Inclui a orientação interpolada do IMU (yaw, pitch e roll ao tempo estimado do centro da janela), para futura fusão com geometria calibrada. **Não calcula ainda azimute, elevação, erro angular, 3D, distância ou projeção pela câmera.**
- Tempo máximo por captura: **120 segundos**, ou encerre antes com **Parar**. O WAV original, os dados brutos IMU e as janelas de análise são salvos localmente; nenhum upload automático.
- Exporte WAV, IMU CSV e o novo **Exportar contínuo CSV**, contendo carimbo temporal acústico do centro da janela, quaternion/pose, banda dominante, energia, flatness espectral, atrasos de todas as bandas, status e índice-piloto.

### ESTALOS_CALIBRACAO (modo anterior)

Retém a detecção de impulsos da v0.3.2, o CSV `analysis_*`, os filtros de qualidade e a marcação opcional da referência `MIC_INFERIOR`, `MIC_SUPERIOR`, `CENTRO` e `VARREDURA_FONTE_FIXA`. Máximo 30 segundos.

## Limitações importantes

O Galaxy S25 declara para CAMCORDER os microfones como `C0:PROCESSED`. O Android pode variar processamento e atraso relativo. Uma **única linha de base de dois microfones não determina azimute e elevação** independentemente; a ambiguidade 3D permanece. Rotação e IMU foram registrados, mas não resolvem essa ambiguidade automaticamente; o ajuste geométrico dependerá de mais referências e validação fora da amostra.

**A fonte mais alta não é necessariamente a que aparece na banda mais energética.** Quando há várias fontes superpostas, a versão 0.4 não as separa nem consegue provar qual é predominante. Também não é uma câmera acústica pronta, nem visualiza a direção no visor.

## Como testar o novo modo

Use `SOM_CONTINUO`, `CAMCORDER`, 2 canais, 48000 Hz. S25 na horizontal e parado, sem obstruir microfones. Com outro celular ou alto-falante a 0,5–2 m:
1. Reproduza **ruído branco ou rosa** por 10–20 segundos, primeiro próximo à extremidade física inferior, depois à superior e por fim ao centro. Faça gravações separadas, com referência no seletor.
2. Repita com um **ventilador/motor ou chiado contínuo**. Pode haver somente uma banda válida, e o app deve sinalizar baixa confiança.
3. Faça uma **varredura** de 20–40 segundos com som contínuo fixo, movendo lentamente apenas o S25. Exporte WAV, CSV contínuo e IMU.

A v0.4 pode revelar que sons periódicos/estreitos são intrinsecamente mais ambíguos. **Não force um número quando o status não for MULTIBANDA_OK.** Em seguida avaliaremos se a relação entre movimento e atraso consegue generalizar antes de desenvolver qualquer overlay de câmera.

## Desenvolvimento

O código da análise contínua fica em `ContinuousTracker.java` e dispõe de testes JVM sintéticos com som de banda larga atrasado, tom estreito, silêncio, sinais positivo/negativo e alinhamento de janela. CI executa testes e gera APK debug.
