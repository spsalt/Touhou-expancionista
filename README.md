# Touhou 67: Antimony of Recogna's Expansion

Trabalho semestral de Programação Orientada a Objetos — UNESP Bauru.
Bullet hell em Java + Swing. História em `Roteiro.txt`.

**Integrantes:** Christhian Lucio Nalia, Samuel Psaltikidis, Lucas Fernandes

---

## Como rodar

Da **raiz do repositório** (os caminhos de `sprites/` e `config/` são relativos à pasta atual):

```
rodar.bat          (Windows)
./rodar.sh         (Linux/Mac)
```

Ou manualmente:

```
javac -encoding UTF-8 -d out $(find src -name "*.java")
java -cp out src.Main
```

## Controles

| Tecla | Ação |
|---|---|
| WASD / setas | mover |
| Z | atirar |
| **C** | **liga/desliga o autofire** (atira sozinho; estado aparece no HUD) |
| **V** / clique no ícone do HUD | **GPT Expansion** — ataque especial (ver seção abaixo) |
| X (segurar) | modo foco — anda devagar, fecha o leque, mostra a hitbox |
| ENTER | confirmar / avançar cutscene |
| ESC | pausar (ou pular a cutscene inteira) |
| F5 | recarregar `game.properties` e sprites sem fechar o jogo |

O autofire já começa ligado; pra mudar isso use `jogador.autofireInicial`.
Segurar Z continua funcionando normalmente pra quem preferir.

---

## Ajustando a jogabilidade

**Não mexa em números dentro do código.** Todos os valores de tuning
(velocidade, HP, cadência de tiro, tamanho do campo, ritmo das ondas)
estão em **`config/game.properties`**, comentado linha a linha.

Fluxo pra testar um ajuste:

1. edita o `.properties`
2. aperta **F5** com o jogo aberto
3. o valor novo já vale (menos tamanho de janela/campo, que precisam de restart)

Se uma chave sumir ou vier com valor inválido, o jogo usa o padrão embutido
no código e avisa no console — nunca quebra.

Chaves úteis pra debug (fim do arquivo):

- `debug.mostrarHitboxInimigo` — círculo amarelo em volta dos inimigos
- `debug.balasDosCantos` — código de teste antigo: 4 balas dos cantos mirando em você
- `debug.pontosDeTeste` — chuva de itens de XP

---

## Convenções do código

Mantidas do combinado inicial do grupo:

- Toda classe tem **`tick()`** (lógica: posição, colisão, estado) e
  **`render(Graphics2D g)`** (só desenho, nada de lógica).
- Atributos **privados**, acessados por getters e setters.
- Tratamento de exceções em todo I/O (leitura de config, carga de sprites).
- `Default.java` é o esqueleto pra começar uma classe nova.

### Telas

A tela atual é a String estática `Main.gameState`:

`"Menu"` · `"Game"` · `"Pause"` · `"GameOver"` · `"Vitoria"`

Tanto `tick()` quanto `render()` do `Main` ramificam nela.

### Fases e estágios

Cada fase se divide em estágios. Ao trocar de estágio o cronômetro `time`
zera, e o que acontece é escrito **em função de `time`** — sem máquina de
estado complicada, só "no tick X, spawna Y".

Mesma ideia pros inimigos: `Enemy` tem um `t` que conta os ticks desde o
spawn, e movimento/ataque são fórmulas em cima dele.

---

## Estrutura

```
config/game.properties     TODOS os ajustes de jogabilidade
sprites/                   PNGs (bosses, enemies, player, GFX, ambient)
audio/                     trilha sonora (WAV — ver seção Música)
src/
  Main.java                janela, game loop (Thread própria), colisões, estados
  Config.java              lê o .properties, com padrão embutido pra cada chave
  Assets.java              cache de imagens; devolve null se faltar o PNG
  Player.java              movimento, tiro, vidas, invulnerabilidade, XP
  Menu.java                tela inicial
  Hud.java                 painel lateral (pontos, vidas, bombas, nível)
  Background.java          fundo rolante da fase + efeitos
  Cutscene.java            cenas de diálogo estilo visual novel
  Musica.java              tocador de trilha (WAV, via javax.sound)
  GptExpansion.java        ataque especial (a "bomba")
  Point.java               item de XP dropado pelos inimigos
  Default.java             esqueleto pra classes novas
  bulletTypes/
    Bullet.java            base: posição, raio, dano, de quem é a bala
    IntegralBullet.java    movimento por integração (velocidade + aceleração)
  bulletTypes/
    Bullet3D.java          bala em espaço 3D, projetada em perspectiva
  enemyTypes/
    Enemy.java             base: HP, morte, drop, barra de vida, mira
    WaveEnemy.java         base dos inimigos de onda (tiro, sprite, config)
    PendulumEnemy.java     padrão pêndulo
    ArcEnemy.java          padrão arco
    HorizontalEnemy.java   padrão horizontal
    BossEnemy.java         base de chefe: spell cards, barra de HP, fases
    Adriana.java           primeira chefe (duas formas)
    spellCards/
      SpellCard.java       base abstrata de um ataque nomeado
      IntegralSpell.java   ∫  desenha a integral com balas
      SomatorioSpell.java  Σ  dois sigmas fechando o corredor
      AreaRiemannSpell.java  retângulos de Riemann
      EsferaSpell.java     ∮  a esfera 3D
  phases/
    phase1.java            "INDO PARA DCO"
```

