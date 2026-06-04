"""
Entrena modelos XGBoost separados por perfil de camara (frontal / lateral).

Mejoras de esta version:
  1. GROUPKFOLD: el cross-validation respeta los grupos por video, evitando
     data leakage cuando un mismo video aporta varias reps.
  2. ESPEJO: cada rep se duplica con su version reflejada (intercambio
     L<->R y negacion de sust_dx). Las KPIs son invariantes al espejo
     (depth/torso/knees/symmetry/rhythm), por lo que es augmentacion
     gratuita y simetriza el modelo.

Formato esperado de etiquetas_kpis.json:
{
  "video1.npy": [
    {
      "start": 0, "end": 51,
      "depth": "1", "torso": "3", "knees": "3", "symmetry": "3", "rhythm": "3",
      "perfil": "frontal"
    },
    ...
  ],
  ...
}

KPIs etiquetados como "-" se ignoran en el entrenamiento.
Convencion: 1=malo, 2=medio, 3=bueno.

Uso:
    python training_xgb.py
    python training_xgb.py --no_mirror     # desactiva el espejo
"""

import os
import json
import pickle
import argparse
import numpy as np
import pandas as pd
from tqdm import tqdm

from xgboost import XGBClassifier
from sklearn.model_selection import StratifiedGroupKFold, GroupKFold
from sklearn.utils.class_weight import compute_sample_weight
from sklearn.metrics import classification_report

from rep_features import extract_rep_features, FEATURE_NAMES, N_FEATURES
from mirror_utils import mirror_npy_full, ALL_COLUMNS as DF_COLUMNS

try:
    import optuna
    optuna.logging.set_verbosity(optuna.logging.WARNING)
    OPTUNA_AVAILABLE = True
except ImportError:
    OPTUNA_AVAILABLE = False

# KPIs entrenables por perfil
PERFIL_KPIS = {
    "frontal": ["depth", "torso", "knees", "symmetry", "rhythm"],
    "lateral": ["depth", "torso", "rhythm"],
}

ALL_KPIS = ["depth", "torso", "knees", "symmetry", "rhythm"]

# KPIs que se entrenan como binarios: clases {0,1} -> 0 (problema), {2} -> 1 (bueno).
# El payload incluye binary_kpis para que el inferidor remapee 0->1 y 1->3.
BINARY_KPIS = {"knees", "symmetry"}


def _npy_to_df(arr: np.ndarray) -> pd.DataFrame:
    """Reconstruye el DataFrame desde el .npy ya procesado."""
    if arr.ndim == 1:
        raise ValueError("Array 1D: no es un DataFrame procesado.")
    n_cols = arr.shape[1]
    cols = (DF_COLUMNS[:n_cols] if n_cols <= len(DF_COLUMNS)
            else DF_COLUMNS + [f"col_{i}" for i in range(n_cols - len(DF_COLUMNS))])
    return pd.DataFrame(arr, columns=cols)


def _parse_label(value):
    """Convierte etiqueta a entero o None si es '-'."""
    if value is None or value == "-" or str(value).strip() == "-":
        return None
    try:
        return int(value) - 1  # "1"/"2"/"3" -> 0/1/2
    except (ValueError, TypeError):
        return None


# ─────────────────────────────────────────────────────────────────────────────
# Carga de datos
# ─────────────────────────────────────────────────────────────────────────────

