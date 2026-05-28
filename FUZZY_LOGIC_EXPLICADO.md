# 🧠 Documentação da Implementação Fuzzy do Caminhão

## 📊 Visão Geral da Arquitetura

O sistema de IA do caminhão utiliza **Lógica Fuzzy com Defuzzificação de Sugeno** em uma máquina de estados com 2 fases:

```
FASE 1: Alinhamento Estratégico
    └─→ Chegar perto do ponto (400, 220)
    
FASE 2: Descida para a Vaga
    └─→ Apontar para cima e descer até (400, 32)
```

---

## 1️⃣ VARIÁVEIS UTILIZADAS

### Variáveis de Estado
```java
int fase = 1;                    // Qual fase está (1 ou 2)
boolean emRecuperacao = false;   // Se está em marcha ré por colisão
int tempoRecuperacao = 0;        // Tempo restante da ré (ms)
```

### Variáveis de Movimento
```java
double vel = 0;                  // Velocidade linear (-100 a 100)
double ang = 0;                  // Ângulo do caminhão em radianos [-π, π]
double angVolante = 0;           // Ângulo do volante (-90 a 90 graus)
double volanteinfvalue = 0.0;    // Valor de entrada para girar o volante
```

### Variáveis de Posição
```java
double X, Y;                     // Coordenadas do caminhão na tela
double oldx, oldy;               // Posição anterior (para detecção de colisão)
```

### Variáveis Alvo
```java
double targetX = 400.0;          // Centro horizontal (objetivo em ambas as fases)
double targetY;                  // Varia: 220 (Fase 1) ou -π/2 (Fase 2)
double angleToTarget;            // Ângulo que deve seguir para chegar ao alvo
```

---

## 2️⃣ FUZZIFICAÇÃO

### O que é Fuzzificação?
Converter valores **crisp** (exatos) em **graus de pertinência** fuzzy (suavizados).

### Função de Pertinência Triangular
```java
private double pertinencia(double x, double a, double b, double c, double d) {
    // Cria um trapézio com 4 pontos:
    // a = início da rampa ascendente
    // b = início do platô (valor máximo = 1.0)
    // c = fim do platô
    // d = fim da rampa descendente
    
    if (x <= a || x >= d) return 0.0;           // Fora do intervalo
    if (x >= b && x <= c) return 1.0;           // No pico
    if (x > a && x < b) return (x - a) / (b - a);  // Rampa ascendente
    if (x > c && x < d) return (d - x) / (d - c);  // Rampa descendente
    return 0.0;
}
```

### Conjuntos Fuzzy de DIREÇÃO (baseados no erro angular)

```
Erro Angular (graus): -180° ... -3° ... -0.5° ... 0 ... 0.5° ... 3° ... 180°
                       |         |                  |                |        |
                    -180        -3               CENTER               3       180

ESQUERDA (vermelho):
    pertinencia(erroGraus, -180, -180, -3, -0.5)
    └─ Fortemente à esquerda: peso alto na ação "virar esquerda"

CENTRO (azul):
    pertinencia(erroGraus, -1.5, 0, 0, 1.5)
    └─ Bem alinhado: peso alto em "seguir reto"

DIREITA (verde):
    pertinencia(erroGraus, 0.5, 3, 180, 180)
    └─ Fortemente à direita: peso alto na ação "virar direita"
```

**Exemplo Prático:**
```
Se erroGraus = -15°:
    esquerda = pertinencia(-15, -180, -180, -3, -0.5) = 1.0   (muito à esquerda!)
    centro   = pertinencia(-15, -1.5, 0, 0, 1.5) = 0.0         (não está no centro)
    direita  = pertinencia(-15, 0.5, 3, 180, 180) = 0.0        (não está à direita)
    
→ Resultado: DEVE VIRAR ESQUERDA!
```

### Conjuntos Fuzzy de VELOCIDADE (baseados no erro angular)

```
Erro Absoluto (|graus|): 0° ... 4° ... 15° ... 180°
                          |         |         |      |

MUITO TORTO (vermelho):
    pertinencia(absErro, 10, 25, 180, 180)
    └─ Ângulo muito errado: usar velocidade baixa (0.30)

BEM ALINHADO (azul):
    pertinencia(absErro, 0, 0, 4, 15)
    └─ Ângulo correto: usar velocidade máxima (1.0)
```

