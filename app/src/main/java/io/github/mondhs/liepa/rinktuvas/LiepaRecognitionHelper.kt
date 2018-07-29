package io.github.mondhs.liepa.rinktuvas

import android.content.SharedPreferences
import android.util.Log
import edu.cmu.pocketsphinx.Hypothesis
import java.io.File
import java.net.URL
import java.util.*
import android.os.Build



/**
 * Created by mondhs on 18.2.19.
 */

class LiepaRecognitionContext{
    companion object {
        const val KEY_PHONE_ID: String = "phone_id"
        const val KEY_PHONE_MODEL: String = "phone_model"
        const val KEY_USER_NAME: String = "user_name"
        const val KEY_USER_AGE_GROUP: String = "age_group"
        const val KEY_USER_GENDER: String = "gender"
        const val KEY_PREF_RECOGNITION_AUTOMATION_IND: String = "is_automation"
        const val KEY_PREF_AGREE_TERMS: String = "is_agree_terms"
        const val KEY_RECOGN_IS_RECOGNIZED: String = "is_recognized"
        const val KEY_RECOGN_REQUESTED_TEXT: String = "requested_text"
        const val KEY_RECOGN_RECOGNIZED_TEXT: String = "recognized_text"
        const val KEY_PHRASES_TESTED_NUM: String = "phrases_tested_num"
        const val KEY_PHRASES_CORRECT_NUM: String = "phrases_correct_num"


    }


    val allCommandList: MutableList<String> = mutableListOf()
    val activeCommandList: MutableList<String> = mutableListOf()
    var currentPhraseText: String = ""
    var previousPhraseText: String = ""
    var phrasesTestedNum = 0
    var phrasesCorrectNum = 0
    var isRecognitionActive = false
    var audioDir: File = File("./")
    var liepaCommandFile: File = File(audioDir, "test.gram")

    var lastRecognitionWordsFound: Boolean = false

    var prefRecognitionAutomationInd:Boolean = true
    var prefAgreeTermsInd:Boolean = false

    var phoneId: String = ""
    var phoneModel: String = ""
    var userName: String = ""
    var userAgeGroup: String = ""
    var userGender: String = ""
    var internetEnabled: Boolean = false




}

class LiepaTransportHelper {

    val TAG= "LiepaTransportHelper"

    fun prepareForRecognition(audioDir:File){
        require(audioDir.exists() && audioDir.isDirectory)
        audioDir.walk().filter { it.name.endsWith("raw") }.forEach {
                Log.i( TAG, "[prepareForRecognition]Deleted: $it"  )
                it.delete()
        }

    }

    fun processAudioFile(context: LiepaRecognitionContext, hypothesis: Hypothesis?, isRecognized:Boolean) {
        val audioDir = context.audioDir
        val requestedText  = context.previousPhraseText
        val recognizedText:String = hypothesis?.hypstr?.toString() ?: "";

        Log.i(TAG,"processAudioFile+++")
        var fileCount = 0
        require(audioDir.exists() && audioDir.isDirectory)
        //look at all files thats ends with .raw. sort higher number first. remove first as it is current wich system is working with
        audioDir.walk().filter { it.name.endsWith("raw") }.sortedByDescending { it.name}.drop(1).forEach {
                fileCount++
                if(fileCount==1) {
                    Log.i( TAG, "[processAudioFile]Sending: $it"  )
                    val url = URL("https://lieparinktuvas-1530815329656.appspot.com/upload")
                    val workingFile = File(audioDir, UUID.randomUUID().toString() + ".audio")
                    Log.i(TAG, "Renaming file from ${it.name} to ${workingFile.name}")
                    it.renameTo(workingFile)
                    Thread {
                        val multipart = Multipart(url)
                        multipart.addFormField(LiepaRecognitionContext.KEY_RECOGN_REQUESTED_TEXT, requestedText)
                        multipart.addFormField(LiepaRecognitionContext.KEY_RECOGN_RECOGNIZED_TEXT, recognizedText)
                        multipart.addFormField(LiepaRecognitionContext.KEY_RECOGN_IS_RECOGNIZED, isRecognized.toString())
                        multipart.addFormField(LiepaRecognitionContext.KEY_PHONE_ID, context.phoneId)
                        multipart.addFormField(LiepaRecognitionContext.KEY_PHONE_MODEL, context.phoneModel.toString())
                        multipart.addFormField(LiepaRecognitionContext.KEY_USER_NAME, context.userName)
                        multipart.addFormField(LiepaRecognitionContext.KEY_USER_GENDER, context.userGender )
                        multipart.addFormField(LiepaRecognitionContext.KEY_USER_AGE_GROUP, context.userAgeGroup.toString())

                        multipart.addFileFlacPart("file", workingFile, workingFile.name, "audio/raw")
                        multipart.upload(
                                onSuccess = { w ->
                                    Log.i(TAG, "[processAudioFile] uploaded successfully File($workingFile)  $w")
                                },
                                onFailure = { e ->
                                    Log.i(TAG, "[processAudioFile]upload failed File($workingFile)! $e")
                                })
                        Log.i( TAG, "[processAudioFile]Uploaded and deleted: $workingFile"  )
                        workingFile.delete()
                    }.start()
                }else{
                    it.delete()
                    Log.i( TAG, "[processAudioFile]Deleted: $it"  )
                }


        }
    }

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
    fun initContext(liepaCommandFile: File, audioDir:File, sharedPref :SharedPreferences): LiepaRecognitionContext {
        var ctx = LiepaRecognitionContext()
        ctx.liepaCommandFile = liepaCommandFile
        val liepaCommandFileContent = liepaCommandFile.readText()
        ctx.allCommandList.addAll(this.parseGrammar(liepaCommandFileContent))
        ctx.audioDir = audioDir
        ctx.phoneId = retrievePhoneId(sharedPref)
        ctx.phrasesTestedNum = sharedPref.getInt(LiepaRecognitionContext.KEY_PHRASES_TESTED_NUM, ctx.phrasesTestedNum)
        ctx.phrasesCorrectNum = sharedPref.getInt(LiepaRecognitionContext.KEY_PHRASES_CORRECT_NUM, ctx.phrasesCorrectNum)
        ctx.phoneModel = Build.MANUFACTURER + " ; " + Build.MODEL + " ; " + Build.PRODUCT
        ctx.userName = sharedPref.getString(LiepaRecognitionContext.KEY_USER_NAME, ctx.userName)
        try {
            ctx.userAgeGroup = sharedPref.getString(LiepaRecognitionContext.KEY_USER_AGE_GROUP, ctx.userAgeGroup)
        }catch (e: Throwable) {
            //do nothing, use default value. This needed due to refactoring. In next version should be removed.
        }
        ctx.userGender = sharedPref.getString(LiepaRecognitionContext.KEY_USER_GENDER, ctx.userGender)
        ctx.prefRecognitionAutomationInd = sharedPref.getBoolean(LiepaRecognitionContext.KEY_PREF_RECOGNITION_AUTOMATION_IND, ctx.prefRecognitionAutomationInd)
        
        


        ctx.prefAgreeTermsInd = sharedPref.getBoolean(LiepaRecognitionContext.KEY_PREF_AGREE_TERMS, ctx.prefAgreeTermsInd)

        return ctx
    }

