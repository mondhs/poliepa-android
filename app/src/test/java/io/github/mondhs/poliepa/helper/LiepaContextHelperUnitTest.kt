package io.github.mondhs.poliepa.helper

import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class LiepaContextHelperUnitTest {
    companion object {
        const val GIVEN_GRAMMAR = """#JSGF V1.0;

    grammar patikra;

    public <COMMAND> =
    labas |
    labas rytas ;"""
    }
    private val helper = LiepaContextHelper()
    private val grammarGenerator = LiepaGrammarGenerator()


    @Test
    fun shouldSetupLolcalContext(){
        val givenCtx = LiepaRecognitionContext()
//        helper.setupLocalPhrase(givenCtx, Companion.GIVEN_GRAMMAR)
        givenCtx.allCommandList.addAll(grammarGenerator.extractPhrasesFromGrammar(Companion.GIVEN_GRAMMAR))
//      ctx.allCommandList.addAll(grammarGenerator.extractPhrasesFromGrammar(ctx.liepaCommandsGrammar))
        assertEquals("List of result", 2, givenCtx.allCommandList.size.toLong())
        assertEquals("List of result", 0, givenCtx.activeCommandList.size.toLong())
    }

    @Test
    fun shouldGetNextWord(){
        val givenCtx = LiepaRecognitionContext()
//        helper.setupLocalPhrase(givenCtx, Companion.GIVEN_GRAMMAR)
        givenCtx.allCommandList.addAll(grammarGenerator.extractPhrasesFromGrammar(Companion.GIVEN_GRAMMAR))
        val result: MutableList<String> = mutableListOf()

        for (i in mutableListOf(1,2,3,4)){
            var currentWord = helper.updateNextWord(givenCtx)
            result.add(currentWord)
        }
        result.sort()

        assertArrayEquals(listOf("labas", "labas", "labas rytas", "labas rytas").toTypedArray(), result.toTypedArray())
    }

    @Test
    fun shouldCompareRecognitionResults(){
        val givenCtx = LiepaRecognitionContext()
//        helper.setupLocalPhrase(LiepaRecognitionContext(), Companion.GIVEN_GRAMMAR)
        givenCtx.allCommandList.addAll(grammarGenerator.extractPhrasesFromGrammar(Companion.GIVEN_GRAMMAR))
        givenCtx.currentPhraseText = "labas"


        var result = helper.updateRecognitionResult(givenCtx, isRecognizedCorrectly = true)
        assertEquals("Recognition correct ratio", 100, result)

        result = helper.updateRecognitionResult(givenCtx, isRecognizedCorrectly = false)
        assertEquals("Recognition correct ratio", 50, result)

        result = helper.updateRecognitionResult(givenCtx, isRecognizedCorrectly = true)
        assertEquals("Recognition correct ratio", 66, result)


    }


}