**Exemplo Prático:**
```
Se absErro = 5°:
    muitoTorto  = pertinencia(5, 10, 25, 180, 180) = 0.0    (não está muito torto)
    bemAlinhado = pertinencia(5, 0, 0, 4, 15) = 0.83        (está bem alinhado!)
    
→ Resultado: USE VELOCIDADE ALTA!
```

---

## 3️⃣ REGRAS FUZZY

### Base de Regras Implementadas

#### **Regra 1: Controle de Direção**
```
SE (erro angular é ESQUERDA)
ENTÃO (saída de volante = -1.0)

SE (erro angular é CENTRO)
ENTÃO (saída de volante = 0.0)

SE (erro angular é DIREITA)
ENTÃO (saída de volante = 1.0)
```

#### **Regra 2: Controle de Velocidade**
```
SE (ângulo muito errado [muitoTorto])
ENTÃO (velocidade = 30% da máxima)

SE (ângulo bem alinhado [bemAlinhado])
ENTÃO (velocidade = 100% da máxima)
```

#### **Regra 3: Recuperação (Recovery Patch)**
```
SE (Y < 130 E (X < 378 OU X > 422) E não está em recuperação)
ENTÃO (emRecuperacao = true, tempoRecuperacao = 900ms)

SE (em recuperação)
ENTÃO (marcha ré com velocidade -65%, volante corrigindo para centro)
```

#### **Regra 4: Limite de Velocidade em Entrada da Vaga**
```
SE (Fase 2 E Y < 120)
ENTÃO (velocidade máxima = 50% da capacidade)
```

---

## 4️⃣ DEFUZZIFICAÇÃO (Sugeno)

### O que é Defuzzificação?
Converter os **graus de pertinência fuzzy** em um **valor crisp** (real) para controlar o caminhão.

### Método de Sugeno Utilizado

**Para o Volante:**
```java
double volante = (esquerda * -1.0 + centro * 0.0 + direita * 1.0) /
                 (esquerda + centro + direita + 0.0001);
                 
// Tradução:
// volante = (peso_esquerda × -1) + (peso_centro × 0) + (peso_direita × 1)
//           ─────────────────────────────────────────────────────────────
//           (peso_esquerda + peso_centro + peso_direita)
```

**Para a Velocidade:**
```java
double velocidadeFuzzy = (muitoTorto * 0.30 + bemAlinhado * 1.0) /
                         (muitoTorto + bemAlinhado + 0.0001);
                         
// Tradução:
// velocidade = (peso_torto × 0.30) + (peso_alinhado × 1.0)
//              ──────────────────────────────────────────
//              (peso_torto + peso_alinhado)
```

### Exemplo de Defuzzificação

**Cenário:** Erro angular = -10° (precisa virar esquerda)

```
Passo 1: Calcular pertinências
    esquerda = 0.85  (está bastante à esquerda)
    centro   = 0.10  (um pouco no intervalo de centro)
    direita  = 0.0   (não está à direita)

Passo 2: Calcular resultado (Sugeno)
    volante = (0.85 × -1.0) + (0.10 × 0.0) + (0.0 × 1.0)
              ───────────────────────────────────────────
              0.85 + 0.10 + 0.0 + 0.0001
    
    volante = -0.85 / 0.9501 = -0.894
    
    Interpretação: Virar volante para ESQUERDA com força 0.894
```

---

## 5️⃣ RESULTADO FINAL

### Transformação em Ações

```java
rodaVolanteAI(volante);     // Envia [-1.0, 1.0] para o motor do volante
aceleraAI(velocidadeFuzzy); // Envia [0, 1.0] para o motor de velocidade
```

**Dentro de `aceleraAI`:**
```java
public void aceleraAI(double v) {
    vel = Math.max(-100.0, Math.min(100.0, v * 100.0));
    // velocidadeFuzzy 1.0 → vel = 100 (máxima)
    // velocidadeFuzzy 0.5 → vel = 50  (média)
    // velocidadeFuzzy 0.0 → vel = 0   (parado)
}
```

---

## 6️⃣ FLUXO COMPLETO EM AÇÃO

### Ciclo de Execução