def load_dataset_by_perfil(data_dir: str, labels_path: str,
                           fps: float = 30.0, use_mirror: bool = True):
    """
    Carga el dataset agrupando por perfil. Si use_mirror=True duplica cada
    rep con su version reflejada (mismo grupo, mismas etiquetas).

    Returns:
        dict con estructura:
        {
          "frontal": {"X": np.ndarray, "y": {kpi: list}, "groups": np.ndarray},
          "lateral": {...},
        }
        groups[i] = nombre de archivo del rep i (mismo para original y mirror).
    """
    with open(labels_path, "r", encoding="utf-8") as f:
        labels = json.load(f)

    data = {
        "frontal": {"X": [], "y": {kpi: [] for kpi in ALL_KPIS},
                    "groups": [], "n_files": set()},
        "lateral": {"X": [], "y": {kpi: [] for kpi in ALL_KPIS},
                    "groups": [], "n_files": set()},
    }

    skipped_files = 0
    skipped_reps = 0
    sin_perfil = 0
    n_originales = 0
    n_mirrors = 0

    print(f"Cargando dataset desde {labels_path}...")
    print(f"Augmentacion por espejo: {'ACTIVADA' if use_mirror else 'DESACTIVADA'}")

    for fname in tqdm(sorted(labels.keys())):
        fp = os.path.join(data_dir, fname)
        if not os.path.exists(fp):
            skipped_files += 1
            continue

        try:
            raw = np.load(fp).astype(np.float32)
            df = _npy_to_df(raw)
        except Exception as e:
            print(f"  [WARN] {fname}: {e}")
            skipped_files += 1
            continue

        # Pre-calcular version reflejada del .npy (una sola vez por archivo)
        if use_mirror:
            try:
                raw_mir = mirror_npy_full(raw, fps=fps)
                df_mir = _npy_to_df(raw_mir)
            except Exception as e:
                print(f"  [WARN] mirror de {fname}: {e}")
                df_mir = None
        else:
            df_mir = None

        entry = labels[fname]
        reps_list = entry if isinstance(entry, list) else entry.get("reps", [])

        for rep in reps_list:
            perfil = rep.get("perfil")
            if perfil not in ("frontal", "lateral"):
                sin_perfil += 1
                continue

            try:
                start = int(rep["start"])
                end = int(rep["end"])
                feats = extract_rep_features(df, start, end, fps)
            except Exception:
                skipped_reps += 1
                continue

            # Etiquetas (validas para original y mirror)
            label_dict = {kpi: _parse_label(rep.get(kpi)) for kpi in ALL_KPIS}

            # Original
            data[perfil]["X"].append(feats)
            data[perfil]["groups"].append(fname)
            for kpi in ALL_KPIS:
                data[perfil]["y"][kpi].append(label_dict[kpi])
            n_originales += 1

            # Mirror (mismas etiquetas, mismo grupo)
            if df_mir is not None:
                try:
                    feats_mir = extract_rep_features(df_mir, start, end, fps)
                    data[perfil]["X"].append(feats_mir)
                    data[perfil]["groups"].append(fname)   # mismo grupo
                    for kpi in ALL_KPIS:
                        data[perfil]["y"][kpi].append(label_dict[kpi])
                    n_mirrors += 1
                except Exception:
                    pass

            data[perfil]["n_files"].add(fname)

    # Convertir a arrays
    result = {}
    for perfil in ["frontal", "lateral"]:
        X = (np.array(data[perfil]["X"], dtype=np.float32)
             if data[perfil]["X"] else np.empty((0, N_FEATURES)))
        y = {kpi: data[perfil]["y"][kpi] for kpi in ALL_KPIS}
        groups = np.array(data[perfil]["groups"])
        result[perfil] = {
            "X": X, "y": y, "groups": groups,
            "n_files": len(data[perfil]["n_files"]),
        }

    print()
    print("Resumen de carga:")
    print(f"  Reps originales: {n_originales}")
    if use_mirror:
        print(f"  Reps mirror    : {n_mirrors}")
        print(f"  Total          : {n_originales + n_mirrors}")
    for perfil in ["frontal", "lateral"]:
        nf = result[perfil]['n_files']
        nr = result[perfil]['X'].shape[0]
        print(f"  {perfil.upper():<8}: {nr:>4} reps  ({nf} archivos)")
    if sin_perfil > 0:
        print(f"  Reps sin perfil definido: {sin_perfil} (omitidas)")
    if skipped_files > 0:
        print(f"  Archivos omitidos: {skipped_files}")
    print()

    return result


# ─────────────────────────────────────────────────────────────────────────────
# Helpers: oversampling y Optuna
# ─────────────────────────────────────────────────────────────────────────────

def _oversample(X, y, seed=42):
    """
    Random oversampling agresivo: replica muestras de las clases minoritarias
    hasta igualar el conteo de la mayoritaria.  Devuelve (X_res, y_res).
    """
    rng = np.random.default_rng(seed)
    classes, counts = np.unique(y, return_counts=True)
    max_count = int(counts.max())
    parts_X, parts_y = [X], [y]
    for cls, cnt in zip(classes, counts):
        if cnt < max_count:
            idx = np.where(y == cls)[0]
            extra = rng.choice(idx, size=max_count - int(cnt), replace=True)
            parts_X.append(X[extra])
            parts_y.append(y[extra])
    return np.concatenate(parts_X), np.concatenate(parts_y)


def _tune_with_optuna(X_tr, y_tr, w_tr, X_val, y_val, is_binary, n_trials=40):
    """
    Busqueda TPE sobre el primer fold.  Optimiza accuracy en validacion.
    """
    eval_metric = "logloss" if is_binary else "mlogloss"

    def objective(trial):
        params = dict(
            n_estimators    = trial.suggest_int("n_estimators",  150, 600),
            max_depth       = trial.suggest_int("max_depth",       3,   7),
            learning_rate   = trial.suggest_float("learning_rate", 0.02, 0.15, log=True),
            subsample       = trial.suggest_float("subsample",     0.6, 1.0),
            colsample_bytree= trial.suggest_float("colsample_bytree", 0.6, 1.0),
            min_child_weight= trial.suggest_int("min_child_weight", 1, 10),
            reg_lambda      = trial.suggest_float("reg_lambda", 1e-3, 10.0, log=True),
        )
        clf = XGBClassifier(eval_metric=eval_metric, random_state=42,
                            n_jobs=-1, **params)
        clf.fit(X_tr, y_tr, sample_weight=w_tr, verbose=False)
        return float((clf.predict(X_val) == y_val).mean())

    sampler = optuna.samplers.TPESampler(seed=42)
    study = optuna.create_study(direction="maximize", sampler=sampler)
    study.optimize(objective, n_trials=n_trials, show_progress_bar=False)
    return study.best_params