### Ondas e padrões de movimento

Cada onda **sorteia um padrão** e lança todos os seus inimigos naquele
padrão, um a um, com atraso entre eles (a "fila"). Sortear por onda e não
por inimigo é de propósito: o jogador consegue ler a onda inteira e planejar
o desvio, em vez de encarar três padrões embaralhados ao mesmo tempo.

| Padrão | Chance | Movimento | Config |
|---|---|---|---|
| Pêndulo | 50% | desce pelo topo, freia no meio, segue e sai por baixo | `inimigo.pendulo.*` |
| Arco | 25% | entra pelo lado, curva 90° e sai por cima | `inimigo.arco.*` |
| Horizontal | 25% | atravessa de um lado ao outro | `inimigo.horizontal.*` |

As chances são pesos em `fase1.pesoPendulo` / `pesoArco` / `pesoHorizontal` —
não precisam somar 100.

**A desaceleração do pêndulo** é uma curva de sino sobre a distância até a
zona lenta, não um `if` por trecho:

```
fator = 1 - lentidaoMaxima * e^( -d² / (2 · largura²) )
```

Longe da zona o fator vale ~1 (velocidade cheia); no centro cai pra ~0.15.
Fica suave nas duas pontas de graça, e você regula tudo com dois números.

**O arco** gira o ângulo da velocidade em vez de mexer em `x`/`y` direto.
A config pede o **raio da curva** (que dá pra enxergar na tela) e a
velocidade angular sai de `velocidade / raio`.

> Lembre que na tela o Y cresce pra **baixo**: ângulo `0` = direita,
> `PI/2` = baixo, `-PI/2` = cima.

As ondas **crescem ao longo da fase** (`fase1.inimigosPorOndaInicio` →
`inimigosPorOndaFim`). Deixe os dois iguais pra desligar a rampa.

**Números medidos com os valores atuais:** o estágio 1 dura **~2min33**
(na faixa de um estágio de Touhou), com 30 ondas, 195 inimigos e pico de
~17 vivos ao mesmo tempo. A duração é basicamente
`totalDeOndas × intervaloEntreOndas ÷ 60` segundos.

Pra testar um ajuste sem a aleatoriedade atrapalhar, ponha um número em
`fase1.seed` — as ondas passam a sair sempre na mesma ordem.

### Progressão de nível

O nível define quantas balas saem no leque, então subir rápido demais
achata o jogo. O custo é exponencial e tem teto:

```
custo(N) = jogador.xpBase × jogador.fatorXpPorNivel ^ (N-1)
```

Com os valores atuais (12 e 2.4): **N2=12, N3=29, N4=69, N5=166, N6=398**
itens. Cada inimigo dropa 2 (`inimigo.itensAoMorrer`).

Medido: no fim do estágio 1 o jogador chega ao **nível 5** matando tudo, ou
**nível 4** matando 60% — o teto (`jogador.nivelMaximo=6`) fica reservado
pras fases dos chefes. Já no teto, cada item vira pontos em vez de XP.

Pra subir mais rápido, baixe `jogador.xpBase` ou `jogador.fatorXpPorNivel`.

### Como as balas funcionam

`IntegralBullet` guarda velocidade `(dx, dy)` e aceleração `(d2x, d2y)` e integra:

```
dx += d2x;   x += dx;
```

Quase todo padrão sai só escolhendo números, sem `if` especial:

| Quer | Use |
|---|---|
| linha reta | aceleração `0, 0` |
| bala que cai | `d2y` positivo pequeno |
| curva | aceleração apontando pro lado |

### Fundo da fase

