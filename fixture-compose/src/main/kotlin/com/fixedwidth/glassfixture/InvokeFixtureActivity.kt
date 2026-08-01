package com.fixedwidth.glassfixture

import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button as ComposeButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Classic View toolkit. Every target a native-invoke test needs, and a counter so actuation is
 * read rather than inferred: an enabled button, a disabled one, a checkbox to toggle, and a
 * list long enough that its later rows sit below the fold.
 */
class InvokeViewFixtureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        var hits = 0
        val counter = TextView(this).apply { text = "hits=0"; contentDescription = "Counter" }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(counter)
        col.addView(Button(this).apply {
            text = "Save"
            contentDescription = "SaveBtn"
            setOnClickListener { hits += 1; counter.text = "hits=$hits" }
        })
        col.addView(Button(this).apply {
            text = "Gated"
            contentDescription = "GatedBtn"
            isEnabled = false
            setOnClickListener { hits += 100; counter.text = "hits=$hits" }
        })
        col.addView(CheckBox(this).apply { text = "Agree"; contentDescription = "AgreeBox" })
        repeat(300) { i ->
            col.addView(Button(this).apply {
                text = "row $i"
                contentDescription = "Row$i"
                setOnClickListener { hits += 1; counter.text = "hits=$hits row=$i" }
            })
        }
        setContentView(ScrollView(this).apply { addView(col) })
    }
}

/**
 * Compose. The label and the clickable node are different nodes here, which is the case the
 * host's ancestor climb exists for.
 */
class InvokeComposeFixtureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var hits by remember { mutableIntStateOf(0) }
            Column(Modifier.padding(24.dp)) {
                ComposeButton(onClick = { hits += 1 }) { Text("Save") }
                Text("hits=$hits", Modifier.semantics { contentDescription = "Counter" })
            }
        }
    }
}
