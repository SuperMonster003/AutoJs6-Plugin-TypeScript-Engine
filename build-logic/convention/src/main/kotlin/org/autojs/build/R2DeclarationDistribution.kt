package org.autojs.build

import groovy.json.JsonSlurper
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.gradle.api.GradleException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.Base64
import java.util.Comparator
import java.util.Locale
import java.util.TreeMap
import java.util.UUID

data class R2DeclarationDistributionLimits(
    val maximumTarballBytes: Long = 4L * 1024L * 1024L,
    val maximumArchiveEntries: Int = 4_096,
    val maximumArchiveFileBytes: Long = 16L * 1024L * 1024L,
    val maximumArchiveBytes: Long = 64L * 1024L * 1024L,
) {
    init {
        require(maximumTarballBytes > 0L)
        require(maximumArchiveEntries > 0)
        require(maximumArchiveFileBytes > 0L)
        require(maximumArchiveBytes > 0L)
        require(maximumArchiveFileBytes <= maximumArchiveBytes)
    }
}

data class R2DeclarationDigests(
    val sha1: String,
    val sha256: String,
    val integrity: String,
)

class R2DeclarationDistributionException(
    message: String,
    cause: Throwable? = null,
) : GradleException(message, cause)

internal data class R2RhinoDownloadSpec(
    val source: URI,
    val tarballBytes: Long,
    val digests: R2DeclarationDigests,
)

/**
 * Builds the R2 declaration assets from one public, digest-pinned npm tarball
 * and one checked-in plugin declaration layer. The lock owns external source
 * acquisition; the generated manifest owns exact packaged-file integrity.
 */
object R2DeclarationDistribution {

    const val MANIFEST_FILE_NAME = "distribution.json"
    const val GENERATOR_ID = "autojs6-plugin-typescript-engine"
    const val GENERATOR_VERSION = 7
    const val MANIFEST_SCHEMA_VERSION = 2

    private val STABLE_TYPESCRIPT_6_VERSION = Regex("6\\.\\d+\\.\\d+")
    private const val RHINO_PACKAGE = "@sm003/autojs6-dts"
    private const val RHINO_VERSION = "2.1.3"
    private const val RHINO_CONVERSION = "autojs6-rhino-modules-v2"
    private const val DECLARATION_RELEASE_PACKAGE =
        "autojs6-plugin-typescript-engine-declarations"
    private const val NODE_SOURCE_KIND = "plugin-bundled-declaration-layer"
    private const val NODE_PACKAGE_NAME = "@autojs6/node-types"
    private const val LICENSE = "MPL-2.0"

    private const val RHINO_DECLARATION_SOURCE_ROOT =
        "package/declarations/autojs6/"
    private val RHINO_DECLARATION_SOURCE_NAMES = listOf(
        "aj6-declarations.d.ts",
        "aj6-int-app.d.ts",
        "aj6-int-array-observe.d.ts",
        "aj6-int-arrayx.d.ts",
        "aj6-int-autojs.d.ts",
        "aj6-int-automator.d.ts",
        "aj6-int-barcode.d.ts",
        "aj6-int-base64.d.ts",
        "aj6-int-colors.d.ts",
        "aj6-int-console.d.ts",
        "aj6-int-continuation.d.ts",
        "aj6-int-crypto.d.ts",
        "aj6-int-device.d.ts",
        "aj6-int-dialogs.d.ts",
        "aj6-int-engines.d.ts",
        "aj6-int-events.d.ts",
        "aj6-int-files.d.ts",
        "aj6-int-floaty.d.ts",
        "aj6-int-globals.d.ts",
        "aj6-int-http.d.ts",
        "aj6-int-i18n.d.ts",
        "aj6-int-images.d.ts",
        "aj6-int-init.d.ts",
        "aj6-int-jvm-npm.d.ts",
        "aj6-int-mathx.d.ts",
        "aj6-int-media.d.ts",
        "aj6-int-notice.d.ts",
        "aj6-int-numberx.d.ts",
        "aj6-int-object-observe.d.ts",
        "aj6-int-ocr.d.ts",
        "aj6-int-opencc.d.ts",
        "aj6-int-plugins.d.ts",
        "aj6-int-qrcode.d.ts",
        "aj6-int-recorder.d.ts",
        "aj6-int-result-adapter.d.ts",
        "aj6-int-rhino.d.ts",
        "aj6-int-root-automator.d.ts",
        "aj6-int-s13n.d.ts",
        "aj6-int-selector.d.ts",
        "aj6-int-sensors.d.ts",
        "aj6-int-shell.d.ts",
        "aj6-int-shizuku.d.ts",
        "aj6-int-storages.d.ts",
        "aj6-int-tasks.d.ts",
        "aj6-int-threads.d.ts",
        "aj6-int-timers.d.ts",
        "aj6-int-toast.d.ts",
        "aj6-int-ui.d.ts",
        "aj6-int-util.d.ts",
        "aj6-int-web.d.ts",
    )
    private val RHINO_REQUIRED_ARCHIVE_PATHS = (
        RHINO_DECLARATION_SOURCE_NAMES.map { name ->
            "$RHINO_DECLARATION_SOURCE_ROOT$name"
        } + "package/LICENSE"
        ).sorted()

    private const val GENERATOR_DOMAIN =
        "autojs6.typescript.declarations.generator.v1\n"
    private const val LAYER_DOMAIN =
        "autojs6.typescript.declarations.layer.v1\n"
    private const val PROFILE_DOMAIN =
        "autojs6.typescript.declarations.profile.v1\n"
    private const val DISTRIBUTION_DOMAIN =
        "autojs6.typescript.declarations.distribution.v2\n"

    private const val BUFFER_SIZE = 64 * 1024
    private const val MAXIMUM_NODE_SOURCE_FILES = 512
    private const val MAXIMUM_NODE_SOURCE_FILE_BYTES = 8L * 1024L * 1024L
    private const val MAXIMUM_NODE_SOURCE_TOTAL_BYTES = 16L * 1024L * 1024L
    private const val TAR_BLOCK_SIZE = 512
    private const val MAXIMUM_TAR_TRAILING_ZERO_BLOCKS = 20
    private const val TAR_NAME_OFFSET = 0
    private const val TAR_NAME_LENGTH = 100
    private const val TAR_SIZE_OFFSET = 124
    private const val TAR_SIZE_LENGTH = 12
    private const val TAR_CHECKSUM_OFFSET = 148
    private const val TAR_CHECKSUM_LENGTH = 8
    private const val TAR_TYPE_OFFSET = 156
    private const val TAR_PREFIX_OFFSET = 345
    private const val TAR_PREFIX_LENGTH = 155

    val defaultLimits = R2DeclarationDistributionLimits()

    @JvmStatic
    @JvmOverloads
    fun generate(
        lockFile: File,
        rhinoTarballFile: File,
        nodeSourceDirectory: File,
        typescriptDistributionDirectory: File,
        outputDirectory: File,
        declarationReleaseVersion: String,
        limits: R2DeclarationDistributionLimits = defaultLimits,
    ) {
        val expected = reconstruct(
            lockFile,
            rhinoTarballFile,
            nodeSourceDirectory,
            typescriptDistributionDirectory,
            declarationReleaseVersion,
            limits,
        )
        val output = outputDirectory.toPath().toAbsolutePath().normalize()
        val parent = output.parent
            ?: fail("Declaration distribution output must have a parent: $output")
        Files.createDirectories(parent)
        refuseSymbolicLink(output, "declaration distribution output")
        val staging = Files.createTempDirectory(parent, ".${output.fileName}.staging-")
        try {
            expected.files.forEach { (path, bytes) ->
                writeNewFile(staging, path, bytes)
            }
            writeNewFile(staging, MANIFEST_FILE_NAME, expected.manifestBytes)
            verifyGeneratedDirectory(expected, staging)
            publishDirectory(staging, output)
        } catch (e: Throwable) {
            deleteTreeIfPresent(staging)
            if (e is R2DeclarationDistributionException) throw e
            throw R2DeclarationDistributionException(
                "Failed to generate the R2 declaration distribution",
                e,
            )
        }
    }

    @JvmStatic
    @JvmOverloads
    fun verify(
        lockFile: File,
        rhinoTarballFile: File,
        nodeSourceDirectory: File,
        typescriptDistributionDirectory: File,
        distributionDirectory: File,
        declarationReleaseVersion: String,
        limits: R2DeclarationDistributionLimits = defaultLimits,
    ) {
        val expected = reconstruct(
            lockFile,
            rhinoTarballFile,
            nodeSourceDirectory,
            typescriptDistributionDirectory,
            declarationReleaseVersion,
            limits,
        )
        verifyGeneratedDirectory(
            expected,
            distributionDirectory.toPath().toAbsolutePath().normalize(),
        )
    }

    @JvmStatic
    @JvmOverloads
    fun verifyRhinoTarball(
        lockFile: File,
        tarballFile: File,
        maximumTarballBytes: Long = defaultLimits.maximumTarballBytes,
    ): R2DeclarationDigests {
        val lock = readLock(lockFile)
        return readVerifiedRhinoTarball(
            lock.rhino,
            tarballFile.toPath(),
            maximumTarballBytes,
        ).digests
    }

    internal fun rhinoDownloadSpec(lockFile: File): R2RhinoDownloadSpec {
        val rhino = readLock(lockFile).rhino
        return R2RhinoDownloadSpec(
            source = rhino.source,
            tarballBytes = rhino.tarballBytes,
            digests = rhino.digests,
        )
    }