# ─────────────────────────────────────────────────────────────────────────────
# Entrenamiento
# ─────────────────────────────────────────────────────────────────────────────

DEFAULT_PARAMS = dict(
    n_estimators=300, max_depth=4, learning_rate=0.05,
    subsample=0.8, colsample_bytree=0.8,
)


def train_kpi_model(X_full, y_full_list, groups_full, kpi_name,
                    n_splits=5, use_oversample=False, use_optuna=False):
    """
    Entrena un modelo para un KPI con StratifiedGroupKFold (sin data leakage).

    Si kpi_name in BINARY_KPIS, las clases {0,1} se colapsan en 0 (problema)
    y la clase 2 pasa a ser 1 (bueno).
    """
    is_binary = kpi_name in BINARY_KPIS

    # Filtrar muestras con etiqueta valida
    idx_validos = [i for i, v in enumerate(y_full_list) if v is not None]
    if not idx_validos:
        print(f"  [{kpi_name}] sin datos validos, omitiendo")
        return None

    X = X_full[idx_validos]
    y = np.array([y_full_list[i] for i in idx_validos], dtype=np.int32)
    groups = groups_full[idx_validos]

    if is_binary:
        # {0, 1} -> 0 (problema),  {2} -> 1 (bueno)
        y = (y == 2).astype(np.int32)
        n_classes = 2
        tag = " [BINARIO]"
    else:
        n_classes = 3
        tag = ""

    if len(np.unique(y)) < 2:
        print(f"  [{kpi_name}{tag}] solo tiene una clase ({np.unique(y)}), omitiendo")
        return None

    sample_w = compute_sample_weight("balanced", y)

    class_counts = np.bincount(y, minlength=n_classes)
    min_class_count = int(class_counts[class_counts > 0].min())
    n_groups_unique = len(np.unique(groups))
    can_cv = min_class_count >= n_splits and n_groups_unique >= n_splits

    print(f"  [{kpi_name}{tag}] {len(X)} reps  ({n_groups_unique} videos)  "
          f"| clases: {dict(zip(*np.unique(y, return_counts=True)))}")

    eval_metric = "logloss" if is_binary else "mlogloss"
    best_params = dict(DEFAULT_PARAMS)

    if can_cv:
        # StratifiedGroupKFold: balancea clases Y respeta grupos
        try:
            skf = StratifiedGroupKFold(n_splits=n_splits, shuffle=True, random_state=42)
            splits = list(skf.split(X, y, groups))
        except ValueError:
            # Fallback a GroupKFold si StratifiedGroupKFold no encuentra splits validos
            print(f"    [INFO] StratifiedGroupKFold fallo, usando GroupKFold")
            skf = GroupKFold(n_splits=n_splits)
            splits = list(skf.split(X, y, groups))

        # Optuna sobre el primer fold (rapido y representativo)
        if use_optuna:
            if not OPTUNA_AVAILABLE:
                print(f"    [WARN] optuna no instalado, usando params por defecto "
                      f"(pip install optuna)")
            else:
                tr_idx, val_idx = splits[0]
                X_tr, y_tr, w_tr = X[tr_idx], y[tr_idx], sample_w[tr_idx]
                if use_oversample:
                    X_tr, y_tr = _oversample(X_tr, y_tr)
                    w_tr = np.ones(len(y_tr), dtype=np.float32)
                best_params = _tune_with_optuna(
                    X_tr, y_tr, w_tr, X[val_idx], y[val_idx],
                    is_binary=is_binary, n_trials=40,
                )
                pretty = {k: (round(v, 4) if isinstance(v, float) else v)
                          for k, v in best_params.items()}
                print(f"    Optuna best: {pretty}")

        fold_accs = []
        for fold, (tr_idx, val_idx) in enumerate(splits, 1):
            X_tr, y_tr, w_tr = X[tr_idx], y[tr_idx], sample_w[tr_idx]
            if use_oversample:
                X_tr, y_tr = _oversample(X_tr, y_tr, seed=42 + fold)
                w_tr = np.ones(len(y_tr), dtype=np.float32)

            clf_fold = XGBClassifier(
                eval_metric=eval_metric, random_state=42, n_jobs=-1, **best_params,
            )
            clf_fold.fit(
                X_tr, y_tr, sample_weight=w_tr,
                eval_set=[(X[val_idx], y[val_idx])], verbose=False,
            )
            preds = clf_fold.predict(X[val_idx])
            fold_accs.append(float((preds == y[val_idx]).mean()))

        print(f"    CV accuracy (GroupKFold): {np.mean(fold_accs):.3f}  "
              f"(folds: {[f'{a:.2f}' for a in fold_accs]})")
    else:
        print(f"    [WARN] datos insuficientes para CV "
              f"(min_class={min_class_count}, n_groups={n_groups_unique}, k={n_splits})")

    # Entrenamiento final con todos los datos (oversample tambien aqui si procede)
    X_fit, y_fit, w_fit = X, y, sample_w
    if use_oversample:
        X_fit, y_fit = _oversample(X_fit, y_fit, seed=42)
        w_fit = np.ones(len(y_fit), dtype=np.float32)

    clf_final = XGBClassifier(
        eval_metric=eval_metric, random_state=42, n_jobs=-1, **best_params,
    )
    clf_final.fit(X_fit, y_fit, sample_weight=w_fit, verbose=False)

    top10 = np.argsort(clf_final.feature_importances_)[-10:][::-1]
    print(f"    Top features: {[FEATURE_NAMES[i] for i in top10[:5]]}")

    return clf_final


