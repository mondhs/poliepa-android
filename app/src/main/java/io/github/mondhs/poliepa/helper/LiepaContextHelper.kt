package io.github.mondhs.poliepa.helper

import android.content.SharedPreferences
import android.util.Log
import java.io.File
import java.util.*
import android.os.Build
import kotlinx.coroutines.experimental.withTimeout
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.net.URL


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
        const val KEY_PREF_SEND_TO_CLOUD: String = "send_to_cloud"
        const val KEY_PREF_REQUEST_PHRASE_DELAY: String = "request_phase_delay_in_sec"


    }


    public val allCommandList: MutableList<String> = mutableListOf()
    val activeCommandList: MutableList<String> = mutableListOf()
    var currentPhraseText: String = ""
    var previousPhraseText: String = ""
    var phrasesTestedNum = 0
    var phrasesCorrectNum = 0
    var audioDir: File = File("./")

    var lastRecognitionWordsFound: Boolean = false

    var prefRecognitionAutomationInd:Boolean = true
    var prefAgreeTermsInd:Boolean = false
    var prefSendToCloud:Boolean = true

    var phoneId: String = ""
    var phoneModel: String = ""
    var userName: String = ""
    var userAgeGroup: String = ""
    var userGender: String = ""
    var internetEnabled: Boolean = true

    var isRecordingStarted: Boolean = true//it will be reversed to false during start
    var requestPhaseDelayInSec: Int = 3
    var liepaCommandsGrammar: String = ""
    var liepaCommandsDictionaryFileName: String = "liepa-lt-lt.dict"
    var assetsDir: File = File("./")


}


class LiepaContextHelper{

    val TAG= LiepaContextHelper::class.java.simpleName

    private val grammarGenerator = LiepaGrammarGenerator()
    private val dictionaryGenerator = LiepaDictionaryGenerator()

    /**
     * Initialize context to track recognition results
     * @return new context instance
     */
    fun initContext(assetsDir:File, sharedPref :SharedPreferences): LiepaRecognitionContext {
        var ctx = LiepaRecognitionContext()
        val audioDir = File(assetsDir, "audio/")


        setupLocalPhrase(ctx, File(assetsDir, "liepa_commands.gram").readText())
        //initialize first text phrase
        updateNextWord(ctx)

        ctx.audioDir = audioDir
        ctx.assetsDir = assetsDir
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
        ctx.prefSendToCloud = sharedPref.getBoolean(LiepaRecognitionContext.KEY_PREF_SEND_TO_CLOUD, ctx.prefSendToCloud)
        ctx.requestPhaseDelayInSec = sharedPref.getInt(LiepaRecognitionContext.KEY_PREF_REQUEST_PHRASE_DELAY, ctx.requestPhaseDelayInSec)


        return ctx
    }

    fun setupLocalPhrase(ctx: LiepaRecognitionContext, grammarText: String) {
        // Create grammar-based search for digit recognition
        ctx.liepaCommandsGrammar = grammarText
        ctx.allCommandList.addAll(grammarGenerator.extractPhrasesFromGrammar(ctx.liepaCommandsGrammar))

    }

    /**
     * extracted phrases processed to create recognition artifacts: dictionary and grammar
     */
    public fun updateCtxWithRemotePhrase(ctx: LiepaRecognitionContext, assetsDir:File):Boolean {

        val dictionaryFileName = "liepa-generated-lt-lt.dict"

        var remotePhrases = retrievePhrasesRemotely()
        if(remotePhrases.isEmpty()){
            return false
        }
        val grammarFile = File(assetsDir, "liepa_generated_commands.gram")
        var remoteGrammar = grammarGenerator.generateGrammarFromPhrases(remotePhrases)
        grammarFile.printWriter().use { out ->
            out.print(remoteGrammar)
        }
        val uniqueWords = grammarGenerator.extractUniqueWordsFromPhrases(remotePhrases)

        val generatedDictionaryMap = dictionaryGenerator.transcribeDictionary(uniqueWords)
        val generatedDictionaryText = dictionaryGenerator.dictionaryToString(generatedDictionaryMap)

        val dictionaryFile = File(assetsDir, dictionaryFileName)
        dictionaryFile.parentFile.mkdirs()
        dictionaryFile.printWriter().use { out ->
            out.print(generatedDictionaryText)
        }

        //update context
        ctx.liepaCommandsDictionaryFileName = dictionaryFileName
        ctx.allCommandList.addAll(remotePhrases)
        ctx.liepaCommandsGrammar = remoteGrammar
        //change to first phrase from new set
        updateNextWord(ctx)



        return true
    }

