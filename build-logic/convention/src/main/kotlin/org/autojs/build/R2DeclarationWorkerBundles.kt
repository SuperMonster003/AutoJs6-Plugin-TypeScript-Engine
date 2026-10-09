package org.autojs.build

import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

class R2DeclarationWorkerBundleException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * Generates the same-origin JavaScript bundles through which the compiler Worker preloads the
 * plugin-owned declaration layers (startup-latency L2-1).
 *
 * Every bundle is derived byte-for-byte from the already verified declaration distribution
 * (`assets/r2-declarations/v2`) and TypeScript distribution (`assets/typescript/<version>`): the
 * common bundle carries the 108 standard libraries, the Rhino and Node bundles carry their engine
 * layers. Each file record keeps the VFS path, UTF-8 byte length and SHA-256 that the native host
 * advertises in `declarationInventoryFingerprint`, so the Worker can prove the preloaded texts are
 * exactly the inventory the request was admitted against before any of them enters a project VFS,
 * plus the token count locked by `tools/sync-declaration-token-counts.mjs` (startup-latency L2-7)
 * so the Worker admits the layers against its token budget without scanning them again. The
 * native asset origin verifies each bundle's bytes against `bundles.json` before the Worker may
 * import it. Output is deterministic; [verify] regenerates it and rejects any byte that differs.
 */
object R2DeclarationWorkerBundles {

    const val SCHEMA_VERSION = 1
    const val TOKEN_LOCK_SCHEMA_VERSION = 1
    const val TOKEN_LOCK_TOOL = "tools/sync-declaration-token-counts.mjs"
    const val MANIFEST_FILE_NAME = "bundles.json"
    const val COMMON_LAYER = "common"
    const val RHINO_LAYER = "rhino"
    const val NODE_LAYER = "node"
    const val GLOBAL_REGISTRY = "__autojs6DeclarationLayers"

    val bundleFileNames: Map<String, String> = linkedMapOf(
        COMMON_LAYER to "declarations-common.js",
        RHINO_LAYER to "declarations-rhino.js",
        NODE_LAYER to "declarations-node.js",
    )