    private fun reconstruct(
        lockFile: File,
        rhinoTarballFile: File,
        nodeSourceDirectory: File,
        typescriptDistributionDirectory: File,
        declarationReleaseVersion: String,
        limits: R2DeclarationDistributionLimits,
    ): ExpectedDistribution {
        validateDeclarationReleaseVersion(declarationReleaseVersion)
        val lock = readLock(lockFile)
        verifyTypeScriptDistributionBinding(lock.common, typescriptDistributionDirectory)
        val rhinoTarball = readVerifiedRhinoTarball(
            lock.rhino,
            rhinoTarballFile.toPath(),
            limits.maximumTarballBytes,
        )
        val rhinoInputs = inspectRhinoArchive(lock.rhino, rhinoTarball.bytes, limits)
        val nodeInputs = readAndVerifyNodeSources(lock.node, nodeSourceDirectory.toPath())

        val files = TreeMap<String, ByteArray>()
        fun add(path: String, bytes: ByteArray) {
            validateRelativePath(path, "generated asset")
            if (files.put(path, bytes) != null) {
                fail("Generated declaration asset path is duplicated: $path")
            }
        }

        val binding = commonBinding(lock.common)
        add("layers/common/binding.json", CanonicalJson.encode(binding))
        val rhinoProjectionFiles = createRhinoProjection(rhinoInputs)
        rhinoProjectionFiles.forEach { (name, bytes) ->
            add("layers/rhino/$name", bytes)
        }

        val nodeDeclarationPaths = nodeInputs.keys
            .filter { path -> path.endsWith(".d.ts") }
            .map { path ->
                val target = "layers/node/$path"
                add(target, nodeInputs.getValue(path))
                target
            }
            .sorted()
        add(
            "layers/node/package.json",
            nodeInputs.getValue(lock.node.packageFile),
        )
        add(
            "licenses/rhino/MPL-2.0.txt",
            rhinoInputs.getValue(lock.rhino.licenseEntry),
        )
        add(
            "licenses/node/MPL-2.0.txt",
            nodeInputs.getValue(lock.node.licenseFile),
        )
        add("notices/rhino-source.txt", rhinoNotice(lock.rhino))
        add(
            "notices/node-source.txt",
            nodeNotice(lock.node, declarationReleaseVersion),
        )

        val fileRecords = files.map { (path, bytes) -> fileRecord(path, bytes) }
        ensureOrdinalPaths(fileRecords.map(TypeScriptDistributionFile::path), "generated files")
        val byPath = fileRecords.associateBy(TypeScriptDistributionFile::path)
        val rhinoDeclarationPaths = rhinoProjectionFiles.keys
            .map { name -> "layers/rhino/$name" }
            .sorted()

        val commonLayer = linkedMapOf<String, Any?>(
            "sourceId" to "common",
            "entryPoint" to null,
            "fileCount" to lock.common.declarationFileCount,
            "totalBytes" to lock.common.declarationTotalBytes,
            "fingerprint" to lock.common.standardLibrariesFingerprint,
            "files" to emptyList<String>(),
        )
        val rhinoLayerFingerprint = layerFingerprint(
            "rhino",
            rhinoDeclarationPaths.map(byPath::getValue),
        )
        val rhinoLayer = linkedMapOf<String, Any?>(
            "sourceId" to "rhino",
            "entryPoint" to "layers/rhino/index.d.ts",
            "fileCount" to rhinoDeclarationPaths.size.toLong(),
            "totalBytes" to rhinoDeclarationPaths.sumOf { byPath.getValue(it).size },
            "fingerprint" to rhinoLayerFingerprint,
            "files" to rhinoDeclarationPaths,
        )
        val nodeLayerFingerprint = layerFingerprint(
            "node",
            nodeDeclarationPaths.map(byPath::getValue),
        )
        val nodeLayer = linkedMapOf<String, Any?>(
            "sourceId" to "node",
            "entryPoint" to "layers/node/index.d.ts",
            "fileCount" to nodeDeclarationPaths.size.toLong(),
            "totalBytes" to nodeDeclarationPaths.sumOf { byPath.getValue(it).size },
            "fingerprint" to nodeLayerFingerprint,
            "files" to nodeDeclarationPaths,
        )
        if (nodeLayer["entryPoint"] !in nodeDeclarationPaths) {
            fail("Node declaration entry point is absent from the locked snapshot")
        }

        val layerFingerprints = mapOf(
            "common" to lock.common.standardLibrariesFingerprint,
            "node" to nodeLayerFingerprint,
            "rhino" to rhinoLayerFingerprint,
        )
        val profiles = mapOf(
            "node" to profileMap(
                id = "node",
                layers = listOf("common", "node"),
                entryPoint = "layers/node/index.d.ts",
                layerFingerprints = layerFingerprints,
            ),
            "rhino" to profileMap(
                id = "rhino",
                layers = listOf("common", "rhino"),
                entryPoint = "layers/rhino/index.d.ts",
                layerFingerprints = layerFingerprints,
            ),
        )
        val layers = mapOf(
            "common" to commonLayer,
            "node" to nodeLayer,
            "rhino" to rhinoLayer,
        )
        val generatorFingerprint = generatorFingerprint()
        val generator = mapOf(
            "id" to GENERATOR_ID,
            "version" to GENERATOR_VERSION.toLong(),
            "fingerprint" to generatorFingerprint,
        )
        val release = mapOf(
            "package" to DECLARATION_RELEASE_PACKAGE,
            "version" to declarationReleaseVersion,
        )
        val sources = sourceDescriptors(lock, declarationReleaseVersion)
        val distributionFingerprint = distributionFingerprint(
            generatorFingerprint,
            release,
            sources,
            layers,
            profiles,
            fileRecords,
        )
        val manifest = mapOf(
            "schemaVersion" to MANIFEST_SCHEMA_VERSION.toLong(),
            "generator" to generator,
            "release" to release,
            "sources" to sources,
            "layers" to layers,
            "profiles" to profiles,
            "files" to fileRecords.map(::fileMap),
            "fingerprint" to distributionFingerprint,
        )
        return ExpectedDistribution(files, CanonicalJson.encode(manifest))
    }

    private fun commonBinding(common: CommonLock): Map<String, Any?> {
        val assetRoot = typeScriptAssetRoot(common.version)
        return mapOf(
            "schemaVersion" to 1L,
            "package" to common.packageName,
            "version" to common.version,
            "distributionAssetRoot" to assetRoot,
            "distributionManifest" to "$assetRoot/distribution.json",
            "distributionFingerprint" to common.distributionFingerprint,
            "standardLibrariesFingerprint" to common.standardLibrariesFingerprint,
            "fileCount" to common.declarationFileCount,
            "totalBytes" to common.declarationTotalBytes,
        )
    }

    private fun sourceDescriptors(
        lock: R2Lock,
        declarationReleaseVersion: String,
    ): Map<String, Map<String, Any?>> {
        val assetRoot = typeScriptAssetRoot(lock.common.version)
        return mapOf(
            "common" to mapOf(
                "kind" to "typescript-standard-libraries",
                "package" to lock.common.packageName,
                "version" to lock.common.version,
                "distributionAssetRoot" to assetRoot,
                "distributionManifest" to "$assetRoot/distribution.json",
                "bindingFile" to "layers/common/binding.json",
                "distributionFingerprint" to lock.common.distributionFingerprint,
                "standardLibrariesFingerprint" to lock.common.standardLibrariesFingerprint,
                "fileCount" to lock.common.declarationFileCount,
                "totalBytes" to lock.common.declarationTotalBytes,
            ),
            "rhino" to mapOf(
                "kind" to "npm-tarball-projection-with-plugin-supplement",
                "package" to lock.rhino.packageName,
                "version" to lock.rhino.version,
                "source" to lock.rhino.source.toASCIIString(),
                "sha1" to lock.rhino.digests.sha1,
                "sha256" to lock.rhino.digests.sha256,
                "integrity" to lock.rhino.digests.integrity,
                "conversion" to lock.rhino.conversion,
                "license" to lock.rhino.license,
                "licenseFile" to "licenses/rhino/MPL-2.0.txt",
                "noticeFile" to "notices/rhino-source.txt",
            ),
            "node" to mapOf(
                "kind" to NODE_SOURCE_KIND,
                "releasePackage" to DECLARATION_RELEASE_PACKAGE,
                "releaseVersion" to declarationReleaseVersion,
                "license" to lock.node.license,
                "licenseFile" to "licenses/node/MPL-2.0.txt",
                "noticeFile" to "notices/node-source.txt",
            ),
        )
    }

    private fun typeScriptAssetRoot(version: String) = "typescript/$version"

