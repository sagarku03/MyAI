package com.jarvisai.assistant.data.render

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Translates mhchem-style chemistry expressions (`\ce{...}` or plain chemistry equations)
 * into clean LaTeX for native JLatexMath rendering, and parses simple SMILES strings into
 * a 2D skeletal graph for native Canvas visualization.
 */
object ChemNotation {

    private val ARROW_REPLACEMENTS = listOf(
        "<=>" to "\\rightleftharpoons",
        "<->" to "\\leftrightarrow",
        "->" to "\\rightarrow",
        "<-" to "\\leftarrow",
        "=>" to "\\Rightarrow",
        "⇌" to "\\rightleftharpoons",
        "→" to "\\rightarrow",
        "←" to "\\leftarrow",
        "↔" to "\\leftrightarrow"
    )

    private val STATE_REGEX = Regex("""\((aq|s|l|g)\)""")
    private val CHARGE_REGEX = Regex("""([A-Za-z0-9\)\]])\^(\d*[+-])""")
    private val SUBSCRIPT_REGEX = Regex("""([A-Za-z\)\]])(\d+)""")

    /**
     * Converts a chemical equation (e.g. `2 H2(g) + O2(g) -> 2 H2O(l)` or `\ce{SO4^2-}`)
     * into standard LaTeX renderable by JLatexMathDrawable.
     */
    fun toLatex(raw: String): String {
        var expr = raw.trim()
        if (expr.startsWith("\\ce{") && expr.endsWith("}")) {
            expr = expr.substring(4, expr.length - 1).trim()
        }
        val tokens = expr.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val out = StringBuilder()
        for (tok in tokens) {
            val matchedArrow = ARROW_REPLACEMENTS.firstOrNull { (k, _) -> tok == k }
            when {
                matchedArrow != null -> out.append(" ").append(matchedArrow.second).append(" ")
                tok == "+" -> out.append(" + ")
                tok == "^" -> out.append("\\uparrow ")
                tok == "v" -> out.append("\\downarrow ")
                tok.all { it.isDigit() } -> out.append(tok).append("\\,")
                else -> out.append(formatFormulaToken(tok)).append(" ")
            }
        }
        return out.toString().trim()
    }

    private fun formatFormulaToken(token: String): String {
        var s = token
        // Preserve trailing charge like Fe^3+ or SO4^2-
        s = CHARGE_REGEX.replace(s) { m -> "${m.groupValues[1]}^{${m.groupValues[2]}}" }
        // Subscripts after element symbols or closing brackets
        s = SUBSCRIPT_REGEX.replace(s) { m -> "${m.groupValues[1]}_{${m.groupValues[2]}}" }
        // Phase states (aq), (s), (l), (g)
        s = STATE_REGEX.replace(s) { m -> "\\text{(${m.groupValues[1]})}" }
        // Upright element letters
        return "\\mathrm{$s}"
            .replace("\\mathrm{\\text{", "\\text{")
    }

    data class AtomNode(
        val index: Int,
        val symbol: String,
        val x: Float,
        val y: Float,
        val aromatic: Boolean = false
    )

    data class BondEdge(
        val from: Int,
        val to: Int,
        val order: Int = 1 // 1=single, 2=double, 3=triple
    )

    data class SmilesGraph(
        val smiles: String,
        val formula: String,
        val atoms: List<AtomNode>,
        val bonds: List<BondEdge>
    )