    fun writeContext(ctx:LiepaRecognitionContext, sharedPref :SharedPreferences): LiepaRecognitionContext {
        with (sharedPref.edit()) {
            putString(LiepaRecognitionContext.KEY_USER_NAME, ctx.userName)
            putString(LiepaRecognitionContext.KEY_USER_GENDER, ctx.userGender)
            putString(LiepaRecognitionContext.KEY_USER_AGE_GROUP, ctx.userAgeGroup)
            putBoolean(LiepaRecognitionContext.KEY_PREF_AGREE_TERMS, ctx.prefAgreeTermsInd)
            putBoolean(LiepaRecognitionContext.KEY_PREF_RECOGNITION_AUTOMATION_IND, ctx.prefRecognitionAutomationInd)
            putInt(LiepaRecognitionContext.KEY_PHRASES_TESTED_NUM, ctx.phrasesTestedNum)
            putInt(LiepaRecognitionContext.KEY_PHRASES_CORRECT_NUM, ctx.phrasesCorrectNum)


            commit()
        }
        return ctx
    }


    /////// Phone ID logic /////

    private fun readPhoneIdFromMemory(sharedPref :SharedPreferences):String{
        return sharedPref.getString(LiepaRecognitionContext.KEY_PHONE_ID, "")
    }
    private fun writePhoneIdToMemory(phoneId:String, sharedPref :SharedPreferences):String{
        with (sharedPref.edit()) {
            putString(LiepaRecognitionContext.KEY_PHONE_ID, phoneId)
            commit()
        }

        return phoneId
    }

    private fun retrievePhoneId(sharedPref :SharedPreferences): String {
        val phoneIdMemory = readPhoneIdFromMemory(sharedPref)
        if(phoneIdMemory.isNotBlank()){
            return phoneIdMemory
        }
        val phoneIdCalculated = UUID.randomUUID().toString()
        return writePhoneIdToMemory(phoneIdCalculated, sharedPref)
    }

    /**
     * Get next command from the grammar list. if list is empty get fresh list and start over again.
     * @return next command to be pronounced
     */
    fun nextWord(ctx: LiepaRecognitionContext): String {
        var newPhrase = "Nėra žodžių(kažkas blogai su žodynu)"

        if(ctx.allCommandList.size == 0) {
            return newPhrase
        }else if(ctx.activeCommandList.size == 0) {
            ctx.allCommandList.shuffle()
            ctx.activeCommandList.addAll(ctx.allCommandList)
        }
        newPhrase =  ctx.activeCommandList.removeAt(0)
        ctx.previousPhraseText =ctx.currentPhraseText
        ctx.currentPhraseText = newPhrase
        return newPhrase
    }

    fun isRecognized(requestedText: String, recognizedText: String): Boolean {

        if (recognizedText.trim().toLowerCase() == requestedText.trim().toLowerCase()) {
            return true
        }
        return false
    }

    /**
     * Compare recognized command with what we expected
     * @return ratio between correct and total matches
     */
    fun updateRecognitionResult(ctx: LiepaRecognitionContext, hypstr: String): Int {
        ctx.phrasesTestedNum++
        if(isRecognized(ctx.previousPhraseText,hypstr)){
            ctx.phrasesCorrectNum++
        }
        var correctCmdRatio = 0;
        if(ctx.phrasesTestedNum > 0){
            correctCmdRatio = (ctx.phrasesCorrectNum*100/ctx.phrasesTestedNum)
        }
        return  correctCmdRatio

    }


}