    @JvmStatic
    fun generate(
        declarationDistributionDirectory: File,
        typescriptDistributionDirectory: File,
        tokenCountLockFile: File,
        outputDirectory: File,
    ) {
        val expected = reconstruct(
            declarationDistributionDirectory,
            typescriptDistributionDirectory,
            tokenCountLockFile,
        )
        val output = outputDirectory.toPath().toAbsolutePath().normalize()
        val parent = output.parent
            ?: fail("Worker bundle output must have a parent: $output")
        Files.createDirectories(parent)
        refuseSymbolicLink(output, "worker bundle output")
        val staging = Files.createTempDirectory(parent, ".${output.fileName}.staging-")
        try {
            expected.forEach { (name, bytes) -> writeNewFile(staging, name, bytes) }
            verifyDirectory(expected, staging)
            deleteTreeIfPresent(output)
            Files.move(staging, output, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: Throwable) {
            deleteTreeIfPresent(staging)
            if (e is R2DeclarationWorkerBundleException) throw e
            throw R2DeclarationWorkerBundleException("Failed to generate the declaration Worker bundles", e)
        }
    }

    @JvmStatic
    fun verify(
        declarationDistributionDirectory: File,
        typescriptDistributionDirectory: File,
        tokenCountLockFile: File,
        bundleDirectory: File,
    ) {
        val expected = reconstruct(
            declarationDistributionDirectory,
            typescriptDistributionDirectory,
            tokenCountLockFile,
        )
        verifyDirectory(expected, bundleDirectory.toPath().toAbsolutePath().normalize())
    }

    // ------------------------------------------------------------------------------------------

    private class LayerFile(
        val path: String,
        val byteLength: Long,
        val sha256: String,
        val text: String,
        val tokenCount: Long,
    )

    private class Layer(
        val layer: String,
        val fingerprint: String,
        val profileFingerprint: String?,
        val files: List<LayerFile>,
    ) {
        val totalBytes: Long get() = files.sumOf(LayerFile::byteLength)
        val totalTokens: Long get() = files.sumOf(LayerFile::tokenCount)
    }

    private class TokenLockEntry(
        val sha256: String,
        val tokenCount: Long,
    )

    /**
     * The committed token lock: VFS path -> (SHA-256, token count), produced by
     * [TOKEN_LOCK_TOOL] with the Worker's scanner configuration. Every layer file must have an
     * entry with the same digest, and the lock must not keep entries for files that no longer
     * exist, so a declaration or TypeScript upgrade without a re-sync fails here with a pointer.
     */
    private fun readTokenLock(lockFile: File, typescriptVersion: String): Map<String, TokenLockEntry> {
        val lock = readJsonObject(lockFile.toPath().toAbsolutePath().normalize())
        if (lock["schemaVersion"] != TOKEN_LOCK_SCHEMA_VERSION) {
            fail("Declaration token lock schema must be $TOKEN_LOCK_SCHEMA_VERSION; regenerate with $TOKEN_LOCK_TOOL")
        }
        val scanner = lock.obj("scanner")
        if (scanner.str("typescript") != typescriptVersion) {
            fail("Declaration token lock was produced for TypeScript ${scanner.str("typescript")}, not $typescriptVersion; regenerate with $TOKEN_LOCK_TOOL")
        }
        if (scanner.str("scriptTarget") != "ES2018" || scanner.str("languageVariant") != "Standard" || scanner["skipTrivia"] != true) {
            fail("Declaration token lock scanner configuration drifted from the Worker; regenerate with $TOKEN_LOCK_TOOL")
        }
        return lock.obj("files").entries.associate { (key, value) ->
            val path = key as? String ?: fail("Declaration token lock has a non-string path")
            val entry = value as? Map<*, *> ?: fail("Declaration token lock entry for $path is not an object")
            val tokenCount = (entry["tokenCount"] as? Number)?.toLong()
                ?: fail("Declaration token lock entry for $path has no integral tokenCount")
            if (tokenCount < 0 || entry["tokenCount"] is Double || entry["tokenCount"] is Float) {
                fail("Declaration token lock entry for $path must be a non-negative integer")
            }
            path to TokenLockEntry(entry.str("sha256"), tokenCount)
        }
    }

    private fun reconstruct(
        declarationDistributionDirectory: File,
        typescriptDistributionDirectory: File,
        tokenCountLockFile: File,
    ): Map<String, ByteArray> {
        val declarationRoot = declarationDistributionDirectory.toPath().toAbsolutePath().normalize()
        val typescriptRoot = typescriptDistributionDirectory.toPath().toAbsolutePath().normalize()
        val declarationManifest = readJsonObject(declarationRoot.resolve("distribution.json"))
        val typescriptManifest = readJsonObject(typescriptRoot.resolve("distribution.json"))
        if (declarationManifest["schemaVersion"] != 2) {
            fail("Declaration distribution manifest schema must be 2")
        }
        val tokenLock = readTokenLock(
            tokenCountLockFile,
            typescriptManifest.obj("package").str("version"),
        )
        val lockedPaths = HashSet<String>()
        val layers = declarationManifest.obj("layers")
        val profiles = declarationManifest.obj("profiles")
        val declarationFiles = declarationManifest.list("files").associate { entry ->
            val file = entry as Map<*, *>
            file.str("path") to file
        }
        val standardLibraries = typescriptManifest.obj("components").obj("standardLibraries")
        val commonLayerManifest = layers.obj(COMMON_LAYER)
        val commonFingerprint = commonLayerManifest.str("fingerprint")
        if (commonFingerprint != standardLibraries.str("fingerprint")) {
            fail("Common declaration layer does not match the TypeScript standard-library fingerprint")
        }

        val commonFiles = typescriptManifest.list("files")
            .map { entry -> entry as Map<*, *> }
            .filter { file ->
                val path = file.str("path")
                path.startsWith("lib/lib") && path.endsWith(".d.ts") && '/' !in path.removePrefix("lib/")
            }
            .map { file ->
                readLayerFile(
                    root = typescriptRoot,
                    relativePath = file.str("path"),
                    vfsPath = "/declarations/" + file.str("path").removePrefix("lib/"),
                    expectedSize = (file["size"] as Number).toLong(),
                    expectedSha256 = file.str("sha256"),
                    tokenLock = tokenLock,
                    lockedPaths = lockedPaths,
                )
            }
            .sortedBy(LayerFile::path)
        if (commonFiles.size != (commonLayerManifest["fileCount"] as Number).toInt() ||
            commonFiles.sumOf(LayerFile::byteLength) != (commonLayerManifest["totalBytes"] as Number).toLong()
        ) {
            fail("Common declaration layer file count or byte total does not match its manifest")
        }
        val common = Layer(COMMON_LAYER, commonFingerprint, null, commonFiles)

        fun engineLayer(name: String): Layer {
            val layerManifest = layers.obj(name)
            val profileManifest = profiles.obj(name)
            val expectedLayers = profileManifest.list("layers").map { it as String }
            if (expectedLayers != listOf(COMMON_LAYER, name)) {
                fail("Declaration profile $name does not consist of the common and $name layers")
            }
            val files = layerManifest.list("files").map { entry ->
                val relativePath = entry as String
                val descriptor = declarationFiles[relativePath]
                    ?: fail("Declaration layer $name references an unlisted file $relativePath")
                if (!relativePath.startsWith("layers/$name/") || !relativePath.endsWith(".d.ts")) {
                    fail("Declaration layer $name file is outside its layer: $relativePath")
                }
                readLayerFile(
                    root = declarationRoot,
                    relativePath = relativePath,
                    vfsPath = "/declarations/" + relativePath.removePrefix("layers/"),
                    expectedSize = (descriptor["size"] as Number).toLong(),
                    expectedSha256 = descriptor.str("sha256"),
                    tokenLock = tokenLock,
                    lockedPaths = lockedPaths,
                )
            }.sortedBy(LayerFile::path)
            if (files.size != (layerManifest["fileCount"] as Number).toInt() ||
                files.sumOf(LayerFile::byteLength) != (layerManifest["totalBytes"] as Number).toLong()
            ) {
                fail("Declaration layer $name file count or byte total does not match its manifest")
            }
            return Layer(name, layerManifest.str("fingerprint"), profileManifest.str("fingerprint"), files)
        }

        val bundles = linkedMapOf<String, ByteArray>()
        val manifestBundles = linkedMapOf<String, Map<String, Any?>>()
        val renderedLayers = listOf(common, engineLayer(RHINO_LAYER), engineLayer(NODE_LAYER))
        val staleLockPaths = tokenLock.keys - lockedPaths
        if (staleLockPaths.isNotEmpty()) {
            fail("Declaration token lock keeps entries for files that no layer contains: ${staleLockPaths.sorted()}; regenerate with $TOKEN_LOCK_TOOL")
        }
        renderedLayers.forEach { layer ->
            val fileName = bundleFileNames.getValue(layer.layer)
            val bytes = renderBundle(layer).toByteArray(StandardCharsets.UTF_8)
            bundles[fileName] = bytes
            manifestBundles[layer.layer] = linkedMapOf(
                "file" to fileName,
                "fileCount" to layer.files.size,
                "layerFingerprint" to layer.fingerprint,
                "profileFingerprint" to layer.profileFingerprint,
                "sha256" to sha256Hex(bytes),
                "size" to bytes.size,
                "totalBytes" to layer.totalBytes,
                "totalTokens" to layer.totalTokens,
            )
        }
        val manifest = linkedMapOf<String, Any?>(
            "bundles" to manifestBundles,
            "declarationDistributionFingerprint" to declarationManifest.str("fingerprint"),
            "globalRegistry" to GLOBAL_REGISTRY,
            "schemaVersion" to SCHEMA_VERSION,
            "standardLibrariesFingerprint" to commonFingerprint,
        )
        bundles[MANIFEST_FILE_NAME] = (renderJson(manifest) + "\n").toByteArray(StandardCharsets.UTF_8)
        return bundles
    }

    private fun renderBundle(layer: Layer): String = buildString(layer.totalBytes.toInt() + 4096) {
        append("\"use strict\";\n")
        append("// Generated by org.autojs.build.R2DeclarationWorkerBundles from the verified\n")
        append("// declaration distribution and the committed token lock. The native asset origin\n")
        append("// verifies this file's bytes before serving it; the compiler Worker checks the\n")
        append("// record shapes and proves the inventory fingerprint before using any record.\n")
        append("(() => {\n")
        append("    const registry = self.").append(GLOBAL_REGISTRY).append(" ??\n")
        append("        (self.").append(GLOBAL_REGISTRY).append(" = Object.create(null));\n")
        append("    registry[").append(jsString(layer.layer)).append("] = Object.freeze({\n")
        append("        fileCount: ").append(layer.files.size).append(",\n")
        append("        files: Object.freeze([\n")
        layer.files.forEach { file ->
            append("            Object.freeze({\n")
            append("                byteLength: ").append(file.byteLength).append(",\n")
            append("                path: ").append(jsString(file.path)).append(",\n")
            append("                sha256: ").append(jsString(file.sha256)).append(",\n")
            append("                text: ").append(jsString(file.text)).append(",\n")
            append("                tokenCount: ").append(file.tokenCount).append(",\n")
            append("            }),\n")
        }
        append("        ]),\n")
        append("        fingerprint: ").append(jsString(layer.fingerprint)).append(",\n")
        append("        layer: ").append(jsString(layer.layer)).append(",\n")
        append("        profileFingerprint: ")
        append(layer.profileFingerprint?.let(::jsString) ?: "null").append(",\n")
        append("        schemaVersion: ").append(SCHEMA_VERSION).append(",\n")
        append("        totalBytes: ").append(layer.totalBytes).append(",\n")
        append("        totalTokens: ").append(layer.totalTokens).append(",\n")
        append("    });\n")
        append("})();\n")
    }

    private fun readLayerFile(
        root: Path,
        relativePath: String,
        vfsPath: String,
        expectedSize: Long,
        expectedSha256: String,
        tokenLock: Map<String, TokenLockEntry>,
        lockedPaths: MutableSet<String>,
    ): LayerFile {
        val file = root.resolve(relativePath).normalize()
        if (!file.startsWith(root)) fail("Declaration file escapes its distribution: $relativePath")
        refuseSymbolicLink(file, "declaration file")
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            fail("Declaration file does not exist: $relativePath")
        }
        val bytes = Files.readAllBytes(file)
        if (bytes.size.toLong() != expectedSize) fail("Declaration file size drifted: $relativePath")
        val digest = sha256Hex(bytes)
        if (digest != expectedSha256) fail("Declaration file digest drifted: $relativePath")
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
        val text = try {
            decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        } catch (e: java.nio.charset.CharacterCodingException) {
            fail("Declaration file is not strict UTF-8: $relativePath")
        }
        val locked = tokenLock[vfsPath]
            ?: fail("Declaration token lock has no entry for $vfsPath; regenerate with $TOKEN_LOCK_TOOL")
        if (locked.sha256 != digest) {
            fail("Declaration token lock digest drifted for $vfsPath; regenerate with $TOKEN_LOCK_TOOL")
        }
        if (!lockedPaths.add(vfsPath)) fail("Declaration path appears in two layers: $vfsPath")
        return LayerFile(vfsPath, expectedSize, digest, text, locked.tokenCount)
    }

