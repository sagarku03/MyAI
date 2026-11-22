package com.jarvisai.assistant.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Paint
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jarvisai.assistant.data.render.ChemNotation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class StemKind(val prefKey: String, val label: String, val lang: String) {
    CHEM("chem", "CHEMISTRY", "chem"),
    SMILES("smiles", "MOLECULE", "smiles"),
    FUNCTION_PLOT("functionplot", "FUNCTION PLOT", "functionplot"),
    JSXGRAPH("jsxgraph", "GEOMETRY", "jsxgraph"),
    MATTER("matter", "PHYSICS SIM", "matter"),
    TIKZ("tikz", "TIKZ DIAGRAM", "tikz");

    companion object {
        fun fromLang(lang: String): StemKind? = when (lang.trim().lowercase()) {
            "chem", "mhchem", "ce" -> CHEM
            "smiles" -> SMILES
            "functionplot", "function-plot", "function_plot", "plot" -> FUNCTION_PLOT
            "jsxgraph", "geometry" -> JSXGRAPH
            "matter", "matterjs", "matter-js", "physics" -> MATTER
            "tikz", "circuitikz", "tikzpicture" -> TIKZ
            else -> null
        }
    }
}

object StemRenderPreferences {
    private const val PREFS = "agentra_stem_render"
    private val _enabledKinds = MutableStateFlow(StemKind.entries.toSet())
    val enabledKinds: StateFlow<Set<StemKind>> = _enabledKinds.asStateFlow()

    @Volatile
    private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val enabled = StemKind.entries.filter { prefs.getBoolean("kind_${it.prefKey}", true) }.toSet()
        _enabledKinds.value = enabled
        loaded = true
    }

    fun setEnabled(context: Context, kind: StemKind, enabled: Boolean) {
        init(context)
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putBoolean("kind_${kind.prefKey}", enabled).apply()
        val next = _enabledKinds.value.toMutableSet()
        if (enabled) next.add(kind) else next.remove(kind)
        _enabledKinds.value = next
    }
}

@Composable
fun StemBlockView(
    kind: StemKind,
    code: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { StemRenderPreferences.init(context) }
    val enabledKinds by StemRenderPreferences.enabledKinds.collectAsState()

    if (kind !in enabledKinds) {
        CodeBlock(code = code, language = kind.lang, modifier = modifier)
        return
    }

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Render, 1 = Source
    var isCopied by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }

    LaunchedEffect(isCopied) {
        if (isCopied) {
            delay(2000)
            isCopied = false
        }
    }

    val copyCode = {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(kind.label, code))
        isCopied = true
        Toast.makeText(context, "${kind.label} source copied", Toast.LENGTH_SHORT).show()
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .testTag("stem_block_${kind.prefKey}"),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF0D1422),
        border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF131D31))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Science,
                    contentDescription = kind.label,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = kind.label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF38BDF8),
                    letterSpacing = 0.6.sp
                )
                Spacer(Modifier.weight(1f))
                StemTabPill("Render", selectedTab == 0) { selectedTab = 0 }
                Spacer(Modifier.width(4.dp))
                StemTabPill("Code", selectedTab == 1) { selectedTab = 1 }
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = copyCode, modifier = Modifier.size(30.dp)) {
                    Icon(
                        imageVector = if (isCopied) Icons.Default.Check else Icons.Outlined.ContentCopy,
                        contentDescription = "Copy STEM source",
                        tint = if (isCopied) Color(0xFF10B981) else Color(0xFF94A3B8),
                        modifier = Modifier.size(15.dp)
                    )
                }
                IconButton(onClick = { isFullscreen = true }, modifier = Modifier.size(30.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.OpenInFull,
                        contentDescription = "Fullscreen diagram",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            if (selectedTab == 1) {
                CodeBlock(code = code, language = kind.lang, modifier = Modifier.padding(8.dp))
            } else {
                StemDiagramBody(kind = kind, code = code, heightDp = 210)
            }
        }
    }

    if (isFullscreen) {
        Dialog(
            onDismissRequest = { isFullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .padding(12.dp),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF0A0F1A),
                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(kind.label, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        IconButton(onClick = { isFullscreen = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close fullscreen", tint = Color.White)
                        }
                    }
                    StemDiagramBody(kind = kind, code = code, heightDp = 360)
                }
            }
        }
    }
}

