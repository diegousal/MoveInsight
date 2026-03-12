#!/usr/bin/env python3
import cv2
import argparse
import json
import os

LABEL_KEYS = {
    ord('1'): 'stand',
    ord('2'): 'bajando',
    ord('3'): 'abajo',
    ord('4'): 'subiendo'
}

INSTRUCTION_LINES = [
    "ESPACIO: Play/Pause | d: next frame | a: prev frame | [: -speed | ]: +speed",
    "1: stand | 2: bajando | 3: abajo | 4: subiendo | z: undo | q: quit & save",
    "NOTA: Al marcar el 'stand' de cierre, introduce los KPIs en la consola."
]

def draw_overlay(img, text_lines, pos=(10,30), line_height=24):
    for i, line in enumerate(text_lines):
        cv2.putText(img, line, (pos[0], pos[1] + i*line_height),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.55, (0,255,0), 1, cv2.LINE_AA)

def get_kpi_inputs():
    """Pide los KPIs por consola y los normaliza de 0-10 a 0-1."""
    print("\n" + "="*30)
    print(" REPETICIÓN COMPLETADA - KPIs")
    print(" (Introduce valores de 0 a 10)")
    kpis = ["depth", "torso", "stability", "knees", "ritmo"]
    results = {}
    for k in kpis:
        while True:
            try:
                val = float(input(f"  > {k.capitalize()}: "))
                if 0 <= val <= 10:
                    results[k] = round(val / 10.0, 2)
                    break
                else:
                    print("    [!] El valor debe estar entre 0 y 10.")
            except ValueError:
                print("    [!] Introduce un número válido.")
    print("="*30 + "\n")
    return results

def parse_args():
    p = argparse.ArgumentParser()
    p.add_argument('--video', required=True, help='Ruta al archivo de vídeo')
    p.add_argument('--out', default='annotations.json', help='Archivo JSON de salida')
    p.add_argument('--pretty', default='annotations_tuple_format.txt', help='Archivo de salida formato tuplas')
    p.add_argument('--speed', type=float, default=0.5, help='Velocidad inicial')
    return p.parse_args()

def main():
    args = parse_args()
    cap = cv2.VideoCapture(args.video)
    if not cap.isOpened():
        print("No se pudo abrir el vídeo:", args.video)
        return

    total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT) or 0)
    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
    speed = max(0.05, args.speed)
    wait_ms = lambda sp: max(1, int(1000.0 / (fps * sp)))

    playing = False
    current_frame = 0
    labels = []  # Lista de marcas temporales
    reps_final = [] # Lista de reps con sus KPIs
    
    window_name = "Labeler - press q to quit"
    cv2.namedWindow(window_name, cv2.WINDOW_NORMAL)

    while True:
        cap.set(cv2.CAP_PROP_POS_FRAMES, current_frame)
        ret, frame = cap.read()
        if not ret: break

        info = [
            f"File: {os.path.basename(args.video)} | Frame: {current_frame}/{total_frames-1} | speed:{speed:.2f}x",
            f"Reps completadas: {len(reps_final)} | Marcas actuales: {len(labels)}"
        ]
        info.extend(INSTRUCTION_LINES)
        
        display = frame.copy()
        draw_overlay(display, info, pos=(10,26))
        recent = ["Ultimas marcas:"] + [f"- {lab['label']} @ {lab['frame']}" for lab in labels[-4:]]
        draw_overlay(display, recent, pos=(10, 180))

        cv2.imshow(window_name, display)
        key = cv2.waitKey(wait_ms(speed)) & 0xFF

        if key == ord(' '): 
            playing = not playing
        elif key == ord('d'): 
            current_frame = min(total_frames - 1, current_frame + 1); playing = False
        elif key == ord('a'): 
            current_frame = max(0, current_frame - 1); playing = False
        elif key == ord('['):
            speed = max(0.1, speed - 0.1)
        elif key == ord(']'):
            speed = min(3.0, speed + 0.1)
            
        elif key in LABEL_KEYS:
            label_name = LABEL_KEYS[key]
            labels.append({'label': label_name, 'frame': int(current_frame)})
            print(f"[MARK] {label_name} @ {current_frame}")

            # Lógica de detección de repetición completada
            # Buscamos si las últimas 5 etiquetas forman: stand -> bajando -> abajo -> subiendo -> stand
            if len(labels) >= 5:
                window = labels[-5:]
                l_names = [lab['label'] for lab in window]
                if l_names == ['stand', 'bajando', 'abajo', 'subiendo', 'stand']:
                    # Pausamos el vídeo
                    playing = False
                    # Mostramos aviso en pantalla
                    overlay_copy = display.copy()
                    draw_overlay(overlay_copy, ["!!! INTRODUCE KPIs EN CONSOLA !!!"], pos=(10, 400))
                    cv2.imshow(window_name, overlay_copy)
                    cv2.waitKey(1)
                    
                    # Pedimos los KPIs
                    kpis = get_kpi_inputs()
                    frames_tuple = tuple(lab['frame'] for lab in window)
                    
                    reps_final.append({
                        'frames': frames_tuple,
                        'kpis': kpis
                    })
                    
                    # NOTA: El último 'stand' es el primero de la siguiente repetición.
                    # Para no perder el hilo, mantenemos solo ese último 'stand' en la lista de labels
                    # para que la siguiente rep empiece desde ahí.
                    last_stand = labels[-1]
                    labels = [last_stand] 

        elif key == ord('z') and labels:
            removed = labels.pop()
            print(f"[UNDO] Removed {removed['label']}")
        elif key == ord('q'): 
            break

        if playing:
            current_frame += 1
            if current_frame >= total_frames:
                playing = False
                current_frame = total_frames - 1

    cap.release()
    cv2.destroyAllWindows()

    # --- GUARDADO ---
    if reps_final:
        base_key = os.path.splitext(os.path.basename(args.video))[0] + '.npy'
        with open(args.pretty, 'a', encoding='utf-8') as f:
            f.write(f'"{base_key}": {{\n  "reps": [\n')
            for r in reps_final:
                f.write(f"    {{\n      \"frames\": {r['frames']}, \n      \"kpis\": {json.dumps(r['kpis'])}\n    }},\n")
            f.write('  ]\n}\n\n')
        print(f"\nProceso finalizado. Se han guardado {len(reps_final)} repeticiones en {args.pretty}")
    else:
        print("\nNo se guardaron repeticiones completas.")

if __name__ == '__main__':
    main()