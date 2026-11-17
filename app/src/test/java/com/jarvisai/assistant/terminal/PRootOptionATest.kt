package com.jarvisai.assistant.terminal

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.apache.commons.compress.archivers.ar.ArArchiveEntry
import org.apache.commons.compress.archivers.ar.ArArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PRootOptionATest {

    private lateinit var context: Context
    private lateinit var tempFilesToClean: MutableList<File>

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        tempFilesToClean = mutableListOf()
        cleanSandbox()
    }

    @After
    fun tearDown() {
        cleanSandbox()
        tempFilesToClean.forEach { runCatching { it.deleteRecursively() } }
    }

    private fun cleanSandbox() {
        listOf("usr", "home", "proot-runtime", "rootfs", "bin").forEach { rel ->
            File(context.filesDir, rel).deleteRecursively()
        }
        PRootRuntime.setRuntimeMode(context, LinuxRuntimeMode.PROOT_TERMUX)
    }

    @Test
    fun parseSymlinksTxt_and_zipExtraction_fixesDotSlashAndAppliesSymlinks() = runBlocking {
        val tempZip = File.createTempFile("bootrapz_test", ".zip").also { tempFilesToClean.add(it) }
        ZipOutputStream(FileOutputStream(tempZip)).use { zos ->
            zos.putNextEntry(ZipEntry("./usr/bin/bash"))
            zos.write("#!/bin/sh\necho bash\n".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("./home/.bashrc"))
            zos.write("export FOO=bar\n".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("./usr/SYMLINKS.txt"))
            zos.write("bash\u2190bin/sh\n".toByteArray())
            zos.closeEntry()
        }

        val prefixDir = LocalEnvironmentManager.getPrefixDir(context)
        LocalEnvironmentManager.extractZipPreservingPermissions(tempZip, prefixDir)
        LocalEnvironmentManager.fixFilePermissions(prefixDir)

        val bash = File(prefixDir, "bin/bash")
        val shLink = File(prefixDir, "bin/sh")
        val bashrc = File(LocalEnvironmentManager.getHomeDir(context), ".bashrc")
        val resolv = File(prefixDir, "etc/resolv.conf")
        val hosts = File(prefixDir, "etc/hosts")

        assertTrue("Expected ./usr/bin/bash stripped to prefix/bin/bash", bash.exists())
        assertTrue("Expected bash executable", bash.canExecute())
        assertTrue("Expected ./home/.bashrc extracted to files/home/.bashrc", bashrc.exists())
        assertFalse("Must NOT nest files/usr/usr/...", File(prefixDir, "usr").exists())
        assertTrue("Expected SYMLINKS.txt symlink bin/sh -> bash", Files.isSymbolicLink(shLink.toPath()))
        assertEquals("bash", Files.readSymbolicLink(shLink.toPath()).toString())
        assertTrue("Expected DNS resolv.conf generated", resolv.exists() && resolv.readText().contains("1.1.1.1"))
        assertTrue("Expected hosts generated", hosts.exists() && hosts.readText().contains("localhost"))
    }

    @Test
    fun aptPackagesParser_and_dependencyResolver_ordersTransitiveDeps() {
        val samplePackages = """
            Package: libandroid-shmem
            Version: 0.6-1
            Architecture: aarch64
            Filename: pool/main/liba/libandroid-shmem/libandroid-shmem_0.6-1_aarch64.deb

            Package: libtalloc
            Version: 2.4.2
            Architecture: aarch64
            Depends: libc++
            Filename: pool/main/libt/libtalloc/libtalloc_2.4.2_aarch64.deb

            Package: proot
            Version: 5.1.107-65
            Architecture: aarch64
            Depends: libandroid-shmem, libtalloc (>= 2.4.0)
            Filename: pool/main/p/proot/proot_5.1.107-65_aarch64.deb
        """.trimIndent()

        val index = PRootInstaller.parsePackagesIndex(samplePackages)
        assertEquals(3, index.size)

        val order = PRootInstaller.resolveDependencyOrder(index, listOf("proot"))
        assertEquals(listOf("libandroid-shmem", "libtalloc", "proot"), order.map { it.packageName })
    }

    @Test
    fun debExtractor_unpacksDataTarGzAndStripsTermuxUsrPrefix() {
        val tarGzBytes = ByteArrayOutputStream().apply {
            GzipCompressorOutputStream(this).use { gz ->
                TarArchiveOutputStream(gz).use { tar ->
                    val baseTar: org.apache.commons.compress.archivers.ArchiveOutputStream<TarArchiveEntry> = tar
                    val prootData = ByteArray(16_384) { 0x41 }
                    val prootEntry = TarArchiveEntry("./data/data/com.termux/files/usr/bin/proot")
                    prootEntry.size = prootData.size.toLong()
                    prootEntry.mode = 493 // 0755
                    baseTar.putArchiveEntry(prootEntry)
                    baseTar.write(prootData)
                    baseTar.closeArchiveEntry()

                    val libData = ByteArray(128) { 0x42 }
                    val libEntry = TarArchiveEntry("./data/data/com.termux/files/usr/lib/libtalloc.so.2")
                    libEntry.size = libData.size.toLong()
                    libEntry.mode = 493
                    baseTar.putArchiveEntry(libEntry)
                    baseTar.write(libData)
                    baseTar.closeArchiveEntry()

                    val symEntry = TarArchiveEntry("./data/data/com.termux/files/usr/lib/libtalloc.so", TarArchiveEntry.LF_SYMLINK)
                    symEntry.linkName = "libtalloc.so.2"
                    baseTar.putArchiveEntry(symEntry)
                    baseTar.closeArchiveEntry()
                }
            }
        }.toByteArray()

        val tempDeb = File.createTempFile("proot_test", ".deb").also { tempFilesToClean.add(it) }
        ArArchiveOutputStream(FileOutputStream(tempDeb)).use { ar ->
            val baseAr: org.apache.commons.compress.archivers.ArchiveOutputStream<ArArchiveEntry> = ar
            val debianBinary = "2.0\n".toByteArray()
            baseAr.putArchiveEntry(ArArchiveEntry("debian-binary", debianBinary.size.toLong()))
            baseAr.write(debianBinary)
            baseAr.closeArchiveEntry()

            baseAr.putArchiveEntry(ArArchiveEntry("data.tar.gz", tarGzBytes.size.toLong()))
            baseAr.write(tarGzBytes)
            baseAr.closeArchiveEntry()
        }

        val runtimeDir = PRootRuntime.getProotRuntimeDir(context)
        val count = PRootInstaller.extractDebIntoPrefix(tempDeb, runtimeDir)
        assertTrue("Expected at least 3 extracted entries", count >= 3)

        val extractedProot = File(runtimeDir, "bin/proot")
        val extractedLib = File(runtimeDir, "lib/libtalloc.so.2")
        val extractedSym = File(runtimeDir, "lib/libtalloc.so")
        assertTrue(extractedProot.exists() && extractedProot.canExecute())
        assertTrue(extractedLib.exists())
        assertTrue(Files.isSymbolicLink(extractedSym.toPath()))
        assertEquals("libtalloc.so.2", Files.readSymbolicLink(extractedSym.toPath()).toString())
    }

    @Test
    fun prootRuntime_buildsTermuxBindMountArgv_and_fallsBackWhenAbsent() {
        // 1. Without proot binary -> automatic fallback to direct mode
        assertFalse(PRootRuntime.shouldUsePRoot(context))
        assertNull(PRootRuntime.buildActiveInvocation(context, command = "echo hi"))

        // 2. Provision proot + loader + bash in sandbox
        val runtimeDir = PRootRuntime.getProotRuntimeDir(context)
        val prootBin = File(runtimeDir, "bin/proot").apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(16_384) { 0x7F })
            PRootRuntime.ensureExecutable(this)
        }
        val loaderBin = File(runtimeDir, "libexec/proot/loader").apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(4096) { 0x7F })
            PRootRuntime.ensureExecutable(this)
        }
        val prefixDir = LocalEnvironmentManager.getPrefixDir(context)
        File(prefixDir, "bin/bash").apply {
            parentFile?.mkdirs()
            writeText("#!/system/bin/sh\n")
            PRootRuntime.ensureExecutable(this)
        }
        val homeDir = LocalEnvironmentManager.getHomeDir(context)
        val projectsDir = File(homeDir, "jarvis-workspace/projects").apply { mkdirs() }

        assertTrue(PRootRuntime.shouldUsePRoot(context))
        val inv = PRootRuntime.buildActiveInvocation(
            context = context,
            command = "claude --version",
            workingDir = projectsDir.absolutePath,
            loginShell = false
        )
        assertNotNull(inv)
        assertEquals(prootBin.absolutePath, inv!!.executablePath)
        assertEquals(LinuxRuntimeMode.PROOT_TERMUX, inv.mode)
        assertEquals("${PRootRuntime.VIRTUAL_TERMUX_HOME}/jarvis-workspace/projects", inv.guestWorkingDir)
        assertNull("LD_PRELOAD must be stripped from PRoot host env", inv.hostEnv["LD_PRELOAD"])
        assertEquals(loaderBin.absolutePath, inv.hostEnv["PROOT_LOADER"])
        assertEquals(LocalEnvironmentManager.getTmpDir(context).absolutePath, inv.hostEnv["PROOT_TMP_DIR"])

        val joined = inv.argv.joinToString(" ")
        assertTrue(joined.contains("--link2symlink"))
        assertTrue(joined.contains(":${PRootRuntime.VIRTUAL_TERMUX_ROOT}"))
        assertTrue(joined.contains(":${PRootRuntime.VIRTUAL_TERMUX_USER0_ROOT}"))
        assertTrue(joined.contains("${prefixDir.absolutePath}:${PRootRuntime.VIRTUAL_TERMUX_PREFIX}"))
        assertTrue(joined.contains("${homeDir.absolutePath}:${PRootRuntime.VIRTUAL_TERMUX_HOME}"))
        assertEquals("${PRootRuntime.VIRTUAL_TERMUX_HOME}/.npm-global", inv.hostEnv["npm_config_prefix"])
        assertEquals(File(prefixDir, "etc/tls/openssl.cnf").absolutePath, inv.hostEnv["OPENSSL_CONF"])
        assertTrue(joined.endsWith("${PRootRuntime.VIRTUAL_TERMUX_PREFIX}/bin/bash -c claude --version"))

        // 3. Selecting PROOT_UBUNTU before Ubuntu is downloaded falls back safely to PROOT_TERMUX
        PRootRuntime.setRuntimeMode(context, LinuxRuntimeMode.PROOT_UBUNTU)
        assertEquals(LinuxRuntimeMode.PROOT_TERMUX, PRootRuntime.getEffectiveRuntimeMode(context))
        assertTrue(PRootRuntime.shouldUsePRoot(context))

        // 4. When Ubuntu rootfs exists, PROOT_UBUNTU binds /system, /apex, and host Termux prefix
        val ubuntuRoot = PRootRuntime.getUbuntuRootfsDir(context)
        File(ubuntuRoot, "bin/bash").apply {
            parentFile?.mkdirs()
            writeText("#!/bin/sh\n")
            PRootRuntime.ensureExecutable(this)
        }
        File(ubuntuRoot, "usr/bin/env").apply {
            parentFile?.mkdirs()
            writeText("#!/bin/sh\n")
            PRootRuntime.ensureExecutable(this)
        }
        assertEquals(LinuxRuntimeMode.PROOT_UBUNTU, PRootRuntime.getEffectiveRuntimeMode(context))
        val ubuntuInv = PRootRuntime.buildActiveInvocation(context, command = "npm --version")
        assertNotNull(ubuntuInv)
        val ubuntuJoined = ubuntuInv!!.argv.joinToString(" ")
        assertTrue("Ubuntu PRoot must bind VIRTUAL_TERMUX_PREFIX", ubuntuJoined.contains("${prefixDir.absolutePath}:${PRootRuntime.VIRTUAL_TERMUX_PREFIX}"))
        assertTrue("Ubuntu PRoot must bind host prefix", ubuntuJoined.contains("${prefixDir.absolutePath}:${prefixDir.absolutePath}"))
    }

    @Test
    fun findCliBinary_and_nodeNpmWrappers_createPortableExecutables() {
        assertNull(LocalEnvironmentManager.findCliBinary(context, "claude"))

        val prefixDir = LocalEnvironmentManager.getPrefixDir(context)
        val homeDir = LocalEnvironmentManager.getHomeDir(context)

        File(prefixDir, "bin/node").apply {
            parentFile?.mkdirs()
            writeText("#!/system/bin/sh\n")
            PRootRuntime.ensureExecutable(this)
        }
        File(prefixDir, "lib/node_modules/npm/bin/npm-cli.js").apply {
            parentFile?.mkdirs()
            writeText("console.log('npm');\n")
        }
        val npmClaude = File(homeDir, ".npm-global/bin/claude").apply {
            parentFile?.mkdirs()
            writeText("#!/usr/bin/env node\nconsole.log('ok');\n")
            PRootRuntime.ensureExecutable(this)
        }

        LocalEnvironmentManager.ensureNodeAndNpmWrappers(prefixDir, homeDir)

        val opensslCnf = File(prefixDir, "etc/tls/openssl.cnf")
        assertTrue("Expected etc/tls/openssl.cnf created to prevent external com.termux EACCES", opensslCnf.exists())

        val localNpm = File(homeDir, ".local/bin/npm")
        val localClaude = File(homeDir, ".local/bin/claude")
        assertTrue("Expected portable .local/bin/npm wrapper", localNpm.exists() && localNpm.canExecute())
        assertTrue(localNpm.readText().startsWith("#!/system/bin/sh"))
        assertTrue("Wrapper must export OPENSSL_CONF", localNpm.readText().contains("OPENSSL_CONF="))
        assertTrue("Expected portable .local/bin/claude wrapper", localClaude.exists() && localClaude.canExecute())

        val envMap = mutableMapOf<String, String>()
        LocalEnvironmentManager.applyIsolatedToolEnvironment(context, envMap)
        assertEquals(opensslCnf.absolutePath, envMap["OPENSSL_CONF"])

        val found = LocalEnvironmentManager.findCliBinary(context, "claude")
        assertNotNull(found)
        assertTrue(found!!.canExecute())
        assertTrue(npmClaude.exists())
    }
}
