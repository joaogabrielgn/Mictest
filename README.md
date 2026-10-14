# Acoustic Direction Lab v0.3.2 — perfil empírico S25 (sem IA)

Protótipo experimental para localização acústica com canais Android `CAMCORDER` processados, GCC-PHAT e sensores IMU. Não é câmera acústica nem fornece azimute, elevação ou distância reais. O aplicativo é aberto: [Mictest](https://github.com/joaogabrielgn/Mictest).

## O que mudou

1. **O limite ideal de 416 µs não rejeita mais a medição.** Ele gera `exceeds_ideal_air_limit=1` e `ideal_air_excess_us` apenas como informação diagnóstica. O canal processado pode introduzir atrasos eletrônicos. O intervalo de busca GCC-PHAT foi ampliado para encontrar picos além da faixa teórica ideal. Resultados na borda da busca ainda são rejeitados.
2. **O início acústico deixa de ser veto automático.** Mantém `first_rise_delay_us` e `first_onset_agree` no CSV, mas um detector de limiar não deve invalidar sozinho GCC-PHAT consistente, dado processamento independente dos canais.
3. **Duas medidas separadas:**
   - `robust_candidate=1`, mostrado como **TDOA_OK**, indica GCC-PHAT e concordância inicial/completa sob os filtros heurísticos, sem converter em ângulo.
   - `acoustic_repeatable=1` indica pelo menos três vizinhos em 2,5 s com diferença de pose até 8 graus, MAD <=35 µs e proximidade da mediana. Isso pode demorar alguns cliques e é **métrica de repetibilidade local**, não precisão global.
4. **Referência rotulável.** Novo seletor: `NÃO_ROTULADA`, `MIC_INFERIOR`, `MIC_SUPERIOR`, `CENTRO`, `VARREDURA_FONTE_FIXA`. Sua escolha entra em todas as linhas CSV e ajuda a montar calibração e validação posteriores.
5. **Índice experimental 1D no eixo físico dos microfones.** Perfil piloto, derivado das gravações já rotuladas pelo usuário em 14/10/2026:
   - `MIC_INFERIOR`: **−434,7 µs** (no último teste, extremidade **direita**).
   - `CENTRO`: **−2,8 µs**, obtido de poucos eventos.
   - `MIC_SUPERIOR`: **+472,1 µs** (no último teste, extremidade **esquerda**).

A interpolação por trechos fornece o índice adimensional −1 (inferior) a +1 (superior), com zero na referência central. Direções não necessariamente sobre esse eixo podem ter o mesmo índice. **Não é arcoseno, ângulo, posição da imagem, azimute ou localização 3D.** Gravações em ambientes diferentes, com sons diferentes e processamento variável não foram validadas. Fora dos extremos, o app avisa `FORA_PERFIL_PILOTO` e mantém o valor para análise, sem extrapolar uma suposta direção confiável. Os dados não são usados para treinar IA.

### Instalação S25

1. Abra [GitHub Actions](https://github.com/joaogabrielgn/Mictest/actions).
2. Escolha a última execução com marca **verde**, baixe em **Artifacts** o ZIP `AcousticDirectionLab-APK`.
3. Extraia o `app-debug.apk` e instale. Caso a assinatura de debug difira, exporte WAV/CSV antigos antes de desinstalar a versão anterior; a desinstalação pode apagar dados do app.

### Testes

Use `CAMCORDER · 2 canais · 48000 Hz` e S25 horizontal. Desative conexões Bluetooth de áudio para evitar seleção acidental de outro microfone; confirme sempre no relatório os microfones ativos 22/23. No seletor de **posição de referência**, escolha a posição da FONTE relativa às extremidades físicas, não à esquerda/direita da tela.

Para validação **fora da amostra** (não usada para gerar os três valores-piloto), faça três registros estacionários de 10–15 segundos: `MIC_INFERIOR` (fonte ao longo da extremidade inferior física), `MIC_SUPERIOR` e `CENTRO`, com estalos suaves no ar separados >=0,5 s. Se quiser, faça uma quarta gravação com `VARREDURA_FONTE_FIXA` de 20–30 s, fonte fixa e movimentando apenas o telefone, mantendo aproximadamente o mesmo centro no espaço. Exporte WAV + CSV da análise e, da varredura, também IMU CSV.

Objetivo de aceitação desta versão: reconhecer os padrões de sinais opostos, identificar seus limites empíricos, registrar repetibilidade e verificar se o perfil piloto se sustenta em novos dados. **Só depois** tentaremos converter os sinais em direção espacial com incerteza calibrada; para isso serão necessárias referências angulares intermediárias.

### Limitações

Os microfones da Samsung foram relatados como `C0:PROCESSED` por ambos os dispositivos: os canais não são dados brutos garantidos. O perfil piloto é específico do S25 testado, modo CAMCORDER e da coleta mencionada, e não pode ser transferido automaticamente para outros telefones. Perfis e limites de geometria física são informações separadas.
