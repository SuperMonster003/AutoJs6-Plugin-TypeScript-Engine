package org.autojs.build

import groovy.json.JsonSlurper
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarConstants
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Base64
import java.util.TreeMap

class R2DeclarationDistributionTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `generation is deterministic and verification rejects output drift`() {
        val fixture = fixture("canonical", validArchiveEntries())
        val first = temporaryFolder.newFolder("first-output")
        val second = temporaryFolder.newFolder("second-output")

        generate(fixture, first)
        generate(fixture, second)
        verify(fixture, first)
        verify(fixture, second)
        val firstSnapshot = snapshot(first)
        val secondSnapshot = snapshot(second)
        assertEquals(firstSnapshot.keys, secondSnapshot.keys)
        firstSnapshot.forEach { (path, bytes) ->
            assertArrayEquals(path, bytes, secondSnapshot.getValue(path))
        }

        val manifestBytes = firstSnapshot.getValue("distribution.json")
        val manifestText = manifestBytes.toString(StandardCharsets.UTF_8)
        assertTrue(manifestText.endsWith("\n"))
        assertFalse(manifestText.contains('\r'))
        @Suppress("UNCHECKED_CAST")
        val manifest = JsonSlurper().parseText(manifestText) as Map<String, Any?>
        assertEquals(
            setOf(
                "files",
                "fingerprint",
                "generator",
                "layers",
                "profiles",
                "release",
                "schemaVersion",
                "sources",
            ),
            manifest.keys,
        )
        assertEquals(2, (manifest.getValue("schemaVersion") as Number).toInt())
        @Suppress("UNCHECKED_CAST")
        val release = manifest.getValue("release") as Map<String, String>
        assertEquals(
            mapOf(
                "package" to "autojs6-plugin-typescript-engine-declarations",
                "version" to DECLARATION_RELEASE_VERSION,
            ),
            release,
        )
        @Suppress("UNCHECKED_CAST")
        val sources = manifest.getValue("sources") as Map<String, Map<String, Any?>>
        val nodeSource = sources.getValue("node")
        assertEquals("plugin-bundled-declaration-layer", nodeSource["kind"])
        assertEquals(DECLARATION_RELEASE_VERSION, nodeSource["releaseVersion"])
        assertFalse(nodeSource.containsKey("commit"))
        assertFalse(nodeSource.containsKey("hostBuild"))
        assertFalse(nodeSource.containsKey("repositoryIdentity"))
        @Suppress("UNCHECKED_CAST")
        val layers = manifest["layers"] as Map<String, Map<String, Any?>>
        assertEquals(108, (layers.getValue("common").getValue("fileCount") as Number).toInt())
        assertEquals(51, (layers.getValue("rhino").getValue("fileCount") as Number).toInt())
        assertEquals(59, (layers.getValue("node").getValue("fileCount") as Number).toInt())
        @Suppress("UNCHECKED_CAST")
        val nodeFiles = layers.getValue("node").getValue("files") as List<String>
        assertTrue("layers/node/input_observer.d.ts" in nodeFiles)
        val rhinoFiles = firstSnapshot.filterKeys { path ->
            path.startsWith("layers/rhino/") && path.endsWith(".d.ts")
        }
        assertEquals(51, rhinoFiles.size)
        listOf(
            "layers/rhino/app.d.ts",
            "layers/rhino/console.d.ts",
            "layers/rhino/dialogs.d.ts",
            "layers/rhino/files.d.ts",
            "layers/rhino/storages.d.ts",
            "layers/rhino/threads.d.ts",
            "layers/rhino/ui.d.ts",
        ).forEach { path -> assertTrue("projection must contain $path", path in rhinoFiles) }
        val index = rhinoFiles.getValue("layers/rhino/index.d.ts")
            .toString(StandardCharsets.UTF_8)
        assertTrue(index.contains("/// <reference path=\"./app.d.ts\" />"))
        assertTrue(index.contains("/// <reference path=\"./ui.d.ts\" />"))
        listOf(
            "layout(layout: AutoJs6Tsx.Element): void;",
            "declare namespace AutoJs6Tsx {",
            "declare function __autojs6Tsx(",
            "declare const __autojs6TsxFragment: unique symbol;",
            "declare namespace JSX {",
            "interface ContainerAttributes extends BaseAttributes",
            "interface TextAttributes extends BaseAttributes",
            "vertical: AutoJs6Tsx.ContainerAttributes;",
            "text: AutoJs6Tsx.TextAttributes;",
            "button: AutoJs6Tsx.TextAttributes;",
        ).forEach { marker ->
            assertTrue("Rhino TSX projection must contain $marker", index.contains(marker))
        }
        assertFalse(
            "Rhino TSX projection must reject unknown intrinsic tags and attributes",
            index.contains("[tagName: string]") || index.contains("[attribute: string]"),
        )
        val projection = rhinoFiles.values.joinToString("\n") { bytes ->
            bytes.toString(StandardCharsets.UTF_8)
        }
        listOf(
            "HEIGHT",
            "WIDTH",
            "\$toast",
            "automator",
            "captureScreen",
            "click",
            "device",
            "http",
            "images",
            "requestScreenCapture",
            "sleep",
            "swipe",
            "toast",
            "toastLog",
            "ui",
        ).forEach {
            assertTrue("projection must contain $it", it in projection)
        }
        assertFalse(projection.contains("canary", ignoreCase = true))
        assertTrue(
            rhinoFiles.getValue("layers/rhino/device.d.ts")
                .toString(StandardCharsets.UTF_8)
                .startsWith("// @ts-nocheck\n"),
        )