    /** JSON-compatible string literal that is also safe as a JavaScript source literal. */
    internal fun jsString(value: String): String {
        val builder = StringBuilder(value.length + 16)
        builder.append('"')
        value.forEach { character ->
            when (character) {
                '"' -> builder.append("\\\"")
                '\\' -> builder.append("\\\\")
                '\b' -> builder.append("\\b")
                '\u000C' -> builder.append("\\f")
                '\n' -> builder.append("\\n")
                '\r' -> builder.append("\\r")
                '\t' -> builder.append("\\t")
                ' ' -> builder.append("\\u2028")
                ' ' -> builder.append("\\u2029")
                else -> if (character.code < 0x20) {
                    builder.append("\\u").append(String.format("%04x", character.code))
                } else {
                    builder.append(character)
                }
            }
        }
        builder.append('"')
        return builder.toString()
    }

    private fun renderJson(value: Any?, indent: String = ""): String = when (value) {
        null -> "null"
        is String -> jsString(value)
        is Boolean, is Number -> value.toString()
        is Map<*, *> -> {
            if (value.isEmpty()) {
                "{}"
            } else {
                val inner = "$indent  "
                value.entries.joinToString(",\n", "{\n", "\n$indent}") { (key, entry) ->
                    "$inner${jsString(key as String)}: ${renderJson(entry, inner)}"
                }
            }
        }
        is List<*> -> {
            if (value.isEmpty()) {
                "[]"
            } else {
                val inner = "$indent  "
                value.joinToString(",\n", "[\n", "\n$indent]") { entry -> "$inner${renderJson(entry, inner)}" }
            }
        }
        else -> fail("Unsupported JSON value: ${value::class.java.name}")
    }

