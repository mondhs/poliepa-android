package io.github.mondhs.poliepa

import java.io.File

/**
 * Created by mondhs on 18.2.19.
 */

class LiepaRecognitionContext{
    val allCommandList: MutableList<String> = mutableListOf()
    val activeCommandList: MutableList<String> = mutableListOf()
    var currentWord: String = ""
    var previousWord: String = ""
    var testedCommands = 0;
    var correctCommands = 0;
    var isRecogntionActive = false
}

class LiepaRecognitionHelper{

    fun readFile(fileName:String):String{
        return fileName
    }

    /**
     * Oversimplified grammar parser. take only lines that ends with or("|") grammar symbol
     * and strip it to get.
     * @return list of supported commands
     */
    fun parseGrammar(grammarContent:String):List<String>{
        var lines = grammarContent.lines()
                .filter { it.endsWith("|") }
                .map { it.replace("|","").trim() }

        return lines
    }

    /**
     * Initialize context to track recognition results
     * @return new context instance
     */
    fun initConxet(liepaCommandFileConent: String): LiepaRecognitionContext {
        var ctx = LiepaRecognitionContext()
        ctx.allCommandList.addAll(this.parseGrammar(liepaCommandFileConent))
        return ctx


    }

    /**
     * Get next command from the grammar list. if list is empty get fresh list and start over again.
     * @return next command to be pronounced
     */
    fun nextWord(ctx: LiepaRecognitionContext): String {
        var newCommand = "Nėra žodžių(kažkas blogai su žodynu)"

        if(ctx.allCommandList.size == 0) {
            return newCommand
        }else if(ctx.activeCommandList.size == 0) {
            ctx.allCommandList.shuffle()
            ctx.activeCommandList.addAll(ctx.allCommandList)
        }
        newCommand =  ctx.activeCommandList.removeAt(0)
        ctx.previousWord =ctx.currentWord
        ctx.currentWord = newCommand
        return newCommand
    }

    /**
     * Compare recognized command with what we expected
     * @return ratio between correct and total matches
     */
    fun checkRecognition(ctx: LiepaRecognitionContext, hypstr: String): Int {
        ctx.testedCommands++
        if(hypstr.trim().toLowerCase() == ctx.previousWord.trim().toLowerCase()){
            ctx.correctCommands++
        }
        var correctCmdRatio = 0;
        if(ctx.testedCommands > 0){
            correctCmdRatio = (ctx.correctCommands*100/ctx.testedCommands)
        }
        return  correctCmdRatio

    }
}