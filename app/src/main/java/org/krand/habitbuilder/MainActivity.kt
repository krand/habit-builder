package org.krand.habitbuilder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HabitBuilderTheme {
                HabitBuilderApp()
            }
        }
    }
}

@Composable
private fun HabitBuilderTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) HabitColors.dark else HabitColors.light
    MaterialTheme(colorScheme = scheme, content = content)
}