    private fun createRhinoProjection(inputs: Map<String, ByteArray>): Map<String, ByteArray> {
        val automator = inputs
            .getValue("package/declarations/autojs6/aj6-int-automator.d.ts")
            .decodeUtf8("Rhino automator declarations")
        val device = inputs.getValue("package/declarations/autojs6/aj6-int-device.d.ts")
            .decodeUtf8("Rhino device declarations")
        val toast = inputs.getValue("package/declarations/autojs6/aj6-int-toast.d.ts")
            .decodeUtf8("Rhino toast declarations")
        val http = inputs.getValue("package/declarations/autojs6/aj6-int-http.d.ts")
            .decodeUtf8("Rhino HTTP declarations")
        val images = inputs.getValue("package/declarations/autojs6/aj6-int-images.d.ts")
            .decodeUtf8("Rhino images declarations")
        val ui = inputs.getValue("package/declarations/autojs6/aj6-int-ui.d.ts")
            .decodeUtf8("Rhino UI declarations")
        val globals = inputs.getValue("package/declarations/autojs6/aj6-int-globals.d.ts")
            .decodeUtf8("Rhino global declarations")
        val init = inputs.getValue("package/declarations/autojs6/aj6-int-init.d.ts")
            .decodeUtf8("Rhino initialization declarations")
        listOf(
            "interface Automator {",
            "click(x: number, y: number): boolean;",
            "swipe(x1: number, y1: number, x2: number, y2: number, duration: number): boolean;",
            "declare function click(x: number, y: number): boolean;",
            "declare function swipe(x1: number, y1: number, x2: number, y2: number, duration: number): boolean;",
        ).forEach { marker -> requireMarker(automator, marker, "automator") }
        listOf(
            "get width(): number;",
            "get height(): number;",
            "model: string;",
            "sdkInt: number;",
        ).forEach { marker -> requireMarker(device, marker, "device") }
        listOf(
            "interface Http {",
            "get(url: string, options?: Http.RequestBuilderOptions, callback?: Http.Callback): Http.WrappedResponse | void;",
            "interface WrappedResponse {",
            "statusCode: number;",
            "string(): string;",
        ).forEach { marker -> requireMarker(http, marker, "HTTP") }
        listOf(
            "interface Images {",
            "captureScreen(): ImageWrapper;",
            "read(path: string): ImageWrapper;",
            "pixel(image: ImageWrapper, x: number, y: number): ColorInt;",
            "recycle(...images: ImageWrapper[]): boolean;",
            "public width: number;",
            "public height: number;",
            "declare function requestScreenCapture(landscape?: boolean): boolean;",
        ).forEach { marker -> requireMarker(images, marker, "images") }
        listOf(
            "class UI extends org.autojs.autojs.runtime.api.UI {",
            "layout(layout: Xml): void;",
            "findView(stringId: string): android.view.View;",
            "finish(): void;",
        ).forEach { marker -> requireMarker(ui, marker, "UI") }
        listOf(
            "interface Toast {",
            "(text: string | any, isLong?: Toast.IsLong, isForcible?: Toast.IsForcible): void;",
            "dismissAll(): void;",
            "type IsLong = 'long' | 'l' | 'short' | 's' | boolean;",
            "type IsForcible = 'forcible' | 'f' | boolean;",
        ).forEach { marker -> requireMarker(toast, marker, "toast") }
        listOf(
            "declare let HEIGHT: number;",
            "declare let WIDTH: number;",
            "declare function sleep(millis: number): void;",
            "declare function sleep(millisMin: number, millisMax: number): void;",
            "declare function sleep(millis: number, bounds: string): void;",
            "declare function toastLog(msg: string | any, isLong?: Toast.IsLong, isForcible?: Toast.IsForcible): void;",
        ).forEach { marker -> requireMarker(globals, marker, "globals") }
        listOf(
            "declare let automator: Internal.Automator;",
            "declare let device: Internal.Device;",
            "declare let http: Internal.Http;",
            "declare let images: Internal.Images;",
            "declare let ui: Internal.UI;",
            "declare let \$ui: Internal.UI;",
            "declare let toast: Internal.Toast;",
            "declare let \$toast: Internal.Toast;",
        ).forEach { marker -> requireMarker(init, marker, "init") }

        val projected = TreeMap<String, ByteArray>()
        RHINO_DECLARATION_SOURCE_NAMES.forEach { sourceName ->
            val sourcePath = "$RHINO_DECLARATION_SOURCE_ROOT$sourceName"
            val targetName = when (sourceName) {
                "aj6-declarations.d.ts" -> "shared.d.ts"
                else -> sourceName.removePrefix("aj6-int-")
            }
            val source = inputs.getValue(sourcePath)
                .decodeUtf8("Rhino declaration $sourceName")
            val transformed = transformRhinoDeclaration(sourceName, source)
            if (projected.put(targetName, transformed.toByteArray(StandardCharsets.UTF_8)) != null) {
                fail("Projected Rhino declaration path is duplicated: $targetName")
            }
        }
        val supplement = composeSupplement()
        listOf("aj6-int-compose.d.ts", "compose-jsx.d.ts").forEach { name ->
            val source = supplement.getValue(name).decodeUtf8("Compose declaration supplement $name")
            val transformed = source.lineSequence()
                .filterNot { it.trimStart().startsWith("/// <reference") }
                .joinToString("\n").trimEnd() + "\n"
            // Match the bounded Rhino profile's native-type policy while preserving
            // checking in user sources, including Compose props and callback types.
            val bytes = ("// @ts-nocheck\n" + transformed).toByteArray(StandardCharsets.UTF_8)
            val target = name.removePrefix("aj6-int-")
            if (projected.put(target, bytes) != null) {
                fail("Compose supplement collides with a Rhino declaration: $target")
            }
        }
        val index = createRhinoProjectionIndex(projected.keys)
        if (
            projected.put(
                "index.d.ts",
                index.toByteArray(StandardCharsets.UTF_8),
            ) != null
        ) {
            fail("Projected Rhino declaration index collides with a source module")
        }
        return projected
    }

    private fun transformRhinoDeclaration(sourceName: String, source: String): String {
        val normalized = source
            .replace("\r\n", "\n")
            .replace('\r', '\n')
        val withoutReferences = normalized.lineSequence()
            .filterNot { line -> line.trimStart().startsWith("/// <reference") }
            .joinToString("\n")
            .trimEnd() + "\n"
        val compatible = when (sourceName) {
            "aj6-int-array-observe.d.ts" -> withoutReferences.replaceExactly(
                oldValue = "declare interface Array {",
                newValue = "declare interface Array<T> {",
                description = "Rhino Array.observe generic compatibility",
            )
            "aj6-int-rhino.d.ts" -> stripConflictingRhinoBuiltins(withoutReferences)
            else -> withoutReferences
        }
        return buildString {
            // The public package targets TypeScript 4.3 and references Android/JVM declaration
            // graphs that are intentionally outside this bounded compiler profile. Suppressing
            // diagnostics inside the trusted .d.ts files does not disable strict checking for
            // user source files; skipLibCheck remains false in the compiler authority.
            append("// @ts-nocheck\n")
            append("// Generated from @sm003/autojs6-dts@2.1.3: ")
            append(sourceName)
            append(".\n")
            append(compatible)
        }
    }

    private fun stripConflictingRhinoBuiltins(source: String): String {
        var result = source
        listOf(
            "ArrayBuffer",
            "Int8Array",
            "Uint8Array",
            "Uint8ClampedArray",
            "Int16Array",
            "Uint16Array",
            "Int32Array",
            "Uint32Array",
            "Float32Array",
            "Float64Array",
            "DataView",
        ).forEach { typeName ->
            val declaration = Regex(
                "(?m)^declare class ${Regex.escape(typeName)} extends [^\\n]+ \\{\\n}\\n?",
            )
            val matches = declaration.findAll(result).toList()
            if (matches.size != 1) {
                fail(
                    "Pinned Rhino declaration must contain exactly one conflicting " +
                        "$typeName declaration; found ${matches.size}",
                )
            }
            result = declaration.replaceFirst(result, "")
        }
        return result.replaceExactly(
            oldValue = "// @ts-ignore\ndeclare let Error: AutoJsError;\n",
            newValue = "",
            description = "Rhino Error constructor compatibility",
        )
    }

