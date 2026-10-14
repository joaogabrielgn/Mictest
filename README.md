# Acoustic Direction Lab v0.3.1 — correção de eventos acústicos

Aplicativo Android experimental para estudar a localização espacial de sons no Galaxy S25 (SM-S931B) sem inteligência artificial. Mantém gravação PCM multicanal, AudioTimestamp BOOTTIME, quaternions interpolados e exportação WAV/CSV/IMU da v0.3.

## Instalação exclusivamente pelo S25

Abra https://github.com/joaogabrielgn/Mictest/actions, escolha a execução **verde** mais recente **Build Android APK**, clique em **AcousticDirectionLab-APK** em **Artifacts** e extraia `app-debug.apk` do ZIP baixado. Instale pelo app Meus Arquivos. Se a assinatura de depuração impedir atualização, **exporte** seus arquivos antigos antes de desinstalar a v0.3; os dados privados podem ser apagados.

## Principais correções da v0.3.1

- **Bloqueio de disparos repetidos:** janela refratária de 350 ms (contra 140 ms anteriores). Registra a quantidade de blocos candidatos suprimidos. Isso reduz a chance de que reverberação ou cauda do estalo seja computada como um segundo evento. Também significa que estalos reais com menos de 350 ms de intervalo podem ser ignorados.
- **Janela acústica inicial:** o GCC-PHAT é medido nas primeiras 1024 amostras da janela de 2048 frames, que inclui cerca de 5 ms antes do gatilho. A estimativa da janela completa é calculada separadamente; discordâncias acima de 125 µs fazem o candidato ser sinalizado. Janelas usam taper de borda curta em vez de Hann, para não atenuar excessivamente os primeiros transientes.
- **Primeira subida dos canais:** estimativa independente e *heurística* do primeiro transiente em cada canal, comparada ao GCC-PHAT. Início e GCC que divergem acima de 150 µs recebem `INICIO_DIVERGE`. **Não é detecção comprovada do trajeto direto** e pode falhar com ruído, som processado, reflexões e diferenças de volume.
- **Saturação:** um único frame muito próximo do limite digital (>= 99,5% da escala) já é suficiente para rejeição. O filtro anterior aceitava cliques saturados quando representavam menos de 1% da janela.
- **Limite físico:** candidatos além de d/c (142,6 mm/343 m/s = ~416 µs) mais tolerância de 1,25 amostra são rotulados `FORA_LIMITE_FISICO`. **Essa regra é provisória**: canais processados podem introduzir atrasos eletrônicos ainda não calibrados; a rejeição não comprova que o som seja inválido.
- **Estabilidade local:** medianas e MAD robusta calculadas somente entre eventos próximos no tempo (2,5 s) e com orientação de até 8° de diferença. Eventos claramente discordantes (> max(90 µs, quatro desvios MAD escalonados)) podem ser sinalizados `DISPERSAO_ELEVADA`. Não se compara diretamente uma direção diferente durante rotação.
- **Dados transparentes:** o CSV preserva todos os campos da v0.3, acrescentando primeiro transiente de cada canal, atraso inicial, comparações de janelas, número de vizinhos, mediana, MAD e `robust_candidate / robust_reason`.

**QUALIFICADO / robust_candidate=1** significa somente que o evento passou pelos filtros adotados (audio timestamp, pose IMU, GCC, início acústico, saturação, geometria e estabilidade). Não é uma probabilidade, precisão angular ou confirmação de que o app identificou a fonte corretamente.

## Testes de bancada

O fluxo CI roda testes sintéticos do GCC-PHAT, detecção de estalos cruzando buffers, bloqueio de ecos, primeira chegada, restrição geométrica, clipping, sincronização temporal, interpolação de quaternions e mediana/MAD; gera APK ao final.

Na posição **horizontal (paisagem)**, os microfones identificados do S25 ficam aproximadamente próximos às extremidades esquerda/direita, não acima/abaixo da imagem.

Configuração: `CAMCORDER` | `2 canais` | `48000 Hz`.

**Três gravações estáticas:** imóvel no suporte, estalos no ar a 0,5–1 m (sem bater na mesa), posicionados respectivamente na lateral do microfone à esquerda, na extremidade oposta à direita e à frente da traseira, centralizados. 10–15 segundos em cada posição, cliques separados por pelo menos 0,5 s.

**Uma varredura:** deixe a fonte fixa a 1–2 m e movimente somente o S25 durante 25–30 s, girando devagar para esquerda/direita e inclinando depois para cima/baixo; sem cobrir os microfones. Faça estalos separados por >=0,5 s e exporte **WAV + análise CSV + IMU CSV**.

O S25 declarou canais `PROCESSED` para CAMCORDER, portanto a independência e constância do atraso intercanal não estão comprovadas. Mesmo filtros de boa qualidade não recuperam informação espacial inexistente. Esta versão não apresenta nem valida azimute, elevação, distância ou um marcador na câmera. Próxima etapa: avaliar estatisticamente as medições em direções conhecidas e somente depois implementar calibração geométrica.