@Composable
private fun StemTabPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) Color(0xFF38BDF8).copy(alpha = 0.22f) else Color.Transparent,
        border = if (selected) BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f)) else null
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Color.White else Color(0xFF94A3B8),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun StemDiagramBody(kind: StemKind, code: String, heightDp: Int) {
    when (kind) {
        StemKind.CHEM -> {
            val latex = remember(code) { ChemNotation.toLatex(code) }
            Box(modifier = Modifier.padding(12.dp)) {
                NativeMathView(latex = latex, isDisplay = true, textColor = Color(0xFF38BDF8))
            }
        }
        StemKind.SMILES -> {
            SmilesCanvasView(smiles = code, heightDp = heightDp)
        }
        StemKind.FUNCTION_PLOT -> {
            FunctionPlotCanvasView(spec = code, heightDp = heightDp)
        }
        StemKind.JSXGRAPH, StemKind.MATTER, StemKind.TIKZ -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${kind.label} Blueprint (Native Offline Mode)",
                    color = Color(0xFF93C5FD),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = code.trim(),
                    color = Color(0xFFE2E8F0),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun SmilesCanvasView(smiles: String, heightDp: Int) {
    val graph = remember(smiles) { ChemNotation.parseSmiles(smiles) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SMILES: ${graph.smiles}",
                color = Color(0xFF94A3B8),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.5.sp
            )
            if (graph.formula.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1E293B)
                ) {
                    Text(
                        text = graph.formula,
                        color = Color(0xFF10B981),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(heightDp.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF070C16))
        ) {
            if (graph.atoms.isEmpty()) return@Canvas
            val minX = graph.atoms.minOf { it.x }
            val maxX = graph.atoms.maxOf { it.x }
            val minY = graph.atoms.minOf { it.y }
            val maxY = graph.atoms.maxOf { it.y }
            val spanX = max(maxX - minX, 1f)
            val spanY = max(maxY - minY, 1f)
            val pad = 42f
            val scale = min((size.width - pad * 2) / spanX, (size.height - pad * 2) / spanY)
            val offsetX = (size.width - spanX * scale) / 2f - minX * scale
            val offsetY = (size.height - spanY * scale) / 2f - minY * scale

            fun pos(idx: Int): Offset {
                val a = graph.atoms[idx]
                return Offset(a.x * scale + offsetX, a.y * scale + offsetY)
            }

            // Draw bonds
            graph.bonds.forEach { bond ->
                val p1 = pos(bond.from)
                val p2 = pos(bond.to)
                val dx = p2.x - p1.x
                val dy = p2.y - p1.y
                val len = max(hypot(dx, dy), 1f)
                val nx = -dy / len * 4.5f
                val ny = dx / len * 4.5f

                when (bond.order) {
                    2 -> {
                        drawLine(Color(0xFF38BDF8), Offset(p1.x + nx, p1.y + ny), Offset(p2.x + nx, p2.y + ny), strokeWidth = 3f, cap = StrokeCap.Round)
                        drawLine(Color(0xFF38BDF8), Offset(p1.x - nx, p1.y - ny), Offset(p2.x - nx, p2.y - ny), strokeWidth = 3f, cap = StrokeCap.Round)
                    }
                    3 -> {
                        drawLine(Color(0xFFF59E0B), p1, p2, strokeWidth = 3f, cap = StrokeCap.Round)
                        drawLine(Color(0xFFF59E0B), Offset(p1.x + nx * 1.4f, p1.y + ny * 1.4f), Offset(p2.x + nx * 1.4f, p2.y + ny * 1.4f), strokeWidth = 2.5f)
                        drawLine(Color(0xFFF59E0B), Offset(p1.x - nx * 1.4f, p1.y - ny * 1.4f), Offset(p2.x - nx * 1.4f, p2.y - ny * 1.4f), strokeWidth = 2.5f)
                    }
                    else -> {
                        drawLine(Color(0xFF94A3B8), p1, p2, strokeWidth = 3.2f, cap = StrokeCap.Round)
                    }
                }
            }

            // Draw atoms
            val textPaint = Paint().apply {
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                textSize = 26f
                isFakeBoldText = true
            }
            graph.atoms.forEach { atom ->
                val p = pos(atom.index)
                val elemColor = when (atom.symbol) {
                    "O" -> Color(0xFFEF4444)
                    "N" -> Color(0xFF60A5FA)
                    "S" -> Color(0xFFFACC15)
                    "Cl", "F", "Br" -> Color(0xFF10B981)
                    "P" -> Color(0xFFF97316)
                    else -> Color(0xFFE2E8F0)
                }
                drawCircle(Color(0xFF0D1422), radius = 16f, center = p)
                drawCircle(elemColor.copy(alpha = 0.25f), radius = 16f, center = p)
                textPaint.color = android.graphics.Color.argb(
                    (elemColor.alpha * 255).toInt(),
                    (elemColor.red * 255).toInt(),
                    (elemColor.green * 255).toInt(),
                    (elemColor.blue * 255).toInt()
                )
                drawContext.canvas.nativeCanvas.drawText(atom.symbol, p.x, p.y + 9f, textPaint)
            }
        }
    }
}

