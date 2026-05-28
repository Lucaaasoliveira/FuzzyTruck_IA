# Lógica Fuzzy - Detalhamento Técnico

## 1. Conjuntos Fuzzy Definidos

### Entrada: Erro Lateral (X - SLOT_X) em pixels

```
-50      -20      -5        0        5       20       50
 |--------|--------|--------|--------|--------|--------|
 
 ESQUERDA   LEVE_ESQ  CENTRO  LEVE_DIR   DIREITA
 (muita)    (pouca)   (OK)    (pouca)   (muita)
```

**Regra de Fuzzificação:**
```
se |lateralError| < 5 px:
    membership(CENTRO) = 1.0 → rodaVolante(0)

se -20 < lateralError < -5 px:
    membership(LEVE_ESQ) = alto → rodaVolante(-0.5)

se lateralError < -20 px:
    membership(ESQUERDA) = alto → rodaVolante(-1)
    
[espelho para direita]
```

---

### Entrada: Erro de Heading (ang - angleUp) em radianos

```
-π/2       -π/6      0        π/6      π/2
 |--------|--------|--------|--------|
 
 VIRADO_ESQ LEVE_ESQ  OK     LEVE_DIR VIRADO_DIR
 (180°-120°) (60°-30°) (±15°) (30°-60°) (120°-180°)
```

**Conversão em Graus:**
```
-180°      -120°     -30°     0°      30°      120°     180°
  |----------|----------|----------|----------|----------|
  
  MUITO_ESQ  LEVE_ESQ    OK      LEVE_DIR  MUITO_DIR
```

---

### Entrada: Distância até Linha de Preparação (ALIGN_Y - Y) em pixels

```
0         2        5       10        20        50       100+
|---------|---------|---------|---------|---------|---------|

CHEGOU   MUITO_PERTO  PERTO   MODERADO  MÉDIO    LONGE   MUITO_LONGE
(stop)   (10 px/s)  (20 px/s) (40 px/s) (70 px/s) (100 px/s) (100 px/s)
```

---

### Saída: Comando de Volante

```
-1         -0.5       0        0.5        1
|----------|----------|----------|----------|
VIRAR_ESQ  LEVE_ESQ   RETO   LEVE_DIR  VIRAR_DIR
```

**Tradução para Motor:**
```
volanteinfvalue = -1 → volante gira esquerda (90°)
volanteinfvalue =  0 → volante reto (0°)
volanteinfvalue = +1 → volante gira direita (90°)
```

---

## 2. Base de Regras Fuzzy

### FASE 1: PREPARACAO

**Regra 1 - Corrente Lateral**
```
IF (error_lateral = ESQUERDA OR LEVE_ESQ)
THEN comando_volante = VIRAR_DIREITA
```

**Regra 2 - Corrente de Heading**
```
IF (error_heading = VIRADO_ESQ OR LEVE_ESQ)
THEN comando_volante = VIRAR_DIREITA
```

**Regra 3 - Manutenção em Centro**
```
IF (error_lateral = CENTRO AND error_heading = OK)
THEN comando_volante = RETO
```

**Regra 4 - Desaceleração por Proximidade**
```
IF distancia_linha = PERTO
THEN velocidade = LENTA
```

**Regra 5 - Aceleração em Distância**
```
IF distancia_linha = LONGE
THEN velocidade = RÁPIDA
```

---

### FASE 2: ESTACIONANDO

**Regra 1 - Steering Mínimo**
```
IF |error_lateral| < 8 px
THEN comando_volante = RETO
```

**Regra 2 - Correção Suave Lateral**
```
IF 8 < |error_lateral| < 30 px
THEN comando_volante = LEVE (±0.5)
```

**Regra 3 - Velocidade Proporcional**
```
IF distancia_slot = LONGE (>100 px)
THEN velocidade = 60 px/s

IF distancia_slot = PERTO (20-50 px)
THEN velocidade = 25 px/s

IF distancia_slot = MUITO_PERTO (<10 px)
THEN velocidade = 5 px/s
```

---

## 3. Processo de Inferência Fuzzy

### Exemplo Prático: Caminhão em PREPARACAO

**Estado Atual:**
- X = 380, Y = 180
- ang = -1.2 rad (≈ -69°)
- Objetivo: (400, 165, -1.57 rad)

**Cálculo de Entradas:**
```
lateralError = 380 - 400 = -20 px  → MEMBERSHIP(LEVE_ESQ) = 0.8
headingError = -1.2 - (-1.57) = 0.37 rad ≈ 21° → MEMBERSHIP(LEVE_DIR) = 0.6
distToAlign = 165 - 180 = -15 px (ultrapassou!) → Ativar RECOVERY_PATCH

→ RESULTADO: ATIVAR PATCH 3 (reverter + re-centralizar)
```