    private fun createRhinoProjectionIndex(moduleNames: Collection<String>): String = buildString {
        append("/**\n")
        append(" * AutoJs6 Rhino declaration profile.\n")
        append(" * Public @sm003/autojs6-dts@2.1.3 projection with a bundled Compose supplement.\n")
        append(" * See notices/rhino-source.txt for the separate source identities.\n")
        append(" */\n")
        moduleNames.sorted().forEach { name ->
            append("/// <reference path=\"./")
            append(name)
            append("\" />\n")
        }
        append("\n")
        append("// Strict refinements for APIs exercised by the host runtime contract.\n")
        append("declare namespace Internal {\n")
        append("  interface Http {\n")
        append("    get(url: string, options?: Http.RequestBuilderOptions): Http.WrappedResponse;\n")
        append("    get(\n")
        append("      url: string,\n")
        append("      options: Http.RequestBuilderOptions,\n")
        append("      callback: Http.Callback,\n")
        append("    ): void;\n")
        append("  }\n")
        append("\n")
        append("  interface Images {\n")
        append("    read(path: string, isStrict?: boolean): ImageWrapper | null;\n")
        append("  }\n")
        append("\n")
        append("  interface UI {\n")
        append("    layout(layout: string): void;\n")
        append("    layout(layout: AutoJs6Tsx.Element): void;\n")
        append("    findView(stringId: string): UI.View | null;\n")
        append("  }\n")
        append("}\n")
        append("\n")
        append("declare namespace UI {\n")
        append("  interface View {\n")
        append("    click(listener: () => void): void;\n")
        append("    setText(text: string): void;\n")
        append("  }\n")
        append("}\n")
        append("\n")
        append("// Private compiler/runtime contract for Rhino TSX lowering.\n")
        append("declare namespace AutoJs6Tsx {\n")
        append("  type Scalar = string | number | boolean;\n")
        append("  type TextChild = string | number | boolean | null | undefined;\n")
        append("  type ClickHandler = (this: EventEmitter$, view: UI.View) => void;\n")
        append("  interface BaseAttributes {\n")
        append("    id?: string;\n")
        listOf(
            "w",
            "h",
            "width",
            "height",
            "margin",
            "marginLeft",
            "marginRight",
            "marginTop",
            "marginBottom",
            "marginStart",
            "marginEnd",
            "padding",
            "paddingLeft",
            "paddingRight",
            "paddingTop",
            "paddingBottom",
            "paddingStart",
            "paddingEnd",
            "gravity",
            "layout_gravity",
            "background",
            "alpha",
            "visibility",
            "enabled",
            "clickable",
        ).forEach { attribute ->
            append("    $attribute?: Scalar;\n")
        }
        append("    onClick?: ClickHandler;\n")
        append("  }\n")
        append("  interface Element {\n")
        append("    readonly __autojs6TsxElement: unique symbol;\n")
        append("  }\n")
        append("  type Child = Element | readonly Child[] | TextChild;\n")
        append("  interface ContainerAttributes extends BaseAttributes {\n")
        append("    children?: Child | readonly Child[];\n")
        append("    orientation?: Scalar;\n")
        append("    vertical?: Scalar;\n")
        append("  }\n")
        append("  interface TextAttributes extends BaseAttributes {\n")
        append("    children?: TextChild | readonly TextChild[];\n")
        listOf(
            "text",
            "textSize",
            "size",
            "textColor",
            "textStyle",
            "textAllCaps",
            "allCaps",
            "isAllCaps",
            "hint",
            "hintColor",
            "lines",
            "maxLines",
            "singleLine",
            "inputType",
            "checked",
        ).forEach { attribute ->
            append("    $attribute?: Scalar;\n")
        }
        append("  }\n")
        append("  interface ImageAttributes extends BaseAttributes {\n")
        append("    src?: Scalar;\n")
        append("    tint?: Scalar;\n")
        append("    scaleType?: Scalar;\n")
        append("  }\n")
        append("  interface ControlAttributes extends BaseAttributes {\n")
        append("    checked?: Scalar;\n")
        append("    progress?: Scalar;\n")
        append("    max?: Scalar;\n")
        append("    min?: Scalar;\n")
        append("  }\n")
        append("}\n")
        append("declare const __autojs6TsxFragment: unique symbol;\n")
        append("declare function __autojs6Tsx(\n")
        append("  type: string | typeof __autojs6TsxFragment,\n")
        append("  props: AutoJs6Tsx.BaseAttributes | null,\n")
        append("  ...children: AutoJs6Tsx.Child[]\n")
        append("): AutoJs6Tsx.Element;\n")
        append("declare namespace JSX {\n")
        // TypeScript assigns one global JSX expression type to every tag. Marking
        // it as XML would reject Compose factories and mislabel namespace tags.
        // Like the editor declaration package, leave expression domain checking
        // to the host; attributes and ordinary API calls remain precisely typed.
        append("  interface ElementChildrenAttribute { children: {}; }\n")
        append("  interface IntrinsicElements {\n")
        append("    compose: AutoJs6Tsx.BaseAttributes & { children?: never };\n")
        listOf(
            "actionmenu",
            "appbar",
            "card",
            "drawer",
            "frame",
            "grid",
            "horizontal",
            "linear",
            "list",
            "radiogroup",
            "radios",
            "relative",
            "scroll",
            "tab",
            "tabs",
            "toolbar",
            "vertical",
            "viewflipper",
            "viewpager",
            "viewswitcher",
        ).forEach { tagName ->
            append("    $tagName: AutoJs6Tsx.ContainerAttributes;\n")
        }
        listOf(
            "btn",
            "button",
            "checkbox",
            "checkedtext",
            "edittext",
            "input",
            "radio",
            "radiobutton",
            "switch",
            "text",
            "textclock",
            "textswitcher",
            "togglebutton",
        ).forEach { tagName ->
            append("    $tagName: AutoJs6Tsx.TextAttributes;\n")
        }
        listOf("fab", "image", "imagebutton", "img").forEach { tagName ->
            append("    $tagName: AutoJs6Tsx.ImageAttributes;\n")
        }
        listOf(
            "calendar",
            "canvas",
            "chronometer",
            "console",
            "datepicker",
            "numberpicker",
            "progressbar",
            "quickcontactbadge",
            "ratingbar",
            "search",
            "seekbar",
            "space",
            "spinner",
            "timepicker",
            "video",
            "view",
            "web",
            "webview",
        ).forEach { tagName ->
            append("    $tagName: AutoJs6Tsx.ControlAttributes;\n")
        }
        append("  }\n")
        append("}\n")
        append("\n")
        append("declare namespace android {\n")
        append("  namespace view {\n")
        append("    interface View {\n")
        append("      setText(text: string): void;\n")
        append("    }\n")
        append("  }\n")
        append("}\n")
    }

    private fun String.replaceExactly(
        oldValue: String,
        newValue: String,
        description: String,
    ): String {
        val first = indexOf(oldValue)
        if (first < 0 || first != lastIndexOf(oldValue)) {
            fail("Pinned declaration does not contain exactly one $description marker")
        }
        return replaceRange(first, first + oldValue.length, newValue)
    }

    private fun requireMarker(source: String, marker: String, description: String) {
        if (marker !in source) {
            fail("Pinned Rhino $description declaration surface is missing: $marker")
        }
    }

    private fun rhinoNotice(lock: RhinoLock): ByteArray = (
        "AutoJs6 Rhino declaration source notice\n" +
            "Package: ${lock.packageName}@${lock.version}\n" +
            "Source: ${lock.source.toASCIIString()}\n" +
            "SHA-1: ${lock.digests.sha1}\n" +
            "SHA-256: ${lock.digests.sha256}\n" +
            "Integrity: ${lock.digests.integrity}\n" +
            "Conversion: ${lock.conversion}\n" +
            "License: ${lock.license}\n" +
            "\nPlugin-bundled Compose declaration supplement:\n" +
            composeSupplement().getValue("source.json").decodeUtf8("Compose source notice")
        ).toByteArray(StandardCharsets.UTF_8)

    private fun composeSupplement(): Map<String, ByteArray> {
        fun resource(name: String): ByteArray = R2DeclarationDistribution::class.java
            .getResourceAsStream("/r2-compose/$name")?.use { it.readBytes() }
            ?: fail("Missing bundled Compose declaration resource: $name")
        val source = resource("source.json")
        val metadata = JsonSlurper().parse(source) as? Map<*, *>
            ?: fail("Invalid Compose supplement source metadata")
        if (metadata["license"] != LICENSE ||
            metadata["distribution"] != "local integration snapshot, not an npm release") {
            fail("Invalid Compose supplement provenance")
        }
        val hashes = metadata["files"] as? Map<*, *>
            ?: fail("Compose supplement hashes are missing")
        val names = setOf("aj6-int-compose.d.ts", "compose-jsx.d.ts")
        if (hashes.keys != names) fail("Unexpected Compose supplement source inventory")
        return names.associateWith { name ->
            val bytes = resource(name)
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            if (hashes[name] != digest) fail("Compose supplement digest mismatch: $name")
            bytes
        } + ("source.json" to source)
    }

    private fun nodeNotice(
        lock: NodeLock,
        declarationReleaseVersion: String,
    ): ByteArray = (
        "AutoJs6 Node declaration source notice\n" +
            "Declaration release: " +
            "$DECLARATION_RELEASE_PACKAGE@$declarationReleaseVersion\n" +
            "Source: plugin-bundled r2-declaration-sources/node\n" +
            "Resolver package: $NODE_PACKAGE_NAME\n" +
            "License: ${lock.license}\n" +
            "Packaged file integrity is recorded by distribution.json.\n"
        ).toByteArray(StandardCharsets.UTF_8)

    private fun verifyTypeScriptDistributionBinding(
        common: CommonLock,
        directory: File,
    ) {
        val root = directory.toPath().toAbsolutePath().normalize()
        refuseSymbolicLink(root, "TypeScript distribution root")
        val manifestPath = root.resolve(TypeScriptDistribution.MANIFEST_FILE_NAME)
        refuseSymbolicLink(manifestPath, "TypeScript distribution manifest")
        if (!Files.isRegularFile(manifestPath, LinkOption.NOFOLLOW_LINKS)) {
            fail("TypeScript distribution manifest does not exist: $manifestPath")
        }
        val manifest = parseJsonObject(
            JsonSlurper().parse(manifestPath.toFile()),
            "TypeScript distribution manifest",
        )
        if (manifest.number("schemaVersion") != 1L) {
            fail("TypeScript distribution schemaVersion is not 1")
        }
        val packageInfo = manifest.objectValue("package")
        if (
            packageInfo.string("name") != common.packageName ||
            packageInfo.string("version") != common.version
        ) {
            fail("TypeScript distribution package identity does not match the common lock")
        }
        if (manifest.string("fingerprint") != common.distributionFingerprint) {
            fail("TypeScript distribution fingerprint does not match the common lock")
        }
        val libraries = manifest.objectValue("components")
            .objectValue("standardLibraries")
        if (
            libraries.string("fingerprint") != common.standardLibrariesFingerprint ||
            libraries.number("fileCount") != common.declarationFileCount ||
            libraries.number("totalBytes") != common.declarationTotalBytes
        ) {
            fail("TypeScript standard-library component does not match the common lock")
        }
    }