@Composable
private fun FunctionPlotCanvasView(spec: String, heightDp: Int) {
    val parsed = remember(spec) { parsePlotSpec(spec) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "y = ${parsed.expressions.joinToString(" , ")}",
            color = Color(0xFF93C5FD),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(heightDp.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF070C16))
        ) {
            val xMin = parsed.xMin
            val xMax = parsed.xMax
            val yMin = parsed.yMin
            val yMax = parsed.yMax
            fun sx(x: Double): Float = ((x - xMin) / (xMax - xMin) * size.width).toFloat()
            fun sy(y: Double): Float = (size.height - (y - yMin) / (yMax - yMin) * size.height).toFloat()

            // Grid & Axes
            val zeroX = sx(0.0)
            val zeroY = sy(0.0)
            if (zeroX in 0f..size.width) {
                drawLine(Color(0xFF334155), Offset(zeroX, 0f), Offset(zeroX, size.height), strokeWidth = 2f)
            }
            if (zeroY in 0f..size.height) {
                drawLine(Color(0xFF334155), Offset(0f, zeroY), Offset(size.width, zeroY), strokeWidth = 2f)
            }

            val colors = listOf(Color(0xFF38BDF8), Color(0xFF10B981), Color(0xFFF59E0B), Color(0xFFF472B6))
            parsed.expressions.forEachIndexed { idx, expr ->
                val path = Path()
                var started = false
                val samples = 240
                for (i in 0..samples) {
                    val x = xMin + (xMax - xMin) * i / samples.toDouble()
                    val y = evalSimpleExpr(expr, x)
                    if (y.isFinite() && abs(y) < 1e6) {
                        val px = sx(x)
                        val py = sy(y)
                        if (!started) {
                            path.moveTo(px, py)
                            started = true
                        } else {
                            path.lineTo(px, py)
                        }
                    } else {
                        started = false
                    }
                }
                drawPath(path, color = colors[idx % colors.size], style = Stroke(width = 3.5f))
            }
        }
    }
}

private data class PlotSpec(
    val expressions: List<String>,
    val xMin: Double = -6.0,
    val xMax: Double = 6.0,
    val yMin: Double = -4.0,
    val yMax: Double = 4.0
)