**Fuzzificação:**
- Erro lateral dominante (|-20| > |21°|)
- Aplicar `fuzzySteeringLateral(-20)`
  - -20 < -5, then check: lateralError < -20? NO
  - -20 < lateralError < -5? YES → return -0.5
- Comando: `rodaVolante(-0.5)` → volante para esquerda

**Defuzzificação:**
```
rodaVolante(-0.5) → converter para volanteinfvalue:
  -0.5 → (int)sign(-0.5) = -1
  → volanteinfvalue = -1
  → volante gira 90° para esquerda
```

---

## 4. Recovery Patches (Correções Automáticas)

### Patch 1: Top Wall Outside Slot
```
Condição: Y < 95 AND (X < 378 OR X > 422)

Situação: Caminhão muito alto e fora do slot
Ação: Reverter para baixo + virar para centro

Código:
if (Y < 95 && (X < (SLOT_X - SLOT_WIDTH) || X > (SLOT_X + SLOT_WIDTH))) {
    acelera(-1);  // Vel = -100 (reversa)
    rodaVolante(lateralError > 0 ? -1 : 1);  // Virar para centro
    return;
}
```

### Patch 2: Side Stuck
```
Condição: Y < 160 AND |X - 400| > 55

Situação: Preso ao lado da abertura da vaga
Ação: Reverter para sair lateralmente

Código:
if (Y < ALIGN_Y - 5 && Math.abs(lateralError) > SLOT_WIDTH + 10) {
    acelera(-1);
    rodaVolante(lateralError > 0 ? -1 : 1);
    return;
}
```

### Patch 3: Near Line Misaligned
```
Condição: |Y - 165| < 20 AND |X - 400| > 30

Situação: Próximo da linha de preparação mas muito descentrado
Ação: Reverter + re-centralizar

Código:
if (Math.abs(distToAlign) < 20 && Math.abs(lateralError) > 30) {
    acelera(-1);
    rodaVolante(lateralError > 0 ? -1 : 1);
    return;
}
```

### Patch 4: Heading Issue (Implícito)
```
Condição: |Y - 165| < 20 AND |heading_error| > 30°

Situação: Perto da linha mas ainda muito virado
Ação: Pequeno avanço + correção de steering

Código: Handled in normal fuzzy logic
(quando fuzzySteeringHeading() retorna alto valor)
```

---

## 5. Transição de Fases

```
PREPARACAO (estado = 0)
    ↓
    Monitora: laneReadyTime += DiffTime
    Condições de Sucesso:
    1. nearAlign = |Y - 165| < 10 ✓
    2. centered = |X - 400| < 15 ✓
    3. headingOK = |ang - (-π/2)| < 15° ✓
    ↓
    laneReadyTime > 100 ms? YES
    ↓
ESTACIONANDO (estado = 1)
    ↓
    Monitora: Y (distância até slot)
    ↓
    Y < 5 AND |X - 400| < 10? YES
    ↓
PARADO (final)
```

---

## 6. Normalização de Ângulo

Para manter `ang` na faixa [-π, π]:

```java
private double normalizeAngle(double angle) {
    while (angle > Math.PI) angle -= 2 * Math.PI;  // Reduz rotações CCW
    while (angle < -Math.PI) angle += 2 * Math.PI; // Reduz rotações CW
    return angle;
}
```

**Por que isso importa:**
- Sem normalização: após múltiplas rotações, `ang` fica 3π, 5π, etc
- `Math.atan2()` retorna valores em [-π, π], causando descontinuidades
- `normalizeAngle()` garante cálculo correto do erro de heading

---

## 7. Velocidade Suavizada

```java
double smoothVelocity = 0;  // Acumulador

// Em fuzzyApproachSpeed():
smoothVelocity = fuzzyApproachSpeed(distToAlign);  // Calcula nova
vel = (int)smoothVelocity;                          // Aplica

// Benefícios:
// - Evita "bang-bang" (0 → 100 → 0)
// - Suaviza aceleração/frenagem
// - Mantém compatibilidade com acelera() manual
```

---

## 8. Pontos Críticos de Debug

Se o caminhão não se comporta como esperado:

```java
// Adicione estas linhas em calculaIA():
System.out.println("=== DEBUG IA ===");
System.out.println("Estado: " + (estado == 0 ? "PREPARACAO" : "ESTACIONANDO"));
System.out.println("Posição: (" + (int)X + ", " + (int)Y + ")");
System.out.println("Ângulo: " + Math.toDegrees(ang) + "°");
System.out.println("Lateral Error: " + (int)lateralError + " px");
System.out.println("Heading Error: " + Math.toDegrees(headingError) + "°");
System.out.println("Vel: " + vel + ", VolanteCmd: " + volanteinfvalue);
System.out.println("==================\n");
```