    /**
     * Lightweight offline SMILES parser that builds a clean 2D skeletal graph for organic molecules
     * (chains, branches `(...)`, rings `1..9`, double `=` / triple `#` bonds, aromatic rings).
     */
    fun parseSmiles(rawSmiles: String): SmilesGraph {
        val smiles = rawSmiles.lines().firstOrNull { it.trim().isNotEmpty() }?.trim().orEmpty()
        val atoms = mutableListOf<AtomNode>()
        val bonds = mutableListOf<BondEdge>()
        val branchStack = ArrayDeque<Int>()
        val ringOpenings = mutableMapOf<Char, Pair<Int, Int>>() // digit -> (atomIdx, bondOrder)

        var prevAtom = -1
        var pendingOrder = 1
        var i = 0
        var angleRad = 0.0
        val step = 1.0f

        while (i < smiles.length) {
            val ch = smiles[i]
            when {
                ch == '(' -> {
                    if (prevAtom >= 0) branchStack.addLast(prevAtom)
                    angleRad += PI / 3.0
                    i++
                }
                ch == ')' -> {
                    if (branchStack.isNotEmpty()) prevAtom = branchStack.removeLast()
                    angleRad -= PI / 3.0
                    i++
                }
                ch == '=' -> {
                    pendingOrder = 2
                    i++
                }
                ch == '#' -> {
                    pendingOrder = 3
                    i++
                }
                ch == '-' || ch == '/' || ch == '\\' || ch == ':' -> {
                    pendingOrder = 1
                    i++
                }
                ch.isDigit() -> {
                    val existing = ringOpenings.remove(ch)
                    if (existing != null && prevAtom >= 0) {
                        bonds.add(BondEdge(existing.first, prevAtom, maxOf(existing.second, pendingOrder)))
                    } else if (prevAtom >= 0) {
                        ringOpenings[ch] = prevAtom to pendingOrder
                    }
                    pendingOrder = 1
                    i++
                }
                ch == '[' -> {
                    val end = smiles.indexOf(']', i + 1)
                    val sym = if (end > i) smiles.substring(i + 1, end) else ch.toString()
                    val idx = addAtom(atoms, bonds, prevAtom, sym, pendingOrder, false, angleRad, step)
                    prevAtom = idx
                    pendingOrder = 1
                    angleRad += if (idx % 2 == 0) PI / 3.0 else -PI / 3.0
                    i = if (end > i) end + 1 else i + 1
                }
                ch.isLetter() -> {
                    val aromatic = ch.isLowerCase()
                    val sym = if (!aromatic && i + 1 < smiles.length && smiles[i + 1].isLowerCase() &&
                        smiles.substring(i, i + 2) in TWO_LETTER_ELEMENTS
                    ) {
                        val two = smiles.substring(i, i + 2)
                        i += 2
                        two
                    } else {
                        i++
                        ch.uppercaseChar().toString()
                    }
                    val idx = addAtom(atoms, bonds, prevAtom, sym, pendingOrder, aromatic, angleRad, step)
                    prevAtom = idx
                    pendingOrder = 1
                    angleRad += if (aromatic) PI / 3.0 else if (idx % 2 == 0) PI / 3.0 else -PI / 3.0
                }
                else -> i++
            }
        }

        // Compute compact empirical formula
        val counts = linkedMapOf<String, Int>()
        atoms.forEach { node ->
            val elem = node.symbol.takeWhile { it.isLetter() }.ifBlank { node.symbol }
            counts[elem] = (counts[elem] ?: 0) + 1
        }
        val formula = counts.entries.joinToString("") { (el, c) -> if (c == 1) el else "$el$c" }
        return SmilesGraph(smiles = smiles, formula = formula, atoms = atoms, bonds = bonds)
    }

    private fun addAtom(
        atoms: MutableList<AtomNode>,
        bonds: MutableList<BondEdge>,
        prevAtom: Int,
        symbol: String,
        bondOrder: Int,
        aromatic: Boolean,
        angleRad: Double,
        step: Float
    ): Int {
        val (nx, ny) = if (prevAtom < 0) {
            0f to 0f
        } else {
            val p = atoms[prevAtom]
            (p.x + (cos(angleRad) * step).toFloat()) to (p.y + (sin(angleRad) * step).toFloat())
        }
        val idx = atoms.size
        atoms.add(AtomNode(idx, symbol, nx, ny, aromatic))
        if (prevAtom >= 0) {
            bonds.add(BondEdge(prevAtom, idx, bondOrder))
        }
        return idx
    }

    private val TWO_LETTER_ELEMENTS = setOf(
        "Cl", "Br", "Na", "Mg", "Al", "Si", "Ca", "Fe", "Cu", "Zn", "Ag", "Au", "Hg", "Pb", "Li", "Be", "Ne", "Ar", "He"
    )
}
