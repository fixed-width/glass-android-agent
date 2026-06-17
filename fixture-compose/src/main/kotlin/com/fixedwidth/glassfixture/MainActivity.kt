package com.fixedwidth.glassfixture

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
            }
        }
    }
}