    private fun inspectRhinoArchive(
        lock: RhinoLock,
        tarballBytes: ByteArray,
        limits: R2DeclarationDistributionLimits,
    ): Map<String, ByteArray> {
        val tarBytes = decompressAndPreflightTarball(tarballBytes, limits)
        val requiredPaths = RHINO_REQUIRED_ARCHIVE_PATHS.toSet()
        val selected = TreeMap<String, ByteArray>()
        try {
            TarArchiveInputStream(
                ByteArrayInputStream(tarBytes),
                StandardCharsets.UTF_8.name(),
            ).use { tarInput ->
                while (true) {
                    val entry = tarInput.nextEntry ?: break
                    val name = validateNpmArchivePath(entry.name)
                    if (!entry.isCheckSumOK || !entry.isFile || !tarInput.canReadEntryData(entry)) {
                        fail("Rhino archive entry cannot be read safely: $name")
                    }
                    val output = if (name in requiredPaths) {
                        ByteArrayOutputStream()
                    } else {
                        null
                    }
                    copyCurrentTarEntry(tarInput, output, entry.size, name)
                    if (output != null && selected.put(name, output.toByteArray()) != null) {
                        fail("Rhino archive contains duplicate selected path: $name")
                    }
                }
            }
        } catch (e: R2DeclarationDistributionException) {
            throw e
        } catch (e: Throwable) {
            throw R2DeclarationDistributionException(
                "Failed to inspect the pinned Rhino npm tarball",
                e,
            )
        }
        if (selected.keys != requiredPaths) {
            fail(
                "Rhino archive selected inventory differs from the modular projection: " +
                    "expected=$requiredPaths, actual=${selected.keys}",
            )
        }
        val licenseText = selected.getValue(lock.licenseEntry)
            .decodeUtf8("Rhino license")
        if ("Mozilla Public License Version 2.0" !in licenseText) {
            fail("Pinned Rhino package license is not MPL-2.0")
        }
        return selected
    }

