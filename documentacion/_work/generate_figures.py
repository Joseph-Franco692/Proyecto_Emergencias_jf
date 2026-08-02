from PIL import Image, ImageDraw, ImageFont
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "documentacion" / "figuras"
OUT.mkdir(parents=True, exist_ok=True)

NAVY = "#102A43"
BLUE = "#2F6BFF"
CYAN = "#16B8D4"
RED = "#E64545"
GOLD = "#F0A11A"
GREEN = "#1FA971"
BG = "#F4F7FA"
CARD = "#FFFFFF"
MUTED = "#52677D"
LINE = "#B8C7D6"

def font(size, bold=False):
    candidates = [
        Path("C:/Windows/Fonts/arialbd.ttf" if bold else "C:/Windows/Fonts/arial.ttf"),
        Path("C:/Windows/Fonts/calibrib.ttf" if bold else "C:/Windows/Fonts/calibri.ttf"),
    ]
    for p in candidates:
        if p.exists():
            return ImageFont.truetype(str(p), size)
    return ImageFont.load_default()

def text_center(d, xy, text, f, fill=NAVY):
    box = d.textbbox((0, 0), text, font=f)
    d.text((xy[0] - (box[2]-box[0])/2, xy[1] - (box[3]-box[1])/2), text, font=f, fill=fill)

def rounded(d, box, fill=CARD, outline=LINE, radius=22, width=2):
    d.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=width)

def arrow(d, a, b, color=BLUE, width=5, label=None, offset=(0,0)):
    d.line([a, b], fill=color, width=width)
    import math
    ang = math.atan2(b[1]-a[1], b[0]-a[0])
    size = 13
    p1 = (b[0]-size*math.cos(ang-0.55), b[1]-size*math.sin(ang-0.55))
    p2 = (b[0]-size*math.cos(ang+0.55), b[1]-size*math.sin(ang+0.55))
    d.polygon([b, p1, p2], fill=color)
    if label:
        mx = (a[0]+b[0])/2 + offset[0]
        my = (a[1]+b[1])/2 + offset[1]
        bb = d.textbbox((0,0), label, font=font(18, True))
        pad = 8
        d.rounded_rectangle((mx-(bb[2]-bb[0])/2-pad, my-(bb[3]-bb[1])/2-pad,
                             mx+(bb[2]-bb[0])/2+pad, my+(bb[3]-bb[1])/2+pad),
                            radius=8, fill=BG)
        text_center(d, (mx,my), label, font(18, True), color)

def node(d, box, title, lines, accent=BLUE):
    rounded(d, box)
    d.rounded_rectangle((box[0], box[1], box[0]+12, box[3]), radius=6, fill=accent)
    d.text((box[0]+28, box[1]+18), title, font=font(24, True), fill=NAVY)
    y = box[1]+58
    for line in lines:
        d.text((box[0]+28, y), line, font=font(18), fill=MUTED)
        y += 25

def architecture():
    im = Image.new("RGB", (1800, 1100), BG)
    d = ImageDraw.Draw(im)
    text_center(d, (900, 55), "Arquitectura distribuida final — Gestión Bomberil", font(34, True))
    text_center(d, (900, 95), "Canales síncronos, tiempo real, servicios externos y persistencia híbrida", font(20), MUTED)

    node(d, (50, 170, 330, 350), "Clientes", ["Ciudadano", "Administrador", "Operador"], CYAN)
    node(d, (435, 145, 755, 375), "Docker Swarm Ingress", ["Nginx / routing mesh", "Frontend Angular × 2", "Puerto público 8080"], BLUE)
    node(d, (860, 145, 1210, 375), "Backend Spring Boot × 2", ["API REST + JWT", "STOMP/WebSocket", "Reglas de negocio"], RED)

    node(d, (1320, 135, 1740, 270), "PostgreSQL × 1", ["Datos transaccionales", "Bloqueos y estados ACID"], NAVY)
    node(d, (1320, 305, 1740, 440), "PocketBase × 1", ["Archivos multimedia privados", "Metadatos + SHA-256"], GREEN)
    node(d, (1320, 475, 1740, 610), "Ollama × 1", ["Modelo local llama3.2", "Copiloto operativo acotado"], CYAN)

    node(d, (860, 530, 1210, 735), "Ejecución asíncrona", ["ThreadPoolTaskExecutor", "4–8 hilos", "Cola en memoria: 500"], GOLD)
    node(d, (430, 530, 755, 735), "Servicios externos", ["PayPal Sandbox", "SMTP / Gmail", "Google OAuth 2.0"], GREEN)
    node(d, (50, 530, 330, 735), "Nodo IoT ESP32", ["Sensor MQ-2", "LCD, RGB y buzzer", "HTTP/HTTPS vía ngrok"], RED)

    arrow(d, (330,260), (435,260), BLUE, label="HTTPS")
    arrow(d, (755,260), (860,260), BLUE, label="REST / STOMP")
    arrow(d, (1210,210), (1320,205), NAVY, label="JPA")
    arrow(d, (1210,285), (1320,365), GREEN, label="HTTP privado", offset=(0,-18))
    arrow(d, (1210,340), (1320,535), CYAN, label="API local", offset=(0,18))
    arrow(d, (1035,375), (1035,530), GOLD, label="@Async")
    arrow(d, (860,630), (755,630), GREEN, label="HTTPS")
    # Telemetry reaches the backend through the public ngrok HTTPS tunnel.
    d.line([(330,630),(800,630),(800,330),(860,330)], fill=RED, width=5)
    d.polygon([(860,330),(845,322),(845,338)], fill=RED)
    d.rounded_rectangle((545,604,710,642), radius=8, fill=BG)
    text_center(d, (628,623), "ngrok HTTPS", font(18, True), RED)

    rounded(d, (50, 820, 1740, 1030), fill="#EAF1F7", outline=LINE)
    d.text((80, 845), "Lectura arquitectónica", font=font(24, True), fill=NAVY)
    notes = [
        "• Swarm mantiene dos réplicas de Angular y dos de Spring Boot; el routing mesh distribuye solicitudes.",
        "• PostgreSQL conserva el estado consistente; PocketBase separa el binario y PostgreSQL conserva su referencia e integridad.",
        "• REST cubre las operaciones síncronas; STOMP/WebSocket difunde incidentes y cambios operativos en tiempo real.",
        "• El procesamiento asíncrono actual usa una cola en memoria. No equivale a un broker durable externo.",
        "• ESP32 publica telemetría; Spring Boot valida la credencial técnica y vincula las lecturas al reporte del operador.",
    ]
    y=890
    for n in notes:
        d.text((90,y), n, font=font(19), fill=NAVY); y+=29
    im.save(OUT/"arquitectura-final.png", quality=95)

