package com.accu.ui.audio

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val EEL_KEYWORDS = setOf("function", "local", "global", "instance", "this", "while", "loop", "if", "else", "slider", "in_pin", "out_pin", "desc", "gmem")
val EEL_FUNCTIONS = setOf("sin", "cos", "tan", "atan", "atan2", "sqrt", "exp", "log", "log10", "abs", "floor", "ceil", "min", "max", "sign", "pow", "rand")
val EEL_SECTIONS = setOf("@init", "@slider", "@sample", "@block", "@gfx", "@serialize")

fun buildAnnotatedEEL(text: String): AnnotatedString {
    return buildAnnotatedString {
        val lines = text.split("\n")
        lines.forEachIndexed { lineIdx, line ->
            var i = 0
            while (i < line.length) {
                when {
                    line[i] == '/' && i + 1 < line.length && line[i + 1] == '/' -> {
                        withStyle(SpanStyle(color = Color(0xFF6A9955))) { append(line.substring(i)) }
                        i = line.length
                    }
                    line[i] == '"' -> {
                        val end = line.indexOf('"', i + 1).takeIf { it != -1 } ?: line.length - 1
                        withStyle(SpanStyle(color = Color(0xFFCE9178))) { append(line.substring(i, end + 1)) }
                        i = end + 1
                    }
                    line[i].isLetter() || line[i] == '@' || line[i] == '_' -> {
                        var end = i
                        while (end < line.length && (line[end].isLetterOrDigit() || line[end] == '_' || (end == i && line[end] == '@'))) end++
                        val word = line.substring(i, end)
                        val color = when {
                            EEL_SECTIONS.contains(word) -> Color(0xFFC586C0)
                            EEL_KEYWORDS.contains(word) -> Color(0xFF569CD6)
                            EEL_FUNCTIONS.contains(word) -> Color(0xFFDCDCAA)
                            word.all { it.isUpperCase() || it == '_' } && word.length > 1 -> Color(0xFF4FC1FF)
                            else -> Color(0xFFD4D4D4)
                        }
                        withStyle(SpanStyle(color = color)) { append(word) }
                        i = end
                    }
                    line[i].isDigit() -> {
                        var end = i
                        while (end < line.length && (line[end].isDigit() || line[end] == '.' || line[end] == 'x' || line[end] in 'a'..'f' || line[end] in 'A'..'F')) end++
                        withStyle(SpanStyle(color = Color(0xFFB5CEA8))) { append(line.substring(i, end)) }
                        i = end
                    }
                    else -> { append(line[i]); i++ }
                }
            }
            if (lineIdx < lines.lastIndex) append("\n")
        }
    }
}

