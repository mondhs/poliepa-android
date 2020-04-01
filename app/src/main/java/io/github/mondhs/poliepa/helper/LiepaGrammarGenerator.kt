package io.github.mondhs.poliepa.helper

class LiepaGrammarGenerator{
    companion object {
        val supportedSymbolsRe = "[^aąbcčdeęėfghiįxyjklmnopqrsštuųūvwzž1234567890\\s\\n]+".toRegex()
        const private val GRAMMAR_FORMAT = """#JSGF V1.0;

grammar auto_generated_grammar;

public <COMMAND> =
%s;"""

    }

    /**
     * generate from phrases to grammar format
     */
    fun generateGrammarFromPhrases(phrases:List<String>): String {
        val cleanedPhrases = phrases.map {
            supportedSymbolsRe.replace(it, "")
        }
        return GRAMMAR_FORMAT.format(cleanedPhrases.joinToString (" |\n"))
    }

    /**
     * Set of unique words that exists in phrases
     */
    fun extractUniqueWordsFromPhrases(phrases:List<String>): Set<String> {
        val uniqueWords = mutableSetOf<String>()
        for (phrase in phrases){
            uniqueWords.addAll(phrase.split("\\s".toRegex()).toCollection(destination = HashSet()))
        }
        return  uniqueWords
    }

    /**
     * Oversimplified grammar parser. take only lines that ends with or("|") grammar symbol
     * and strip it to get.
     * @return list of supported commands
     */
    fun extractPhrasesFromGrammar(grammarContent:String):List<String>{
        var lines = grammarContent.lines()
                .filter {
                    var phrase = it.trim()
                    !phrase.startsWith("#")
                        && !phrase.startsWith("grammar")
                        && !phrase.startsWith("public")
                        && phrase.isNotEmpty()}
                .map { it.replace("[|;]".toRegex(),"").trim() }

        return lines
    }

}