def flow():
    im = Image.new("RGB", (1800, 950), BG); d=ImageDraw.Draw(im)
    text_center(d,(900,50),"Flujo operativo completo del incidente",font(34,True))
    items = [
        ("1. Reporte ciudadano", ["Tipo, descripción, GPS", "y evidencia multimedia"], CYAN),
        ("2. Persistencia", ["PostgreSQL + PocketBase", "hash SHA-256"], NAVY),
        ("3. Alerta en vivo", ["STOMP/WebSocket", "dashboard administrador"], RED),
        ("4. Despacho", ["Bloqueo pesimista", "unidad y operador"], GOLD),
        ("5. Atención", ["Ruta, llegada y estados", "EN_RUTA / EN_SITIO"], BLUE),
        ("6. IoT post-incendio", ["Telemetría MQ-2", "habitabilidad"], GREEN),
        ("7. Cierre", ["Bitácora y liberación", "reporte ATENDIDO"], RED),
    ]
    boxes=[]
    x=35
    for i,(t,ls,c) in enumerate(items):
        w=225
        box=(x,160,x+w,340); boxes.append(box); node(d,box,t,ls,c); x+=255
    for i in range(len(boxes)-1):
        arrow(d,(boxes[i][2],250),(boxes[i+1][0],250),BLUE,width=4)
    rounded(d,(80,455,1720,850),fill=CARD,outline=LINE)
    d.text((120,485),"Reglas de consistencia y tiempo real",font=font(26,True),fill=NAVY)
    rows=[
        ("Validación", "Campos ecuatorianos, tamaño y tipo de archivo, coordenadas y estados permitidos."),
        ("Concurrencia", "El despacho bloquea la unidad y evita asignaciones simultáneas o duplicadas."),
        ("Notificación", "Cada transición publica eventos; el cliente también aplica reconciliación periódica entre réplicas."),
        ("Trazabilidad", "Los cierres preservan operador, unidad, novedades, primera/última lectura IoT y resultado."),
        ("Disponibilidad", "Al finalizar, la unidad vuelve a DISPONIBLE y el incidente queda ATENDIDO, sin nuevo despacho."),
    ]
    y=545
    for k,v in rows:
        d.rounded_rectangle((120,y,360,y+48),radius=10,fill="#E8F0FF")
        text_center(d,(240,y+24),k,font(20,True),BLUE)
        d.text((390,y+11),v,font=font(19),fill=NAVY)
        y+=60
    im.save(OUT/"flujo-operativo.png",quality=95)