val SAMPLE_LIVEPROG = """desc: Bass Boost + Treble Enhancer

slider1:bass_gain=6<0,20,0.5>Bass Gain (dB)
slider2:treble_gain=4<0,20,0.5>Treble Gain (dB)
slider3:bass_freq=120<20,500,1>Bass Frequency (Hz)
slider4:treble_freq=8000<2000,20000,100>Treble Frequency (Hz)

@init
  // Initialize filter states
  bass_x1 = 0; bass_x2 = 0;
  bass_y1 = 0; bass_y2 = 0;
  treble_x1 = 0; treble_x2 = 0;
  treble_y1 = 0; treble_y2 = 0;

@slider
  // Recalculate coefficients on slider change
  bass_w0  = 2 * ${'$'}pi * bass_freq / srate;
  treble_w0 = 2 * ${'$'}pi * treble_freq / srate;
  
  // Low shelf for bass
  bass_A = pow(10, bass_gain / 40);
  bass_b0 = bass_A * ((bass_A + 1) - (bass_A - 1)*cos(bass_w0));
  
  // High shelf for treble
  treble_A = pow(10, treble_gain / 40);

@sample
  // Apply bass shelf
  spl0 = spl0 * bass_b0 + bass_y1;
  spl1 = spl1 * bass_b0 + bass_y1;
  
  // Output
  spl0 = min(max(spl0, -1), 1);
  spl1 = min(max(spl1, -1), 1);
""".trimIndent()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveprogEditorScreen(onBack: () -> Unit) {
    var code by remember { mutableStateOf(TextFieldValue(SAMPLE_LIVEPROG)) }
    var isRunning by remember { mutableStateOf(false) }
    var showParams by remember { mutableStateOf(false) }
    var fontSize by remember { mutableStateOf(12) }
    val undoStack = remember { mutableStateListOf<TextFieldValue>() }
    val redoStack = remember { mutableStateListOf<TextFieldValue>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Liveprog Editor", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = { if (undoStack.isNotEmpty()) { redoStack.add(code); code = undoStack.removeAt(undoStack.lastIndex) } }) {
                        Icon(Icons.Default.Undo, "Undo")
                    }
                    IconButton(onClick = { if (redoStack.isNotEmpty()) { undoStack.add(code); code = redoStack.removeAt(redoStack.lastIndex) } }) {
                        Icon(Icons.Default.Redo, "Redo")
                    }
                    IconButton(onClick = { showParams = !showParams }) { Icon(Icons.Default.Tune, "Parameters") }
                    IconButton(onClick = { isRunning = !isRunning }) {
                        Icon(if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow, if (isRunning) "Stop" else "Run")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf("@init", "@slider", "@sample", "@block", "@gfx").forEach { section ->
                    TextButton(onClick = {
                        val idx = code.text.indexOf(section)
                        if (idx != -1) code = code.copy(selection = TextRange(idx))
                    }) {
                        Text(section, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { fontSize = (fontSize - 1).coerceAtLeast(8) }, modifier = Modifier.size(28.dp)) {
                    Text("A-", fontSize = 10.sp)
                }
                Text("$fontSize", fontSize = 11.sp)
                IconButton(onClick = { fontSize = (fontSize + 1).coerceAtMost(24) }, modifier = Modifier.size(28.dp)) {
                    Text("A+", fontSize = 10.sp)
                }
            }

            if (isRunning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                    Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.RadioButtonChecked, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Script running — audio processing active", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF1E1E1E))
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .width(36.dp)
                            .fillMaxHeight()
                            .background(Color(0xFF252526))
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.End,
                    ) {
                        val lineCount = code.text.count { it == '\n' } + 1
                        repeat(lineCount) { line ->
                            Text(
                                "${line + 1}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = fontSize.sp,
                                color = Color(0xFF858585),
                                modifier = Modifier.padding(end = 6.dp),
                            )
                        }
                    }
                    BasicTextField(
                        value = code,
                        onValueChange = { newVal ->
                            if (newVal.text != code.text) {
                                undoStack.add(code)
                                if (undoStack.size > 100) undoStack.removeAt(0)
                                redoStack.clear()
                            }
                            code = newVal
                        },
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = fontSize.sp,
                            color = Color(0xFFD4D4D4),
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .horizontalScroll(rememberScrollState())
                            .verticalScroll(rememberScrollState())
                            .padding(8.dp),
                        visualTransformation = { input ->
                            TransformedText(buildAnnotatedEEL(input.text), OffsetMapping.Identity)
                        },
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(Color(0xFF528BFF)),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().background(Color(0xFF1E1E1E)).horizontalScroll(rememberScrollState()).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf("(", ")", "[", "]", "{", "}", ";", "=", "+", "-", "*", "/", "&&", "||", "!").forEach { sym ->
                    Surface(
                        modifier = Modifier.clickable {
                            val text = code.text
                            val sel = code.selection.start
                            val newText = text.substring(0, sel) + sym + text.substring(sel)
                            code = code.copy(text = newText, selection = TextRange(sel + sym.length))
                        },
                        color = Color(0xFF3C3C3C),
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text(sym, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
