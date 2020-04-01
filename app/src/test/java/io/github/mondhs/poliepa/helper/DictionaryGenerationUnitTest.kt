package io.github.mondhs.poliepa.helper

import org.junit.Assert
import org.junit.Test

class DictionaryGeneratorUnitTest{
    @Test
    fun testGrammarGenerator() {
        //given
        val generator = LiepaDictionaryGenerator()
        //when
        val result = generator.dictionaryToString(generator.transcribeDictionary("daug labas rytas facebookas ieva"))
        //then
        Assert.assertEquals("Dictionary result", """daug	D A U K
facebookas	F E I Z B U K A S
ieva	J. I E V A
labas	L A B A S
rytas	R I_ T A S
""", result)
    }

    @Test
    fun testGrammarUnknownSymbolsGenerator() {
        //given
        val generator = LiepaDictionaryGenerator()
        //when
        val result = generator.dictionaryToString(generator.transcribeDictionary("qw2x!@#\\$%^&*()as"))
        //then
        Assert.assertEquals("Dictionary result", "qw2xas\tK U V D U K S A S\n", result)
    }
}