A foto do DCO (`sprites/ambient/dco.png`) rola em loop, com cinco camadas
por cima — todas em `fundo.*` no `game.properties`:

1. foto rolando pra baixo → sensação de estar avançando
2. escurecimento chapado
3. tinta colorida pulsante
4. poeira luminosa subindo (parallax barato, sem segunda imagem)
5. vinheta nas bordas

As camadas 2, 3 e 5 **não são enfeite**: em bullet hell dá pra ter 100
projéteis na tela, e foto crua torna o jogo ilegível. Pra ver a foto
original, zere `fundo.escurecimento` e `fundo.tintAlpha`.

**A foto não emenda com ela mesma** — o asfalto do rodapé encostaria no céu
do topo e apareceria um corte a cada volta. `fecharOCiclo()` resolve
dissolvendo as últimas `fundo.faixaDeEmenda` linhas por cima do topo:

```
bloco[y] = mistura( foto[y + alturaBloco], foto[y], y / faixa )
```

Assim a primeira linha do bloco vale `foto[alturaBloco]` e a última vale
`foto[alturaBloco-1]` — ao dar a volta, uma cai exatamente na seguinte da
foto original. (Medido: descontinuidade cai de 84.1 pra 5.68, e duas linhas
vizinhas quaisquer já diferem 5.45.)

Pra trocar por outra foto: jogue o arquivo em `sprites/ambient/` e mude
`fundo.imagem`. O reescalonamento é automático; só ajuste a
`faixaDeEmenda` se a nova imagem tiver topo e rodapé muito diferentes.

### Retratos (menu e jogador)

`sprites/player/estudante.png` é uma foto processada em retrato circular
(recorte quadrado + máscara com borda suave) e usada em dois lugares:

- **Menu** (`Menu.desenharRetratoDoJogador`): moldura dourada no canto
  superior direito, tipo "carta de personagem".
- **Jogador em jogo** (`Player.renderRetrato`): centrado na hitbox, escalado
  por `jogador.escalaSprite` — mesma ideia do `Enemy.escalaSprite`: a
  **arte** pode ser grande sem que a **hitbox real** (`jogador.raioHitbox`)
  mude, senão o jogo fica injusto. O círculo verde da hitbox é sempre
  desenhado por cima do retrato, porque é o único que importa pra desviar.

Pra trocar a foto, basta substituir o arquivo (mantendo o nome ou ajustando
o caminho no código) — o processamento de recorte já foi feito uma vez e
salvo, não acontece em tempo de execução.

### GPT Expansion (ataque especial)

