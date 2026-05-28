import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;

public class MeuAgente extends Agente {

	Color color;

	double vel = 0;
	double ang = 0;

	double angVolante = 0;
	double volanteinfvalue = 0.0;
	int volanteRotationSpeed = 120;

	double oldx = 0;
	double oldy = 0;

	boolean start = false;

	// Fases de aproximação
	int fase = 1;

	// Variáveis para o Mecanismo de Recuperação (Histerese)
	boolean emRecuperacao = false;
	int tempoRecuperacao = 0;

	ArrayList<Rectangle> listaDeObstaculos = null;
	Polygon poly = new Polygon();
	Polygon poly2 = new Polygon();

	public MeuAgente(int x, int y, Color color, ArrayList<Rectangle> listaDeObstaculos) {
		X = x;
		Y = y;
		this.color = color;
		this.listaDeObstaculos = listaDeObstaculos;

		poly.addPoint(-20, -10);
		poly.addPoint(25, -10);
		poly.addPoint(25, 10);
		poly.addPoint(-20, 10);
	}

	private double normalizeAngle(double a) {
		while (a > Math.PI) a -= 2 * Math.PI;
		while (a <= -Math.PI) a += 2 * Math.PI;
		return a;
	}

	@Override
	public void SimulaSe(int DiffTime) {
		oldx = X;
		oldy = Y;

		if (start) {
			calculaIA(DiffTime);
		}

		angVolante += volanteinfvalue * volanteRotationSpeed * DiffTime / 1000.0;

		if(angVolante > 90) angVolante = 90;
		if(angVolante < -90) angVolante = -90;

		if(start || vel != 0){
			ang += vel/100.0 * ((angVolante * Math.PI / 2) / 90.0f) * DiffTime / 1000.0;
			ang = normalizeAngle(ang);

			X += Math.cos(ang) * vel * DiffTime / 1000.0;
			Y += Math.sin(ang) * vel * DiffTime / 1000.0;
		}

		poly2 = new Polygon(poly.xpoints, poly.ypoints, poly.npoints);

		for(int i = 0; i < poly2.npoints; i++){
			double px = poly2.xpoints[i];
			double py = poly2.ypoints[i];

			double x2 = px * Math.cos(ang) - py * Math.sin(ang);
			double y2 = py * Math.cos(ang) + px * Math.sin(ang);

			poly2.xpoints[i] = (int)x2;
			poly2.ypoints[i] = (int)y2;
		}

		poly2.translate((int)X, (int)Y);

		for(int i = 0; i < listaDeObstaculos.size(); i++){
			if(poly2.intersects(listaDeObstaculos.get(i))){
				X = oldx;
				Y = oldy;

				if (start && vel > 0 && !emRecuperacao) {
					emRecuperacao = true;
					tempoRecuperacao = 700; // Tempo de ré rápido para descolar
				}
				break;
			}
		}
	}

	@Override
	public void DesenhaSe(Graphics2D dbg, int XMundo, int YMundo) {
		dbg.setColor(Color.red);
		dbg.draw(poly2);
		dbg.setColor(color);

		AffineTransform trans = dbg.getTransform();
		dbg.translate(X, Y);
		dbg.rotate(ang);
		dbg.drawRect(-20, -10, 40, 20);
		dbg.drawRect(20, -5, 5, 10);
		dbg.setTransform(trans);
	}

	public void rodaVolanteAI(double v){
		volanteinfvalue = Math.max(-1.0, Math.min(1.0, v));
	}

	public void aceleraAI(double v){
		vel = Math.max(-100.0, Math.min(100.0, v * 100.0));
	}

	public void rodaVolante(int v) {
		if (!start) {
			if(v > 0) volanteinfvalue = 1;
			else if(v < 0) volanteinfvalue = -1;
			else volanteinfvalue = 0;
		}
	}

	public void acelera(int v) {
		if (!start) {
			if(v > 0) vel = 100;
			else if(v < 0) vel = -100;
			else vel = 0;
		}
	}

