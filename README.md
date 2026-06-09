# Touhou-expancionista
Trabalho do Paiola

O arquivo "Default.java" vai servir como base para codar suas partes.
O programa vai ser estruturado em dois principais métodos de cada classe: tick e render.
No tick, ocorrerá toda a lógica, como detecção de colisão e atualização de posição.
O render terá apenas a parte gráfica, como desenhar o objeto em si.
Os atributos de cada classe deve ser privado, sendo geridos por getters e setters por outras classes.
Toda parte do código deve ter tratamento de excessões.

As principais telas do jogo serão definidas por uma String stática da main chamada "gameState", que poderá ser definida como "Menu", "Game", "Statistics" e demais, em que cada um (definido por if/elifs do tick e da render) definirá uma nova lógica de chamada de ticks/render.

No momento, o jogo começa no estado "Menu", em que seu tick espera 3 segundos antes de definir o estado "Game", que por sua vez spawnna um círculo simples q simula uma hurtbox para o Player.

Uma ideia que tenho para as fases do jogo é criar nelas alguns estágios, que serão como mini fases antes de um boss. Cada estágio vai iniciar um novo cronômetro chamado 't'. Para cada inimigo ou projétil, suas posições e ações podem ser definidas pelo tempo t, incluindo valores de x e y como funções polinomiais no t da fase ou um cronômetro a partir do seu próprio spawn, definido tudo no seu construtor.