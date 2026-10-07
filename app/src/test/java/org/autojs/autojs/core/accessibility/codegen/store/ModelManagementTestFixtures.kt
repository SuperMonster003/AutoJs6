package org.autojs.autojs.core.accessibility.codegen.store

import com.google.gson.JsonArray
import com.google.gson.JsonParser
import org.autojs.autojs.core.accessibility.codegen.model.ModelPackageReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

internal class ModelManagementTestFixtures(val root: File, withCatalog: Boolean = true) {
    val v1 = resource("models/tiny-test-v1.ajcgm")
    val v2 = repack(v1)
    val catalogJson = JsonParser.parseString(Base64.getDecoder().decode(
        JsonParser.parseString(resource("catalog/codegen_model_catalog_v1.json").toString(Charsets.UTF_8)).asJsonObject["catalogBase64"].asString
    ).toString(Charsets.UTF_8)).asJsonObject.apply {
        addProperty("catalogVersion", 2)
        addProperty("releaseTag", "codegen-models-v2")
        val model = getAsJsonArray("models")[0].asJsonObject
        val version = model.getAsJsonArray("versions")[0].asJsonObject.deepCopy()
        version.addProperty("versionCode", 2)
        version.addProperty("versionName", "1.0.1")
        val artifact = version.getAsJsonObject("artifact")
        val oldName = artifact["fileName"].asString
        val sha = ModelCatalogJson.digest(v2)
        val newName = "autojs6-codegen-autojs6.codegen.tiny-test-v2-${sha.take(8)}.ajcgm"
        artifact.addProperty("fileName", newName)
        artifact.addProperty("size", v2.size)
        artifact.addProperty("sha256", sha)
        artifact.addProperty("url", artifact["url"].asString.replace(oldName, newName).replace("codegen-models-v1/", "codegen-models-v2/"))
        artifact.add("urls", JsonArray().apply { add(artifact["url"].asString) })
        model.getAsJsonArray("versions").add(version)
        model.addProperty("recommendedVersionCode", 2)
        if (!withCatalog) add("models", JsonArray())
    }
    val selection = object : CurrentModelSelection { override var currentKey: String? = null }
    val repository = ModelCatalogRepository(File(root, "catalog"), hostVersionCode = Int.MAX_VALUE,
        signatureVerifier = ModelCatalogTestFixtures.verifier, expectedRepository = catalogJson["repository"].asString,
        bootstrapEnvelopeBytes = ModelCatalogTestFixtures.bootstrap(catalogJson.toString().toByteArray()))
    val store = ModelStore(File(root, "models"), ModelPackageReader(),
        verifiedCatalogs = { listOfNotNull(repository.current()) }, selection = selection)
    fun installed(version: Int) = store.install((if (version == 1) v1 else v2).inputStream(), "fixture-$version.ajcgm")
    fun update(old: InstalledModel) = requireNotNull(ModelUpdates.find(old, requireNotNull(repository.current()).copy(remoteArtifactsAllowed = true)))
    fun noStages(): Boolean = !File(store.rootDirectory, "staging").walkTopDown().any { it.isFile }

    companion object {
        private fun resource(name: String) = requireNotNull(ModelManagementTestFixtures::class.java.getResourceAsStream("/codegen/$name")).use { it.readBytes() }
        private fun repack(original: ByteArray): ByteArray = ByteArrayOutputStream().also { result ->
            ZipOutputStream(result).use { output ->
                ZipInputStream(original.inputStream()).use { input ->
                    while (true) {
                        val entry = input.nextEntry ?: break
                        var content = input.readBytes()
                        if (entry.name == "manifest.json") {
                            content = JsonParser.parseString(content.toString(Charsets.UTF_8)).asJsonObject.apply {
                                addProperty("versionCode", 2); addProperty("versionName", "1.0.1")
                            }.toString().toByteArray(Charsets.UTF_8)
                        }
                        output.putNextEntry(ZipEntry(entry.name)); output.write(content); output.closeEntry()
                    }
                }
            }
        }.toByteArray()
    }
}
