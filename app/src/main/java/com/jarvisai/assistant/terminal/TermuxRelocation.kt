package com.jarvisai.assistant.terminal

import android.content.Context
import android.system.Os
import android.system.OsConstants
import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit

/**
 * Option B Native Termux Engine (No PRoot):
 * Rewrites Termux packages built for `com.termux` (or `ai.antigem` / `ai.antidev`) byte-for-byte
 * to `ai.agentra` (exact same 10-byte ASCII length) across ELF binaries, shared libraries, shebangs,
 * config files, and symlinks inside `files/usr` and `files/home` (`pathsOnly = true`).
 *
 * Also installs permanent APT `DPkg::Pre-Install-Pkgs` hooks, a `dpkg` wrapper with `dpkg-divert`,
 * and Agentra's compiled `am.apk` so future `pkg install` / `apt install` / `dpkg -i` packages
 * work natively on `ai.agentra`.
 */
object TermuxRelocation {
    private const val TAG = "TermuxRelocation"
    const val TERMUX_PACKAGE = "com.termux"

    /** All 10-byte legacy/upstream Termux package names we can relocate byte-for-byte into `ai.agentra`. */
    private val SOURCE_PACKAGES = listOf("com.termux", "ai.antigem", "ai.antidev")

    /** Zip-based & DEX formats keep CRCs/checksums and must never be byte-patched blindly. */
    private val SKIPPED_EXTENSIONS = setOf("apk", "jar", "zip", "aar", "whl", "dex", "jmod")

    /**
     * Suffixes immediately following `com.termux` inside Java/X11 class/action identifiers that
     * must stay unchanged (e.g. `com.termux.termuxam.Am`, `com.termux.x11.Loader`).
     */
    private val KEPT_SUFFIXES: List<ByteArray> = listOf(".termuxam", ".x11.").map { it.toByteArray(Charsets.US_ASCII) }
    private val LOOKAHEAD = KEPT_SUFFIXES.maxOf { it.size }
    private const val CHUNK = 256 * 1024
    private const val SLASH = '/'.code.toByte()

    data class Stats(var files: Int = 0, var patchedFiles: Int = 0, var links: Int = 0)

    /** Returns `ai.agentra` (or any 10-byte non-`com.termux` package ID); null if length differs. */
    fun targetPackage(context: Context): String? =
        context.packageName.takeIf { it != TERMUX_PACKAGE && it.length == TERMUX_PACKAGE.length }

    /** Relocates an entire extracted tree (`usr/`) for this app's 10-byte package ID. */
    fun relocateIfNeeded(context: Context, root: File, onProgress: ((Int) -> Unit)? = null): Stats? {
        val pkg = targetPackage(context) ?: return null
        return relocateTree(root, pkg, onProgress = onProgress, pathsOnly = false)
    }

    /**
     * Rewrites every file and symlink under [root] from `com.termux` / `ai.antigem` / `ai.antidev` -> [newPackage].
     * With [pathsOnly], only occurrences immediately preceded by `/` (`/data/data/...`) are rewritten so
     * user source code under `$HOME` is untouched.
     */
    fun relocateTree(
        root: File,
        newPackage: String,
        onProgress: ((Int) -> Unit)? = null,
        pathsOnly: Boolean = false
    ): Stats {
        require(newPackage.length == TERMUX_PACKAGE.length) {
            "$newPackage must be exactly ${TERMUX_PACKAGE.length} bytes to match $TERMUX_PACKAGE"
        }
        val patterns = SOURCE_PACKAGES
            .filter { it != newPackage }
            .map { it.toByteArray(Charsets.US_ASCII) }
        val newBytes = newPackage.toByteArray(Charsets.US_ASCII)

        val stats = Stats()
        if (!root.exists() || patterns.isEmpty()) return stats

        val dirs = ArrayDeque<File>().apply { add(root) }
        while (dirs.isNotEmpty()) {
            val dir = dirs.removeLast()
            val children = if (dir.name == ".git") arrayOf(File(dir, "config")) else dir.listFiles() ?: continue
            for (f in children) {
                val mode = try { Os.lstat(f.absolutePath).st_mode } catch (_: Exception) { continue }
                when {
                    OsConstants.S_ISLNK(mode) -> {
                        if (relink(f, newPackage, pathsOnly)) stats.links++
                    }
                    OsConstants.S_ISDIR(mode) -> dirs.add(f)
                    OsConstants.S_ISREG(mode) -> {
                        stats.files++
                        var patchedAny = false
                        for (oldBytes in patterns) {
                            if (patchFile(f, mode, oldBytes, newBytes, pathsOnly)) {
                                patchedAny = true
                            }
                        }
                        if (patchedAny) stats.patchedFiles++
                        if (stats.files % 500 == 0) onProgress?.invoke(stats.files)
                    }
                }
            }
        }
        return stats
    }