O ataque especial da proposta original ("ativar seu GPT interior e destruir
todos os trabalhos"). Usa `sprites/GFX/gpt_logo.png` — a logo original foi
recolorida pra dourado claro (a cor de nível máximo no HUD) pra ficar
legível contra o fundo escuro da fase.

**Como ativar:** tecla **V**, ou clique no ícone dourado no HUD (painel
lateral, seção "GPT Expansion"). Gasta uma carga (`jogador.bombas`,
começa com 3). O clique é tratado com um `MouseListener` no painel de
jogo — a única parte do jogo que usa mouse além do teclado.

**O efeito** (`GptExpansion.java`) nasce na posição do jogador e não o
segue depois. Três fases, todas em função do tempo (mesma filosofia de
`Enemy`/`phase1`, sem máquina de estado):

1. **Expansão** — raio cresce em `√progresso` (rápido no início, suave no
   fim — efeito de onda de choque)
2. **Sustentação** — segura no tamanho máximo
3. **Fade** — desaparece aos poucos

A colisão é verificada **todo tick contra o raio atual**, não só uma vez no
final — então o efeito vai matando conforme cresce. Mata todo inimigo que
tocar (`levarDano(getHp())`, dano igual ao HP atual — morre na hora mas
ainda solta pontos e itens normalmente) e apaga toda bala que tocar, sua ou
inimiga (`setAlive(false)`). Ajustes em `gptExpansao.*` no `.properties`.

> A logo usada é a marca registrada da OpenAI. Está aqui só pelo contexto
> do projeto (a proposta já brincava com "ativar seu GPT interior") e para
> fins de um trabalho acadêmico não-comercial — vale trocar por uma arte
> própria antes de publicar o repositório de forma mais ampla.

### Cutscenes

`Cutscene.java` é um motor genérico de diálogo estilo visual novel: uma
lista de `Fala` (título, narração ou fala de personagem) percorrida uma a
uma, com efeito de máquina de escrever. Z/ENTER completa a linha na hora
(primeiro toque) ou avança (segundo toque); ESC pula a cena inteira.

Há **dois formatos** de apresentação:

- **Narração sobre foto** — cenário de fundo (a portaria da UNESP) com a
  caixa de texto no rodapé. Usado na abertura.
- **Estilo Touhou** — retratos grandes dos personagens nos dois lados;
  quem fala aparece aceso e maior, quem está calado fica escurecido. É o
  que deixa claro de quem é a fala sem precisar ler o nome.

O **conteúdo** de cada cena mora em métodos fábrica no fim do arquivo, e
cada `Fala` tem um comentário com o número da linha do `Roteiro.txt` que
ela encena:

| Método | Roteiro | Quando |
|---|---|---|
| `criarIntro()` | linhas 1–8 | antes do estágio 1 |
| `criarEncontroAdriana()` | linhas 11–26 | antes do estágio 2 |
| `criarTransformacaoAdriana()` | linhas 27–32 | antes do estágio 3 |
| `criarDerrotaAdriana()` | linhas 34–40 | ao fim do estágio 3 |

Estado do jogo: `Menu` → **`Cutscene`** → `Game`. Chamar
`Main.mostrarCutscene(cena)` congela o jogo; quando a cena acaba o `tick()`
devolve pra `"Game"` sozinho, então a fase pede uma cutscene com uma linha
só. Pra pular tudo enquanto testa, ligue `debug.pularCutscenes=true`.

> Os retratos são recortes de rosto, não ilustrações de corpo inteiro como
> na série original. Por isso ficam em ~0.55 da altura da tela: acima disso
> saem cortados pelas bordas e parece defeito.

**Para criar a próxima cutscene** (o Clayton): escreva outro método
`criarX()` no mesmo formato e chame `Main.mostrarCutscene()` no estágio
certo de `phase1` — os estágios 2 e 3 servem de modelo pronto.

### Música

O `javax.sound.sampled` puro (sem bibliotecas externas) só lê **WAV**, não
MP3. Por isso a trilha fica em `audio/fase1.wav` — convertida uma vez com
`ffmpeg` a partir do arquivo original, mono/22050Hz pra não pesar o repo.

Toca em loop contínuo assim que a **fase** começa (não durante a
cutscene), pausa com ESC e retoma de onde parou. Ajustes em `musica.*`:
`ativada`, `arquivo`, `volume`. Se a máquina não tiver placa de som, o jogo
detecta e segue mudo — nunca trava por causa de áudio.

Pra trocar a música: converta o arquivo novo pra WAV
(`ffmpeg -i entrada.mp3 -ac 1 -ar 22050 audio/nome.wav`) e aponte
`musica.arquivo` pra ele.

### Chefes e spell cards

Um chefe **não é um inimigo com muito HP** — é uma *sequência de spell
cards*. Cada spell card tem HP e tempo próprios; zerou o HP (ou estourou o
tempo), o chefe limpa a tela, solta itens e passa pro próximo ataque.

`SpellCard` é o padrão **Strategy**: cada padrão de bala é uma classe
isolada em `spellCards/`. Pra criar um ataque novo você escreve uma classe
e não toca em mais nada — `BossEnemy` nem precisa saber que ela existe.

`BossEnemy` já dá de graça: barra de HP no topo, contador de ataques
restantes, anúncio do nome, invulnerabilidade na troca, deriva horizontal e
a morte com drop. A subclasse (`Adriana`) só escolhe **quais** spell cards
e **qual** sprite.

**Adriana** (Roteiro.txt linhas 19–40) tem duas formas, cada uma com dois
ataques, todos temáticos do cálculo que ela cobra em prova:

| Forma | Spell card | Como funciona |
|---|---|---|
| base | **∫ Integral Indefinida** | o símbolo ∫ é *um período completo de senoide na vertical*: `x=sin(2πs)·A`, `y=(s-0.5)·H`. Balas nascem formando o glifo e partem juntas |
| base | **Σ Somatório de Faltas** | polilinha de 4 segmentos formando o Σ; dois sigmas descem fechando o corredor central |
| maligna | **∑f(x)Δx Área Sob a Curva** | retângulos de Riemann: colunas com altura `f(x)`. Uma coluna a cada N fica vazia — é a saída |
| maligna | **∮ Sólido de Revolução** | a esfera **3D** (ver abaixo) |

Dois detalhes de implementação que valem saber:

- No Σ, as balas são distribuídas por **comprimento** ao longo da
  polilinha, não por segmento. Distribuir por segmento amontoaria a barra
  curta e deixaria buracos grandes demais na diagonal longa.
- Na integral, a **proporção** do glifo importa: com largura perto da
  altura vira uma onda genérica. `34×280` foi o que leu como "∫".

### O ataque 3D

`Bullet3D` vive num espaço tridimensional de verdade e é projetada na tela:

```
escala = D / (D + z)
xTela  = centroX + x3 · escala
yTela  = centroY + y3 · escala
```

Bala longe (z grande) fica pequena e perto do centro; bala perto fica
grande e afastada. O **raio de colisão acompanha a escala** — sem isso a
bala desenhada minúscula lá no fundo continuaria matando com o tamanho
cheio. Balas que passam por trás da câmera são descartadas, senão a divisão
inverteria o sinal e elas apareceriam espelhadas.

**Distribuição na esfera:** sortear latitude e longitude uniformemente
*amontoa os pontos nos polos*, porque faixas perto do polo têm área bem
menor que as do equador — daria dois tufos e um vazio no meio. A correção é
a **espiral de Fibonacci** (`EsferaSpell`): como o ângulo áureo é
irracional, nenhuma volta cai sobre a anterior e os pontos se espalham por
igual.

`adriana.esfera.distanciaCamera` é o parâmetro que controla a força da
perspectiva. Medido: com **240** a escala varia de 0.64 a 2.26 e o volume
lê bem; com 420 quase não se percebe profundidade; abaixo de 200 as balas
da frente ficam grandes demais.

### Como adicionar um inimigo novo

1. Crie a classe em `src/enemyTypes/` herdando de **`WaveEnemy`** (inimigo
   comum) ou de **`BossEnemy`** (chefe).
2. Sobrescreva `mover()` — use o `t` herdado pra montar a fórmula do caminho.
   `WaveEnemy` já dá o `atirar()` mirado de graça; só sobrescreva se quiser
   um padrão de bala diferente.
3. Leia os números do `Config`, não escreva literais.
4. Spawne no estágio certo de `phase1` (veja `nascerUm()`).

`HorizontalEnemy` é o modelo mais curto; `ArcEnemy` mostra movimento por
ângulo; `PendulumEnemy` mostra velocidade variável por fórmula.

**Para um chefe**, o caminho é outro: crie os spell cards em
`spellCards/` (herdando de `SpellCard`, sobrescrevendo `atacar()`) e uma
classe curta em `enemyTypes/` que só lista quais usar — `Adriana.java` tem
50 linhas e serve de molde direto pro Clayton.

---

## Estado atual

**Pronto:**

- game loop em Thread própria, ~60 ticks/s com recuperação de frames perdidos
- campo de jogo estilo Touhou + painel lateral de status
- jogador: movimento, modo foco, autofire, leque de tiro por nível, vidas,
  invulnerabilidade com pisca-pisca, XP e level up
- inimigos: hierarquia de 3 níveis, HP, barra de vida, drop de itens, tiro mirado
- três padrões de movimento (pêndulo, arco, horizontal) sorteados por peso
- colisão bala↔inimigo e bala↔jogador
- estágio 1 da fase 1 com ~2min33, 30 ondas em fila e rampa de dificuldade
- fundo rolante com a foto do DCO + efeitos (tinta, poeira, vinheta)
- música de fundo em loop durante a fase, com pause/retomada
- retrato do jogador no menu e como sprite em jogo
- GPT Expansion: ataque especial com raio de dano, tecla + clique de mouse
- **Adriana**: chefe completa com 2 formas e 4 spell cards
- 4 cutscenes cobrindo o roteiro até a derrota dela (linhas 1–40)
- ataque em 3D com projeção em perspectiva de verdade
- menu, pause, game over, tela de fase limpa
- carregamento de sprites com fallback pra formas geométricas

**A fazer (por ordem):**

1. **Clayton** — estágios 4 e 5 de `phase1`, com as falas do roteiro
   (linhas 42–56) e sprites `clayton-base.png` / `clayton-tabmaligno.png`.
   Os estágios 2 e 3 (Adriana) são o molde pronto: cutscene → spawn →
   esperar a lista esvaziar.
2. **Papa** — o roteiro para na linha 58 (`PAPA:`) sem as falas dele.
   Precisa escrever antes de implementar; sprites já existem.
3. **Efeitos sonoros** — a música da fase já toca; falta som de tiro, hit,
   level up e spell card. `src/LATEX.m4a` segue no repo, sem uso ainda.
4. **Placar em arquivo** — salvar e listar as melhores pontuações.
