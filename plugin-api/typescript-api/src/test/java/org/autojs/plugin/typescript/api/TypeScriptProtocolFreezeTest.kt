package org.autojs.plugin.typescript.api

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TypeScriptProtocolFreezeTest {

    @Test
    fun realAidlSourcesMatchEveryFrozenDescriptorAndTransaction() {
        val canonical = binderFixture()
        val interfaces = canonical.list("interfaces")
        assertEquals(2, interfaces.size)

        interfaces.forEach { rawInterface ->
            val frozen = rawInterface.objectValue()
            val source = repositoryRoot.resolve(frozen.string("source"))
            assertTrue("Missing canonical AIDL source $source.", source.isFile)
            val actual = parseAidl(source.readText())

            assertEquals(frozen.string("name"), actual.name)
            assertEquals(frozen.string("descriptor"), actual.descriptor)
            assertEquals(frozen.boolean("oneway"), actual.oneway)

            val frozenTransactions = frozen.list("transactions")
            assertEquals(actual.methods.size, frozenTransactions.size)
            actual.methods.zip(frozenTransactions).forEachIndexed {
                    index,
                    (actualMethod, rawTransaction),
                ->
                val transaction = rawTransaction.objectValue()
                assertEquals(index + 1L, transaction.long("code"))
                assertEquals(
                    "IBinder.FIRST_CALL_TRANSACTION + $index",
                    transaction.string("codeExpression"),
                )
                assertEquals(actualMethod.name, transaction.string("name"))
                assertEquals(actualMethod.returnType, transaction.string("returnType"))
                assertEquals(actualMethod.signature, transaction.string("signature"))

                val parameters = transaction.list("parameters")
                assertEquals(actualMethod.parameters.size, parameters.size)
                actualMethod.parameters.zip(parameters).forEach {
                        (actualParameter, rawParameter),
                    ->
                    val parameter = rawParameter.objectValue()
                    assertEquals(actualParameter.name, parameter.string("name"))
                    assertEquals(actualParameter.type, parameter.string("type"))
                    assertEquals(actualParameter.direction, parameter.string("direction"))
                }
            }
        }
    }

    @Test
    fun contractHashBundleRulesBudgetsAndFdEnvelopeMatchCanonicalFixture() {
        val canonical = binderFixture()
        val contract = canonical.objectValue("contract")
        assertEquals(
            TypeScriptCompilerContract.VERSION.toLong(),
            contract.long("version"),
        )
        assertEquals(TypeScriptCompilerContract.HASH, contract.string("hash"))
        assertEquals(TypeScriptCompilerContract.HASH_INPUT, contract.string("hashInput"))
        assertEquals(
            TypeScriptCompilerContract.HASH,
            sha256(TypeScriptCompilerContract.HASH_INPUT.toByteArray()),
        )

        val compilerInterface = canonical.list("interfaces")
            .map { it.objectValue() }
            .single { it.string("name") == "ITypeScriptCompilerPlugin" }
        val callbackInterface = canonical.list("interfaces")
            .map { it.objectValue() }
            .single { it.string("name") == "ITypeScriptCompilationCallback" }
        val frozenHashInput = buildString {
            append(TypeScriptCompilerContract.HASH_INPUT.substringBefore('|'))
            compilerInterface.list("transactions").forEach { rawTransaction ->
                append('|')
                append(rawTransaction.objectValue().string("hashSignature"))
            }
            append('|')
            append(callbackInterface.string("hashDescriptor"))
            append('|')
            append(canonical.objectValue("bundleEnvelope").string("hashToken"))
            append('|')
            append(canonical.objectValue("fileDescriptorEnvelope").string("hashToken"))
        }
        assertEquals(TypeScriptCompilerContract.HASH_INPUT, frozenHashInput)

        val bundleEnvelope = canonical.objectValue("bundleEnvelope")
        assertEquals(
            EXPECTED_SCALAR_RUNTIME_TYPES,
            bundleEnvelope.list("allowedScalarRuntimeTypes").map { it.stringValue() },
        )
        assertEquals(
            EXPECTED_ARRAY_RUNTIME_TYPES,
            bundleEnvelope.list("allowedArrayRuntimeTypes").map { it.stringValue() },
        )
        assertEquals("reject", bundleEnvelope.string("topLevelNullValues"))
        assertTrue(bundleEnvelope.string("unknownKeys").startsWith("ignore only"))
        assertTrue(bundleEnvelope.list("rawTransportRules").size >= 5)
        assertTrue(bundleEnvelope.list("rejectedRuntimeFamilies").size >= 6)
        bundleEnvelope.objectValue("budgets").values.forEach { rawBudget ->
            assertContractConstant(rawBudget.objectValue())
        }

        val fdEnvelope = canonical.objectValue("fileDescriptorEnvelope")
        assertContractConstant(fdEnvelope.objectValue("inputArchiveTransportVersion"))
        assertContractConstant(fdEnvelope.objectValue("outputArchiveTransportVersion"))
        assertEquals(
            compilerInterface.list("transactions")
                .map { it.objectValue().string("name") }
                .toSet(),
            fdEnvelope.objectValue("transactionPolicies").keys,
        )
        assertTrue(fdEnvelope.list("ownership").size >= 8)
        assertTrue(
            fdEnvelope.string("terminalCallbackRule")
                .contains("admission rejection sends zero callbacks"),
        )
    }

    @Test
    fun typedSemanticBundleFixturesMatchCompiledContractConstants() {
        val canonical = bundleFixture()
        assertEquals(
            TypeScriptCompilerContract.VERSION.toLong(),
            canonical.long("contractVersion"),
        )
        assertEquals(TypeScriptCompilerContract.HASH, canonical.string("contractHash"))
        assertEquals(
            EXPECTED_FIXTURE_TYPES,
            canonical.objectValue("representation").list("types").map { it.stringValue() },
        )

        val fixtures = canonical.list("fixtures").map { it.objectValue() }
        assertEquals(
            EXPECTED_FIXTURE_CHANNELS,
            fixtures.associate {
                it.string("name") to it.string("channel")
            },
        )

        fixtures.forEach { fixture ->
            val seenKeys = mutableSetOf<String>()
            fixture.list("entries").forEach { rawEntry ->
                val entry = rawEntry.objectValue()
                val key = entry.string("key")
                assertTrue(
                    "${fixture.string("name")} repeats Bundle key '$key'.",
                    seenKeys.add(key),
                )
                val type = entry.string("type")
                assertTrue("Unknown typed fixture type '$type'.", type in EXPECTED_FIXTURE_TYPES)
                assertTrue(entry.containsKey("required"))
                validateTypedValue(type, entry["value"])

                entry["keyConstant"]?.let { rawConstant ->
                    val owner = entry.string("keyOwner")
                    assertEquals(
                        key,
                        constantValue(owner, rawConstant.stringValue()),
                    )
                }
                entry["valueConstant"]?.let { rawConstant ->
                    assertEquivalentConstant(
                        entry["value"],
                        constantValue(
                            "TypeScriptCompilerContract",
                            rawConstant.stringValue(),
                        ),
                    )
                }
            }
        }

        val unknownCompatibleRequest = fixtures.single {
            it.string("name") == "compilation-request-with-unknown-legal-key"
        }
        val unknownEntry = unknownCompatibleRequest.list("entries")
            .map { it.objectValue() }
            .single { it["unknownLegalKey"] == true }
        assertEquals("futureOptionalMetadata", unknownEntry.string("key"))
        assertEquals("string[]", unknownEntry.string("type"))
        assertTrue(unknownEntry.list("value").contains(null))

        val rejection = fixtures.single { it.string("name") == "admission-r1-reject" }
        val rejectionEntries = rejection.list("entries")
            .map { it.objectValue() }
            .associateBy { it.string("key") }
        assertEquals(false, rejectionEntries.getValue("accepted")["value"])
        assertEquals(
            TypeScriptCompilerContract.ERROR_R0_NOT_AVAILABLE,
            rejectionEntries.getValue("errorCode").string("value"),
        )
    }

    @Test
    fun canonicalFixtureBytesRemainFrozen() {
        assertEquals(
            BINDER_FIXTURE_SHA256,
            sha256(repositoryRoot.resolve(BINDER_FIXTURE).readBytes()),
        )
        assertEquals(
            BUNDLE_FIXTURE_SHA256,
            sha256(repositoryRoot.resolve(BUNDLE_FIXTURE).readBytes()),
        )
    }

    private fun binderFixture(): Map<String, Any?> =
        parseFixture(BINDER_FIXTURE)

    private fun bundleFixture(): Map<String, Any?> =
        parseFixture(BUNDLE_FIXTURE)

    private fun parseFixture(path: String): Map<String, Any?> {
        val file = repositoryRoot.resolve(path)
        assertTrue("Missing protocol fixture $file.", file.isFile)
        return CanonicalJsonParser.parse(file.readText()).objectValue()
    }

    private fun parseAidl(source: String): AidlInterface {
        val withoutComments = source
            .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("//.*"), "")
        val packageName = requireNotNull(
            Regex("\\bpackage\\s+([\\w.]+)\\s*;").find(withoutComments),
        ).groupValues[1]
        val declaration = requireNotNull(
            Regex(
                "\\b(oneway\\s+)?interface\\s+(\\w+)\\s*\\{(.*)}",
                setOf(RegexOption.DOT_MATCHES_ALL),
            ).find(withoutComments),
        )
        val oneway = declaration.groupValues[1].isNotBlank()
        val name = declaration.groupValues[2]
        val body = declaration.groupValues[3]
        val methods = body.split(';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map(::parseAidlMethod)
        return AidlInterface(
            name = name,
            descriptor = "$packageName.$name",
            oneway = oneway,
            methods = methods,
        )
    }

    private fun parseAidlMethod(declaration: String): AidlMethod {
        val normalized = declaration.replace(Regex("\\s+"), " ").trim()
        val match = requireNotNull(
            Regex("^(\\w+)\\s+(\\w+)\\s*\\((.*)\\)$").matchEntire(normalized),
        ) { "Unsupported AIDL method declaration: $normalized" }
        val parameters = match.groupValues[3]
            .takeIf { it.isNotBlank() }
            ?.split(',')
            .orEmpty()
            .map { rawParameter ->
                val parameter = requireNotNull(
                    Regex("^(?:(in|out|inout)\\s+)?([\\w.]+)\\s+(\\w+)$")
                        .matchEntire(rawParameter.trim()),
                ) { "Unsupported AIDL parameter: $rawParameter" }
                AidlParameter(
                    name = parameter.groupValues[3],
                    type = parameter.groupValues[2].substringAfterLast('.'),
                    direction = parameter.groupValues[1].ifBlank { "none" },
                )
            }
        val returnType = match.groupValues[1].substringAfterLast('.')
        val name = match.groupValues[2]
        val signature = buildString {
            append(returnType)
            append(' ')
            append(name)
            append('(')
            append(
                parameters.joinToString(", ") { parameter ->
                    buildString {
                        if (parameter.direction != "none") {
                            append(parameter.direction)
                            append(' ')
                        }
                        append(parameter.type)
                        append(' ')
                        append(parameter.name)
                    }
                },
            )
            append(')')
        }
        return AidlMethod(
            name = name,
            returnType = returnType,
            parameters = parameters,
            signature = signature,
        )
    }

    private fun assertContractConstant(reference: Map<String, Any?>) {
        val constantName = reference.string("contractConstant")
        val expected = reference["value"]
        assertEquivalentConstant(
            expected,
            constantValue("TypeScriptCompilerContract", constantName),
        )
    }

    private fun constantValue(
        owner: String,
        name: String,
    ): Any {
        val type = when (owner) {
            "TypeScriptCompilerContract" -> TypeScriptCompilerContract::class.java
            "TypeScriptPluginCapabilityKeys" -> TypeScriptPluginCapabilityKeys::class.java
            else -> error("Unknown fixture constant owner '$owner'.")
        }
        val field = type.getField(name)
        val value = field.get(null)
        assertNotNull("$owner.$name resolved to null.", value)
        return requireNotNull(value)
    }

    private fun assertEquivalentConstant(
        expected: Any?,
        actual: Any?,
    ) {
        if (expected is Number && actual is Number) {
            assertEquals(expected.toLong(), actual.toLong())
        } else {
            assertEquals(expected, actual)
        }
    }

    private fun validateTypedValue(
        type: String,
        value: Any?,
    ) {
        when (type) {
            "boolean" -> assertTrue(value is Boolean)
            "int" -> assertTrue(
                value is Long &&
                    value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong(),
            )
            "long" -> assertTrue(value is Long)
            "string" -> assertTrue(value is String)
            "string[]" -> {
                assertTrue(value is List<*>)
                val values = value as List<*>
                assertTrue(
                    values.size <= TypeScriptCompilerContract.MAX_METADATA_ARRAY_ELEMENTS,
                )
                assertTrue(values.all { it == null || it is String })
            }
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private fun Any?.objectValue(): Map<String, Any?> {
        assertTrue("Expected JSON object but was ${this?.javaClass}.", this is Map<*, *>)
        @Suppress("UNCHECKED_CAST")
        return this as Map<String, Any?>
    }

    private fun Map<String, Any?>.objectValue(key: String): Map<String, Any?> =
        get(key).objectValue()

    private fun Map<String, Any?>.list(key: String): List<Any?> {
        val value = get(key)
        assertTrue("Expected JSON array '$key'.", value is List<*>)
        @Suppress("UNCHECKED_CAST")
        return value as List<Any?>
    }

    private fun Any?.stringValue(): String {
        assertTrue("Expected JSON string but was ${this?.javaClass}.", this is String)
        return this as String
    }

    private fun Map<String, Any?>.string(key: String): String =
        get(key).stringValue()

    private fun Map<String, Any?>.long(key: String): Long {
        val value = get(key)
        assertTrue("Expected JSON integer '$key'.", value is Long)
        return value as Long
    }

    private fun Map<String, Any?>.boolean(key: String): Boolean {
        val value = get(key)
        assertTrue("Expected JSON boolean '$key'.", value is Boolean)
        return value as Boolean
    }

    private data class AidlInterface(
        val name: String,
        val descriptor: String,
        val oneway: Boolean,
        val methods: List<AidlMethod>,
    )

    private data class AidlMethod(
        val name: String,
        val returnType: String,
        val parameters: List<AidlParameter>,
        val signature: String,
    )

    private data class AidlParameter(
        val name: String,
        val type: String,
        val direction: String,
    )

    private companion object {
        const val REPOSITORY_ROOT_PROPERTY = "typescript.protocol.repositoryRoot"
        const val BINDER_FIXTURE = "compat/v1/binder-transactions.json"
        const val BUNDLE_FIXTURE = "compat/v1/bundle-fixtures.json"
        const val BINDER_FIXTURE_SHA256 =
            "51cb769946c8b7d88d2c349351a7f41921b064d726e5f6b969a9f7facda5bf81"
        const val BUNDLE_FIXTURE_SHA256 =
            "56e7be0fbd97a013b386f4c5fdb3c5c430326520bd3eb35b7d73367a8428200b"

        val repositoryRoot = File(
            requireNotNull(System.getProperty(REPOSITORY_ROOT_PROPERTY)) {
                "Gradle must provide $REPOSITORY_ROOT_PROPERTY."
            },
        )

        val EXPECTED_SCALAR_RUNTIME_TYPES = listOf(
            "java.lang.Boolean",
            "java.lang.Byte",
            "java.lang.Character",
            "java.lang.Double",
            "java.lang.Float",
            "java.lang.Integer",
            "java.lang.Long",
            "java.lang.Short",
            "java.lang.String",
        )

        val EXPECTED_ARRAY_RUNTIME_TYPES = listOf(
            "boolean[]",
            "byte[]",
            "char[]",
            "double[]",
            "float[]",
            "int[]",
            "long[]",
            "short[]",
            "java.lang.String[]",
        )

        val EXPECTED_FIXTURE_TYPES = listOf(
            "boolean",
            "int",
            "long",
            "string",
            "string[]",
        )

        val EXPECTED_FIXTURE_CHANNELS = linkedMapOf(
            "compiler-info-bootstrap" to "getCompilerInfo.result",
            "prewarm-options" to "prewarm.options",
            "compilation-request-with-unknown-legal-key" to "startCompilation.request",
            "admission-r1-reject" to "startCompilation.ticket",
            "cancellation" to "cancelCompilation.cancellation",
            "progress" to "callback.onProgress",
            "completion-emitted" to "callback.onCompleted",
        )
    }
}