    private fun readAndVerifyNodeSources(
        lock: NodeLock,
        sourceDirectory: Path,
    ): Map<String, ByteArray> {
        val root = sourceDirectory.toAbsolutePath().normalize()
        refuseSymbolicLink(root, "vendored Node declaration source root")
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            fail("Vendored Node declaration source root does not exist: $root")
        }
        val actual = TreeMap<String, ByteArray>()
        val seenCase = mutableMapOf<String, String>()
        Files.walk(root).use { paths ->
            paths.forEach { path ->
                if (path == root) return@forEach
                val relative = root.relativize(path).joinToString("/") { it.toString() }
                validateRelativePath(relative, "vendored Node source")
                seenCase.putIfAbsent(relative.lowercase(Locale.ROOT), relative)?.let { other ->
                    fail("Vendored Node source has a case-fold collision: $other / $relative")
                }
                if (Files.isSymbolicLink(path)) {
                    fail("Vendored Node source contains symbolic link: $relative")
                }
                when {
                    Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) -> Unit
                    Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) -> {
                        if (
                            relative != lock.licenseFile &&
                            relative != lock.packageFile &&
                            !relative.endsWith(".d.ts")
                        ) {
                            fail("Vendored Node source contains an unclassified file: $relative")
                        }
                        val bytes = Files.readAllBytes(path)
                        if (bytes.size.toLong() > MAXIMUM_NODE_SOURCE_FILE_BYTES) {
                            fail("Vendored Node source exceeds the per-file budget: $relative")
                        }
                        actual[relative] = bytes
                    }
                    else -> fail("Vendored Node source contains unsupported file: $relative")
                }
            }
        }
        if (actual.size > MAXIMUM_NODE_SOURCE_FILES) {
            fail("Vendored Node source exceeds the file-count budget")
        }
        if (
            actual.values.sumOf { bytes -> bytes.size.toLong() } >
            MAXIMUM_NODE_SOURCE_TOTAL_BYTES
        ) {
            fail("Vendored Node source exceeds the aggregate byte budget")
        }
        if (
            lock.licenseFile !in actual ||
            lock.packageFile !in actual ||
            "index.d.ts" !in actual ||
            "input_observer.d.ts" !in actual
        ) {
            fail("Vendored Node source is missing a required support or declaration file")
        }
        val packageInfo = parseJsonObject(
            JsonSlurper().parseText(actual.getValue(lock.packageFile).decodeUtf8("Node package")),
            "vendored Node package.json",
        )
        if (
            packageInfo.string("name") != NODE_PACKAGE_NAME ||
            packageInfo.string("version").isEmpty() ||
            packageInfo.string("types") != "index.d.ts"
        ) {
            fail("Vendored Node package identity or entry point is unexpected")
        }
        val licenseText = actual.getValue(lock.licenseFile).decodeUtf8("Node license")
        if ("Mozilla Public License Version 2.0" !in licenseText) {
            fail("Vendored Node snapshot license is not MPL-2.0")
        }
        return actual
    }

    private fun generatorFingerprint(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(GENERATOR_DOMAIN.toByteArray(StandardCharsets.US_ASCII))
        updateRecord(digest, "id", GENERATOR_ID)
        updateRecord(digest, "version", GENERATOR_VERSION.toString())
        return digest.digest().toLowerHex()
    }

    private fun layerFingerprint(
        id: String,
        files: List<TypeScriptDistributionFile>,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(LAYER_DOMAIN.toByteArray(StandardCharsets.US_ASCII))
        updateRecord(digest, "layer", id)
        files.sortedBy(TypeScriptDistributionFile::path).forEach { file ->
            updateRecord(digest, "file", file.path, file.size.toString(), file.sha256)
        }
        return digest.digest().toLowerHex()
    }

    private fun profileMap(
        id: String,
        layers: List<String>,
        entryPoint: String,
        layerFingerprints: Map<String, String>,
    ): Map<String, Any?> {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(PROFILE_DOMAIN.toByteArray(StandardCharsets.US_ASCII))
        updateRecord(digest, "profile", id)
        layers.forEach { layer ->
            updateRecord(digest, "layer", layer, layerFingerprints.getValue(layer))
        }
        updateRecord(digest, "entryPoint", entryPoint)
        return mapOf(
            "layers" to layers,
            "fingerprint" to digest.digest().toLowerHex(),
        )
    }

    private fun distributionFingerprint(
        generatorFingerprint: String,
        release: Map<String, Any?>,
        sources: Map<String, Map<String, Any? >>,
        layers: Map<String, Map<String, Any? >>,
        profiles: Map<String, Map<String, Any? >>,
        files: List<TypeScriptDistributionFile>,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(DISTRIBUTION_DOMAIN.toByteArray(StandardCharsets.US_ASCII))
        updateRecord(digest, "generator", generatorFingerprint)
        updateRecord(
            digest,
            "release",
            sha256(CanonicalJson.encode(release, trailingNewline = false)),
        )
        sources.toSortedMap().forEach { (id, descriptor) ->
            updateRecord(
                digest,
                "source",
                id,
                sha256(CanonicalJson.encode(descriptor, trailingNewline = false)),
            )
        }
        layers.toSortedMap().forEach { (id, layer) ->
            updateRecord(digest, "layer", id, layer.getValue("fingerprint") as String)
        }
        profiles.toSortedMap().forEach { (id, profile) ->
            updateRecord(digest, "profile", id, profile.getValue("fingerprint") as String)
        }
        files.sortedBy(TypeScriptDistributionFile::path).forEach { file ->
            updateRecord(digest, "file", file.path, file.size.toString(), file.sha256)
        }
        return digest.digest().toLowerHex()
    }

    private fun validateDeclarationReleaseVersion(value: String) {
        if (
            value.isEmpty() ||
            value.length > 128 ||
            !value.matches(DECLARATION_RELEASE_VERSION)
        ) {
            fail("Declaration release version is not a canonical plugin version: $value")
        }
    }

    private fun updateRecord(digest: MessageDigest, vararg fields: String) {
        fields.forEachIndexed { index, field ->
            if (index > 0) digest.update(0.toByte())
            digest.update(field.toByteArray(StandardCharsets.UTF_8))
        }
        digest.update('\n'.code.toByte())
    }

    private fun fileRecord(path: String, bytes: ByteArray) = TypeScriptDistributionFile(
        path = path,
        size = bytes.size.toLong(),
        sha256 = sha256(bytes),
    )

    private fun fileMap(file: TypeScriptDistributionFile): Map<String, Any?> = mapOf(
        "path" to file.path,
        "size" to file.size,
        "sha256" to file.sha256,
    )

    private fun verifyGeneratedDirectory(
        expected: ExpectedDistribution,
        root: Path,
    ) {
        refuseSymbolicLink(root, "declaration distribution root")
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            fail("Declaration distribution directory does not exist: $root")
        }
        val actualFiles = TreeMap<String, TypeScriptDistributionFile>()
        val actualDirectories = mutableSetOf<String>()
        val seenCase = mutableMapOf<String, String>()
        var manifestBytes: ByteArray? = null
        Files.walk(root).use { paths ->
            paths.forEach { path ->
                if (path == root) return@forEach
                val relative = root.relativize(path).joinToString("/") { it.toString() }
                validateRelativePath(relative, "generated declaration distribution")
                seenCase.putIfAbsent(relative.lowercase(Locale.ROOT), relative)?.let { other ->
                    fail("Generated declaration distribution has case collision: $other / $relative")
                }
                if (Files.isSymbolicLink(path)) {
                    fail("Generated declaration distribution contains symlink: $relative")
                }
                when {
                    Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) -> {
                        actualDirectories += relative
                    }
                    Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) -> {
                        val bytes = Files.readAllBytes(path)
                        if (relative == MANIFEST_FILE_NAME) {
                            manifestBytes = bytes
                        } else {
                            actualFiles[relative] = fileRecord(relative, bytes)
                        }
                    }
                    else -> fail("Generated declaration distribution has unsupported file: $relative")
                }
            }
        }
        val expectedRecords = expected.files.map { (path, bytes) -> fileRecord(path, bytes) }
            .associateBy(TypeScriptDistributionFile::path)
            .toSortedMap()
        if (actualFiles != expectedRecords) {
            fail(describeDifference("Declaration distribution files", expectedRecords, actualFiles))
        }
        val expectedDirectories = expected.files.keys
            .flatMap { path -> path.parentDirectories() }
            .toSet()
        if (actualDirectories != expectedDirectories) {
            fail(
                "Declaration distribution directories differ: " +
                    "expected=$expectedDirectories, actual=$actualDirectories",
            )
        }
        val actualManifest = manifestBytes
            ?: fail("Declaration distribution is missing $MANIFEST_FILE_NAME")
        if (!MessageDigest.isEqual(expected.manifestBytes, actualManifest)) {
            fail("$MANIFEST_FILE_NAME is not canonical for the locked declaration sources")
        }
    }

    private fun describeDifference(
        description: String,
        expected: Map<String, TypeScriptDistributionFile>,
        actual: Map<String, TypeScriptDistributionFile>,
    ): String {
        val missing = expected.keys - actual.keys
        val extra = actual.keys - expected.keys
        val changed = (expected.keys intersect actual.keys).filter { expected[it] != actual[it] }
        return "$description differ: missing=$missing, extra=$extra, changed=$changed"
    }

    private fun writeNewFile(root: Path, relative: String, bytes: ByteArray) {
        validateRelativePath(relative, "generated asset")
        val target = root.resolve(relative).normalize()
        if (!target.startsWith(root)) fail("Generated asset escapes output: $relative")
        Files.createDirectories(target.parent)
        Files.write(
            target,
            bytes,
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE,
        )
    }

    private fun publishDirectory(staging: Path, output: Path) {
        if (!Files.exists(output, LinkOption.NOFOLLOW_LINKS)) {
            moveDirectory(staging, output)
            return
        }
        refuseSymbolicLink(output, "existing declaration distribution output")
        if (!Files.isDirectory(output, LinkOption.NOFOLLOW_LINKS)) {
            fail("Declaration distribution output exists and is not a directory: $output")
        }
        val backup = output.parent.resolve(".${output.fileName}.backup-${UUID.randomUUID()}")
        moveDirectory(output, backup)
        try {
            moveDirectory(staging, output)
        } catch (publishError: Throwable) {
            try {
                moveDirectory(backup, output)
            } catch (restoreError: Throwable) {
                publishError.addSuppressed(restoreError)
            }
            throw publishError
        }
        deleteTreeIfPresent(backup)
    }

    private fun moveDirectory(source: Path, target: Path) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, target)
        }
    }

    private fun deleteTreeIfPresent(root: Path) {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return
        Files.walk(root).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    private fun readVerifiedRhinoTarball(
        lock: RhinoLock,
        tarball: Path,
        maximumBytes: Long,
    ): VerifiedRhinoTarball {
        if (maximumBytes <= 0L) fail("Maximum Rhino tarball size must be positive")
        refuseSymbolicLink(tarball, "Rhino npm tarball")
        if (!Files.isRegularFile(tarball, LinkOption.NOFOLLOW_LINKS)) {
            fail("Rhino npm tarball does not exist: $tarball")
        }
        val sha1 = MessageDigest.getInstance("SHA-1")
        val sha256 = MessageDigest.getInstance("SHA-256")
        val sha512 = MessageDigest.getInstance("SHA-512")
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_SIZE)
        var size = 0L
        Files.newInputStream(tarball).buffered().use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                if (size > maximumBytes - read) {
                    fail("Rhino npm tarball exceeds $maximumBytes bytes")
                }
                size += read
                sha1.update(buffer, 0, read)
                sha256.update(buffer, 0, read)
                sha512.update(buffer, 0, read)
                output.write(buffer, 0, read)
            }
        }
        if (size != lock.tarballBytes) {
            fail("Rhino npm tarball size mismatch: expected=${lock.tarballBytes}, actual=$size")
        }
        val actual = R2DeclarationDigests(
            sha1 = sha1.digest().toLowerHex(),
            sha256 = sha256.digest().toLowerHex(),
            integrity = "sha512-${Base64.getEncoder().encodeToString(sha512.digest())}",
        )
        if (actual != lock.digests) {
            fail("Rhino npm tarball digest mismatch: expected=${lock.digests}, actual=$actual")
        }
        return VerifiedRhinoTarball(output.toByteArray(), actual)
    }

    private fun decompressAndPreflightTarball(
        tarballBytes: ByteArray,
        limits: R2DeclarationDistributionLimits,
    ): ByteArray {
        if (limits.maximumArchiveBytes > Int.MAX_VALUE) {
            fail("maximumArchiveBytes exceeds the in-memory preflight limit")
        }
        try {
            val compressedInput = ByteArrayInputStream(tarballBytes)
            val tarOutput = ByteArrayOutputStream()
            // Gradle's parent classloader currently supplies commons-compress 1.26.x. Use its
            // stable constructor API and prove exact single-member consumption below.
            GzipCompressorInputStream(compressedInput, false)
                .use { gzipInput ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var expanded = 0L
                    while (true) {
                        val read = gzipInput.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        if (expanded > limits.maximumArchiveBytes - read) {
                            fail("Rhino archive expands beyond ${limits.maximumArchiveBytes} bytes")
                        }
                        expanded += read
                        tarOutput.write(buffer, 0, read)
                    }
                    if (
                        gzipInput.compressedCount != tarballBytes.size.toLong() ||
                        compressedInput.available() != 0
                    ) {
                        fail("Rhino npm tarball contains trailing compressed data")
                    }
                }
            return tarOutput.toByteArray().also { preflightTarArchive(it, limits) }
        } catch (e: R2DeclarationDistributionException) {
            throw e
        } catch (e: Throwable) {
            throw R2DeclarationDistributionException(
                "Failed to decompress the Rhino npm tarball safely",
                e,
            )
        }
    }

    private fun preflightTarArchive(
        bytes: ByteArray,
        limits: R2DeclarationDistributionLimits,
    ) {
        if (bytes.isEmpty() || bytes.size % TAR_BLOCK_SIZE != 0) {
            fail("Expanded Rhino tar stream is empty or not aligned to 512-byte blocks")
        }
        val names = mutableMapOf<String, String>()
        var offset = 0
        var entries = 0
        var fileBytes = 0L
        while (offset < bytes.size) {
            if (bytes.isZeroBlock(offset)) {
                if (
                    offset + 2 * TAR_BLOCK_SIZE > bytes.size ||
                    !bytes.isZeroBlock(offset + TAR_BLOCK_SIZE)
                ) {
                    fail("Rhino tar archive must terminate with at least two zero blocks")
                }
                val trailingBlocks = (bytes.size - offset) / TAR_BLOCK_SIZE
                if (trailingBlocks > MAXIMUM_TAR_TRAILING_ZERO_BLOCKS) {
                    fail("Rhino tar archive has too many trailing zero blocks: $trailingBlocks")
                }
                if (bytes.anyNonZero(offset, bytes.size)) {
                    fail("Rhino tar archive contains data after its end marker")
                }
                return
            }
            verifyTarHeaderChecksum(bytes, offset)
            entries += 1
            if (entries > limits.maximumArchiveEntries) {
                fail("Rhino archive contains more than ${limits.maximumArchiveEntries} entries")
            }
            val type = bytes[offset + TAR_TYPE_OFFSET].toInt() and 0xff
            if (type != 0 && type != '0'.code) {
                val label = when (type.toChar()) {
                    '1' -> "hard link"
                    '2' -> "symbolic link"
                    'g' -> "global PAX metadata"
                    'x' -> "PAX metadata"
                    'K' -> "GNU long-link metadata"
                    'L' -> "GNU long-name metadata"
                    else -> "type 0x${type.toString(16).padStart(2, '0')}"
                }
                fail("Rhino archive contains unsupported $label entry")
            }
            val name = readTarEntryName(bytes, offset)
            validateNpmArchivePath(name)
            names.putIfAbsent(name.lowercase(Locale.ROOT), name)?.let { existing ->
                fail("Rhino archive contains duplicate or case collision: $existing / $name")
            }
            val declaredSize = parseTarOctal(
                bytes,
                offset + TAR_SIZE_OFFSET,
                TAR_SIZE_LENGTH,
                "size",
            )
            if (declaredSize > limits.maximumArchiveFileBytes) {
                fail("Rhino archive file exceeds limit: $name ($declaredSize bytes)")
            }
            if (fileBytes > limits.maximumArchiveBytes - declaredSize) {
                fail("Rhino archive expands beyond ${limits.maximumArchiveBytes} bytes")
            }
            fileBytes += declaredSize
            val dataOffset = offset.toLong() + TAR_BLOCK_SIZE
            val padded = (declaredSize + TAR_BLOCK_SIZE - 1L) / TAR_BLOCK_SIZE * TAR_BLOCK_SIZE
            val next = dataOffset + padded
            if (next > bytes.size.toLong() || next > Int.MAX_VALUE) {
                fail("Rhino tar archive entry is truncated: $name")
            }
            if (bytes.anyNonZero((dataOffset + declaredSize).toInt(), next.toInt())) {
                fail("Rhino tar archive entry has non-zero padding: $name")
            }
            offset = next.toInt()
        }
        fail("Rhino tar archive is missing its two-block end marker")
    }

    private fun verifyTarHeaderChecksum(bytes: ByteArray, offset: Int) {
        val expected = parseTarOctal(
            bytes,
            offset + TAR_CHECKSUM_OFFSET,
            TAR_CHECKSUM_LENGTH,
            "checksum",
        )
        var actual = 0L
        for (index in 0 until TAR_BLOCK_SIZE) {
            actual += when (index) {
                in TAR_CHECKSUM_OFFSET until TAR_CHECKSUM_OFFSET + TAR_CHECKSUM_LENGTH -> 0x20
                else -> bytes[offset + index].toInt() and 0xff
            }
        }
        if (actual != expected) {
            fail("Rhino tar header checksum mismatch: expected=$expected, actual=$actual")
        }
    }

    private fun readTarEntryName(bytes: ByteArray, offset: Int): String {
        val name = readTarString(bytes, offset + TAR_NAME_OFFSET, TAR_NAME_LENGTH, "name")
        val prefix = readTarString(
            bytes,
            offset + TAR_PREFIX_OFFSET,
            TAR_PREFIX_LENGTH,
            "prefix",
        )
        if (name.isEmpty()) fail("Rhino tar header has an empty entry name")
        return if (prefix.isEmpty()) name else "$prefix/$name"
    }

    private fun readTarString(
        bytes: ByteArray,
        offset: Int,
        length: Int,
        field: String,
    ): String {
        val end = offset + length
        var terminator = offset
        while (terminator < end && bytes[terminator].toInt() != 0) terminator += 1
        for (index in terminator until end) {
            if (bytes[index].toInt() != 0) {
                fail("Rhino tar $field field has data after NUL")
            }
        }
        return String(bytes, offset, terminator - offset, StandardCharsets.US_ASCII)
    }

    private fun parseTarOctal(
        bytes: ByteArray,
        offset: Int,
        length: Int,
        field: String,
    ): Long {
        val end = offset + length
        if (bytes[offset].toInt() and 0x80 != 0) {
            fail("Rhino tar $field uses unsupported base-256 encoding")
        }
        var index = offset
        while (index < end && (bytes[index].toInt() == 0 || bytes[index].toInt() == 0x20)) {
            index += 1
        }
        var value = 0L
        var digits = 0
        while (index < end && bytes[index].toInt() in '0'.code..'7'.code) {
            val digit = bytes[index].toInt() - '0'.code
            if (value > (Long.MAX_VALUE - digit) / 8L) fail("Rhino tar $field overflows")
            value = value * 8L + digit
            digits += 1
            index += 1
        }
        while (index < end) {
            if (bytes[index].toInt() != 0 && bytes[index].toInt() != 0x20) {
                fail("Rhino tar $field is not canonical octal")
            }
            index += 1
        }
        return if (digits == 0) 0L else value
    }

    private fun copyCurrentTarEntry(
        input: InputStream,
        output: ByteArrayOutputStream?,
        declaredSize: Long,
        name: String,
    ) {
        val buffer = ByteArray(BUFFER_SIZE)
        var actual = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read == 0) continue
            if (actual > declaredSize - read) {
                fail("Rhino archive entry exceeds declared size: $name")
            }
            actual += read
            output?.write(buffer, 0, read)
        }
        if (actual != declaredSize) {
            fail("Rhino archive entry size mismatch: $name")
        }
    }

    private fun validateNpmArchivePath(raw: String): String {
        validateRelativePath(raw, "Rhino archive")
        if (raw != "package" && !raw.startsWith("package/")) {
            fail("Rhino archive entry is outside the npm package root: $raw")
        }
        return raw
    }

    private fun validateRelativePath(path: String, description: String) {
        if (path.isEmpty()) fail("$description contains an empty path")
        if (path.any { it.code < 0x21 || it.code > 0x7e }) {
            fail("$description path is not printable ASCII: ${path.asPrintablePath()}")
        }
        if ('\\' in path || path.startsWith('/') || DRIVE_PATH.matchesAt(path, 0)) {
            fail("$description path is absolute or non-portable: $path")
        }
        val parts = path.split('/')
        if (parts.any { it.isEmpty() || it == "." || it == ".." }) {
            fail("$description path is not canonical: $path")
        }
    }

    private fun ensureOrdinalPaths(paths: List<String>, description: String) {
        if (paths != paths.sorted()) fail("$description are not in ordinal path order")
        val folded = mutableSetOf<String>()
        paths.forEach { path ->
            validateRelativePath(path, description)
            if (!folded.add(path.lowercase(Locale.ROOT))) {
                fail("$description contain a case-fold collision: $path")
            }
        }
    }

    private fun refuseSymbolicLink(path: Path, description: String) {
        if (Files.isSymbolicLink(path)) fail("$description must not be a symbolic link: $path")
    }

    private fun String.parentDirectories(): List<String> {
        val result = mutableListOf<String>()
        var parent = substringBeforeLast('/', "")
        while (parent.isNotEmpty()) {
            result += parent
            parent = parent.substringBeforeLast('/', "")
        }
        return result
    }

    private fun String.asPrintablePath(): String = buildString {
        this@asPrintablePath.forEach { character ->
            if (character.code in 0x20..0x7e) append(character)
            else append("\\u%04x".format(character.code))
        }
    }

    private fun ByteArray.isZeroBlock(offset: Int): Boolean =
        !anyNonZero(offset, offset + TAR_BLOCK_SIZE)

    private fun ByteArray.anyNonZero(start: Int, end: Int): Boolean {
        for (index in start until end) if (this[index].toInt() != 0) return true
        return false
    }

    private fun ByteArray.decodeUtf8(description: String): String = try {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(this))
            .toString()
    } catch (e: Throwable) {
        throw R2DeclarationDistributionException("$description is not valid UTF-8", e)
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toLowerHex()

    private fun ByteArray.toLowerHex(): String =
        joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun fail(message: String): Nothing =
        throw R2DeclarationDistributionException(message)

    private val DRIVE_PATH = Regex("^[A-Za-z]:")
    private val DECLARATION_RELEASE_VERSION =
        Regex("[0-9A-Za-z][0-9A-Za-z._+\\-]{0,127}")

    private data class ExpectedDistribution(
        val files: Map<String, ByteArray>,
        val manifestBytes: ByteArray,
    )

    private data class VerifiedRhinoTarball(
        val bytes: ByteArray,
        val digests: R2DeclarationDigests,
    )

    private data class CommonLock(
        val packageName: String,
        val version: String,
        val distributionFingerprint: String,
        val standardLibrariesFingerprint: String,
        val declarationFileCount: Long,
        val declarationTotalBytes: Long,
    )

    private data class RhinoLock(
        val packageName: String,
        val version: String,
        val source: URI,
        val tarballBytes: Long,
        val digests: R2DeclarationDigests,
        val conversion: String,
        val license: String,
        val licenseEntry: String,
    )

    private data class NodeLock(
        val sourceDirectory: String,
        val license: String,
        val licenseFile: String,
        val packageFile: String,
    )

    private data class R2Lock(
        val common: CommonLock,
        val rhino: RhinoLock,
        val node: NodeLock,
    )

    private fun readLock(file: File): R2Lock {
        if (!file.isFile) fail("R2 declaration lock does not exist: $file")
        val root = try {
            parseJsonObject(JsonSlurper().parse(file), "R2 declaration lock")
        } catch (e: R2DeclarationDistributionException) {
            throw e
        } catch (e: Throwable) {
            throw R2DeclarationDistributionException("R2 declaration lock is invalid JSON", e)
        }
        root.requireExactKeys(setOf("schemaVersion", "common", "rhino", "node"))
        if (root.number("schemaVersion") != 2L) fail("Unsupported R2 declaration lock schema")

        val commonObject = root.objectValue("common").also {
            it.requireExactKeys(
                setOf(
                    "package",
                    "version",
                    "distributionFingerprint",
                    "standardLibrariesFingerprint",
                    "declarationFileCount",
                    "declarationTotalBytes",
                ),
            )
        }
        val common = CommonLock(
            packageName = commonObject.string("package"),
            version = commonObject.string("version"),
            distributionFingerprint = commonObject.lowerHex("distributionFingerprint", 64),
            standardLibrariesFingerprint = commonObject.lowerHex(
                "standardLibrariesFingerprint",
                64,
            ),
            declarationFileCount = commonObject.number("declarationFileCount"),
            declarationTotalBytes = commonObject.number("declarationTotalBytes"),
        )
        if (common.packageName != "typescript") {
            fail("R2 common declaration lock must select the TypeScript package")
        }
        if (!STABLE_TYPESCRIPT_6_VERSION.matches(common.version)) {
            fail("R2 common declaration lock must select a stable TypeScript 6.x version")
        }
        if (common.declarationFileCount <= 0 || common.declarationTotalBytes <= 0) {
            fail("R2 common declaration lock must declare a positive library inventory")
        }

        val rhinoObject = root.objectValue("rhino").also {
            it.requireExactKeys(
                setOf(
                    "package", "version", "source", "tarballBytes", "sha1", "sha256",
                    "integrity", "conversion", "license", "licenseEntry",
                ),
            )
        }
        val rhinoVersion = rhinoObject.string("version")
        val rhinoSourceText = rhinoObject.string("source")
        val expectedRhinoSource =
            "https://registry.npmjs.org/@sm003/autojs6-dts/-/autojs6-dts-$rhinoVersion.tgz"
        if (rhinoSourceText != expectedRhinoSource) {
            fail("R2 Rhino source must be the exact scoped npm registry URL")
        }
        val rhinoSource = try {
            URI(rhinoSourceText)
        } catch (e: Throwable) {
            throw R2DeclarationDistributionException("R2 Rhino source URI is invalid", e)
        }
        if (
            rhinoSource.scheme != "https" ||
            rhinoSource.host != "registry.npmjs.org" ||
            rhinoSource.userInfo != null ||
            rhinoSource.query != null ||
            rhinoSource.fragment != null
        ) {
            fail("R2 Rhino source is not a safe HTTPS npm URL")
        }
        val integrity = rhinoObject.string("integrity")
        validateIntegrity(integrity)
        val rhino = RhinoLock(
            packageName = rhinoObject.string("package"),
            version = rhinoVersion,
            source = rhinoSource,
            tarballBytes = rhinoObject.positiveNumber("tarballBytes"),
            digests = R2DeclarationDigests(
                sha1 = rhinoObject.lowerHex("sha1", 40),
                sha256 = rhinoObject.lowerHex("sha256", 64),
                integrity = integrity,
            ),
            conversion = rhinoObject.string("conversion"),
            license = rhinoObject.string("license"),
            licenseEntry = rhinoObject.string("licenseEntry"),
        )
        if (
            rhino.packageName != RHINO_PACKAGE ||
            rhino.version != RHINO_VERSION ||
            rhino.conversion != RHINO_CONVERSION ||
            rhino.license != LICENSE ||
            rhino.licenseEntry != "package/LICENSE"
        ) {
            fail("R2 Rhino lock identity, conversion, or license is not frozen")
        }

        val nodeObject = root.objectValue("node").also {
            it.requireExactKeys(
                setOf(
                    "sourceDirectory", "license", "licenseFile", "packageFile",
                ),
            )
        }
        val node = NodeLock(
            sourceDirectory = nodeObject.string("sourceDirectory"),
            license = nodeObject.string("license"),
            licenseFile = nodeObject.string("licenseFile"),
            packageFile = nodeObject.string("packageFile"),
        )
        if (
            node.sourceDirectory != "r2-declaration-sources/node" ||
            node.license != LICENSE ||
            node.licenseFile != "LICENSE" ||
            node.packageFile != "package.json"
        ) {
            fail("R2 Node lock source directory, license, or support files are invalid")
        }

        val lock = R2Lock(common, rhino, node)
        val canonical = CanonicalJson.encode(lockMap(lock))
        val actual = Files.readAllBytes(file.toPath())
        if (!MessageDigest.isEqual(canonical, actual)) {
            val mismatch = firstMismatch(canonical, actual)
            fail(
                "R2 declaration lock is not canonical or contains unrepresented data: " +
                    "expectedBytes=${canonical.size}, actualBytes=${actual.size}, " +
                    "firstMismatch=$mismatch, " +
                    "expectedNear=${mismatchSnippet(canonical, mismatch)}, " +
                    "actualNear=${mismatchSnippet(actual, mismatch)}",
            )
        }
        return lock
    }

    private fun lockMap(lock: R2Lock): Map<String, Any?> = mapOf(
        "schemaVersion" to 2L,
        "common" to mapOf(
            "package" to lock.common.packageName,
            "version" to lock.common.version,
            "distributionFingerprint" to lock.common.distributionFingerprint,
            "standardLibrariesFingerprint" to lock.common.standardLibrariesFingerprint,
            "declarationFileCount" to lock.common.declarationFileCount,
            "declarationTotalBytes" to lock.common.declarationTotalBytes,
        ),
        "rhino" to mapOf(
            "package" to lock.rhino.packageName,
            "version" to lock.rhino.version,
            "source" to lock.rhino.source.toASCIIString(),
            "tarballBytes" to lock.rhino.tarballBytes,
            "sha1" to lock.rhino.digests.sha1,
            "sha256" to lock.rhino.digests.sha256,
            "integrity" to lock.rhino.digests.integrity,
            "conversion" to lock.rhino.conversion,
            "license" to lock.rhino.license,
            "licenseEntry" to lock.rhino.licenseEntry,
        ),
        "node" to mapOf(
            "sourceDirectory" to lock.node.sourceDirectory,
            "license" to lock.node.license,
            "licenseFile" to lock.node.licenseFile,
            "packageFile" to lock.node.packageFile,
        ),
    )

    private fun validateIntegrity(value: String) {
        if (!value.startsWith("sha512-")) fail("R2 Rhino integrity must use SHA-512 SRI")
        val decoded = try {
            Base64.getDecoder().decode(value.removePrefix("sha512-"))
        } catch (e: IllegalArgumentException) {
            throw R2DeclarationDistributionException("R2 Rhino SRI is not valid Base64", e)
        }
        if (decoded.size != 64) fail("R2 Rhino SHA-512 SRI has the wrong length")
        if ("sha512-${Base64.getEncoder().encodeToString(decoded)}" != value) {
            fail("R2 Rhino SHA-512 SRI is not canonical")
        }
    }

    private fun firstMismatch(expected: ByteArray, actual: ByteArray): Int {
        val common = minOf(expected.size, actual.size)
        for (index in 0 until common) {
            if (expected[index] != actual[index]) return index
        }
        return common
    }

    private fun mismatchSnippet(bytes: ByteArray, mismatch: Int): String {
        val start = maxOf(0, mismatch - 16)
        val end = minOf(bytes.size, mismatch + 48)
        return String(bytes, start, end - start, StandardCharsets.UTF_8)
            .replace("\n", "\\n")
    }

    private class JsonObject(
        private val values: Map<String, Any?>,
        private val description: String,
    ) {
        fun requireExactKeys(expected: Set<String>) {
            if (values.keys != expected) {
                fail("$description keys differ: expected=$expected, actual=${values.keys}")
            }
        }

        fun string(key: String): String {
            val value = values[key] as? String
                ?: fail("$description is missing string '$key'")
            if (value.isEmpty()) fail("$description has empty string '$key'")
            return value
        }

        fun lowerHex(key: String, length: Int): String {
            val value = string(key)
            if (value.length != length || value.any { it !in '0'..'9' && it !in 'a'..'f' }) {
                fail("$description '$key' must be $length lowercase hex characters")
            }
            return value
        }

        fun number(key: String): Long {
            val value = values[key] as? Number
                ?: fail("$description is missing integer '$key'")
            val text = value.toString()
            if (!CANONICAL_NON_NEGATIVE_INTEGER.matches(text)) {
                fail("$description '$key' is not a canonical non-negative integer")
            }
            return text.toLongOrNull()
                ?: fail("$description '$key' exceeds 64-bit integer range")
        }

        fun positiveNumber(key: String): Long = number(key).also {
            if (it <= 0L) fail("$description '$key' must be positive")
        }

        fun objectValue(key: String): JsonObject =
            parseJsonObject(values[key], "$description.$key")

        fun array(key: String): List<Any?> = values[key] as? List<*>
            ?: fail("$description is missing array '$key'")
    }

    private fun parseJsonObject(value: Any?, description: String): JsonObject {
        val raw = value as? Map<*, *> ?: fail("$description must be an object")
        val typed = LinkedHashMap<String, Any?>()
        raw.forEach { (key, item) ->
            val stringKey = key as? String ?: fail("$description has a non-string key")
            typed[stringKey] = item
        }
        return JsonObject(typed, description)
    }

    private object CanonicalJson {
        fun encode(value: Any?, trailingNewline: Boolean = true): ByteArray =
            buildString {
                appendValue(value)
                if (trailingNewline) append('\n')
            }.toByteArray(StandardCharsets.UTF_8)

        private fun StringBuilder.appendValue(value: Any?) {
            when (value) {
                null -> append("null")
                is Boolean -> append(if (value) "true" else "false")
                is Byte, is Short, is Int, is Long -> append(value.toString())
                is String -> appendString(value)
                is List<*> -> {
                    append('[')
                    value.forEachIndexed { index, item ->
                        if (index > 0) append(',')
                        appendValue(item)
                    }
                    append(']')
                }
                is Map<*, *> -> {
                    val entries = value.entries.map { entry ->
                        val key = entry.key as? String
                            ?: fail("Canonical JSON keys must be strings")
                        key to entry.value
                    }.sortedBy { it.first }
                    append('{')
                    entries.forEachIndexed { index, (key, item) ->
                        if (index > 0) append(',')
                        appendString(key)
                        append(':')
                        appendValue(item)
                    }
                    append('}')
                }
                else -> fail("Unsupported canonical JSON value: ${value::class.java.name}")
            }
        }

        private fun StringBuilder.appendString(value: String) {
            append('"')
            value.forEach { character ->
                when (character) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\b' -> append("\\b")
                    '\u000c' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> if (character.code < 0x20) {
                        append("\\u%04x".format(character.code))
                    } else {
                        append(character)
                    }
                }
            }
            append('"')
        }
    }

    private val CANONICAL_NON_NEGATIVE_INTEGER = Regex("0|[1-9][0-9]*")

}
