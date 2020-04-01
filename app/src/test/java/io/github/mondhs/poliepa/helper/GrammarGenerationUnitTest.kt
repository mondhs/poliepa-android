package io.github.mondhs.poliepa.helper

import org.junit.Assert
import org.junit.Test

class GrammarGeneratorUnitTest{

    companion object {
        const val RESULT_GRAMMAR = """#JSGF V1.0;

grammar auto_generated_grammar;

public <COMMAND> =
labas |
labas rytas;"""
    }

    private val generator = LiepaGrammarGenerator()

    @Test
    fun shouldGenerateGrammarFromPhrases() {
        //given
        val phrases = "labas,labas rytas".split(",")
        val generator = LiepaGrammarGenerator()
        //when
        val result = generator.generateGrammarFromPhrases(phrases)
        //then
        Assert.assertEquals("Grammar result", Companion.RESULT_GRAMMAR, result)
    }
    @Test
    fun shouldGenerateGrammarFromPhrasesUnknownSymbols() {
        //given
        val phrases = "qw2x!@#\\$%^&*()as".split(",")
        val generator = LiepaGrammarGenerator()
        //when
        val result = generator.generateGrammarFromPhrases(phrases)
        //then
        Assert.assertEquals("Grammar result", """#JSGF V1.0;

grammar auto_generated_grammar;

public <COMMAND> =
qw2xas;""", result)
    }

    @Test
    fun shouldExtractUniqueWordsFromGrammar() {
        //given
        val phrases = "labas,labas rytas".split(",")
        val generator = LiepaGrammarGenerator()
        //when
        val result = generator.extractUniqueWordsFromPhrases(phrases)
        //then
        Assert.assertEquals("Grammar result", "labas\nrytas", result.joinToString("\n") { it })
    }

    @Test
    fun shouldExtractPhrasesFromGrammar() {
        //given


        //when
        val result = generator.extractPhrasesFromGrammar(RESULT_GRAMMAR)
        //then
        Assert.assertEquals("Phrase result", "labas\nlabas rytas", result.joinToString("\n") { it })
    }




}