    private fun verifyDirectory(expected: Map<String, ByteArray>, root: Path) {
        refuseSymbolicLink(root, "worker bundle directory")
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            fail("Worker bundle directory does not exist: $root")
        }
        val actual = Files.walk(root).use { stream ->
            stream.filter { Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) }
                .map { root.relativize(it).toString().replace(File.separatorChar, '/') }
                .toList()
                .toSet()
        }
        if (actual != expected.keys) {
            fail("Worker bundle directory does not match the expected files: expected=${expected.keys}, actual=$actual")
        }
        expected.forEach { (name, bytes) ->
            val file = root.resolve(name)
            refuseSymbolicLink(file, "worker bundle $name")
            if (!MessageDigest.isEqual(Files.readAllBytes(file), bytes)) {
                fail("Worker bundle $name does not match its regenerated content")
            }
        }
    }

    private fun writeNewFile(root: Path, relative: String, bytes: ByteArray) {
        val target = root.resolve(relative).normalize()
        if (!target.startsWith(root)) fail("Worker bundle path escapes its root: $relative")
        Files.createDirectories(target.parent)
        Files.write(target, bytes, java.nio.file.StandardOpenOption.CREATE_NEW, java.nio.file.StandardOpenOption.WRITE)
    }

    private fun deleteTreeIfPresent(root: Path) {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return
        Files.walk(root).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }

    private fun refuseSymbolicLink(path: Path, description: String) {
        if (Files.isSymbolicLink(path)) fail("Refusing symbolic link for $description: $path")
    }

    private fun readJsonObject(path: Path): Map<*, *> {
        refuseSymbolicLink(path, "manifest")
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) fail("Manifest does not exist: $path")
        return JsonSlurper().parse(path.toFile()) as? Map<*, *> ?: fail("Manifest is not a JSON object: $path")
    }

    private fun Map<*, *>.obj(key: String): Map<*, *> =
        this[key] as? Map<*, *> ?: fail("Manifest field $key is not an object")

    private fun Map<*, *>.list(key: String): List<*> =
        this[key] as? List<*> ?: fail("Manifest field $key is not an array")

    private fun Map<*, *>.str(key: String): String =
        this[key] as? String ?: fail("Manifest field $key is not a string")

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun fail(message: String): Nothing = throw R2DeclarationWorkerBundleException(message)
}

