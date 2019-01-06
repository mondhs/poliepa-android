package io.github.mondhs.poliepa

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import edu.cmu.pocketsphinx.Hypothesis
import edu.cmu.pocketsphinx.RecognitionListener
import edu.cmu.pocketsphinx.SpeechRecognizer
import edu.cmu.pocketsphinx.SpeechRecognizerSetup
import io.github.mondhs.poliepa.helper.*
import kotlinx.android.synthetic.main.recognition_manual_activity.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.longToast
import org.jetbrains.anko.onComplete
import org.jetbrains.anko.uiThread
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException


/* Named searches allow to quickly reconfigure the decoder */
private const val LIEPA_CMD = "liepa_commands"


/* Used to handle permission request */
private const val PERMISSIONS_REQUEST_RECORD_AUDIO = 1


class RecognitionManualActivity : AppCompatActivity() {

    private val TAG = RecognitionManualActivity::class.java.simpleName + "_TAG"


    private var recognizer: SpeechRecognizer? = null

    private var liepaContext = LiepaRecognitionContext()

    private val liepaHelper = LiepaContextHelper()

    private val liepaTransportHelper = LiepaTransportJsonHelper()


//    private var uiThreadHandler: Handler = Handler()

    private val recognitionListenerImpl = object : RecognitionListener {
        var uttStartTime:Long = 0
        var lastPartialResultTime:Long = 0
        var uttStartFromSampelNo:Long = 0
        var uttno:Int = 0
        /*************************
         * RecognitionListener
         */

        override fun onResult(hypothesis: Hypothesis?, samplesSeqNo:Long, timeFromStartMs:Long) {
            Log.i(TAG, "[onResult]+++")
            hypothesis?.let {

                val isRecognizedCorrectly: Boolean = liepaHelper.isRecognized(liepaContext.currentPhraseText, it.hypstr)
                val timeCpuRatio = (timeFromStartMs-this.uttStartTime)/((samplesSeqNo-this.uttStartFromSampelNo)/16.0)

                val recognitionResult = TransportRecognitionResult()
                recognitionResult.isRecognized = isRecognizedCorrectly
                recognitionResult.recognizedText = it.hypstr
                recognitionResult.requestedText  = liepaContext.currentPhraseText
                recognitionResult.uttno = recognizer?.decoder?.uttno ?: 0
                recognitionResult.timeCpuRatio = timeCpuRatio
                val wordList:MutableList<String> = mutableListOf()
                recognizer?.decoder?.seg()?.forEach {
                    wordList.add("{'word':'${it.word}','start':'${it.startFrame}','end':'${it.endFrame}','ascore':'${it.ascore}','lback':'${it.lback}','lscore':'${it.lscore}','prob':'${it.prob}' }")
                }
                val wordListStr = wordList.joinToString (",",prefix="[",postfix = "]")
                recognitionResult.wordRegions = wordListStr
                Log.i(TAG, "wordListStr: $wordListStr")

                liepaTransportHelper.processAudioFile(liepaContext, recognitionResult)

                //request new phrase
                ui_pronounce_request_text.setText("")


                //for automatic mode start again
//            if(liepaContext.prefRecognitionAutomationInd){
//                if(this.recognizer != null) switchRecordingMode(liepaContext, recognizer!!)
//            }
//            this.recognizer?.decoder?.lattice?.write(liepaContext.audioDir.path)
//            this.recognizer?.decoder?.lattice?.writeHtk(liepaContext.audioDir.path)

            }
        }

        override fun onPartialResult(hypothesis: Hypothesis?, samplesSeqNo:Long, timeFromStartMs:Long, maxSampleValue:Int) {
            hypothesis?.let{
                Log.i(TAG, "[onPartialResult]+++")
                lastPartialResultTime = timeFromStartMs
            }

        }

        override fun onTimeout() {
            Log.i(TAG, "[${this.uttno}][onTimeout]+++")
            if (recognizer != null) switchRecordingMode(RecordStateRequest.STOP, liepaContext, recognizer!!) //pause to process results
        }

        override fun onBeginningOfSpeech(samplesSeqNo:Long, timeFromStartMs:Long) {
            this.uttno = recognizer?.decoder?.uttno ?: 0
            Log.i(TAG, "[${this.uttno}][onBeginningOfSpeech]+++  $samplesSeqNo $timeFromStartMs")
            this.uttStartTime = timeFromStartMs
            this.uttStartFromSampelNo = samplesSeqNo
            this.lastPartialResultTime = timeFromStartMs
            onRecordingStart()
        }

        override fun onEndOfSpeech(samplesSeqNo:Long, timeFromStartMs:Long) {
            Log.i(TAG, "[${this.uttno}][onEndOfSpeech]+++ audioSamples: ${samplesSeqNo-this.uttStartFromSampelNo} processingTime = ${timeFromStartMs-this.uttStartTime}")
        }

        override fun onError(error: Exception?) {
            Log.i(TAG, "[onError]+++ " + error?.message, error)
            ui_pronounce_request_text.setText(error?.message)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.recognition_manual_activity)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)