    /**
     * Contact server and read text into string
     */
    private fun retrievePhrasesRemotely(): List<String> {
//        return ("labas,labas rytas,labas vakaras,viso gero,malonu susipažinti,ačiū,kur yra prausykla,atsiprašau," +
//                "gal jūs galite man padėti,kiek kainuoja ,sėkmės,kas tai,kaip galiu nuvykti,kur yra viešbutis," +
//                "kur yra metro stotis,aš nesuprantu,atleiskite,aš esu iš lietuvos,mano vardas,susirgau,vieną minutę prašau," +
//                "gerai,jūs juokaujate,ar galėtumėte,aš galiu tai padaryti pats,sveiki atvyke,kaip apie," +
//                "geros kelionės,gero savaitgalio,šaunu,ar tu kalbi angliškai,jonas myli oną,ona myli joną," +
//                "jau saulelė vėl atkopdama budino svietą ir žiemos šaltos trūsus pargraudama juokės").split(",")

//        val url = "http://lieparinktuvas-1530815329656.appspot.com/phrases"

        return URL("http://lieparinktuvas-1530815329656.appspot.com/phrases").readText().split("\n")
    }

    fun writeContext(ctx: LiepaRecognitionContext, sharedPref :SharedPreferences): LiepaRecognitionContext {
        with (sharedPref.edit()) {
            putString(LiepaRecognitionContext.KEY_USER_NAME, ctx.userName)
            putString(LiepaRecognitionContext.KEY_USER_GENDER, ctx.userGender)
            putString(LiepaRecognitionContext.KEY_USER_AGE_GROUP, ctx.userAgeGroup)
            putBoolean(LiepaRecognitionContext.KEY_PREF_AGREE_TERMS, ctx.prefAgreeTermsInd)
            putBoolean(LiepaRecognitionContext.KEY_PREF_SEND_TO_CLOUD, ctx.prefSendToCloud)
            putBoolean(LiepaRecognitionContext.KEY_PREF_RECOGNITION_AUTOMATION_IND, ctx.prefRecognitionAutomationInd)
            putInt(LiepaRecognitionContext.KEY_PHRASES_TESTED_NUM, ctx.phrasesTestedNum)
            putInt(LiepaRecognitionContext.KEY_PHRASES_CORRECT_NUM, ctx.phrasesCorrectNum)
            putInt(LiepaRecognitionContext.KEY_PREF_REQUEST_PHRASE_DELAY, ctx.requestPhaseDelayInSec)


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
    fun updateNextWord(ctx: LiepaRecognitionContext): String {
        val currentPhraseText = ctx.currentPhraseText
        Log.i(TAG, "[nextWord]+++ $currentPhraseText")
        var newPhrase = "Nėra žodžių(kažkas blogai su žodynu)"

        if(ctx.allCommandList.size == 0) {
            return newPhrase
        }else if(ctx.activeCommandList.size == 0) {
            ctx.allCommandList.shuffle()
            ctx.activeCommandList.addAll(ctx.allCommandList)
        }
        newPhrase =  ctx.activeCommandList.removeAt(0)
        ctx.previousPhraseText =currentPhraseText
        ctx.currentPhraseText = newPhrase

        Log.i(TAG, "[nextWord]--- $currentPhraseText New: ${newPhrase}")
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
    fun updateRecognitionResult(ctx: LiepaRecognitionContext, isRecognizedCorrectly: Boolean): Int {
        ctx.phrasesTestedNum++
        if(isRecognizedCorrectly){
            ctx.phrasesCorrectNum++
        }
        var correctCmdRatio = 0;
        if(ctx.phrasesTestedNum > 0){
            correctCmdRatio = (ctx.phrasesCorrectNum*100/ctx.phrasesTestedNum)
        }
        return  correctCmdRatio

    }


}