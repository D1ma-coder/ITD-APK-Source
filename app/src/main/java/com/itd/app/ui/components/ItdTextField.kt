package com.itd.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itd.app.R

@Composable
fun ItdTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
    contentType: ContentType? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    isError: Boolean = error != null,
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Column(modifier) {
        OutlinedTextField(
            value,
            onValueChange,
            Modifier.fillMaxWidth().then(
                if (contentType != null) Modifier.semantics { this.contentType = contentType } else Modifier
            ),
            label = { Text(label) },
            trailingIcon = if (password) {
                {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            painterResource(if (passwordVisible) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                            if (passwordVisible) "Скрыть пароль" else "Показать пароль",
                            Modifier.size(22.dp),
                            tint = colors.onSurfaceVariant,
                        )
                    }
                }
            } else {
                trailingIcon
            },
            isError = isError,
            visualTransformation = if (password && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(onDone = { onDone() }, onNext = { onDone() }),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                errorContainerColor = Color.Transparent,
                focusedBorderColor = colors.primary,
                unfocusedBorderColor = colors.outline,
                errorBorderColor = colors.error,
            ),
        )
        AnimatedVisibility(
            isError && !error.isNullOrBlank(),
            enter = fadeIn(tween(200)) + expandVertically(tween(200)),
        ) {
            Text(
                error ?: "",
                Modifier.padding(start = 16.dp, top = 4.dp),
                color = colors.error,
                fontSize = 12.sp,
            )
        }
    }
}
