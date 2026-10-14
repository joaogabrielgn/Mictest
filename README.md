# Acoustic Direction Lab v0.4.2 — Coerência, movimento e direção no eixo

Protótipo de pesquisa acústica sem IA, desenvolvido para o Galaxy S25 SM-S931B, Android 17. [Repositório Mictest](https://github.com/joaogabrielgn/Mictest).

### Objetivo real

Avaliar continuamente sons predominantes, incluindo chiados, motores, ventiladores, voz e ruído ambiente, **sem depender de estalos**. Com apenas dois canais de microfones internos PROCESSADOS e nenhum algoritmo de separação de fontes, ainda não é possível garantir qual fonte simultânea é a mais alta nem recuperar sua direção 3D de forma única.

### Novidades v0.4.2

- **Coerência espectral intercanal estimada:** média exponencial de produtos cruzados de FFT e energias dos canais, calculando magnitude-squared coherence aproximada por frequência, e ponderando a correlação de fase por esta coerência. Janelas sucessivas de 85,3 ms se sobrepõem; a coerência *não* equivale a repetições independentes nem prova trajeto direto. Com rotação, o filtro espectral tem pequena memória e pode atrasar o pico.
- **Picos alternativos:** para cada banda (180–950, 950–3000, 3000–8500 Hz e adaptativa), registra atraso principal, segundo pico, razão relativa e número de máximos locais relevantes. Sons tonais podem ter vários picos e permanecer ambíguos.
- **Movimento físico do S25:** usa o quaternion interpolado na hora acústica da janela. Estima deslocamento angular aproximado nos últimos 600 ms e um **limite superior de variação de atraso** baseado na distância estimada entre os microfones (142,6 mm), velocidade do som ~343 m/s e margem de 45 µs. Status `PARADO`, `ROTACAO_PLAUSIVEL`, `ROTACAO_INCOMPATIVEL`, `SEM_DADO_SINCRONIZADO`. É uma regra de plausibilidade com processamento Samsung desconhecido, **não prova uma fonte fixa nem determina direção 3D**.
- **Estabilidade corrigida:** a v0.4.1 chamava de `TEMPORAL_ESTAVEL` mesmo durante a rotação. A v0.4.2 só apresenta estabilidade na interface após o S25 ficar praticamente imóvel por >=700 ms e haver atrasos temporalmente estáveis. O CSV mantém a evidência original (`temporal_stable`) e registra `pose_adjusted_stable` para auditoria.
- **Indicador visual 1D:** novo eixo horizontal com `Inferior — Centro — Superior`, usando o perfil-piloto de 14/10/2026 (−434,7 / −2,8 / +472,1 µs). Exibe marcador somente em janelas elegíveis; âmbar indica prévia durante movimento. Faixa ao redor reflete dispersão instrumental do atraso, não incerteza angular calibrada. Isso **não** é um overlay de câmera nem identifica lado da tela se o telefone foi virado.
- **CSV contínuo expandido:** campos adicionais para movimento, velocidade angular, compatibilidade do atraso com a rotação, estabilidade com pose, quatro coerências e picos alternativos.
- Modo `ESTALOS_CALIBRACAO` preservado. Sem IA, sem permissões novas e sem upload automático.

### Instalação

Abra [GitHub Actions](https://github.com/joaogabrielgn/Mictest/actions), escolha a última execução **verde**, baixe o artefato `AcousticDirectionLab-APK`, extraia `app-debug.apk` e instale. Se o Android impedir a atualização por chave de assinatura debug incompatível, **exporte os WAVs/CSVs antigos antes de desinstalar** (os arquivos privados podem ser apagados).

### Teste recomendado para esta versão

Configure `SOM_CONTINUO` / `CAMCORDER` / `2 canais` / `48000 Hz` e S25 em paisagem.

1. Coloque um ventilador ou segundo celular emitindo ruído contínuo fixo a 0,5–1 m de **MIC_INFERIOR**, sem obstruir os microfones, e grave 20–30 s com S25 parado.
2. Repita em **MIC_SUPERIOR** e **CENTRO**, em gravações separadas.
3. Faça uma **varredura** de 30–40 s com **a fonte FIXA** e o S25 girando lentamente (não translade muito o telefone). Exporte WAV, `continuous CSV` e `IMU CSV`.

No CSV compare as medianas dos atrasos válidos em cada extremidade e o novo campo `pose_adjusted_stable`. Na varredura observe o campo `motion_status`: não interprete `ROTACAO_PLAUSIVEL` como localização espacial comprovada. A calibração por câmera e direção 3D exigirão pontos com ângulos conhecidos e validação fora do conjunto usado para calibrar.

### Desenvolvimento

Testes sintéticos cobrem silêncio, sinais tonais, atrasos negativos/positivos, coerência entre canais, quaternions, movimento rápido, limites físicos de plausibilidade e estabilidade temporal. CI compila APK debug após os testes.