        File(first, "layers/rhino/index.d.ts").appendText("tamper")
        assertThrows(R2DeclarationDistributionException::class.java) {
            verify(fixture, first)
        }
        generate(fixture, first)
        File(first, "layers/node/input_observer.d.ts").delete()
        assertThrows(R2DeclarationDistributionException::class.java) {
            verify(fixture, first)
        }
        generate(fixture, first)
        File(first, "extra.txt").writeText("extra")
        assertThrows(R2DeclarationDistributionException::class.java) {
            verify(fixture, first)
        }
    }

    @Test
    fun `vendored content changes fingerprints without a private hash lock`() {
        val valid = fixture("source-tamper", validArchiveEntries())
        val originalOutput = temporaryFolder.newFolder("source-original-output")
        generate(valid, originalOutput)
        @Suppress("UNCHECKED_CAST")
        val originalManifest = JsonSlurper().parse(
            File(originalOutput, "distribution.json"),
        ) as Map<String, Any?>
        File(valid.nodeSource, "module-00.d.ts").appendText("tamper")
        val changedOutput = temporaryFolder.newFolder("source-tamper-output")
        generate(valid, changedOutput)
        verify(valid, changedOutput)
        @Suppress("UNCHECKED_CAST")
        val changedManifest = JsonSlurper().parse(
            File(changedOutput, "distribution.json"),
        ) as Map<String, Any?>
        assertNotEquals(originalManifest["fingerprint"], changedManifest["fingerprint"])
        @Suppress("UNCHECKED_CAST")
        val originalLayers = originalManifest.getValue("layers") as Map<String, Map<String, Any?>>
        @Suppress("UNCHECKED_CAST")
        val changedLayers = changedManifest.getValue("layers") as Map<String, Map<String, Any?>>
        assertNotEquals(
            originalLayers.getValue("node")["fingerprint"],
            changedLayers.getValue("node")["fingerprint"],
        )

        val lockText = valid.lock.readText()
        listOf("commit", "gitBlobSha1", "hostBuild", "repositoryIdentity").forEach { key ->
            assertFalse("lock must not contain $key", lockText.contains("\"$key\""))
        }

        val linkFixture = fixture(
            "archive-link",
            validArchiveEntries() + ArchiveSpec.symbolicLink(
                "package/unselected-link",
                "declarations/autojs6/aj6-int-init.d.ts",
            ),
        )
        assertThrows(R2DeclarationDistributionException::class.java) {
            generate(linkFixture, temporaryFolder.newFolder("archive-link-output"))
        }

        val collisionFixture = fixture(
            "archive-collision",
            validArchiveEntries() +
                ArchiveSpec.file("package/Unselected.txt", "a") +
                ArchiveSpec.file("package/unselected.txt", "b"),
        )
        assertThrows(R2DeclarationDistributionException::class.java) {
            generate(collisionFixture, temporaryFolder.newFolder("archive-collision-output"))
        }
    }

    @Test
    fun `release version is independent from content cache fingerprints`() {
        val fixture = fixture("release-identity", validArchiveEntries())
        val currentOutput = temporaryFolder.newFolder("release-current-output")
        val nextOutput = temporaryFolder.newFolder("release-next-output")
        generate(fixture, currentOutput, DECLARATION_RELEASE_VERSION)
        generate(fixture, nextOutput, "0.5.1")

        @Suppress("UNCHECKED_CAST")
        val current = JsonSlurper().parse(
            File(currentOutput, "distribution.json"),
        ) as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val next = JsonSlurper().parse(
            File(nextOutput, "distribution.json"),
        ) as Map<String, Any?>
        assertNotEquals(current["release"], next["release"])
        assertNotEquals(current["fingerprint"], next["fingerprint"])

        @Suppress("UNCHECKED_CAST")
        val currentLayers = current.getValue("layers") as Map<String, Map<String, Any?>>
        @Suppress("UNCHECKED_CAST")
        val nextLayers = next.getValue("layers") as Map<String, Map<String, Any?>>
        assertEquals(
            currentLayers.mapValues { (_, layer) -> layer["fingerprint"] },
            nextLayers.mapValues { (_, layer) -> layer["fingerprint"] },
        )
        @Suppress("UNCHECKED_CAST")
        val currentProfiles = current.getValue("profiles") as Map<String, Map<String, Any?>>
        @Suppress("UNCHECKED_CAST")
        val nextProfiles = next.getValue("profiles") as Map<String, Map<String, Any?>>
        assertEquals(
            currentProfiles.mapValues { (_, profile) -> profile["fingerprint"] },
            nextProfiles.mapValues { (_, profile) -> profile["fingerprint"] },
        )
    }

    private fun fixture(name: String, archiveEntries: List<ArchiveSpec>): Fixture {
        val root = temporaryFolder.newFolder(name)
        val tarball = createTarball(File(root, "rhino.tgz"), archiveEntries)
        val nodeSource = File(root, "node").apply { mkdirs() }
        createNodeSources(nodeSource)
        val typeScript = File(root, "typescript").apply { mkdirs() }
        File(typeScript, "distribution.json").writeText(
            """{"components":{"standardLibraries":{"fileCount":108,"fingerprint":"${R2DeclarationDistribution.COMMON_STANDARD_LIBRARIES_FINGERPRINT}","totalBytes":3784758}},"fingerprint":"${R2DeclarationDistribution.COMMON_DISTRIBUTION_FINGERPRINT}","package":{"name":"typescript","version":"6.0.3"},"schemaVersion":1}""",
            Charsets.UTF_8,
        )
        val lock = File(root, "r2-declarations.lock.json")
        createLock(lock, tarball)
        return Fixture(lock, tarball, nodeSource, typeScript)
    }

    private fun generate(
        fixture: Fixture,
        output: File,
        releaseVersion: String = DECLARATION_RELEASE_VERSION,
    ) {
        R2DeclarationDistribution.generate(
            fixture.lock,
            fixture.tarball,
            fixture.nodeSource,
            fixture.typeScriptDistribution,
            output,
            releaseVersion,
        )
    }

    private fun verify(fixture: Fixture, output: File) {
        R2DeclarationDistribution.verify(
            fixture.lock,
            fixture.tarball,
            fixture.nodeSource,
            fixture.typeScriptDistribution,
            output,
            DECLARATION_RELEASE_VERSION,
        )
    }

    private fun createNodeSources(root: File) {
        File(root, "LICENSE").writeText("Mozilla Public License Version 2.0\n")
        File(root, "index.d.ts").writeText("export {};\n")
        File(root, "input_observer.d.ts").writeText("export interface InputObserver {}\n")
        repeat(57) { index ->
            File(root, "module-${index.toString().padStart(2, '0')}.d.ts")
                .writeText("export interface Module$index {}\n")
        }
        File(root, "package.json").writeText(
            """{"name":"@autojs6/node-types","private":true,"types":"index.d.ts","version":"1.0.0"}
""",
        )
    }

    private fun createLock(
        target: File,
        tarball: File,
    ) {
        val tarballBytes = tarball.readBytes()
        val lock = mapOf(
            "schemaVersion" to 2L,
            "common" to mapOf(
                "package" to "typescript",
                "version" to "6.0.3",
                "distributionFingerprint" to R2DeclarationDistribution.COMMON_DISTRIBUTION_FINGERPRINT,
                "standardLibrariesFingerprint" to R2DeclarationDistribution.COMMON_STANDARD_LIBRARIES_FINGERPRINT,
                "declarationFileCount" to 108L,
                "declarationTotalBytes" to 3_784_758L,
            ),
            "rhino" to mapOf(
                "package" to "@sm003/autojs6-dts",
                "version" to "2.1.3",
                "source" to "https://registry.npmjs.org/@sm003/autojs6-dts/-/autojs6-dts-2.1.3.tgz",
                "tarballBytes" to tarballBytes.size.toLong(),
                "sha1" to digest("SHA-1", tarballBytes),
                "sha256" to digest("SHA-256", tarballBytes),
                "integrity" to "sha512-${Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-512").digest(tarballBytes))}",
                "conversion" to "autojs6-rhino-modules-v2",
                "license" to "MPL-2.0",
                "licenseEntry" to "package/LICENSE",
            ),
            "node" to mapOf(
                "sourceDirectory" to "r2-declaration-sources/node",
                "license" to "MPL-2.0",
                "licenseFile" to "LICENSE",
                "packageFile" to "package.json",
            ),
        )
        target.writeBytes(CanonicalJson.encode(lock))
    }

    private fun validArchiveEntries(): List<ArchiveSpec> {
        val explicit = listOf(
        ArchiveSpec.file("package/LICENSE", "Mozilla Public License Version 2.0\n"),
        ArchiveSpec.file(
            "package/declarations/autojs6/aj6-int-automator.d.ts",
            """declare namespace Internal {
  interface Automator {
    click(x: number, y: number): boolean;
    swipe(x1: number, y1: number, x2: number, y2: number, duration: number): boolean;
  }
}
declare function click(x: number, y: number): boolean;
declare function swipe(x1: number, y1: number, x2: number, y2: number, duration: number): boolean;
""",
        ),
        ArchiveSpec.file(
            "package/declarations/autojs6/aj6-int-device.d.ts",
            """declare namespace Internal {
  class Device {
    get width(): number;
    get height(): number;
    model: string;
    sdkInt: number;
  }
}
""",
        ),
        ArchiveSpec.file(
            "package/declarations/autojs6/aj6-int-globals.d.ts",
            """declare let HEIGHT: number;
declare let WIDTH: number;
declare function sleep(millis: number): void;
declare function sleep(millisMin: number, millisMax: number): void;
declare function sleep(millis: number, bounds: string): void;
declare function toastLog(msg: string | any, isLong?: Toast.IsLong, isForcible?: Toast.IsForcible): void;
""",
        ),
        ArchiveSpec.file(
            "package/declarations/autojs6/aj6-int-http.d.ts",
            """declare namespace Internal {
  interface Http {
    get(url: string, options?: Http.RequestBuilderOptions, callback?: Http.Callback): Http.WrappedResponse | void;
  }
}
declare namespace Http {
  interface WrappedResponse {
    statusCode: number;
    body: { string(): string; };
  }
}
""",
        ),
        ArchiveSpec.file(
            "package/declarations/autojs6/aj6-int-images.d.ts",
            """declare namespace Internal {
  interface Images {
    captureScreen(): ImageWrapper;
    read(path: string): ImageWrapper;
    pixel(image: ImageWrapper, x: number, y: number): ColorInt;
    recycle(...images: ImageWrapper[]): boolean;
  }
}
declare class ImageWrapper {
  public width: number;
  public height: number;
}
declare function requestScreenCapture(landscape?: boolean): boolean;
""",
        ),
        ArchiveSpec.file(
            "package/declarations/autojs6/aj6-int-init.d.ts",
            """declare let automator: Internal.Automator;
declare let device: Internal.Device;
declare let http: Internal.Http;
declare let images: Internal.Images;
declare let ui: Internal.UI;
declare let ${'$'}ui: Internal.UI;
declare let toast: Internal.Toast;
declare let ${'$'}toast: Internal.Toast;
""",
        ),
        ArchiveSpec.file(
            "package/declarations/autojs6/aj6-int-toast.d.ts",
            """declare namespace Internal {
  interface Toast {
    (text: string | any, isLong?: Toast.IsLong, isForcible?: Toast.IsForcible): void;
    dismissAll(): void;
  }
}
declare namespace Toast {
  type IsLong = 'long' | 'l' | 'short' | 's' | boolean;
  type IsForcible = 'forcible' | 'f' | boolean;
}
""",
        ),
        ArchiveSpec.file(
            "package/declarations/autojs6/aj6-int-ui.d.ts",
            """declare namespace Internal {
  class UI extends org.autojs.autojs.runtime.api.UI {
    layout(layout: Xml): void;
    findView(stringId: string): android.view.View;
    finish(): void;
  }
}
""",
        ),
        ArchiveSpec.file(
            "package/declarations/autojs6/aj6-int-array-observe.d.ts",
            """declare interface Array {
  observe(object: object, handler: object): object;
}
""",
        ),
        ArchiveSpec.file(
            "package/declarations/autojs6/aj6-int-rhino.d.ts",
            """declare class ArrayBuffer extends Fixture.ArrayBuffer {
}
declare class Int8Array extends Fixture.Int8Array {
}
declare class Uint8Array extends Fixture.Uint8Array {
}
declare class Uint8ClampedArray extends Fixture.Uint8ClampedArray {
}
declare class Int16Array extends Fixture.Int16Array {
}
declare class Uint16Array extends Fixture.Uint16Array {
}
declare class Int32Array extends Fixture.Int32Array {
}
declare class Uint32Array extends Fixture.Uint32Array {
}
declare class Float32Array extends Fixture.Float32Array {
}
declare class Float64Array extends Fixture.Float64Array {
}
declare class DataView extends Fixture.DataView {
}
// @ts-ignore
declare let Error: AutoJsError;
interface AutoJsError extends ErrorConstructor {}
""",
        ),
        ArchiveSpec.file("package/unselected.txt", "not selected"),
        )
        val explicitPaths = explicit.mapTo(HashSet()) { entry -> entry.name }
        return explicit + RHINO_PROJECTION_PATHS
            .filterNot(explicitPaths::contains)
            .map { path ->
                ArchiveSpec.file(path, "declare namespace Internal {}\n")
            }
    }

    private fun createTarball(target: File, entries: List<ArchiveSpec>): File {
        Files.newOutputStream(target.toPath()).use { fileOutput ->
            GzipCompressorOutputStream(fileOutput).use { gzipOutput ->
                TarArchiveOutputStream(gzipOutput, StandardCharsets.UTF_8.name()).use { tar ->
                    tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_ERROR)
                    tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_ERROR)
                    entries.forEach { spec ->
                        val entry = when (spec.type) {
                            EntryType.FILE -> TarArchiveEntry(spec.name, true).apply {
                                size = spec.data.size.toLong()
                            }
                            EntryType.SYMBOLIC_LINK -> TarArchiveEntry(
                                spec.name,
                                TarConstants.LF_SYMLINK,
                                true,
                            ).apply {
                                linkName = requireNotNull(spec.linkName)
                                size = 0L
                            }
                        }
                        entry.setModTime(0L)
                        tar.putArchiveEntry(entry)
                        if (spec.type == EntryType.FILE) tar.write(spec.data)
                        tar.closeArchiveEntry()
                    }
                }
            }
        }
        return target
    }

    private fun snapshot(root: File): Map<String, ByteArray> = root.walkTopDown()
        .filter(File::isFile)
        .associate { it.relativeTo(root).invariantSeparatorsPath to it.readBytes() }
        .toSortedMap()

    private fun digest(algorithm: String, bytes: ByteArray): String =
        MessageDigest.getInstance(algorithm).digest(bytes).toLowerHex()

    private fun ByteArray.toLowerHex(): String =
        joinToString(separator = "") { "%02x".format(it.toInt() and 0xff) }

    private data class Fixture(
        val lock: File,
        val tarball: File,
        val nodeSource: File,
        val typeScriptDistribution: File,
    )

    private enum class EntryType { FILE, SYMBOLIC_LINK }

    private data class ArchiveSpec(
        val name: String,
        val data: ByteArray,
        val type: EntryType,
        val linkName: String? = null,
    ) {
        companion object {
            fun file(name: String, contents: String) = ArchiveSpec(
                name,
                contents.toByteArray(StandardCharsets.UTF_8),
                EntryType.FILE,
            )

            fun symbolicLink(name: String, target: String) = ArchiveSpec(
                name,
                ByteArray(0),
                EntryType.SYMBOLIC_LINK,
                target,
            )
        }
    }

    private object CanonicalJson {
        fun encode(value: Any?): ByteArray =
            (encodeValue(value) + "\n").toByteArray(StandardCharsets.UTF_8)

        private fun encodeValue(value: Any?): String = when (value) {
            null -> "null"
            is Boolean, is Number -> value.toString()
            is String -> buildString {
                append('"')
                value.forEach { character ->
                    when (character) {
                        '"' -> append("\\\"")
                        '\\' -> append("\\\\")
                        '\n' -> append("\\n")
                        '\r' -> append("\\r")
                        '\t' -> append("\\t")
                        else -> append(character)
                    }
                }
                append('"')
            }
            is List<*> -> value.joinToString(
                separator = ",",
                prefix = "[",
                postfix = "]",
            ) { encodeValue(it) }
            is Map<*, *> -> value.entries
                .map { (key, item) -> key as String to item }
                .sortedBy { it.first }
                .joinToString(separator = ",", prefix = "{", postfix = "}") { (key, item) ->
                    "${encodeValue(key)}:${encodeValue(item)}"
                }
            else -> error("unsupported JSON type: ${value::class.java.name}")
        }
    }

    companion object {
        private const val DECLARATION_RELEASE_VERSION = "0.5.0"

        private val RHINO_PROJECTION_PATHS = listOf(
            "package/declarations/autojs6/aj6-declarations.d.ts",
            "package/declarations/autojs6/aj6-int-app.d.ts",
            "package/declarations/autojs6/aj6-int-array-observe.d.ts",
            "package/declarations/autojs6/aj6-int-arrayx.d.ts",
            "package/declarations/autojs6/aj6-int-autojs.d.ts",
            "package/declarations/autojs6/aj6-int-automator.d.ts",
            "package/declarations/autojs6/aj6-int-barcode.d.ts",
            "package/declarations/autojs6/aj6-int-base64.d.ts",
            "package/declarations/autojs6/aj6-int-colors.d.ts",
            "package/declarations/autojs6/aj6-int-console.d.ts",
            "package/declarations/autojs6/aj6-int-continuation.d.ts",
            "package/declarations/autojs6/aj6-int-crypto.d.ts",
            "package/declarations/autojs6/aj6-int-device.d.ts",
            "package/declarations/autojs6/aj6-int-dialogs.d.ts",
            "package/declarations/autojs6/aj6-int-engines.d.ts",
            "package/declarations/autojs6/aj6-int-events.d.ts",
            "package/declarations/autojs6/aj6-int-files.d.ts",
            "package/declarations/autojs6/aj6-int-floaty.d.ts",
            "package/declarations/autojs6/aj6-int-globals.d.ts",
            "package/declarations/autojs6/aj6-int-http.d.ts",
            "package/declarations/autojs6/aj6-int-i18n.d.ts",
            "package/declarations/autojs6/aj6-int-images.d.ts",
            "package/declarations/autojs6/aj6-int-init.d.ts",
            "package/declarations/autojs6/aj6-int-jvm-npm.d.ts",
            "package/declarations/autojs6/aj6-int-mathx.d.ts",
            "package/declarations/autojs6/aj6-int-media.d.ts",
            "package/declarations/autojs6/aj6-int-notice.d.ts",
            "package/declarations/autojs6/aj6-int-numberx.d.ts",
            "package/declarations/autojs6/aj6-int-object-observe.d.ts",
            "package/declarations/autojs6/aj6-int-ocr.d.ts",
            "package/declarations/autojs6/aj6-int-opencc.d.ts",
            "package/declarations/autojs6/aj6-int-plugins.d.ts",
            "package/declarations/autojs6/aj6-int-qrcode.d.ts",
            "package/declarations/autojs6/aj6-int-recorder.d.ts",
            "package/declarations/autojs6/aj6-int-result-adapter.d.ts",
            "package/declarations/autojs6/aj6-int-rhino.d.ts",
            "package/declarations/autojs6/aj6-int-root-automator.d.ts",
            "package/declarations/autojs6/aj6-int-s13n.d.ts",
            "package/declarations/autojs6/aj6-int-selector.d.ts",
            "package/declarations/autojs6/aj6-int-sensors.d.ts",
            "package/declarations/autojs6/aj6-int-shell.d.ts",
            "package/declarations/autojs6/aj6-int-shizuku.d.ts",
            "package/declarations/autojs6/aj6-int-storages.d.ts",
            "package/declarations/autojs6/aj6-int-tasks.d.ts",
            "package/declarations/autojs6/aj6-int-threads.d.ts",
            "package/declarations/autojs6/aj6-int-timers.d.ts",
            "package/declarations/autojs6/aj6-int-toast.d.ts",
            "package/declarations/autojs6/aj6-int-ui.d.ts",
            "package/declarations/autojs6/aj6-int-util.d.ts",
            "package/declarations/autojs6/aj6-int-web.d.ts",
        )
    }
}
