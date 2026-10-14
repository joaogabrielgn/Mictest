# Acoustic Direction Lab v0.4.3 — espectro assimétrico + reconstrução 3D experimental

Protótipo Android, sem IA. Desenvolvido com o Samsung Galaxy S25, modelo SM-S931B, em `CAMCORDER` estéreo 48 kHz. [Código no GitHub](https://github.com/joaogabrielgn/Mictest).

## Instalação no S25

Abra [GitHub Actions](https://github.com/joaogabrielgn/Mictest/actions), selecione a execução concluída em verde mais recente, baixe `AcousticDirectionLab-APK`, extraia `app-debug.apk` e instale. Se a assinatura de depuração impedir atualizar, exporte os WAVs e CSVs antigos antes de desinstalar.

## 1. Correção do lado do microfone inferior

Os testes anteriores indicaram ~61% de janelas com marcador visível no microfone inferior contra ~97% na extremidade superior. Havia um veto único `flatness < 0,0015` mesmo para sinais com atraso repetível, além de saturação visual no índice-piloto abaixo de -1.

- A v0.4.3 avalia uma emissão muito tonal junto com **coerência entre canais, razão do pico e força de picos alternativos**. Uma banda fixa com coerência >=0,35, pico >=2,8, segundo pico <0,82 do principal e no máximo 4 máximos relevantes pode manter um atraso tonal como **candidato**, em vez de descartá-lo imediatamente.
- **Não declara som tonal 3D ou ângulo correto apenas por repetibilidade.** O estado `TONAL_TEMPORAL_PROVISORIO` é só uma evidência temporal, que não prova que o pico escolhido seja o trajeto direto. Se os picos permanecem ambíguos, continua `TONAL_AMBIGUO`.
- O indicador 1D exibe aproximadamente −1,18 a +1,18, com marcas fixas para −1 (microfone inferior) e +1 (superior). Pequenos excessos da calibração deixam de parecer travamentos na borda. Ainda é índice empírico, não ângulo.

## 2. Primeira reconstrução espacial 3D — EXPERIMENTAL

O novo `WorldDirectionSolver` transforma a linha de base física aproximada entre os dois microfones em um eixo orientado no mundo via quaternion de cada amostra IMU. Converte o atraso estéreo pelo **perfil-piloto empírico** para um índice de projeção relativo ao eixo. Ajusta uma direção estacionária por mínimos quadrados robustos (Huber) ao longo da varredura.

**Hipóteses fortes:** fonte aproximadamente fixa durante toda a coleta; distância suficiente para um modelo direcional distante; orientação da bússola/sensor estável; relação aproximadamente linear entre índice calibrado e projeção acústica; rota de captação Samsung sem mudanças de atraso conforme o gesto. O Android declara canais `PROCESSED`; nenhuma dessas hipóteses foi validada na prática para reconstrução 3D.

A interface inclui uma seção **Reconstrução 3D por rotação — experimental** com componentes angulares horizontais e verticais **relativos à direção óptica traseira do aparelho**, calculados nos eixos físicos do dispositivo; NÃO são coordenadas de pixels da câmera, não usam FOV/intrínsecos da lente e não são geolocalização. Não existe captura de câmera nem AR nesta versão.

Para que um resultado apareça, o algoritmo exige:
- 20 ou mais amostras separadas temporalmente, com pelo menos 3 s de observações e histórico de até 45 s.
- Eixos de microfone cobrindo pelo menos ~40° e condição tridimensional observável: autovalor mínimo/máximo da matriz de orientação >=0,022. Apenas giro horizontal em um plano deixa o problema degenerado e deve **recusar 3D**.
- Norma do ajuste preliminar compatível com direção unitária (~0,55 a 1,42) e RMS residual na projeção <=0,18.
- Dados de áudio não saturados, pico válido, pose sincronizada e movimento plausível. O sistema não tem garantia de separar múltiplas fontes e, portanto, pode rejeitar sons mistos ou produzir resultados fisicamente enviesados.

Quando não há cobertura ou consistência suficiente, a interface mostra `COLETANDO_ROTACOES`, `VARREDURA_ANGULAR_INSUFICIENTE`, `EIXOS_NAO_COBREM_3D`, `AJUSTE_INCONSISTENTE`, entre outros. **Não transforma RMS em precisão angular.** Mesmo quando aparece `DIRECAO_3D_EXPERIMENTAL`, a direção ainda não é validada em graus.

O botão `Reiniciar varredura 3D` limpa as observações para começar com uma nova fonte. Todas as janelas continuam no `continuous CSV`, com status do solver, quantidade de dados, cobertura angular, condicionamento, erro, componentes 3D e ângulos derivados. Mantém os dados do IMU e WAV originais.

## 3. Teste recomendado

Use `SOM_CONTINUO` / `CAMCORDER` / `2 canais` / `48000 Hz`.

1. **Teste estacionário:** 15–20 segundos cada: MIC_INFERIOR, MIC_SUPERIOR e CENTRO. Fonte contínua moderada e S25 apoiado horizontalmente. O indicador 1D deve ficar visível com maior frequência no inferior em comparação à v0.4.2, mas sempre marcar ambiguidade tonal quando apropriado.
2. **Teste 3D:** deixe uma fonte contínua única, fixa a 1–2 m (preferencialmente ruído rosa/branco em outro aparelho). Gire o S25 lentamente **para esquerda e direita** e também **incline para cima/baixo e role o eixo longitudinal**, mantendo aproximadamente o mesmo centro espacial, por 25–40 segundos. Se só girar na horizontal, a reconstrução 3D deverá ser recusada por geometria insuficiente.
3. Repita a varredura com a fonte em outra posição conhecida e verifique se a indicação angular muda coerentemente. A maior prioridade é rejeitar falsas soluções, não apresentar qualquer ponto a todo custo.

Exporte WAV + `continuous CSV` de cada teste e IMU CSV das varreduras. Compare o desempenho com a v0.4.2. A v0.4.3 ainda não identifica qual de dois sons simultâneos é o mais forte, não calcula distância e não projeta no vídeo.

## Limitações do protótipo

A inversão de atrasos no eixo foi reproduzida em testes anteriores, mas o canal processado não é comprovadamente equivalente a um atraso acústico geométrico puro. A reconstrução 3D utiliza uma aproximação que pode falhar em locais reverberantes, com fonogramas tonais, múltiplas fontes ou variações de orientação. Sem teste de erro com referência angular independente, toda a direção 3D deve ser tratada como **hipótese de engenharia**, não medição validada.
