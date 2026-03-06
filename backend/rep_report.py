import numpy as np



def obtener_informe_reps(preds):
    fases = np.argmax(preds[0][0], axis=-1)
    informe = []
    en_rep = False
    inicio = 0
    paso_por_fondo = False # Para evitar falsos positivos si no bajó de verdad

    for i in range(1, len(fases)):
        f_actual = fases[i]
        f_previa = fases[i-1]

        # 1. DETECTAR INICIO (Si pasamos de 0 o 3 a 1)
        if f_actual == 1 and not en_rep:
            en_rep = True
            inicio = i
            paso_por_fondo = False
        
        if f_actual == 2:
            paso_por_fondo = True

        # 2. DETECTAR FIN (Si estamos en rep y volvemos a 0 O empezamos otra bajada tras subir)
        # La condición (f_previa == 3 and f_actual == 1) es la clave para las 6 repes
        cerrar_por_reposo = (f_actual == 0 and en_rep)
        cerrar_por_nueva_repe = (en_rep and f_previa == 3 and f_actual == 1)

        if cerrar_por_reposo or cerrar_por_nueva_repe:
            fin = i
            # Solo guardamos si realmente hubo un movimiento completo
            if paso_por_fondo:
                rep_data = {
                    "Rep": len(informe) + 1,
                    "Profundidad": np.mean(preds[1][0][inicio:fin]),
                    "Espalda": np.mean(preds[2][0][inicio:fin]),
                    "Estabilidad": np.mean(preds[3][0][inicio:fin]),
                    "Simetría": np.mean(preds[4][0][inicio:fin]),
                    "Rodillas": np.mean(preds[5][0][inicio:fin]),
                    "Ritmo": np.mean(preds[6][0][inicio:fin])
                }
                informe.append(rep_data)
            
            # Si cerramos porque empezó una nueva, reseteamos el inicio a este frame
            if cerrar_por_nueva_repe:
                inicio = i
                en_rep = True
                paso_por_fondo = False
            else:
                en_rep = False

    return informe

def mostrar_informe(informe):
    print("\n" + "="*40)
    print("   INFORME TÉCNICO DE SENTADILLA")
    print("="*40)
    if not informe:
        print("No se detectaron repeticiones completas.")
    for r in informe:
        print(f"REPETICIÓN {r['Rep']}:")
        print(f"  > Profundidad: {r['Profundidad']:.2%}")
        print(f"  > Posición Espalda: {r['Espalda']:.2%}")
        print(f"  > Estabilidad: {r['Estabilidad']:.2%}")
        print(f"  > Ritmo/Control: {r['Ritmo']:.2%}")
        print(f"  > Simetría L/R: {r['Simetría']:.2%}")
        print("-"*20)
