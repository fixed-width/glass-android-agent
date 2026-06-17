package com.fixedwidth.glassfixture

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var name by remember { mutableStateOf("") }
            var clicks by remember { mutableStateOf(0) }
            Column(Modifier.padding(24.dp)) {
                TextField(value = name, onValueChange = { name = it },
                    modifier = Modifier.semantics { contentDescription = "Name" })
                Button(onClick = { clicks++ }) { Text("Save") }
                Text("Clicked $clicks", Modifier.semantics { contentDescription = "Counter" })
                var scale by remember { mutableFloatStateOf(1f) }
                Box(
                    Modifier
                        .size(200.dp)
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .background(Color(0xFF3366CC))
                        .semantics { contentDescription = "PinchBox" }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, _, zoom, _ ->
                                scale = (scale * zoom).coerceIn(0.5f, 5f)
                                Log.i("fixture", "zoom: %.3f".format(scale))
                            }
                        },
                )
            }
        }
    }
}