def train_perfil_models(perfil_data, perfil_name, n_splits=5,
                        use_oversample=False, use_optuna=False):
    X = perfil_data["X"]

    if X.shape[0] == 0:
        print(f"  Sin datos para {perfil_name}, omitiendo")
        return {}

    print(f"\n=== Entrenando modelos: {perfil_name.upper()} "
          f"({X.shape[0]} reps, {perfil_data['n_files']} videos) ===")

    models = {}
    for kpi in PERFIL_KPIS[perfil_name]:
        clf = train_kpi_model(
            X, perfil_data["y"][kpi], perfil_data["groups"],
            kpi, n_splits=n_splits,
            use_oversample=use_oversample, use_optuna=use_optuna,
        )
        if clf is not None:
            models[kpi] = clf

    return models


# ─────────────────────────────────────────────────────────────────────────────
# Main
# ─────────────────────────────────────────────────────────────────────────────

def main(args):
    data = load_dataset_by_perfil(
        args.data_dir, args.labels,
        fps=args.fps, use_mirror=not args.no_mirror,
    )

    for perfil in ["frontal", "lateral"]:
        models = train_perfil_models(
            data[perfil], perfil, n_splits=args.cv_folds,
            use_oversample=args.oversample, use_optuna=args.optuna,
        )

        if not models:
            print(f"\nNo se entreno ningun modelo para {perfil.upper()}")
            continue

        payload = {
            "models": models,
            "perfil": perfil,
            "kpis": list(models.keys()),
            "feature_names": FEATURE_NAMES,
            # KPIs entrenados como binarios (problema vs bueno).  El inferidor
            # debe remapear: prediccion 0 -> 1 (problema), prediccion 1 -> 3 (bueno)
            "binary_kpis": [k for k in models.keys() if k in BINARY_KPIS],
        }
        out_path = os.path.join(args.output_dir, f"xgb_squat_models_{perfil}.pkl")
        with open(out_path, "wb") as f:
            pickle.dump(payload, f)
        print(f"\n  Modelo {perfil.upper()} guardado: {out_path}")
        print(f"  KPIs entrenados: {list(models.keys())}")

    print("\n=== Entrenamiento completado ===")
    print("Archivos generados:")
    print(f"  - xgb_squat_models_frontal.pkl  ({len(PERFIL_KPIS['frontal'])} KPIs)")
    print(f"  - xgb_squat_models_lateral.pkl  ({len(PERFIL_KPIS['lateral'])} KPIs)")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="Entrena XGBoost separado por perfil con GroupKFold y espejo"
    )
    parser.add_argument("--data_dir",   default=r"entrenamiento\archivos_entrenamiento")
    parser.add_argument("--labels",     default=r"entrenamiento\etiquetas_kpis.json")
    parser.add_argument("--output_dir", default=".")
    parser.add_argument("--fps",        type=float, default=30.0)
    parser.add_argument("--cv_folds",   type=int,   default=5)
    parser.add_argument("--no_mirror",  action="store_true",
                        help="Desactiva la augmentacion por espejo")
    parser.add_argument("--oversample", action="store_true",
                        help="Random oversampling agresivo de clases minoritarias")
    parser.add_argument("--optuna",     action="store_true",
                        help="Tuning de hiperparametros con Optuna (requiere `pip install optuna`)")
    args = parser.parse_args()
    main(args)
