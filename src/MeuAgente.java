import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;


public class MeuAgente extends Agente {
	
	Color color;
	
	double vel = 100;
	double  ang  = 0;
	
	double angVolante = 0;
	int volanteinfvalue = 0;
	int volanteRotationSpeed = 120;
	
	
	int estado = 0;
	
	double oldx = 0;
	double oldy = 0;
	
	int timeria = 0;
	
	boolean colidiu = false;
	
	boolean start = false;
	
	ArrayList<Rectangle> listaDeObstaculos = null;
	
	Polygon poly = new Polygon();
	Polygon poly2 = new Polygon();
	
	public MeuAgente(int x,int y, Color color,ArrayList<Rectangle> listaDeObstaculos) {
		// TODO Auto-generated constructor stub
		X = x;
		Y = y;
		
		this.color = color;
		this.listaDeObstaculos = listaDeObstaculos;
		
		poly.addPoint(-20, -10);
		poly.addPoint(25, -10);
		poly.addPoint(25, 10);
		poly.addPoint(-20, 10);
		
	}
	
	@Override
	public void SimulaSe(int DiffTime) {
		// TODO Auto-generated method stub
		timeria+=DiffTime;
		
		oldx = X;
		oldy = Y;
		
		angVolante += volanteinfvalue*volanteRotationSpeed*DiffTime/1000.0;;
		
		if(angVolante > 90){
			angVolante = 90;
		}
		
		if(angVolante < -90){
			angVolante = -90;
		}
		
		if(timeria>100){
			calculaIA(DiffTime);
			timeria = 0;
		}
		
		if(start){
			ang += vel/100.0*((angVolante*Math.PI/2)/90.0f)*DiffTime/1000.0;
			
			X+=Math.cos(ang)*vel*DiffTime/1000.0;
			Y+=Math.sin(ang)*vel*DiffTime/1000.0;
		}
		
		poly2 = new Polygon(poly.xpoints,poly.ypoints,poly.npoints);

		for(int i = 0; i < poly2.npoints;i++){
			double x = poly2.xpoints[i];
			double y = poly2.ypoints[i];
			
			double x2 = x*Math.cos(ang) - y*Math.sin(ang);
			double y2 = y*Math.cos(ang) + x*Math.sin(ang);
			
			poly2.xpoints[i] = (int)x2;
			poly2.ypoints[i] = (int)y2;
		}		
		
		poly2.translate((int)X, (int)Y);
		
		
		for(int i = 0; i < listaDeObstaculos.size();i++){
			if(poly2.intersects(listaDeObstaculos.get(i))){
				X = oldx;
				Y = oldy;
				break;
			}
		}

	}

	@Override
	public void DesenhaSe(Graphics2D dbg, int XMundo, int YMundo) {
		// TODO Auto-generated method stub
		dbg.setColor(Color.red);
		dbg.draw(poly2);
		
		dbg.setColor(color);
		
		AffineTransform trans = dbg.getTransform();
		
		dbg.translate(X, Y);
		dbg.rotate(ang);
		
		dbg.drawRect(-20, -10, 40, 20);
		
		dbg.drawRect(20, -5, 5,10);
		
		dbg.setTransform(trans);
	
	}
	
	
	public void rodaVolante(int v){
		if(v > 0){
			volanteinfvalue = 1;
		}else if(v < 0){
			volanteinfvalue = -1;
		}else{
			volanteinfvalue = 0;
		}
	}
	
	public void acelera(int v){
		if(v > 0){
			vel = 100;
		}else if(v < 0){
			vel = -100;
		}else{
			vel = 0;
		}
	}

	@Override
	public void calculaIA(int DiffTime) {
		// ========== 1. DADOS CRISP (Entradas Matemáticas) ==========
		// A vaga de estacionamento está aproximadamente nestas coordenadas
		double targetX = 750.0;
		double targetY = 60.0;

		// Calcular distância até a vaga
		double dx = targetX - X;
		double dy = targetY - Y;
		double distancia = Math.sqrt(dx * dx + dy * dy);

		// Normalizar ângulo atual do caminhão (ang) para a faixa 0 a 2PI
		double truckAng = ang % (2 * Math.PI);
		if (truckAng < 0) truckAng += 2 * Math.PI;

		// Calcular ângulo necessário para olhar para a vaga
		double angleToTarget = Math.atan2(dy, dx);
		if (angleToTarget < 0) angleToTarget += 2 * Math.PI;

		// Diferença entre o ângulo atual e o alvo (faixa de -PI a PI)
		double diffAng = angleToTarget - truckAng;
		if (diffAng > Math.PI) diffAng -= 2 * Math.PI;
		if (diffAng < -Math.PI) diffAng += 2 * Math.PI;

		// Converter para graus para facilitar a visualização dos conjuntos fuzzy
		double diffAngDeg = Math.toDegrees(diffAng);

		// ========== 2. INFERÊNCIA FUZZY SIMPLIFICADA (Agresividade) ==========
		// Vamos usar intervalos booleanos para um controle agresivo,
		// mas com regras fuzzy de comportamento.

		// --- Regras de Comportamento Fuzzy ---
		// REGRA 1: "Se o ângulo para a vaga está à esquerda, vire o volante para a esquerda."
		// REGRA 2: "Se o ângulo está no centro (reto), mantenha o volante reto."
		// REGRA 3: "Se a distância é longe, acelere agresivamente para a frente."
		// REGRA 4: "Se a distância é perto, pare e puxe o freio."

		// === Tomada de Decisão Fuzzy por Intervalos Rígidos ===

		// Controle de Direção (Volante)
		// Reduzimos drasticamente a zona "Centro" para garantir que o caminhão vire para
		// qualquer desvio maior que 2 graus.
		if (Math.abs(diffAngDeg) <= 2) {
			// Conjunto Fuzzy: "Direção é Centro" -> Peso Alto -> Ação: Reto
			rodaVolante(0);
		} else if (diffAngDeg < -2) {
			// Conjunto Fuzzy: "Direção é Esquerda" -> Peso Alto -> Ação: Virar Esquerda
			rodaVolante(-1);
		} else {
			// Conjunto Fuzzy: "Direção é Direita" -> Peso Alto -> Ação: Virar Direita
			rodaVolante(1);
		}

		// Controle de Aceleração
		// Definimos "Longe" como qualquer distância maior que 10 pixels para garantir aceleração constante.
		if (distancia <= 10) {
			// Conjunto Fuzzy: "Distância é Perto" -> Peso Alto -> Ação: Parar
			acelera(0);
			start = false; // Puxa o freio de mão (estacionou)
		} else {
			// Conjunto Fuzzy: "Distância é Longe" -> Peso Alto -> Ação: IrFrente (Vel=100)
			acelera(1);
		}
	}
}