```
┌─────────────────────────────────────────────────────────────────┐
│ SimulaSe(DiffTime) é chamado pelo motor de jogo a cada frame   │
└─────────────────────────────────────────────────────────────────┘
                            ↓
        ┌───────────────────────────────────────┐
        │ Se start = true:                      │
        │   - Chama calculaIA(DiffTime)         │
        │   - Fuzzy logic executa               │
        └───────────────────────────────────────┘
                            ↓
        ┌───────────────────────────────────────┐
        │ Atualiza ângulo do volante:           │
        │   angVolante += volanteinfvalue × ... │
        └───────────────────────────────────────┘
                            ↓
        ┌───────────────────────────────────────┐
        │ Atualiza ângulo do caminhão:          │
        │   ang += vel/100 × (angVolante) × ... │
        └───────────────────────────────────────┘
                            ↓
        ┌───────────────────────────────────────┐
        │ Atualiza posição:                     │
        │   X += cos(ang) × vel × DiffTime      │
        │   Y += sin(ang) × vel × DiffTime      │
        └───────────────────────────────────────┘
                            ↓
        ┌───────────────────────────────────────┐
        │ Verifica colisão:                     │
        │   Se colidiu:                         │
        │     X = oldx, Y = oldy (volta)        │
        │     emRecuperacao = true              │
        └───────────────────────────────────────┘
```

---

## 7️⃣ MÁQUINA DE ESTADOS (Importante!)

```
                          ┌──────────────┐
                          │   FASE 1     │
                          │ Alinhamento  │
                          │  targetY=220 │
                          └──────┬───────┘
                                 │
                    Condições: |X-400| < 20 AND Y ≤ 230
                                 │
                                 ↓
                          ┌──────────────┐
                          │   FASE 2     │
                          │  Descida     │
                          │ angleUp=-π/2 │
                          └──────┬───────┘
                                 │
                    Condições: Y ≤ 32
                                 │
                                 ↓
                          ┌──────────────┐
                          │  PARADO      │
                          │  start=false │
                          └──────────────┘
```

---

## ⚠️ PROBLEMA IDENTIFICADO: Loop de Recuperação

### Cenário do Bug:
```
1. Caminhão chega perto da parede (Y < 130, X descentrado)
2. Entra em recuperação (marcha ré por 900ms)
3. Durante ré, volante vira todo para um lado
4. Após 900ms, sai de recuperação
5. IMEDIATAMENTE colide de novo (ainda próximo à parede)
6. Entra em recuperação NOVAMENTE
7. → Loop infinito de ré + volante todo para o lado
```

### Root Cause:
- **Sem cooldown**: Pode entrar em recuperação imediatamente após sair
- **Tempo insuficiente**: 900ms pode não ser o bastante para se afastar
- **Lógica de saída fraca**: `!emRecuperacao` é apenas um flag booleano
- **Sem limite de tentativas**: Pode ficar fazendo isso infinitamente

---

## 🔧 SOLUÇÕES RECOMENDADAS

1. **Aumentar tempo de recuperação**: 900ms → 1500ms
2. **Adicionar cooldown**: Não poder entrar em recuperação por 2 segundos após sair
3. **Melhorar lógica de saída**: Verificar se realmente se afastou antes de permitir nova recuperação
4. **Adicionar contador**: Limitar a quantas vezes pode entrar em recuperação
5. **Fuzzy logic na recuperação**: Em vez de volante todo para um lado, usar lógica fuzzy suave

---

## ✅ Correções aplicadas no código (implementadas)

As seguintes mudanças foram aplicadas diretamente em `MeuAgente.java` para mitigar o loop de recuperação e o volante extremado:

- `RECOVERY_TIME_MS` aumentado para 1200 ms (tempo de ré configurável)
- `recoveryCooldown` (2000 ms) adicionado: impede reentrada imediata em recovery
- `postRecoveryHold` (500 ms) adicionado: suaviza a ação do volante imediatamente após recovery
- Ao terminar recovery o código agora chama `rodaVolanteAI(0)` e ativa `recoveryCooldown` e `postRecoveryHold`
- Durante `postRecoveryHold` a saída do volante (defuzz) é reduzida (multiplicador 0.35)
- A detecção de colisão só inicia recovery se `recoveryCooldown == 0`

Essas correções reduzem fortemente o risco de entrar em um ciclo de ré contínuo e evitam que o volante fique imediatamente saturado para um lado após a manobra.

---

Se quiser, aplico uma melhoria adicional: usar um contador de tentativas de recovery e, após N tentativas seguidas, tentar uma estratégia alternativa (girar 180°, ou mover lateralmente mais tempo). Quer que eu implemente isso também?

