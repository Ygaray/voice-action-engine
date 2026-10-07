package io.github.ygaray.voiceactionengine.voiceadapter

import io.github.ygaray.voiceactionengine.core.CommandInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Executable proof that the label facade works for a caller with no speech-engine class visible: its compiled bytes
 * name no speech-engine class, and it loads and runs through a classloader that refuses the whole speech-engine
 * package.
 */
class SttFreeFacadeTest {
    private val labelFacade = "io.github.ygaray.voiceactionengine.voiceadapter.SttLanguageLabels"
    private val segmentFacade = "io.github.ygaray.voiceactionengine.voiceadapter.FinalSegmentCommandInput"
    private val sttPackageDots = "io.github.ygaray.sttengine"
    private val sttPackageSlashes = "io/github/ygaray/sttengine"
    private val testLoader: ClassLoader = requireNotNull(javaClass.classLoader)

    private fun classBytes(className: String): ByteArray {
        val resource = className.replace('.', '/') + ".class"
        val stream = testLoader.getResourceAsStream(resource)
        assertTrue("compiled class not found on the test classpath: $resource", stream != null)
        return stream!!.use { it.readBytes() }
    }

    private fun names(bytes: ByteArray, packageSlashes: String): Boolean =
        String(bytes, Charsets.ISO_8859_1).contains(packageSlashes)

    /** Defines the label facade itself and hides every speech-engine class, so a hidden dependency cannot link. */
    private class HidingLoader(parent: ClassLoader, private val ownName: String, private val ownBytes: ByteArray) :
        ClassLoader(parent) {
        override fun loadClass(name: String, resolve: Boolean): Class<*> {
            if (name.startsWith("io.github.ygaray.sttengine")) throw ClassNotFoundException(name)
            if (name != ownName) return super.loadClass(name, resolve)
            synchronized(this) {
                val loaded = findLoadedClass(name) ?: defineClass(name, ownBytes, 0, ownBytes.size)
                if (resolve) resolveClass(loaded)
                return loaded
            }
        }
    }

    @Test
    fun theScanCanSeeASpeechEngineReferenceWhereOneExists() {
        // Non-vacuity: the segment facade legitimately names the speech engine, so a scan that misses it is broken.
        assertTrue(names(classBytes(segmentFacade), sttPackageSlashes))
    }

    @Test
    fun theLabelFacadeClassFileNamesNoSpeechEngineClass() {
        assertTrue(!names(classBytes(labelFacade), sttPackageSlashes))
    }

    @Test
    fun theLabelFacadeLoadsAndRunsWhenTheSpeechEnginePackageIsHidden() {
        val loader = HidingLoader(testLoader, labelFacade, classBytes(labelFacade))
        val hidden = runCatching { loader.loadClass("$sttPackageDots.FinalSegment") }.exceptionOrNull()
        assertTrue("the loader must hide the speech engine", hidden is ClassNotFoundException)

        val facade = loader.loadClass(labelFacade)
        val normalize = facade.getMethod("normalizeSttLanguageLabel", String::class.java)
        val build = facade.getMethod("commandInputOf", String::class.java, String::class.java)

        assertEquals("es", normalize.invoke(null, " ES "))
        assertNull(normalize.invoke(null, "en-US"))
        val input = build.invoke(null, "hola", "ES") as CommandInput
        assertEquals("hola", input.transcript)
        assertEquals("es", input.language)
    }
}