def payment():
    im=Image.new("RGB",(1600,780),BG); d=ImageDraw.Draw(im)
    text_center(d,(800,50),"Flujo del pago Premium en PayPal Sandbox",font(34,True))
    entries=[
        ("Formulario",["Datos del lugar","$49,99 USD"],CYAN),
        ("Orden interna",["Estado PENDIENTE","ID de seguimiento"],NAVY),
        ("PayPal Sandbox",["Create Order","Aprobación del pagador"],BLUE),
        ("Captura",["Validación monto/moneda","Idempotencia"],GOLD),
        ("Confirmación",["Estado PAGADO","Visita ≤ 2 días"],GREEN),
    ]
    boxes=[]; x=55
    for t,ls,c in entries:
        b=(x,170,x+260,350); boxes.append(b); node(d,b,t,ls,c); x+=315
    for i in range(4): arrow(d,(boxes[i][2],260),(boxes[i+1][0],260),BLUE,width=4)
    rounded(d,(120,460,1480,690),fill=CARD,outline=LINE)
    d.text((155,490),"Controles implementados",font=font(25,True),fill=NAVY)
    controls=[
        "La tarifa se determina en el servidor; el navegador no puede alterar el valor autorizado.",
        "La captura se concilia contra la orden, la moneda, el identificador propio y el estado recibido.",
        "Los reintentos son idempotentes para impedir cobros o actualizaciones duplicadas.",
        "Las credenciales corresponden al entorno de prueba y se mantienen fuera del repositorio y del informe.",
    ]
    y=540
    for c in controls:
        d.text((165,y),"• "+c,font=font(19),fill=NAVY); y+=33
    im.save(OUT/"flujo-paypal.png",quality=95)

def storage():
    im=Image.new("RGB",(1600,800),BG); d=ImageDraw.Draw(im)
    text_center(d,(800,50),"Flujo de evidencia multimedia e integridad SHA-256",font(34,True))
    entries=[
        ("Ciudadano",["Selecciona imagen","o video"],CYAN),
        ("Spring Boot",["Valida contenido","calcula SHA-256"],RED),
        ("PocketBase",["Guarda binario","colección privada"],GREEN),
        ("PostgreSQL",["Referencia, hash","MIME y tamaño"],NAVY),
        ("Consulta",["Backend autoriza","y sirve el archivo"],BLUE),
    ]
    boxes=[]; x=55
    for t,ls,c in entries:
        b=(x,170,x+260,350); boxes.append(b); node(d,b,t,ls,c); x+=315
    for i in range(4): arrow(d,(boxes[i][2],260),(boxes[i+1][0],260),BLUE,width=4)
    rounded(d,(130,460,1470,700),fill="#EAF7F1",outline=GREEN)
    d.text((165,492),"Propiedad de seguridad",font=font(25,True),fill=NAVY)
    lines=[
        "El cliente no recibe la credencial técnica de PocketBase.",
        "Las reglas de la colección permanecen bloqueadas para acceso público.",
        "El archivo se entrega mediante un endpoint autenticado del backend.",
        "El hash permite verificar que el binario almacenado corresponde a la evidencia registrada.",
    ]
    y=545
    for line in lines:
        d.text((175,y),"• "+line,font=font(20),fill=NAVY); y+=37
    im.save(OUT/"flujo-almacenamiento.png",quality=95)

def cluster():
    im=Image.new("RGB",(1600,900),BG); d=ImageDraw.Draw(im)
    text_center(d,(800,50),"Estado verificado del stack Docker Swarm",font(34,True))
    services=[
        ("frontend","2 / 2","Angular + Nginx","8080",GREEN),
        ("backend","2 / 2","Spring Boot","8082 → 8081",GREEN),
        ("postgres","1 / 1","PostgreSQL 17","5433 → 5432",BLUE),
        ("pocketbase","1 / 1","Almacenamiento","8091 → 8090",BLUE),
        ("ollama","1 / 1","llama3.2 local","11435 → 11434",CYAN),
        ("ngrok","1 / 1","Túnel IoT HTTPS","4041 inspector",GOLD),
    ]
    x0,y0=110,150
    d.rounded_rectangle((x0,y0,1490,220),radius=12,fill=NAVY)
    headers=["Servicio","Réplicas","Función","Puerto local"]
    xs=[140,500,760,1180]
    for x,h in zip(xs,headers): d.text((x,170),h,font=font(22,True),fill="white")
    y=230
    for svc,rep,fn,port,c in services:
        fill=CARD if (y//80)%2 else "#EAF1F7"
        d.rounded_rectangle((x0,y,1490,y+68),radius=8,fill=fill,outline=LINE)
        d.ellipse((145,y+21,169,y+45),fill=c)
        d.text((185,y+20),svc,font=font(22,True),fill=NAVY)
        d.text((500,y+20),rep,font=font(22,True),fill=GREEN)
        d.text((760,y+20),fn,font=font(21),fill=NAVY)
        d.text((1180,y+20),port,font=font(21),fill=MUTED)
        y+=78
    rounded(d,(110,735,1490,840),fill="#FFF5E5",outline=GOLD)
    d.text((145,760),"Verificación funcional:",font=font(22,True),fill=NAVY)
    d.text((375,760),"frontend HTTP 200 · backend UP · PocketBase 200 · Ollama 200 · ngrok 200",
           font=font(21),fill=NAVY)
    d.text((145,798),"Nota:",font=font(20,True),fill=RED)
    d.text((220,798),"es un clúster de un solo nodo físico; las réplicas toleran caída de tareas, no la caída del equipo anfitrión.",
           font=font(19),fill=NAVY)
    im.save(OUT/"estado-swarm.png",quality=95)

if __name__ == "__main__":
    architecture()
    flow()
    payment()
    storage()
    cluster()
    print("Figuras generadas en", OUT)
