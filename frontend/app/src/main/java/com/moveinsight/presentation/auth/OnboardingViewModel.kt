package com.moveinsight.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moveinsight.core.utils.Resource
import com.moveinsight.domain.auth.CompleteOnboardingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── Límites de validación ──────────────────────────────────────────────────

private const val AGE_MIN    = 10
private const val AGE_MAX    = 99
private const val WEIGHT_MIN = 30f
private const val WEIGHT_MAX = 300f

// ── Estado del formulario ──────────────────────────────────────────────────

data class OnboardingFormState(
    val age          : String  = "",   // vacío = no indicado (campo opcional)
    val bodyWeightKg : String  = "",   // vacío = no indicado (campo opcional)
    val level        : String  = "",   // "beginner" | "intermediate" | "advanced"
    val objective    : String  = "",   // "technique" | "progression" | "pain_monitoring" | "health"
    // null = sin error; String = mensaje de error visible
    val ageError     : String? = null,
    val weightError  : String? = null,
)

// ── UiState ────────────────────────────────────────────────────────────────

sealed class OnboardingUiState {
    data object Idle    : OnboardingUiState()
    data object Loading : OnboardingUiState()
    data object Success : OnboardingUiState()
    data class  Error(val message: String) : OnboardingUiState()
}

// ── Eventos efímeros ───────────────────────────────────────────────────────

sealed class OnboardingEvent {
    data object NavigateToHome             : OnboardingEvent()
    data class  ShowSnackbar(val msg: String) : OnboardingEvent()
}

// ── ViewModel ─────────────────────────────────────────────────────────────

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val completeOnboardingUseCase: CompleteOnboardingUseCase
) : ViewModel() {

    private val _form    = MutableStateFlow(OnboardingFormState())
    val form: StateFlow<OnboardingFormState> = _form.asStateFlow()

    private val _uiState = MutableStateFlow<OnboardingUiState>(OnboardingUiState.Idle)
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val _events  = Channel<OnboardingEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    // ── Paso 1: datos personales ────────────────────────────────────────

    /** Acepta la pulsación carácter a carácter; limpia el error activo. */
    fun onAgeChange(v: String) {
        // Solo dígitos, máximo 3 caracteres (999 → se valida en blur/submit)
        if (v.isEmpty() || (v.all { it.isDigit() } && v.length <= 3))
            _form.update { it.copy(age = v, ageError = null) }
    }

    fun onBodyWeightChange(v: String) {
        // Hasta 3 enteros + 1 decimal: "300.0"
        if (v.isEmpty() || v.matches(Regex("^\\d{0,3}(\\.\\d{0,1})?\$")))
            _form.update { it.copy(bodyWeightKg = v, weightError = null) }
    }

    /** Validación on-blur: se llama cuando el campo pierde el foco. */
    fun onAgeFocusLost()   { _form.update { it.copy(ageError    = validateAge()) } }
    fun onWeightFocusLost(){ _form.update { it.copy(weightError = validateWeight()) } }

    /**
     * Valida todo el paso 1 al pulsar "Siguiente".
     * Actualiza los errores en el estado y devuelve true si se puede avanzar.
     */
    fun validatePersonalData(): Boolean {
        val ageErr    = validateAge()
        val weightErr = validateWeight()
        _form.update { it.copy(ageError = ageErr, weightError = weightErr) }
        return ageErr == null && weightErr == null
    }

    // ── Paso 2: nivel y objetivo ────────────────────────────────────────

    fun onLevelSelected(v: String)     { _form.update { it.copy(level     = v) } }
    fun onObjectiveSelected(v: String) { _form.update { it.copy(objective = v) } }

    val canFinish: Boolean get() = _form.value.level.isNotBlank() && _form.value.objective.isNotBlank()

    // ── Envío ────────────────────────────────────────────────────────────

    fun onFinish() {
        if (!canFinish) return
        viewModelScope.launch {
            _uiState.value = OnboardingUiState.Loading
            val f = _form.value
            when (val r = completeOnboardingUseCase(
                age          = f.age.toIntOrNull(),
                bodyWeightKg = f.bodyWeightKg.toFloatOrNull(),
                level        = f.level,
                objective    = f.objective
            )) {
                is Resource.Success -> {
                    _uiState.value = OnboardingUiState.Success
                    _events.send(OnboardingEvent.NavigateToHome)
                }
                is Resource.Error -> {
                    _uiState.value = OnboardingUiState.Error(r.message)
                    _events.send(OnboardingEvent.ShowSnackbar(r.message))
                }
                is Resource.Loading -> Unit
            }
        }
    }

    // ── Helpers de validación ────────────────────────────────────────────

    /** null = sin error. Solo valida si el campo tiene contenido (es opcional). */
    private fun validateAge(value: String = _form.value.age): String? {
        if (value.isBlank()) return null
        val n = value.toIntOrNull() ?: return "Introduce un número válido"
        return when {
            n < AGE_MIN -> "La edad mínima es $AGE_MIN años"
            n > AGE_MAX -> "La edad máxima es $AGE_MAX años"
            else        -> null
        }
    }

    private fun validateWeight(value: String = _form.value.bodyWeightKg): String? {
        if (value.isBlank()) return null
        val n = value.toFloatOrNull() ?: return "Introduce un número válido"
        return when {
            n < WEIGHT_MIN -> "El peso mínimo es ${WEIGHT_MIN.toInt()} kg"
            n > WEIGHT_MAX -> "El peso máximo es ${WEIGHT_MAX.toInt()} kg"
            else           -> null
        }
    }
}
