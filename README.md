# Acoustic Direction Lab v0.5 — Calibração audiovisual com ARCore

Projeto experimental Android, desenvolvido com Samsung Galaxy S25 (SM-S931B), Android 17, sem inteligência artificial. [Código-fonte do Mictest](https://github.com/joaogabrielgn/Mictest).

## O que chegou nesta versão

A versão 0.5 mantém todas as funções de v0.4.3 (captura estéreo contínua, GCC-PHAT, IMU e hipótese 3D) e acrescenta o novo botão **Abrir calibração com câmera + ARCore**.

A tela AR usa o **ARCore como proprietário exclusivo da câmera traseira** e mostra a imagem através de textura OpenGL ES; não tenta abrir CameraX ao mesmo tempo. Possui:

1. **Marcação por toque na própria imagem.** Toque no objeto que está emitindo som. Quando ARCore detecta plano/ponto/depth apropriado, usa uma `AR_ANCHOR` e atualiza a direção da câmera para a fonte, mesmo durante pequenas translações. Se não houver interseção AR confiável, usa `DIRECAO_APENAS`: a direção óptica marcada persiste no espaço durante **rotações**, mas movimentos laterais de mais de ~15 cm tornam a aproximação inválida. O ponto marcado não é uma localização acústica estimada.
2. **Captação simultânea.** Microfone em `CAMCORDER`, `2 canais`, `48000 Hz`. Processa todo o fluxo de áudio, incluindo motores, ventiladores e chiados, em janelas de 4096 frames com avanço de 1024 frames, sem depender de estalos.
3. **Pares audiovisuais.** Para cada medição selecionada, registra TDOA (µs), timestamp acústico `BOOTTIME`, ARCore `BOOTTIME` obtido após a atualização do quadro, diferença temporal entre as duas leituras, direção do eixo físico dos microfones via `Frame.getAndroidSensorPose()` e direção da fonte marcada no mundo. Somente utiliza pares próximos no tempo e captura lenta (<28°/s), sem clipping, com alguma coerência espectral. **Importante:** ARCore e AudioRecord não estão hardware-sincronizados; esta é uma aproximação por relógio monotônico, válida apenas como protótipo. O atraso de câmera desconhecido, diferenças de processamento e rastreamento degradado podem enviesar a calibração.
4. **Calibrador paramétrico robusto**, sem rede neural: ajusta `delay_us = offset + gain * dot(axis_world, source_direction_world)`. Exige >=24 medidas espacialmente variadas, faixa de projeção >=0,55 e desvio médio/ganho fisicamente plausíveis. Não transforma diretamente o ponto marcado em localização do som.
5. **Validação realmente posterior ao ajuste.** O botão `Calibrar / Validar` congela o modelo, passando a gravar medidas novas que nunca atualizam o perfil. A validação calcula MAE de TDOA no conjunto novo e compara com um modelo de atraso constante. Se o erro estiver elevado, o app avisa em vez de declarar sucesso.
6. **Reconstrução 3D experimental com parâmetros calibrados.** Após congelar o modelo, a nova varredura utiliza `(delay-offset)/gain` e vários eixos dos microfones orientados no mundo (ARCore) para reconstruir uma direção. Essa reconstrução é independente da marcação visual durante a inferência; o marcador entra depois apenas para calcular o **erro angular observacional**. Se não houver cobertura tridimensional ou resíduos aceitáveis, a solução é recusada. Mesmo um erro pequeno não prova generalização para outra fonte ou ambiente.
7. **Exportação de um ZIP pelo Android** contendo WAV da última captura, CSV acústico/AR por janela, `calibration_pairs.csv` com fases `TRAIN` e `VALID`, e `metadata.txt`. O usuário escolhe a pasta pelo seletor Android. Nenhum upload automático.

## Como instalar no S25

Abra [GitHub Actions](https://github.com/joaogabrielgn/Mictest/actions), toque na última execução verde e baixe o artefato `AcousticDirectionLab-APK`. Extraia `app-debug.apk` e instale. Se houver conflito de assinatura, exporte dados antigos antes de desinstalar. Para a nova calibração, conceda as permissões de **câmera e microfone** e tenha **Google Play Services para RA** instalado e atualizado. ARCore é opcional na configuração do aplicativo, então os testes acústicos continuam disponíveis sem RA.

## Protocolo recomendado — primeira calibração real

1. Use um segundo celular ou pequeno alto-falante reproduzindo **ruído rosa ou branco** a aproximadamente **1–2 metros**, em volume moderado, num ambiente silencioso. Preferir fonte estática, com banda larga; evitar estalos metálicos muito tonais.
2. Abra **Calibração com câmera + ARCore**. A tela entra no modo paisagem. Espere ARCore informar rastreamento e visualizar a fonte.
3. Toque diretamente sobre o objeto que produz o som. O marcador deve permanecer projetado durante rotações (e pode ir para a borda da imagem). Se o app mostrar `DIRECAO_APENAS`, não desloque lateralmente o S25: gire em torno do próprio centro.
4. Toque **Iniciar áudio**. Faça 15–25 s de varredura bem lenta: esquerda/direita, acima/abaixo e rotação do eixo longitudinal. Não obstrua nenhum microfone. O calibrador exige variedade angular e pode rejeitar movimentos muito rápidos, pois o descompasso vídeo/áudio ainda não foi corrigido.
5. Quando tiver amostras suficientes e a indicação do calibrador for plausível, toque **Calibrar / Validar**. Isso congela os coeficientes do perfil. Não reinicie a fonte, não a mova e não modifique o volume.
6. **Continue ou recomece a captura de áudio** e execute uma nova sequência de giros e inclinações. Somente essa nova parte é usada como `VALID` e para tentativa de reconstrução 3D. Quando houver observabilidade suficiente, o aplicativo apresenta a direção reconstruída e a diferença angular em relação ao ponto marcado.
7. Pare a gravação, toque **Exportar ZIP**, escolha uma pasta do Android e compartilhe o arquivo aqui.

### Como interpretar

- `AJUSTE_PILOTO`: os coeficientes tiveram dispersão plausível nos próprios dados de calibração. Não equivale a acurácia.
- `VALIDACAO_TDOA_OK`: um conjunto **novo** previu bem a medição de atraso quando comparado a um modelo de atraso constante. É validação limitada à mesma fonte e ambiente.
- `DIRECAO_3D_EXPERIMENTAL`: o modelo geométrico conseguiu ajustar uma direção após diversas rotações. Não significa automaticamente que o erro real seja baixo.
- `Erro angular vs referência`: diferença entre a direção 3D acústica e a fonte que o usuário marcou. Somente aparece com direção geométrica disponível.
- `SEM_DIVERSIDADE_GEOMETRICA`, `COLETANDO_MEDIDAS`, `GANHO_OU_ERRO_INCOMPATIVEL`: não force resultado; observe a cobertura e a qualidade dos sinais.

## Limitações que precisam ser resolvidas para produção

1. **Sincronização câmera/áudio ainda aproximada:** ARCore entrega poses e frames processados; o timestamp BOOTTIME é tomado depois de `Session.update()`, enquanto AudioRecord usa hardware AudioTimestamp. Como fase de prova de conceito, rejeitamos poses distantes em tempo e movimentos rápidos. Para máxima precisão precisaremos medir o offset de câmera e áudio e sincronizar/reinterpolar as poses pela exposição real.
2. **Calibração exclusiva do aparelho e rota de captura:** canais CAMCORDER são processados pela Samsung; rota, AGC, supressão de ruído, orientação e tipo de som podem afetar os atrasos.
3. **Uma única fonte:** não existe ainda separação confiável de várias fontes simultâneas nem identificação da fonte mais alta por intensidade.
4. **Bússola/orientação e AR relocalização:** mudanças de pose ARCore podem afetar a direção. Ponto com fallback direcional só vale aproximadamente em rotações sem translação.
5. **Âncora por hit-test:** ARCore pode rastrear um plano próximo que não corresponde à superfície física exata do alto-falante; a posição precisa da fonte ainda é desconhecida.
6. **Câmera posterior e geometria:** não há projeção acústica AR sem marcação, calibração de FOV personalizada, nem visualização de várias fontes no quadro. O marcador verde mostra a referência MANUAL, não o resultado inferido pelo microfone. A direção acústica calculada aparece numericamente apenas durante a validação.

Testes automatizados validam ajustes de offset/ganho robustos, rejeição de calibrações degeneradas, validação independente, relógios de pose e geometria de projeção sintética. A primeira execução no S25 será o teste indispensável do ARCore e da concorrência da câmera com a gravação CAMCORDER.
