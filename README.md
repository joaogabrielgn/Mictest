# Acoustic Direction Lab v0.4.1 — análise contínua com evidência graduada

Repositório: [joaogabrielgn/Mictest](https://github.com/joaogabrielgn/Mictest). Android; protótipo experimental para o Galaxy S25 (SM-S931B). **Sem IA**.

## Objetivo

Detectar atrasos intercanal de sons contínuos, como motores, chiados, ventiladores e voz, inclusive quando a energia se concentra em uma só faixa. Não precisa de estalos fora do modo de calibração. A v0.4 anterior produziu **0 MULTIBANDA_OK entre 4088 janelas** nas quatro gravações reais; os sinais tinham maior parte da energia em graves, com picos ambíguos nesse intervalo.

### Melhorias

- Mantém três bandas fixas do GCC-PHAT (180–950, 950–3000, 3000–8500 Hz) em janelas Hann de 4096 frames e avanço de 1024 frames a 48 kHz (85,3 ms e 21,3 ms).
- Adiciona uma **quarta banda adaptativa** entre 250 e 8500 Hz, escolhida dinamicamente a partir da distribuição espectral com suporte mínimo de 1300 Hz. Ela pode fornecer um atraso candidato em cenas nas quais as bandas fixas não passam no filtro. **É uma estimativa sobreposta às bandas fixas e não é contada como evidência independente.**
- Ajusta limiares de pico/contraste para bandas estreitas e mantém estados explícitos de rejeição (`SILENCIO`, `CLIPPING`, `PICO_AMBIGUO`, `TONAL_AMBIGUO`, `SEM_PICO_CONFIAVEL`).
- Acrescenta o histórico `TemporalEvidence` para selecionar medições repetidas na região temporal de 4 segundos, exigindo pelo menos 12 janelas com atrasos próximos, 0,65 s transcorrido e dispersão robusta MAD <=25 µs. Como janelas se sobrepõem, **essas medições não são observações independentes**. Um som estável ou eco também pode ser sistematicamente errado.
- Mostra níveis distintos: `CANDIDATO_UNICA_BANDA`, `TEMPORAL_ESTAVEL_BANDA_UNICA` (confiança temporal **parcial**) e `MULTIBANDA_OK` (duas faixas fixas com atrasos concordantes). Nenhum desses níveis significa confirmação de direção real, azimute, elevação ou precisão angular.
- Exibe suavização somente com evidência multibanda ou temporalmente estável; não mantém seta/posição antiga em silêncio ou ambiguidade.
- Exporta CSV com as quatro bandas, as fronteiras da banda adaptativa, status e métricas de suporte temporal.
- Mantém modo `ESTALOS_CALIBRACAO`, WAV e CSV dos eventos, IMU e AudioTimestamp da v0.4.

### Como instalar pelo celular

Abra [GitHub Actions](https://github.com/joaogabrielgn/Mictest/actions), entre no último run verde, baixe o artefato `AcousticDirectionLab-APK`, extraia `app-debug.apk` e instale. Se a assinatura debug impedir atualização, **exporte seus arquivos antigos** antes de desinstalar a versão anterior. Isso pode apagar os registros locais do app.

### Como testar

Selecione `SOM_CONTINUO`, `CAMCORDER`, `2 canais`, `48000 Hz`. Celular na horizontal. O campo de referência `MIC_INFERIOR` ou `MIC_SUPERIOR` refere-se à extremidade física do aparelho (não a esquerda ou direita, que mudam ao rotacionar). Faça um teste em cada extremidade com um mesmo ventilador ou emissor de ruído contínuo, a 50–100 cm, por 20–30 segundos. Faça um terceiro no ponto médio, a cerca de 50 cm, e uma varredura com fonte imóvel por 30–40 segundos. Mantenha volume moderado sem clipping. Exporte `WAV + continuous CSV` por gravação e `IMU CSV` da varredura.

Compare a percentagem de janelas com atraso candidato, temporalmente estável e multibanda e, principalmente, os atrasos médios **em dados novos**. Apenas depois dessa validação devemos introduzir modelo geométrico 3D ou indicador sobre a câmera.

### Limites conhecidos

Os canais `CAMCORDER` do Galaxy S25 aparecem no Android como **processados**, com mapeamento físico dos canais não comprovado. Sons monocromáticos podem ser temporalmente estáveis e ainda ter atraso errado. Se houver vários sons simultâneos, o algoritmo não separa fontes nem garante localizar a mais alta. O modo contínuo é um estimador de atraso por janela, não uma câmera acústica ou DOA 3D validado.

O máximo de captura contínua é 120 segundos; modo de estalos, 30 segundos. Os sinais são salvos localmente, sem upload automático.