    private fun relink(link: File, newPackage: String, pathsOnly: Boolean): Boolean {
        val target = try { Os.readlink(link.absolutePath) } catch (_: Exception) { return false }
        if (SOURCE_PACKAGES.none { it != newPackage && target.contains(it) }) return false
        val rewritten = String(rewrite(target.toByteArray(Charsets.UTF_8), newPackage, pathsOnly), Charsets.UTF_8)
        if (rewritten == target) return false
        return try {
            link.delete()
            Os.symlink(rewritten, link.absolutePath)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Relinking ${link.absolutePath} -> $target failed: ${e.message}")
            false
        }
    }

    private fun patchFile(
        file: File,
        mode: Int,
        old: ByteArray,
        new: ByteArray,
        pathsOnly: Boolean
    ): Boolean {
        if (file.extension.lowercase() in SKIPPED_EXTENSIONS) return false
        return try {
            val offsets = findAll(file, old, pathsOnly)
            if (offsets.isEmpty()) return false
            val perms = mode and 0xFFF
            val ownerWritable = (perms and 0x80) != 0
            if (!ownerWritable) Os.chmod(file.absolutePath, perms or 0x80)
            try {
                RandomAccessFile(file, "rw").use { raf ->
                    for (offset in offsets) {
                        raf.seek(offset)
                        raf.write(new)
                    }
                }
            } finally {
                if (!ownerWritable) try { Os.chmod(file.absolutePath, perms) } catch (_: Exception) {}
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Rewriting ${file.absolutePath} failed: ${e.message}")
            false
        }
    }

    internal fun rewrite(bytes: ByteArray, newPackage: String, pathsOnly: Boolean = false): ByteArray {
        val out = bytes.copyOf()
        val new = newPackage.toByteArray(Charsets.US_ASCII)
        for (srcPkg in SOURCE_PACKAGES) {
            if (srcPkg == newPackage) continue
            val old = srcPkg.toByteArray(Charsets.US_ASCII)
            var i = 0
            while (i <= out.size - old.size) {
                if (matchesAt(out, i, old)) {
                    val inPath = !pathsOnly || (i > 0 && out[i - 1] == SLASH)
                    if (inPath && !keptAt(out, i + old.size, out.size)) {
                        System.arraycopy(new, 0, out, i, new.size)
                    }
                    i += old.size
                } else {
                    i++
                }
            }
        }
        return out
    }

    private fun matchesAt(buf: ByteArray, at: Int, pattern: ByteArray): Boolean {
        for (k in pattern.indices) if (buf[at + k] != pattern[k]) return false
        return true
    }

    private fun keptAt(buf: ByteArray, at: Int, len: Int): Boolean =
        KEPT_SUFFIXES.any { s -> at + s.size <= len && matchesAt(buf, at, s) }

    internal fun findAll(file: File, pattern: ByteArray, pathsOnly: Boolean = false): List<Long> {
        val result = ArrayList<Long>()
        val n = pattern.size
        val first = pattern[0]
        val buf = ByteArray(CHUNK + n - 1 + LOOKAHEAD)
        RandomAccessFile(file, "r").use { raf ->
            var base = 0L
            var len = 0
            var before: Byte? = null
            while (true) {
                val read = raf.read(buf, len, buf.size - len)
                val eof = read <= 0
                if (!eof) len += read
                val last = if (eof) len - n else len - n - LOOKAHEAD
                var i = 0
                while (i <= last) {
                    if (buf[i] == first && matchesAt(buf, i, pattern)) {
                        val inPath = !pathsOnly || (if (i > 0) buf[i - 1] else before) == SLASH
                        if (inPath && !keptAt(buf, i + n, len)) result.add(base + i)
                        i += n
                    } else {
                        i++
                    }
                }
                if (eof) break
                val keepFrom = i.coerceAtMost(len)
                if (keepFrom > 0) before = buf[keepFrom - 1]
                System.arraycopy(buf, keepFrom, buf, 0, len - keepFrom)
                len -= keepFrom
                base += keepFrom
            }
        }
        return result
    }

    /**
     * Installs Agentra's permanent APT pre-install hook, `dpkg` wrapper (`dpkg-divert`), and custom
     * `am.apk` built for `ai.agentra`. Idempotent and safe to run on every startup or install.
     */
    fun ensurePackageHooks(context: Context) {
        val pkg = targetPackage(context) ?: return
        val prefixDir = LocalEnvironmentManager.getPrefixDir(context)
        if (!File(prefixDir, "etc/apt").isDirectory) return

        val prefix = "/data/data/$pkg/files/usr"
        val relocator = "$prefix/libexec/agentra/relocate-debs"

        // Clean stale AntiGem apt hook if present from imported bootrapz_2026-11-19.zip
        runCatching {
            File(prefixDir, "etc/apt/apt.conf.d/99antigem-relocate").takeIf { it.exists() }?.delete()
        }

        try {
            writeIfChanged(
                File(prefixDir, "libexec/agentra/relocate-debs"),
                relocateScript(prefix, pkg),
                executable = true
            )
            writeIfChanged(
                File(prefixDir, "etc/apt/apt.conf.d/99agentra-relocate"),
                """
                |// Written by Agentra (edits are overwritten). Termux packages are built for $TERMUX_PACKAGE:
                |// this hook rewrites each .deb for $pkg before dpkg installs it.
                |DPkg::Pre-Install-Pkgs { "$relocator --apt"; };
                |""".trimMargin() + "\n",
                executable = false
            )
        } catch (e: Exception) {
            Log.w(TAG, "apt relocation hook setup failed: ${e.message}")
        }

        try {
            ensureDpkgWrapper(prefixDir, prefix, relocator)
        } catch (e: Exception) {
            Log.w(TAG, "dpkg wrapper setup failed: ${e.message}")
        }

        try {
            ensureAmApk(context, prefixDir, prefix)
        } catch (e: Exception) {
            Log.w(TAG, "am.apk setup failed: ${e.message}")
        }
    }

    private fun ensureDpkgWrapper(prefixDir: File, prefix: String, relocator: String) {
        val dpkg = File(prefixDir, "bin/dpkg")
        if (!File(prefixDir, "bin/dpkg.distrib").isFile && isScript(dpkg)) return
        if (!ensureDiverted(prefixDir, prefix, "bin/dpkg")) return
        writeIfChanged(dpkg, dpkgWrapper(prefix, relocator), executable = true)
    }

    /**
     * Installs Agentra's compiled `assets/bin/am.apk` (with `TERMUX_PACKAGE_NAME = "ai.agentra"`)
     * into `$PREFIX/libexec/termux-am/am.apk` as read-only (`0400`, required on Android 14+).
     */
    private fun ensureAmApk(context: Context, prefixDir: File, prefix: String) {
        val path = "libexec/termux-am/am.apk"
        val target = File(prefixDir, path)
        if (target.parentFile?.isDirectory != true) return
        val ours = try {
            context.assets.open("bin/am.apk").use { it.readBytes() }
        } catch (e: Exception) {
            Log.w(TAG, "assets/bin/am.apk missing: ${e.message}")
            return
        }
        if (target.isFile && target.length() == ours.size.toLong() && target.readBytes().contentEquals(ours)) return
        ensureDiverted(prefixDir, prefix, path)
        val tmp = File(target.parentFile, ".am.apk.tmp")
        tmp.delete()
        tmp.writeBytes(ours)
        try {
            Os.chmod(tmp.absolutePath, 0x100) // 0400 read-only for Android 14+ DCL rule
        } catch (_: Exception) {}
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }

    internal fun divertTermuxApi(context: Context): Boolean {
        val pkg = targetPackage(context) ?: return false
        val prefixDir = LocalEnvironmentManager.getPrefixDir(context)
        val path = "libexec/termux-api"
        val current = File(prefixDir, path)
        val isLink = try {
            OsConstants.S_ISLNK(Os.lstat(current.absolutePath).st_mode)
        } catch (_: Exception) {
            false
        }
        return ensureDiverted(prefixDir, "/data/data/$pkg/files/usr", path, rename = isLink)
    }

    private fun isDiverted(prefixDir: File, prefix: String, path: String): Boolean {
        val lines = try {
            File(prefixDir, "var/lib/dpkg/diversions").readLines()
        } catch (_: Exception) {
            return false
        }
        return (lines.indices step 3).any { lines[it] == "$prefix/$path" }
    }

    private fun ensureDiverted(
        prefixDir: File,
        prefix: String,
        path: String,
        rename: Boolean = true
    ): Boolean {
        if (isDiverted(prefixDir, prefix, path)) return true
        if (rename && runCatching { Os.lstat(File(prefixDir, path).absolutePath) }.isFailure) return false
        val divert = File(prefixDir, "bin/dpkg-divert")
        if (!divert.canExecute()) return false
        val args = mutableListOf(divert.absolutePath, "--add")
        if (rename) args += "--rename"
        args += listOf("--divert", "$prefix/$path.distrib", "$prefix/$path")
        val pb = ProcessBuilder(args).redirectErrorStream(true)
        pb.environment().apply {
            put("PREFIX", prefix)
            put("TMPDIR", "$prefix/tmp")
            put("PATH", "$prefix/bin:/system/bin")
            remove("LD_PRELOAD")
        }
        val proc = pb.start()
        val output = proc.inputStream.bufferedReader().readText()
        val done = proc.waitFor(30, TimeUnit.SECONDS)
        if (!done) proc.destroyForcibly()
        val ok = done && proc.exitValue() == 0 && isDiverted(prefixDir, prefix, path)
        if (!ok) Log.w(TAG, "dpkg-divert $path failed: ${output.trim()}")
        return ok
    }

    private fun isScript(file: File): Boolean = try {
        file.inputStream().use {
            val b = ByteArray(2)
            it.read(b) == 2 && b[0] == '#'.code.toByte() && b[1] == '!'.code.toByte()
        }
    } catch (_: Exception) {
        false
    }

    internal fun writeIfChanged(file: File, content: String, executable: Boolean) {
        if (file.isFile && runCatching { file.readText() == content }.getOrDefault(false)) return
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, ".${file.name}.tmp")
        tmp.writeText(content)
        try {
            Os.chmod(tmp.absolutePath, if (executable) 0x1ED else 0x1A4) // 0755 / 0644
        } catch (_: Exception) {}
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    internal fun relocateScript(prefix: String, pkg: String): String {
        val skipped = SKIPPED_EXTENSIONS.joinToString("|") { "*.$it" }
        val oldRegex = TERMUX_PACKAGE.replace(".", "\\.")
        val placeholder = "\\x01" + TERMUX_PACKAGE.drop(1)
        val sedProg = KEPT_SUFFIXES.joinToString("") { b ->
            val suffix = String(b, Charsets.US_ASCII)
            "s/$oldRegex${suffix.replace(".", "\\.")}/$placeholder$suffix/g; "
        } + "s/$oldRegex/$pkg/g; s/${placeholder.replace(".", "\\.")}/$TERMUX_PACKAGE/g"

        return """
            |#!$prefix/bin/sh
            |# Written by Agentra (edits are overwritten). Termux packages are built for /data/data/$TERMUX_PACKAGE.
            |# Rewrites each .deb on stdin in place for $pkg.
            |OLD='$TERMUX_PACKAGE'
            |NEW='$pkg'
            |export LC_ALL=C
            |umask 022
            |TMP="${'$'}{TMPDIR:-$prefix/tmp}"
            |SED_PROG='$sedProg'
            |APT_MODE=0
            |[ "${'$'}{1:-}" = "--apt" ] && APT_MODE=1
            |
            |relocate_deb() {
            |    work=${'$'}(mktemp -d "${'$'}TMP/agentra-deb.XXXXXX") || return 1
            |    if ! dpkg-deb -R "${'$'}1" "${'$'}work/pkg"; then rm -rf "${'$'}work"; return 1; fi
            |    if [ ! -d "${'$'}work/pkg/data/data/${'$'}OLD" ] && ! grep -rqF "${'$'}OLD" "${'$'}work/pkg" 2>/dev/null; then
            |        rm -rf "${'$'}work"; return 0
            |    fi
            |    echo "Agentra: adapting ${'$'}{1##*/} for ${'$'}NEW"
            |    if [ -d "${'$'}work/pkg/data/data/${'$'}OLD" ]; then
            |        mkdir -p "${'$'}work/pkg/data/data"
            |        mv "${'$'}work/pkg/data/data/${'$'}OLD" "${'$'}work/pkg/data/data/${'$'}NEW" || { rm -rf "${'$'}work"; return 1; }
            |    fi
            |    grep -rlF "${'$'}OLD" "${'$'}work/pkg" 2>/dev/null > "${'$'}work/files"
            |    while IFS= read -r f; do
            |        case "${'$'}f" in $skipped) continue ;; esac
            |        [ -L "${'$'}f" ] && continue
            |        [ -w "${'$'}f" ] || chmod u+w "${'$'}f"
            |        sed -i "${'$'}SED_PROG" "${'$'}f" || { rm -rf "${'$'}work"; return 1; }
            |    done < "${'$'}work/files"
            |    find "${'$'}work/pkg" -type l > "${'$'}work/links"
            |    while IFS= read -r l; do
            |        t=${'$'}(readlink "${'$'}l")
            |        case "${'$'}t" in
            |            *"${'$'}OLD"*) ln -sfn "${'$'}(printf '%s' "${'$'}t" | sed "${'$'}SED_PROG")" "${'$'}l" || { rm -rf "${'$'}work"; return 1; } ;;
            |        esac
            |    done < "${'$'}work/links"
            |    if [ -f "${'$'}work/pkg/DEBIAN/md5sums" ]; then
            |        ( cd "${'$'}work/pkg" && cut -c35- DEBIAN/md5sums | xargs -r -d '\n' md5sum ) > "${'$'}work/md5sums" &&
            |            mv -f "${'$'}work/md5sums" "${'$'}work/pkg/DEBIAN/md5sums"
            |    fi
            |    chmod 0755 "${'$'}work/pkg/DEBIAN"
            |    for s in preinst postinst prerm postrm config; do
            |        [ -f "${'$'}work/pkg/DEBIAN/${'$'}s" ] && chmod 0755 "${'$'}work/pkg/DEBIAN/${'$'}s"
            |    done
            |    if ! dpkg-deb --root-owner-group -Zgzip -z1 -b "${'$'}work/pkg" "${'$'}work/out.deb" >/dev/null; then
            |        rm -rf "${'$'}work"; return 1
            |    fi
            |    mv -f "${'$'}work/out.deb" "${'$'}1"
            |    status=${'$'}?
            |    rm -rf "${'$'}work"
            |    return ${'$'}status
            |}
            |
            |fail=0
            |while IFS= read -r deb; do
            |    case "${'$'}deb" in *.deb) ;; *) continue ;; esac
            |    [ -f "${'$'}deb" ] || continue
            |    if [ "${'$'}APT_MODE" = 1 ]; then
            |        case "${'$'}deb" in */cache/apt/archives/*) ;; *) continue ;; esac
            |    fi
            |    relocate_deb "${'$'}deb" || { echo "Agentra: could not adapt ${'$'}deb" >&2; fail=1; }
            |done
            |exit ${'$'}fail
            |""".trimMargin() + "\n"
    }

    internal fun dpkgWrapper(prefix: String, relocator: String): String = """
        |#!$prefix/bin/sh
        |# Written by Agentra (edits are overwritten). The real dpkg is dpkg.distrib (diverted via dpkg-divert).
        |REAL='$prefix/bin/dpkg.distrib'
        |locked=0
        |[ -n "${'$'}{DPKG_FRONTEND_LOCKED:-}" ] && locked=1
        |
        |needs() {
        |    [ -f "${'$'}1" ] || return 1
        |    case "${'$'}1" in *.deb) ;; *) return 1 ;; esac
        |    if [ "${'$'}locked" = 1 ]; then
        |        target=${'$'}(readlink -f "${'$'}1" 2>/dev/null || printf '%s' "${'$'}1")
        |        case "${'$'}target" in */cache/apt/archives/*) return 1 ;; esac
        |    fi
        |    return 0
        |}
        |
        |dir_needs() {
        |    find -L "${'$'}1" -type f -name '*.deb' 2>/dev/null | while IFS= read -r d; do
        |        if needs "${'$'}d"; then echo yes; break; fi
        |    done | grep -q yes
        |}
        |
        |install=0
        |recursive=0
        |for a in "${'$'}@"; do
        |    case "${'$'}a" in
        |        --install|--unpack|--record-avail) install=1 ;;
        |        --recursive) recursive=1 ;;
        |        --*) ;;
        |        -?*)
        |            case "${'$'}a" in *[iA]*) install=1 ;; esac
        |            case "${'$'}a" in *R*) recursive=1 ;; esac
        |            ;;
        |    esac
        |done
        |found=0
        |if [ "${'$'}install" = 1 ]; then
        |    for a in "${'$'}@"; do
        |        if needs "${'$'}a"; then found=1; break; fi
        |        if [ "${'$'}recursive" = 1 ] && [ -d "${'$'}a" ] && dir_needs "${'$'}a"; then found=1; break; fi
        |    done
        |fi
        |[ "${'$'}found" = 1 ] || exec "${'$'}REAL" "${'$'}@"
        |
        |TMP="${'$'}{TMPDIR:-$prefix/tmp}"
        |work=${'$'}(mktemp -d "${'$'}TMP/agentra-dpkg.XXXXXX") || exit 1
        |trap 'rm -rf "${'$'}work"' EXIT
        |trap 'exit 130' INT TERM
        |n=0
        |adapt() {
        |    n=${'$'}((n + 1))
        |    adapted="${'$'}work/${'$'}n-${'$'}{1##*/}"
        |    cp "${'$'}1" "${'$'}adapted" && printf '%s\n' "${'$'}adapted" | '$relocator' >&2
        |}
        |
        |count=${'$'}#
        |i=0
        |while [ "${'$'}i" -lt "${'$'}count" ]; do
        |    a=${'$'}1
        |    shift
        |    i=${'$'}((i + 1))
        |    if needs "${'$'}a"; then
        |        adapt "${'$'}a" || { echo "dpkg (Agentra): could not adapt ${'$'}a" >&2; exit 1; }
        |        a=${'$'}adapted
        |    elif [ "${'$'}recursive" = 1 ] && [ -d "${'$'}a" ] && dir_needs "${'$'}a"; then
        |        dir="${'$'}work/dir${'$'}i"
        |        mkdir -p "${'$'}dir"
        |        find -L "${'$'}a" -type f -name '*.deb' > "${'$'}work/list${'$'}i"
        |        while IFS= read -r d; do
        |            if needs "${'$'}d"; then
        |                adapt "${'$'}d" || { echo "dpkg (Agentra): could not adapt ${'$'}d" >&2; exit 1; }
        |                mv "${'$'}adapted" "${'$'}dir/"
        |            else
        |                n=${'$'}((n + 1))
        |                ln -s "${'$'}(readlink -f "${'$'}d")" "${'$'}dir/${'$'}n-${'$'}{d##*/}"
        |            fi
        |        done < "${'$'}work/list${'$'}i"
        |        a=${'$'}dir
        |    fi
        |    set -- "${'$'}@" "${'$'}a"
        |done
        |"${'$'}REAL" "${'$'}@"
        |""".trimMargin() + "\n"
}
