# Acoustic Direction Lab v0.3 — captura por evento e sincronização

Projeto experimental Android para estudar localização de fontes sonoras com microfones internos do Galaxy S25 (SM-S931B) e movimentos do aparelho. **Sem IA e sem rastreamento por câmera nesta versão.**

## Instalação no S25

1. Abra [Build Android APK — Actions](https://github.com/joaogabrielgn/Mictest/actions).
2. Escolha a última execução verde **Build Android APK**, abra o artefato **AcousticDirectionLab-APK** e extraia **app-debug.apk**.
3. Execute o APK no celular. Se a atualização falhar devido à chave debug do GitHub Actions, **exporte antes** os WAV/CSV da versão anterior, desinstale o aplicativo anterior e instale v0.3. Dados privados do app são apagados ao desinstalar.

O app pede permissão de microfone; registra áudio e dados de movimento localmente. Não tem internet, uploads automáticos ou câmera. Não armazene gravações com conversas privadas no GitHub público.

## O que a v0.3 implementa

- **Detecta estalos automaticamente**, considerando o áudio como fluxo contínuo; não depende dos limites dos blocos recebidos. Usa a variação de sinal em janelas de 64 frames, gatilho adaptativo e intervalo mínimo de 140 ms. O detector é otimizado para impactos/sons curtos; não detecta necessariamente sons contínuos ou vozes.
- Cada estalo gera uma **janela PCM de 2048 frames** com aproximadamente 256 anteriores ao evento e 1792 posteriores. Aplica o GCC-PHAT da v0.2 especificamente a essa janela (com estimativa subamostral, detecção de clipping e rejeição de correlações ambíguas).
- Usa **AudioRecord.getTimestamp(AudioTimestamp.TIMEBASE_BOOTTIME)** para associar posição de amostra e instante de captura. Quando o timestamp não é disponibilizado ou a continuidade falha, reporta **SEM_TIMESTAMP**. *Nunca* substitui automaticamente esse timestamp pelo instante da leitura do buffer.
- Grava quaternions do sensor de rotação e interpola com **SLERP** para o instante do evento. Reporta pose inválida se faltar uma leitura antes e outra depois ou se houver lacuna temporal excessiva.
- Exporta três arquivos de cada gravação: WAV original, `analysis_*.csv` (uma linha por evento por par de canais) e `imu_*.csv` (sensores brutos com timestamps).
- Acrescenta testes de software para: estalos que cruzam buffers, silêncio, clock em frames, descontinuidade temporal, SLERP e extrapolação proibida, além dos testes GCC-PHAT anteriores.

### Limitações honestas

- O Android informa que os microfones do S25 entregam sinais **PROCESSED**. Capturar 2 canais não comprova que tenham atrasos acústicos brutos estáveis ou que cada canal corresponda a um microfone físico.
- Timestamp de áudio é **estimado pelo Android** e pode ter latência/erros próprios; não equivale a calibração externa absoluta. Sinais curtos podem ser mascarados por processamento de ruído.
- Não existe ainda solução de azimute, elevação, distância, confiança angular ou projeção pela câmera.
- **CANDIDATO** significa que uma correlação passou por filtros heurísticos, não que a direção está correta.
- A v0.3 detecta estalos a partir de transientes da forma de onda; ambientes reverberantes podem gerar falsos eventos.
- Gravação limitada a **30 segundos**; pode ser encerrada antes.

## Protocolo de teste recomendado

**Configuração**: fonte `CAMCORDER`, 2 canais, 48000 Hz. Celular apoiado em suporte, sem tocar no suporte ou na mesa. Fonte sonora: clique curto repetido no ar, preferencialmente um alto-falante ou estalo dos dedos, a 0,5–2 m e sem saturação.

**Controle estacionário**: grave cerca de 15 segundos com a fonte diretamente acima do celular e o telefone parado. Depois faça gravações separadas com fonte abaixo e centralizada atrás da traseira. Mantenha orientações e distâncias conhecidas. Exporte WAV e CSV de análise de cada arquivo.

**Varredura**: deixe a fonte no mesmo lugar e gire lentamente o S25 para os lados, depois incline-o. Grave aproximadamente 30 segundos, evitando obstruir microfones com a mão. Exporte **WAV + análise CSV + IMU CSV** e informe movimentos realizados.

**Critério para continuar**: observar eventos com `audio_time_status=OK` **e** `pose_status=INTERPOLADA` / `EXACT`, suficientes para comparar a consistência dos atrasos entre orientações. Se muitos eventos mostrarem `SEM_TIMESTAMP` ou `IMU_SEM_BRACKET`, teremos de depurar o sincronismo no aparelho antes de avançar.

## Arquivos e unidades do CSV

- `onset_frame`: índice de quadro de áudio (0-based) relativo ao início da gravação.
- `window_start_frame`: primeiro quadro da janela GCC-PHAT (2048 quadros).
- `audio_boottime_ns`: timestamp de captação estimado pelo Android (nanosegundos, BOOTTIME), ou -1 se indisponível.
- `anchor_frame / anchor_boottime_ns`: último par de referência fornecido pelo Android.
- `delay_B_minus_C0_us`: atraso relativo entre canais em microssegundos, positivo se C(B) chega depois de C0.
- `pose_status`: `EXACT`, `INTERPOLADA` ou motivo da ausência.
- `qw,qx,qy,qz`: quaternion interpolado (não calibrado para a câmera).
- `yaw_deg,pitch_deg,roll_deg`: orientação calculada a partir do quaternion.
- `trigger_ratio`: intensidade do transiente relativa ao fundo estimado, **não probabilidade de acerto**.

## Próxima etapa

Verificar em gravações reais a qualidade dos timestamps e a repetibilidade acústica. Depois, implementar calibração de offset entre canais e geometria por varredura com fonte estacionária, ainda sem IA.