/** Generates `assets/r2-declaration-worker-bundles` from the verified distributions. */
@CacheableTask
abstract class GenerateR2DeclarationWorkerBundles : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val declarationDistributionDirectory: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val typescriptDistributionDirectory: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val tokenCountLockFile: RegularFileProperty

    @get:OutputDirectory
    abstract val bundleDirectory: DirectoryProperty

    init {
        group = "build setup"
        description = "Generates the compiler Worker declaration preload bundles."
    }

    @TaskAction
    fun generate() {
        R2DeclarationWorkerBundles.generate(
            declarationDistributionDirectory = declarationDistributionDirectory.get().asFile,
            typescriptDistributionDirectory = typescriptDistributionDirectory.get().asFile,
            tokenCountLockFile = tokenCountLockFile.get().asFile,
            outputDirectory = bundleDirectory.get().asFile,
        )
    }
}

/** Regenerates the Worker declaration bundles and rejects any byte that differs. */
@DisableCachingByDefault(because = "Verification task intentionally has no outputs")
abstract class VerifyR2DeclarationWorkerBundles : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val declarationDistributionDirectory: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val typescriptDistributionDirectory: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val tokenCountLockFile: RegularFileProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val bundleDirectory: DirectoryProperty

    init {
        group = "verification"
        description = "Verifies the compiler Worker declaration preload bundles."
    }

    @TaskAction
    fun verify() {
        R2DeclarationWorkerBundles.verify(
            declarationDistributionDirectory = declarationDistributionDirectory.get().asFile,
            typescriptDistributionDirectory = typescriptDistributionDirectory.get().asFile,
            tokenCountLockFile = tokenCountLockFile.get().asFile,
            bundleDirectory = bundleDirectory.get().asFile,
        )
    }
}
