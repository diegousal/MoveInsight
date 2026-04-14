package com.moveinsight.presentation.components


import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.moveinsight.presentation.theme.DividerColor
import com.moveinsight.presentation.theme.TextSecondary

@Composable
fun NeuroSquatTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    isPassword: Boolean = false,
    isError: Boolean = false,
    errorMessage: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    enabled: Boolean = true,
    singleLine: Boolean = true
) {
    var passwordVisible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value         = value,
        onValueChange = onValueChange,
        label         = { Text(label) },
        leadingIcon   = leadingIcon?.let {
            { Icon(imageVector = it, contentDescription = null, tint = TextSecondary) }
        },
        trailingIcon  = if (isPassword) {
            {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Filled.VisibilityOff
                        else Icons.Filled.Visibility,
                        contentDescription = if (passwordVisible) "Ocultar contraseña"
                        else "Mostrar contraseña",
                        tint = TextSecondary
                    )
                }
            }
        } else null,
        visualTransformation = if (isPassword && !passwordVisible)
            PasswordVisualTransformation() else VisualTransformation.None,
        isError        = isError,
        supportingText = if (isError && !errorMessage.isNullOrBlank()) {
            { Text(errorMessage, color = MaterialTheme.colorScheme.error) }
        } else null,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        enabled         = enabled,
        singleLine      = singleLine,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = DividerColor,
            focusedLabelColor    = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor  = TextSecondary,
            cursorColor          = MaterialTheme.colorScheme.primary,
        ),
        modifier = modifier.fillMaxWidth()
    )
}