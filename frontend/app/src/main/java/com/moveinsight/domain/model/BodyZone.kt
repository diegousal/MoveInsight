package com.moveinsight.domain.model

/**
 * Zonas corporales que el paciente puede marcar en el mapa.
 * El campo [id] es el que se envía al backend.
 * El campo [label] es el texto mostrado en la UI.
 */
enum class BodyZone(val id: String, val label: String, val isFront: Boolean) {
    // ── Vista frontal ──────────────────────────────────────────────────────
    HEAD           ("head",            "Cabeza",          true),
    NECK_FRONT     ("neck",            "Cuello",          true),
    LEFT_SHOULDER  ("left_shoulder",   "Hombro Izq.",     true),
    RIGHT_SHOULDER ("right_shoulder",  "Hombro Der.",     true),
    CHEST          ("chest",           "Pecho",           true),
    ABDOMEN        ("abdomen",         "Abdomen",         true),
    LEFT_HIP       ("left_hip",        "Cadera Izq.",     true),
    RIGHT_HIP      ("right_hip",       "Cadera Der.",     true),
    LEFT_QUAD      ("left_quad",       "Cuádricep Izq.",  true),
    RIGHT_QUAD     ("right_quad",      "Cuádricep Der.",  true),
    LEFT_KNEE      ("left_knee",       "Rodilla Izq.",    true),
    RIGHT_KNEE     ("right_knee",      "Rodilla Der.",    true),
    LEFT_SHIN      ("left_shin",       "Tibia Izq.",      true),
    RIGHT_SHIN     ("right_shin",      "Tibia Der.",      true),
    // ── Vista posterior ────────────────────────────────────────────────────
    UPPER_BACK     ("upper_back",      "Espalda Alta",    false),
    LUMBAR         ("lumbar",          "Lumbar",          false),
    LEFT_GLUTE     ("left_glute",      "Glúteo Izq.",     false),
    RIGHT_GLUTE    ("right_glute",     "Glúteo Der.",     false),
    LEFT_HAMSTRING ("left_hamstring",  "Isquio Izq.",     false),
    RIGHT_HAMSTRING("right_hamstring", "Isquio Der.",     false),
    LEFT_CALF      ("left_calf",       "Gemelo Izq.",     false),
    RIGHT_CALF     ("right_calf",      "Gemelo Der.",     false),
}