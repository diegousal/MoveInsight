import numpy as np


def _suavizar_fases(fases, ventana=7):
    """Suaviza la secuencia de fases con moda en ventana deslizante."""
    suavizado = fases.copy()
    for i in range(len(fases)):
        ini = max(0, i - ventana // 2)
        fin = min(len(fases), i + ventana // 2 + 1)
        suavizado[i] = np.bincount(fases[ini:fin]).argmax()
    return suavizado


def obtener_informe_reps(preds):
    fases_raw = np.argmax(preds[0][0], axis=-1)
    fases = _suavizar_fases(fases_raw, ventana=7)

    # ── DEBUG ─────────────────────────────────────────────────────────────
    unique, counts = np.unique(fases, return_counts=True)
    print("\n[DEBUG] Distribución de fases (suavizadas):")
    for cls, cnt in zip(unique, counts):
        nombre = {0:"idle/padding", 1:"bajando", 2:"fondo", 3:"subiendo"}.get(int(cls), "?")
        print(f"  Clase {cls} ({nombre}): {cnt} frames ({cnt/len(fases)*100:.1f}%)")
    # ── FIN DEBUG ──────────────────────────────────────────────────────────

    informe = []
    en_rep = False
    inicio = 0
    paso_por_fondo = False

    for i in range(1, len(fases)):
        f_actual = int(fases[i])
        f_previa = int(fases[i - 1])

        # INICIO de rep: empezamos a bajar
        if f_actual == 1 and not en_rep:
            en_rep = True
            inicio = i
            paso_por_fondo = False

        # Fondo explícito (clase 2)
        if f_actual == 2 and en_rep:
            paso_por_fondo = True

        # *** FONDO IMPLÍCITO: el modelo saltó de bajando(1) a subiendo(3) ***
        # El modelo no predijo clase 2 pero el movimiento es válido
        if f_previa == 1 and f_actual == 3 and en_rep:
            paso_por_fondo = True

        # CIERRE de rep
        cerrar_por_reposo    = (f_actual == 0 and en_rep)
        cerrar_por_nueva_rep = (en_rep and f_previa == 3 and f_actual == 1)

        if cerrar_por_reposo or cerrar_por_nueva_rep:
            fin = i
            if paso_por_fondo:
                rep_data = {
                    "Rep":         len(informe) + 1,
                    "Profundidad": float(np.mean(preds[1][0][inicio:fin])),
                    "Espalda":     float(np.mean(preds[2][0][inicio:fin])),
                    "Estabilidad": float(np.mean(preds[3][0][inicio:fin])),
                    "Rodillas":    float(np.mean(preds[4][0][inicio:fin])),
                    "Ritmo":       float(np.mean(preds[5][0][inicio:fin])),
                }
                informe.append(rep_data)
                print(f"[DEBUG] ✅ Rep {rep_data['Rep']} detectada: frames {inicio}→{fin}")
            else:
                print(f"[DEBUG] ⚠️  Segmento {inicio}→{fin} descartado (no pasó por fondo)")

            if cerrar_por_nueva_rep:
                inicio = i
                en_rep = True
                paso_por_fondo = False
            else:
                en_rep = False

    # Rep que llega hasta el final del vídeo sin volver a clase 0
    if en_rep and paso_por_fondo:
        fin = len(fases)
        rep_data = {
            "Rep":         len(informe) + 1,
            "Profundidad": float(np.mean(preds[1][0][inicio:fin])),
            "Espalda":     float(np.mean(preds[2][0][inicio:fin])),
            "Estabilidad": float(np.mean(preds[3][0][inicio:fin])),
            "Rodillas":    float(np.mean(preds[4][0][inicio:fin])),
            "Ritmo":       float(np.mean(preds[5][0][inicio:fin])),
        }
        informe.append(rep_data)
        print(f"[DEBUG] ✅ Rep {rep_data['Rep']} detectada (cierre al final): frames {inicio}→{fin}")

    return informe


def mostrar_informe(informe):
    print("\n" + "=" * 40)
    print("   INFORME TÉCNICO DE SENTADILLA")
    print("=" * 40)
    if not informe:
        print("No se detectaron repeticiones completas.")
        return
    for r in informe:
        print(f"\nREPETICIÓN {r['Rep']}:")
        print(f"  > Profundidad:  {r['Profundidad']:.2%}")
        print(f"  > Espalda:      {r['Espalda']:.2%}")
        print(f"  > Estabilidad:  {r['Estabilidad']:.2%}")
        print(f"  > Rodillas:     {r['Rodillas']:.2%}")
        print(f"  > Ritmo:        {r['Ritmo']:.2%}")
        print("-" * 40)