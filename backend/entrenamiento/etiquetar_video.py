#!/usr/bin/env python3
import cv2
import argparse
import json
import os
import tempfile
from collections import deque

LABEL_KEYS = {
    ord('1'): 'stand',
    ord('2'): 'bajando',
    ord('3'): 'abajo',
    ord('4'): 'subiendo'
}

INSTRUCTION_LINES = [
    "ESPACIO: Play/Pause | d: next frame | a: prev frame | [: -speed | ]: +speed | v: speed=0.5",
    "1: stand | 2: bajando | 3: abajo | 4: subiendo | z: undo | q: quit & save",
    "NOTA: Para cerrar la ultima repeticion, marca 'stand' (1) al final del movimiento."
]

def draw_overlay(img, text_lines, pos=(10,30), line_height=24):
    for i, line in enumerate(text_lines):
        cv2.putText(img, line, (pos[0], pos[1] + i*line_height),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.55, (0,255,0), 1, cv2.LINE_AA)

def parse_args():
    p = argparse.ArgumentParser()
    p.add_argument('--video', required=True, help='Ruta al archivo de vídeo')
    p.add_argument('--out', default='annotations.json', help='Archivo JSON de salida (mergeable)')
    p.add_argument('--pretty', default='annotations_tuple_format.txt', help='Archivo append con formato tuplas (visual)')
    p.add_argument('--speed', type=float, default=0.5, help='Velocidad de reproducción inicial (ej: 0.5)')
    return p.parse_args()

def safe_load_json(path):
    if not os.path.exists(path) or os.path.getsize(path) == 0:
        return {}
    try:
        with open(path, 'r', encoding='utf-8') as f:
            return json.load(f)
    except Exception as e:
        bak = path + '.bak'
        print(f"[WARN] No se pudo leer {path} ({e}). Backup en {bak}")
        os.replace(path, bak)
        return {}

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
    labels = []  # [{'label':..., 'frame': int}, ...]
    window_name = "Labeler - press q to quit"
    cv2.namedWindow(window_name, cv2.WINDOW_NORMAL)

    while True:
        cap.set(cv2.CAP_PROP_POS_FRAMES, current_frame)
        ret, frame = cap.read()
        if not ret: break

        info = [
            f"File: {os.path.basename(args.video)}  Frame: {current_frame}/{total_frames-1}  speed:{speed:.2f}x",
            f"Labels: {len(labels)} | Secuencia: stand(1)->baja(2)->abajo(3)->sube(4)->stand(1)"
        ]
        info.extend(INSTRUCTION_LINES)
        display = frame.copy()
        draw_overlay(display, info, pos=(10,26))
        recent = ["Ultimas marcas:"] + [f"{i+1}. {lab['label']} @ {lab['frame']}" for i, lab in enumerate(labels[-5:])]
        draw_overlay(display, recent, pos=(10, 160))

        cv2.imshow(window_name, display)
        key = cv2.waitKey(wait_ms(speed)) & 0xFF

        if key == ord(' '): playing = not playing
        elif key == ord('d'): current_frame = min(total_frames - 1, current_frame + 1); playing = False
        elif key == ord('a'): current_frame = max(0, current_frame - 1); playing = False
        elif key in LABEL_KEYS:
            labels.append({'label': LABEL_KEYS[key], 'frame': int(current_frame)})
            print(f"[MARK] {LABEL_KEYS[key]} @ {current_frame}")
        elif key == ord('z') and labels:
            removed = labels.pop()
            print(f"[UNDO] Removed {removed['label']}")
        elif key == ord('q'): break

        if playing:
            current_frame += 1
            if current_frame >= total_frames:
                playing = False
                current_frame = total_frames - 1

    cap.release()
    cv2.destroyAllWindows()

    # --- PROCESAMIENTO DE REPETICIONES ---
    # Buscamos la secuencia: stand → bajando → abajo → subiendo → stand.
    # La red solo se entrena para segmentar fases, así que no necesitamos
    # etiquetar KPIs a mano: se calcularán geométricamente tras la inferencia.
    reps = []
    i = 0
    while i + 4 < len(labels):
        window  = labels[i:i+5]
        l_names = [lab['label'] for lab in window]

        if l_names == ['stand', 'bajando', 'abajo', 'subiendo', 'stand']:
            frames_tuple = tuple(lab['frame'] for lab in window)
            reps.append({'frames': frames_tuple})
            i += 4
        else:
            i += 1

    # --- GUARDADO ---
    if reps:
        base_key = os.path.splitext(os.path.basename(args.video))[0] + '.npy'
        with open(args.pretty, 'a', encoding='utf-8') as f:
            f.write(f'"{base_key}": {{\n  "reps": [\n')
            for r in reps:
                f.write(f"    {{\n      \"frames\": {r['frames']}\n    }},\n")
            f.write('  ]\n}\n\n')
        print(f"\nSe han guardado {len(reps)} repeticiones en {args.pretty}")
    else:
        print("\nNo se detectaron secuencias completas (1-2-3-4-1).")

if __name__ == '__main__':
    main()