private fun parsePlotSpec(raw: String): PlotSpec {
    val trimmed = raw.trim()
    val json = runCatching { JSONObject(trimmed) }.getOrNull()
    if (json != null) {
        val data = json.optJSONArray("data")
        val exprs = mutableListOf<String>()
        if (data != null) {
            for (i in 0 until data.length()) {
                val fn = data.optJSONObject(i)?.optString("fn").orEmpty().trim()
                if (fn.isNotBlank()) exprs.add(fn)
            }
        }
        val xDom = json.optJSONObject("xAxis")?.optJSONArray("domain")
        val yDom = json.optJSONObject("yAxis")?.optJSONArray("domain")
        return PlotSpec(
            expressions = exprs.ifEmpty { listOf("sin(x)") },
            xMin = xDom?.optDouble(0, -6.0) ?: -6.0,
            xMax = xDom?.optDouble(1, 6.0) ?: 6.0,
            yMin = yDom?.optDouble(0, -4.0) ?: -4.0,
            yMax = yDom?.optDouble(1, 4.0) ?: 4.0
        )
    }
    val lines = trimmed.lines().map { it.trim().removePrefix("y=").removePrefix("y =").trim() }.filter { it.isNotEmpty() }
    return PlotSpec(expressions = lines.ifEmpty { listOf("sin(x)") })
}

/** Recursive-descent evaluator supporting +, -, *, /, ^, x, sin, cos, exp, ln, sqrt, abs. */
private fun evalSimpleExpr(expr: String, xVal: Double): Double =
    runCatching { SimpleExprParser(expr.replace(" ", "").lowercase(), xVal).parseExpr() }.getOrDefault(Double.NaN)

private class SimpleExprParser(private val s: String, private val xVal: Double) {
    private var pos = 0

    private fun peek(): Char = if (pos < s.length) s[pos] else '\u0000'

    fun parseExpr(): Double {
        var left = parseTerm()
        while (peek() == '+' || peek() == '-') {
            val op = s[pos++]
            val right = parseTerm()
            left = if (op == '+') left + right else left - right
        }
        return left
    }

    private fun parseTerm(): Double {
        var left = parsePower()
        while (peek() == '*' || peek() == '/') {
            val op = s[pos++]
            val right = parsePower()
            left = if (op == '*') left * right else left / right
        }
        return left
    }

    private fun parsePower(): Double {
        val base = parseUnary()
        return if (peek() == '^') {
            pos++
            base.pow(parsePower())
        } else {
            base
        }
    }

    private fun parseUnary(): Double = when (peek()) {
        '+' -> { pos++; parseUnary() }
        '-' -> { pos++; -parseUnary() }
        else -> parsePrimary()
    }

    private fun parsePrimary(): Double {
        if (peek() == '(') {
            pos++
            val v = parseExpr()
            if (peek() == ')') pos++
            return v
        }
        if (peek().isDigit() || peek() == '.') {
            val start = pos
            while (peek().isDigit() || peek() == '.') pos++
            return s.substring(start, pos).toDoubleOrNull() ?: Double.NaN
        }
        if (peek().isLetter()) {
            val start = pos
            while (peek().isLetter()) pos++
            val name = s.substring(start, pos)
            if (name == "x") return xVal
            if (name == "pi") return Math.PI
            if (name == "e") return Math.E
            if (peek() == '(') {
                pos++
                val arg = parseExpr()
                if (peek() == ')') pos++
                return when (name) {
                    "sin" -> sin(arg)
                    "cos" -> cos(arg)
                    "tan" -> kotlin.math.tan(arg)
                    "exp" -> exp(arg)
                    "ln", "log" -> ln(arg)
                    "sqrt" -> sqrt(arg)
                    "abs" -> abs(arg)
                    else -> Double.NaN
                }
            }
        }
        return Double.NaN
    }
}
