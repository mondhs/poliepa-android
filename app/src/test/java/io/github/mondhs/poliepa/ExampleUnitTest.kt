package io.github.mondhs.poliepa

import org.junit.Test

import org.junit.Assert.*
import org.junit.Ignore

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    val helper = LiepaRecognitionHelper()

    val GIVEN_GRAMMAR = """#JSGF V1.0;

grammar patikra;

public <COMMAND> =
labas |
labas rytas |"""

    @Test
    fun grammarParser() {
        val rtnList = helper.parseGrammar(GIVEN_GRAMMAR);
        assertEquals("List of result", 2, rtnList.size.toLong())
        assertArrayEquals(listOf("labas", "labas rytas").toTypedArray(), rtnList.toTypedArray())
    }

    @Test
    fun testInitConxet(){
        val ctx = helper.initConxet(GIVEN_GRAMMAR)
        assertEquals("List of result", 2, ctx.allCommandList.size.toLong())
        assertEquals("List of result", 0, ctx.activeCommandList.size.toLong())
    }

    @Test
    fun testNextWord(){
        val givenCtx = helper.initConxet(GIVEN_GRAMMAR)
        val result: MutableList<String> = mutableListOf()

        for (i in mutableListOf(1,2,3,4)){
            var currentWord = helper.nextWord(givenCtx)
            result.add(currentWord)
        }
        result.sort()

        assertArrayEquals(listOf("labas", "labas", "labas rytas", "labas rytas").toTypedArray(), result.toTypedArray())
    }

    @Test
    fun testCheckRecognition(){
        val givenCtx = helper.initConxet(GIVEN_GRAMMAR)
        givenCtx.previousWord = "labas"

        var result = helper.checkRecognition(givenCtx, "labas")
        assertEquals("Recogntion correct ratio", 100, result)

        result = helper.checkRecognition(givenCtx, "labas1")
        assertEquals("Recogntion correct ratio", 50, result)

        result = helper.checkRecognition(givenCtx, "labas")
        assertEquals("Recogntion correct ratio", 66, result)


    }
}

