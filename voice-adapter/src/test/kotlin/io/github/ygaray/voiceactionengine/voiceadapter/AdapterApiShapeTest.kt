package io.github.ygaray.voiceactionengine.voiceadapter

import io.github.ygaray.sttengine.FinalSegment
import io.github.ygaray.voiceactionengine.core.CommandInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Reflection pin of the public surface frozen at the tag: the two JVM facades, two forms each, and no joiner. */
class AdapterApiShapeTest {
    private val segmentFacade = "io.github.ygaray.voiceactionengine.voiceadapter.FinalSegmentCommandInput"
    private val labelFacade = "io.github.ygaray.voiceactionengine.voiceadapter.SttLanguageLabels"
    private val sttPackagePrefix = "io.github.ygaray.sttengine"

    private fun publicStatics(className: String): List<Method> = Class.forName(className).declaredMethods
        .filter { Modifier.isPublic(it.modifiers) && Modifier.isStatic(it.modifiers) && !it.isSynthetic }

    @Test
    fun theSegmentFacadeExposesExactlyTheTwoToCommandInputForms() {
        val methods = publicStatics(segmentFacade)

        assertEquals(listOf("toCommandInput", "toCommandInput"), methods.map { it.name })
        val signatures = methods.map { it.parameterTypes.toList() }.toSet()
        val expected = setOf(
            listOf<Class<*>>(FinalSegment::class.java),
            listOf(FinalSegment::class.java, Any::class.java, String::class.java),
        )
        assertEquals(expected, signatures)
        val contextOnly = listOf<Class<*>>(FinalSegment::class.java, Any::class.java)
        assertTrue(methods.none { it.parameterTypes.toList() == contextOnly })
    }

    @Test
    fun theLabelFacadeExposesCommandInputOfTwiceAndTheNormalizerOnce() {
        val methods = publicStatics(labelFacade)

        val names = methods.map { it.name }.sorted()
        assertEquals(listOf("commandInputOf", "commandInputOf", "normalizeSttLanguageLabel"), names)
        val signatures = methods.filter { it.name == "commandInputOf" }.map { it.parameterTypes.toList() }.toSet()
        val expected = setOf(
            listOf<Class<*>>(String::class.java, String::class.java),
            listOf(String::class.java, String::class.java, Any::class.java, String::class.java),
        )
        assertEquals(expected, signatures)
    }

    /**
     * There is deliberately no label-facade method taking only a context after the text and the label. The call shape
     * `commandInputOf("yes", "en", "run-42")` therefore fails to compile instead of silently treating "run-42" as the
     * context. A compile failure cannot be asserted at run time, so this reflection check is the guard.
     */
    @Test
    fun aThreeArgumentStringCallHasNoContextOnlyTarget() {
        val contextOnly = listOf<Class<*>>(String::class.java, String::class.java, Any::class.java)

        val found = publicStatics(labelFacade).filter { it.parameterTypes.toList() == contextOnly }

        assertTrue(found.isEmpty())
    }

    @Test
    fun noSignatureInTheLabelFacadeNamesAnSttType() {
        for (method in publicStatics(labelFacade)) {
            val types = method.parameterTypes.toList() + method.returnType
            assertTrue(method.name, types.none { it.name.startsWith(sttPackagePrefix) })
        }
    }

    @Test
    fun noPublicMethodOfEitherFacadeTakesACollectionIterableOrList() {
        val joinerTypes = listOf(Collection::class.java, Iterable::class.java, List::class.java)
        for (method in publicStatics(segmentFacade) + publicStatics(labelFacade)) {
            for (type in method.parameterTypes) {
                assertFalse(method.name, joinerTypes.any { it.isAssignableFrom(type) } || type.isArray)
            }
        }
    }

    @Test
    fun everyMethodReturnsACommandInputOrAString() {
        for (method in publicStatics(segmentFacade) + publicStatics(labelFacade)) {
            val returns = method.returnType
            assertTrue(method.name, returns == CommandInput::class.java || returns == String::class.java)
        }
    }
}
