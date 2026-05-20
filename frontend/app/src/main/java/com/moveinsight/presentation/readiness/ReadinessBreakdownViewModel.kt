package com.moveinsight.presentation.readiness

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moveinsight.core.utils.Resource
import com.moveinsight.domain.model.ReadinessBreakdown
import com.moveinsight.domain.repository.GetReadinessBreakdownUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ReadinessBreakdownUiState {
    data object Loading : ReadinessBreakdownUiState()
    data class  Success(val breakdown: ReadinessBreakdown) : ReadinessBreakdownUiState()
    data class  Error(val message: String) : ReadinessBreakdownUiState()
}

@HiltViewModel
class ReadinessBreakdownViewModel @Inject constructor(
    private val getReadinessBreakdownUseCase: GetReadinessBreakdownUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<ReadinessBreakdownUiState>(ReadinessBreakdownUiState.Loading)
    val state: StateFlow<ReadinessBreakdownUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = ReadinessBreakdownUiState.Loading
            when (val r = getReadinessBreakdownUseCase()) {
                is Resource.Success -> _state.value = ReadinessBreakdownUiState.Success(r.data)
                is Resource.Error   -> _state.value = ReadinessBreakdownUiState.Error(r.message)
                is Resource.Loading -> Unit
            }
        }
    }
}