	private double pertinencia(double x, double a, double b, double c, double d) {
		if (x <= a || x >= d) return 0.0;
		if (x >= b && x <= c) return 1.0;
		if (x > a && x < b) return (x - a) / (b - a);
		if (x > c && x < d) return (d - x) / (d - c);
		return 0.0;
	}

	public void calculaIA(int DiffTime) {
		double targetX = 400.0;
		double targetY;
		double angleToTarget;

		// Mecanismo de segurança para quinas superiores fora da vaga
		if (Y < 130 && (X < 378 || X > 422) && !emRecuperacao) {
			emRecuperacao = true;
			tempoRecuperacao = 900;
		}

		// Rotina de Marcha Ré (Recovery Patch)
		if (emRecuperacao) {
			tempoRecuperacao -= DiffTime;
			fase = 1;
			aceleraAI(-0.65);

			// Retorna para o centro de forma mais suave
			if (X < 400) {
				rodaVolanteAI(0.7);
			} else {
				rodaVolanteAI(-0.7);
			}

			if (tempoRecuperacao <= 0) {
				emRecuperacao = false;
			}
			return;
		}

		double truckAng = normalizeAngle(ang);

		// ========================================================
		// MÁQUINA DE ESTADOS ESTABILIZADA (Sem cancelamento bang-bang)
		// ========================================================
		if (fase == 1) {
			// LOOP 1: Ponto de alinhamento estratégico inferior
			targetY = 220.0; // Puxado mais para baixo para dar pista de corrida reta
			angleToTarget = Math.atan2(targetY - Y, targetX - X);

			// Condição de transição robusta: Passou da linha Y=240, está no corredor central?
			// Entra na Fase 2 e NÃO volta atrás. A Fase 2 agora gerencia pequenos desvios.
			if (Math.abs(X - targetX) < 20 && Y <= 230) {
				fase = 2;
			}
		} else {
			// LOOP 2: Trava a rota olhando rigorosamente para cima
			angleToTarget = -Math.PI / 2;

			// Nova regra de correção: Se na Fase 2 ele deslocar lateralmente devido à curva anterior,
			// aplicamos uma pequena compensação fuzzy baseada no erro de X, sem abortar a fase!
			double erroX = targetX - X;
			if (Math.abs(erroX) > 5) {
				// Inclina sutilmente o vetor alvo para compensar o desvio de pista
				angleToTarget += (erroX / 100.0);
			}

			// Chegou no fundo da vaga
			if (Y <= 32) {
				aceleraAI(0);
				rodaVolanteAI(0);
				start = false; // Puxa o freio automaticamente
				fase = 1;
				return;
			}
		}

		// ========================================================
		// LÓGICA FUZZY
		// ========================================================
		double erroAng = normalizeAngle(angleToTarget - truckAng);
		double erroGraus = Math.toDegrees(erroAng);

		// Conjuntos Fuzzy de Direção (Zonas de atuação mais suaves para evitar o vai e vem do volante)
		double esquerda = pertinencia(erroGraus, -180, -180, -3, -0.5);
		double centro   = pertinencia(erroGraus, -1.5, 0, 0, 1.5);
		double direita  = pertinencia(erroGraus, 0.5, 3, 180, 180);

		// Conjuntos Fuzzy de Velocidade baseados no erro de orientação
		double absErro = Math.abs(erroGraus);
		double muitoTorto  = pertinencia(absErro, 10, 25, 180, 180);
		double bemAlinhado = pertinencia(absErro, 0, 0, 4, 15);

		// Defuzzificação de Sugeno
		double volante = (esquerda * -1.0 + centro * 0.0 + direita * 1.0) /
				(esquerda + centro + direita + 0.0001);

		double velocidadeFuzzy = (muitoTorto * 0.30 + bemAlinhado * 1.0) /
				(muitoTorto + bemAlinhado + 0.0001);

		// Se estiver na Fase 2 entrando na vaga de forma apertada, limita a velocidade máxima para evitar batidas
		if (fase == 2 && Y < 120) {
			velocidadeFuzzy = Math.min(velocidadeFuzzy, 0.5);
		}

		rodaVolanteAI(volante);
		aceleraAI(velocidadeFuzzy);
	}
}