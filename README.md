# Acoustic Direction Lab v0.5.1 — calibração audiovisual guiada

Projeto experimental Android para o Galaxy S25 (SM-S931B). Desenvolvido em Java/Android, sem IA. O objetivo de longo prazo é localizar a fonte de som dominante (motor, chiado, voz, ruído contínuo) com dois microfones e rotação do celular. **O protótipo não fornece precisão espacial validada e não separa várias fontes simultâneas.**

## Instalação

Abra [GitHub Actions](https://github.com/joaogabrielgn/Mictest/actions), selecione a **última execução verde**, baixe `AcousticDirectionLab-APK`, extraia `app-debug.apk` e instale. Se a assinatura debug diferir da anterior, exporte seus dados antes de desinstalar. Autorize CAMERA e RECORD_AUDIO; atualize o Google Play Services para RA.

## O que mudou desde a v0.5

### UI redesenhada

- Uma nova tela inicial limpa possui três cartões: **Calibração com câmera**, **Teste dos microfones** e **Arquivos e análises**. O antigo laboratório continua acessível como modo técnico.
- **Arquivos e análises** abre agora um navegador real dos WAVs/CSVs locais mesmo depois que o app foi fechado. É possível visualizar uma amostra do CSV, selecionar arquivos e exportar um ZIP. O ZIP dessa tela guarda os **dados brutos selecionados**; para obter o summary.json e os pares TRAIN/VALID, use o botão Exportar da sessão AR antes de sair.
- A tela de AR prioriza **câmera em tela cheia**, marcador verde da fonte **escolhida manualmente**, barra de três passos, quatro métricas (pares, amplitude de projeção, qualidade, MAE) e um único botão contextual grande. Botões secundários Reiniciar e Exportar; estatísticas e timestamps ficam em **Detalhes técnicos** recolhível.
- Estados inequívocos: `IDLE → SOURCE_MARKED → CALIBRATING → READY_TO_FREEZE → VALIDATING → DONE`. O botão nunca tenta congelar o perfil enquanto o áudio está ativo. A operação congela definitivamente o perfil. **VALID requer gravação subsequente e amostras com timestamp posterior ao congelamento.**
- O fluxo recomenda: marcar a fonte, iniciar áudio, girar/inclinar devagar, parar, congelar, **iniciar nova validação**, parar, exportar.

### Correção da sincronização ARCore + áudio

O principal erro de v0.5 era usar `elapsedRealtimeNanos()` depois de `Session.update()` como timestamp da pose AR. Essa chamada pode entregar imagens antigas por ~190 ms, o que gera erro angular com o S25 em rotação.

- Agora a captura copia a pose ligada a **`Frame.getTimestamp()` (instante do quadro capturado)** e registra separadamente o instante da entrega. A documentação ARCore **não promete formalmente a base desse timestamp**. Uma `FrameClockGate` exige idades positivas e plausíveis de quadro (0–900 ms) e regularidade temporal antes de permitir pareamento com AudioTimestamp TIMEBASE_BOOTTIME. Essa checagem não prova sincronia de hardware; o CSV inclui informação para investigar diferença de relógios.
- Uma fila de janelas acústicas aguarda por quadros AR posteriores ao instante do áudio; isso evita usar a pose antiga só porque a captura da câmera está atrasada. Uma `ArFrameBridge` interpoladora exige quadros de ambos os lados do instante acústico, intervalo máximo de 110 ms, até 70 ms para cada extremidade e nenhuma extrapolação silenciosa. Amostras sem bracketing são rejeitadas com motivo.
- A ponte temporal interpola quaternion, eixo físico e direção marcada (se há âncora AR), e descarta tracking sem referência, movimento rápido e translação >15 cm no modo direção-apenas.
- Persistem limitações: relógios não hardware-sincronizados, rolling shutter, offset do HAL Samsung, ARCore relocalização e posição da âncora visual diferente do alto-falante físico.

### Filtro acústico e calibração

- Novo `PairQuality` calcula score diagnóstico [0–1] de coerência, força/unicidade dos picos GCC-PHAT, nível, saturação, intervalo de interpolação, rotação e estado visual. **O score não é probabilidade de localização correta**. Só pares acima do limiar e sem vetos entram no ajuste.
- `AudiovisualCalibration` continua estimando `delay_us = offset + gain * dot(microphone_axis_world, marked_source_direction_world)` com mínimos quadrados robustos.
- São necessárias >=24 amostras aceitas com projeções distribuídas por >=0,55. O ajuste exige ganho/offset plausíveis e MAE de treinamento <125 µs. O botão **Congelar calibração** só fica disponível após atender esses critérios, e apenas com gravação parada.
- A fase VALID não altera o perfil; mede MAE do TDOA em uma nova trajetória, comparando com o modelo ingênuo de offset constante. O reconstrutor 3D recebe exclusivamente atrasos calibrados e quaternions. A direção marcada é consultada **depois**, para o erro angular observacional.
- O erro angular é relativo à referência visual fornecida pelo usuário, não um padrão laboratorial independente. Solução 3D fica indisponível sem cobertura espacial e consistência suficientes.

### ZIP completo, incluindo todas as gravações

O ZIP exportado contém:
- `train_audio_<data>.wav` e `train_windows_<data>.csv`.
- `valid_audio_<data>.wav` e `valid_windows_<data>.csv`.
- `train_pairs.csv` e `valid_pairs.csv` com projeção conhecida, atraso e qualidade de cada par.
- `summary.json` com fase, offset, ganho, MAE de treino e validação, baseline, cobertura, qualidade média, erro angular 3D observacional e rota ativa dos microfones.
- `metadata.txt` com limitações e bases de tempo.

Se o usuário repetiu uma etapa, os segmentos anteriores também são mantidos no ZIP. A exportação é local por `ACTION_CREATE_DOCUMENT`, sem upload automático.

## Primeiro teste sugerido

1. Posicione um alto-falante ou outro celular com ruído rosa/branco a **1–2 metros**, imóvel, sem outra fonte alta próxima. Evite ambientes muito reverberantes.
2. Entre na **Calibração com câmera**. Quando o ARCore estiver rastreando, toque no objeto emissor. Se ele apresentar `DIRECAO_APENAS`, gire o S25 mantendo seu centro aproximadamente parado; o modo `AR_ANCHOR` tolera pequenas translações, mas a posição da âncora pode não coincidir com o emissor acústico.
3. Toque **Iniciar calibração**. Durante 20–40 s, faça giros lentos esquerda/direita, inclinações para cima/baixo e alguma rotação longitudinal. Movimentos acima de ~32°/s serão rejeitados. Os coeficientes devem estabilizar e a cobertura aumentar.
4. Pare a gravação. Se aparecer **Pronto para congelar**, toque **Congelar calibração**. Se não, continue girando até gerar amostras válidas com direções diversas.
5. Toque **Iniciar validação**, faça uma **nova varredura** com a fonte ainda parada, e pare o áudio. Agora o arquivo contém apenas pares VALID e o modelo não pode se reajustar.
6. Toque **Exportar** e compartilhe o ZIP. Observe o status da validação, MAE em µs e eventual erro 3D em graus relativo ao marcador.

## Critérios de aceite

- Todos os estados transitam conforme testes, sem misturar TRAIN/VALID.
- Frame capture timestamps podem ser comparados com Audio BOOTTIME dentro de limites verificáveis; o tempo `Session.update()` não é usado como se fosse instante de exposição.
- Rejeições são explicáveis: CLOCK, TIME_INTERPOLATION, ACUSTIC_QUALITY, MOTION, TRACKING, etc.
- Não declarar sucesso 3D com rotações planas, picos tonais ambíguos ou validação ausente.
- A build passa por testes JVM, APK debug e revisão no Galaxy S25.

O app continua um instrumento **experimental**, não um localizador de fontes simultâneas nem um sistema de medição angular certificado.

### Indicadores visuais na câmera

Na validação, após uma solução 3D observável, são mostrados **dois marcadores diferentes**: verde para a posição da fonte **marcada pelo usuário** e âmbar para a direção **estimada apenas pelo áudio e movimentos**. Se o modelo não tiver dados suficientes, o segundo marcador permanece oculto. A posição dos marcadores não deve ser interpretada como erro angular validado em laboratório. A área técnica recolhível permite alterar o limiar mínimo do score heurístico (40–75%, padrão 58%).
