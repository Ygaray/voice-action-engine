package io.github.ygaray.voiceactionengine.spike

import io.github.ygaray.voiceactionengine.spike.gold.GoldMatcher
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Host-side re-score of the host-private raw sb answers (RT-02/RT-03), with no device. It reads the private raw jsonl named
 * by `vae.spike.rawFile` and the private gold file named by `vae.spike.goldFile`, grades every positive row strictly and
 * under the RT-03 tolerance, and writes AGGREGATE counts only to `vae.spike.rescoreOut` (never an item id, transcript,
 * label, tool name or model output). With no raw file it skips, so a normal test run is unaffected.
 */
class SbRescoreTest {
    @Test
    fun rescoresThePrivateRawRowsAndWritesAggregatesOnly() {
        val rawPath = System.getProperty("vae.spike.rawFile").orEmpty()
        assumeTrue("vae.spike.rawFile is not set", rawPath.isNotBlank())
        val goldPath = System.getProperty("vae.spike.goldFile").orEmpty()
        val out = System.getProperty("vae.spike.rescoreOut").orEmpty()
        require(goldPath.isNotBlank() && out.isNotBlank()) { "vae.spike.goldFile and vae.spike.rescoreOut are required" }
        val gold = (Json.parseToJsonElement(File(goldPath).readText()) as JsonObject)["items"] as JsonArray
        val byId = gold.associate { element ->
            val item = element as JsonObject
            val expect = item["expect"] as JsonObject
            (item["id"] as JsonPrimitive).content to Expect(
                tool = (expect["tool"] as? JsonPrimitive)?.contentOrNull,
                args = (expect["args"] as? JsonObject) ?: JsonObject(emptyMap()),
            )
        }
        val rows = File(rawPath).readLines().filter { it.isNotBlank() }.map { Json.parseToJsonElement(it) as JsonObject }
        var positives = 0
        var strictCorrect = 0
        var tolerantCorrect = 0
        var deviceCorrect = 0
        var deviceMismatch = 0
        for (row in rows) {
            if ((row["kind"] as JsonPrimitive).content != "POS") continue
            positives++
            val id = (row["item"] as JsonPrimitive).content
            val expected = byId.getValue(id)
            val tool = (row["first_call_name"] as? JsonPrimitive)?.contentOrNull
            val args = (row["first_call_arguments"] as? JsonObject) ?: JsonObject(emptyMap())
            val toolOk = GoldMatcher.toolMatch(expected.tool, tool)
            val strict = toolOk && GoldMatcher.argsMatch(expected.args, args)
            val tolerant = toolOk && GoldMatcher.argsMatch(expected.args, args, id)
            val onDevice = (row["tool_match"] as JsonPrimitive).booleanOrNull == true &&
                (row["args_match"] as JsonPrimitive).booleanOrNull == true
            if (strict) strictCorrect++
            if (tolerant) tolerantCorrect++
            if (onDevice) deviceCorrect++
            if (onDevice != strict) deviceMismatch++
        }
        val report = "SB_RESCORE rows=${rows.size} positives=$positives device_correct=$deviceCorrect " +
            "strict_correct=$strictCorrect tolerant_correct=$tolerantCorrect changed_by_tolerance=${tolerantCorrect - strictCorrect} " +
            "device_vs_host_strict_mismatch=$deviceMismatch\n"
        File(out).writeText(report)
    }

    private class Expect(val tool: String?, val args: JsonObject)
}