        Log.d(TAG, "[onCreate]+++")
        liepaContext = liepaHelper.loadContext()

        ui_record_indication_btn.setOnClickListener{
            if (recognizer != null) {
                if (liepaContext.isRecordingStarted){
                    switchRecordingMode(RecordStateRequest.STOP, liepaContext, recognizer!!)
                }else{
                    switchRecordingMode(RecordStateRequest.RECORD, liepaContext, recognizer!!)
                }
            }
        }

    }

    /**
     *
     */
//    private fun initContext(): LiepaRecognitionContext {
//        val assets = Assets(this)
//        val assetsDir = assets.syncAssets()
//        val aLiepaCtx =liepaHelper.initContext( assetsDir,this.getPreferences(Context.MODE_PRIVATE))
//
//        return aLiepaCtx
//    }


    override fun onResume() {
        super.onResume()
        Log.i(TAG, "[onResume]+++")
        verificationProcedureBeforeLounch()

    }

    private fun verificationProcedureBeforeLounch(){
        Log.i(TAG, "[verificationProcedureBeforeLounch]+++")
        if (!liepaContext.prefAgreeTermsInd){
            finish()
            val intent = Intent(this, UserPreferenceActivity::class.java)
            startActivity(intent)
            return
        }

        if(checkPermissionForRecognition()){
            Log.d(TAG, "[onSuccess] Checked permission+++")
            doRecognition()
        }//else wait till it will be called #onRequestPermissionsResult() and we will repeat this same logic once again
    }

    private fun doRecognition() {
        //update UI




        // Recognizer initialization is a time-consuming and it involves IO,
        // so we execute it in async task

        try{
            doAsync({
                Log.i(TAG, "[doRecognition] doAsync failed ", it)
                finish()
                //proceed with local stuff
            }) {

//                ui_read_phrase_progress.visibility = View.VISIBLE
                Log.i(TAG, "[doRecognition] doAsync+++")
                onComplete {
                    Log.i(TAG, "[doRecognition] onComplete +++")
                    uiThread {
                        setupRecognizer(liepaContext)
                        if (recognizer != null) it.switchRecordingMode(RecordStateRequest.STOP, liepaContext, recognizer!!)
//                        ui_read_phrase_progress.visibility = View.INVISIBLE
                        longToast("Liepa klauso")
                        Log.i(TAG, "[doRecognition] onComplete ---")
                    }
                }
                Log.i(TAG, "[doRecognition] doAsync---")
            }.get(3, TimeUnit.SECONDS)
        }catch (e: TimeoutException){
            throw IllegalArgumentException("Something bad happened",e)
        }

    }




    @Throws(IOException::class)
    private fun setupRecognizer(liepaContext: LiepaRecognitionContext) {

        this.recognizer = SpeechRecognizerSetup.defaultSetup()
                .setAcousticModel(liepaHelper.findRecognitionAcousticModelFile(liepaContext))
                .setDictionary(liepaHelper.findRecognitionDictionaryFile(liepaContext))
                .setRawLogDir(liepaContext.audioDir)
                .recognizer

        recognizer?.addListener(recognitionListenerImpl)
        var languageModelFile = liepaHelper.findRecognitionLanguageModelFile(liepaContext)
        if (RecognitionModel.Type.GRAM == RecognitionModel.Type.valueOf(liepaContext.recognitionModelType)) {
            recognizer?.addGrammarSearch(LIEPA_CMD, languageModelFile)
        } else if ( RecognitionModel.Type.LM == RecognitionModel.Type.valueOf(liepaContext.recognitionModelType)){
            recognizer?.addNgramSearch(LIEPA_CMD, languageModelFile)
        } else if ( RecognitionModel.Type.REMOTE == RecognitionModel.Type.valueOf(liepaContext.recognitionModelType)) {
//            recognizer?.addGrammarSearch(LIEPA_CMD, liepaContext.liepaCommandsGrammar)
            TODO("Implement how remote services works. Should consume plain text instead of files.")
        }

        liepaTransportHelper.prepareForRecognition(liepaContext.audioDir)


    }



    override fun onDestroy() {
        super.onDestroy()
        shutdownRecognition()
    }

    override fun onPause() {
        super.onPause()
        shutdownRecognition()
    }

    private fun shutdownRecognition() {
        Log.i(TAG, "[shutdownRecognition]+++")
//        ui_read_phrase_progress.visibility = View.INVISIBLE
//        uiThreadHandler.removeCallbacksAndMessages(null);
        recognizer?.let {
            it.cancel()
            it.shutdown()
            onRecordingStop()
            liepaContext.isRecordingStarted = false
            longToast("Liepa nebegirdi")
        }
    }



    private fun onRecordingStop(){
        ui_record_indication_btn.setCompoundDrawablesWithIntrinsicBounds( android.R.drawable.ic_btn_speak_now, 0, 0, 0); //setColorFilter(Color.GRAY, PorterDuff.Mode.SRC_ATOP)
        ui_record_indication_btn.setText("Pradėk įrašymą")
        ui_pronounce_request_label.visibility  = View.INVISIBLE
    }

    private fun onRecordingStart(){
//        ui_record_indication_btn.setColorFilter(Color.RED, PorterDuff.Mode.SRC_ATOP)
        ui_record_indication_btn.setCompoundDrawablesWithIntrinsicBounds( android.R.drawable.ic_delete, 0, 0, 0);
        ui_record_indication_btn.setText("Stabdyk įrašymą")
        ui_pronounce_request_label.visibility  = View.VISIBLE
    }

    private fun startRecordingWithDelay(recognizer: SpeechRecognizer,liepaContext: LiepaRecognitionContext){
        val currentPhraseText = liepaContext.currentPhraseText
        Log.i(TAG, "[startRecordingWithDelay]+++ $currentPhraseText")
        recognizer.startListening(LIEPA_CMD, 10000)//phrase should be up to 10 seconds
//        val requestPhaseDelay =  liepaContext.requestPhaseDelayInSec*1000
//        val runnableStartListening = Runnable {
//            Log.i(TAG, "[startRecordingWithDelay] Handler +++ $currentPhraseText")
////            ui_read_phrase_progress.visibility = View.INVISIBLE
//
//            Log.i(TAG, "[startRecordingWithDelay] Handler --- $currentPhraseText")
//        }
//        ui_read_phrase_progress.visibility = View.VISIBLE

//        uiThreadHandler.removeCallbacksAndMessages(null);
//        uiThreadHandler.postDelayed(runnableStartListening, requestPhaseDelay.toLong())
        Log.i(TAG, "[startRecordingWithDelay]--- $currentPhraseText ")
    }


    private fun switchRecordingMode(recordStateRequest: RecordStateRequest, liepaContext: LiepaRecognitionContext, recognizer: SpeechRecognizer){
        Log.i(TAG, "[switchRecordingMode]+++ $recordStateRequest")


        if (RecordStateRequest.RECORD == recordStateRequest && liepaContext.isRecordingStarted) {
            Log.i(TAG, "[${recognitionListenerImpl.uttno}][switchRecordingMode] requested RECORD, but it is already RECORDing. Stopping and restarting recognizer: $recordStateRequest")
            //for automatic continue recording
            recognizer.stop()
            onRecordingStop()
            startRecordingWithDelay(recognizer, liepaContext)
            onRecordingStart()
            //requested RECORD, but recording not started
        }else if (RecordStateRequest.RECORD == recordStateRequest && !liepaContext.isRecordingStarted) {
            Log.i(TAG, "[${recognitionListenerImpl.uttno}][switchRecordingMode] requested RECORD, but recording not started. Starting recognizer: $recordStateRequest")
            ui_pronounce_request_text.setBackgroundColor( ContextCompat.getColor(applicationContext,  R.color.colorPrimary) )
            liepaContext.isRecordingStarted = true
            startRecordingWithDelay(recognizer, liepaContext)
            onRecordingStart()
        }else if (RecordStateRequest.STOP == recordStateRequest && liepaContext.isRecordingStarted) {
            Log.i(TAG, "[${recognitionListenerImpl.uttno}][switchRecordingMode] requested stop and now system is ${liepaContext.isRecordingStarted}==recording. Stopping recognizer: $recordStateRequest")
            //it is recording and requested to stop
            liepaContext.isRecordingStarted = false
            recognizer.stop()
            onRecordingStop()
            Handler().postDelayed({
                val nextPhrase = liepaHelper.updateNextWord(liepaContext)
                ui_pronounce_request_text.setText(nextPhrase)
            }, 500)

            ui_pronounce_request_text.setBackgroundColor( ContextCompat.getColor(applicationContext, android.R.color.transparent) )
            //if requesting stop, remove waiting indicator and kill thread.
            //clean it up
//            liepaTransportHelper.prepareForRecognition(liepaContext.audioDir)
        }
        Log.i(TAG, "[switchRecordingMode]--- $recordStateRequest")


    }

    /**
     * If access are granted it should  #onRequestPermissionsResult()
     */
    private fun checkPermissionForRecognition(): Boolean {
        Log.i(TAG, "[checkPermissionForRecognition]+++")

        val permissionAudioCheck = ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.RECORD_AUDIO)
        if (permissionAudioCheck != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    PERMISSIONS_REQUEST_RECORD_AUDIO)
            return false
        }
        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.i(TAG, "[onRequestPermissionsResult]+++" + requestCode)

        if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) {
            if (grantResults.isNotEmpty()  && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                //it will call onResume() after this logic is done
                Log.i(TAG, "[onRequestPermissionsResult]+++Granted????" + requestCode)
            } else {
                longToast("Ačiū! Grįžktite jei norėsite prisidėti!")
                finish()
            }
        }

    }

    /// Menu logic
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu to use in the action bar
        val inflater = menuInflater
        inflater.inflate(R.menu.recogition_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle presses on the action bar menu items
        when (item.itemId) {
            R.id.action_preference -> {
                shutdownRecognition()
                finish()
                val intent = Intent(this, UserPreferenceActivity::class.java)
                startActivity(intent)
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }



}


