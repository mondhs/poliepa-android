package io.github.mondhs.poliepa

import android.Manifest
import android.content.Context
import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import edu.cmu.pocketsphinx.Hypothesis
import edu.cmu.pocketsphinx.RecognitionListener
import edu.cmu.pocketsphinx.SpeechRecognizer
import edu.cmu.pocketsphinx.SpeechRecognizerSetup
import io.github.mondhs.poliepa.helper.RecognitionModel.Type

import org.jetbrains.anko.doAsync
import org.jetbrains.anko.longToast
import org.jetbrains.anko.uiThread
import java.io.IOException
import java.lang.Exception
import kotlinx.android.synthetic.main.recognition_automatic_activity.*
import org.jetbrains.anko.onComplete
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import android.os.CountDownTimer
import io.github.mondhs.poliepa.helper.*
import java.lang.IllegalArgumentException
import kotlin.system.measureTimeMillis


/* Named searches allow to quickly reconfigure the decoder */
private const val LIEPA_CMD = "liepa_recognition"


/* Used to handle permission request */
private const val PERMISSIONS_REQUEST_RECORD_AUDIO = 1


class RecognitionAutomaticActivity : AppCompatActivity() {



    private val TAG = RecognitionAutomaticActivity::class.java.simpleName+"_TAG"


    private var recognizer: SpeechRecognizer? = null

    private var liepaContext = LiepaRecognitionContext()

    private val liepaHelper = LiepaContextHelper()

    private val liepaTransportHelper = LiepaTransportJsonHelper()


    private var countDownTimer: CountDownTimer? = null

    private val recognitionListenerImpl = object : RecognitionListener {

        var uttStartTime:Long = 0
        var lastPartialResultTime:Long = 0
        var uttStartFromSampelNo:Long = 0
        var uttno:Int = 0
        /**
         *
         */
        override fun onResult(hypothesis: Hypothesis?, samplesSeqNo:Long, timeFromStartMs:Long) {
            Log.i(TAG, "[${this.uttno}][onResult]+++ audioSamples: ${samplesSeqNo-this.uttStartFromSampelNo} processingTime = ${timeFromStartMs-this.uttStartTime}")
            hypothesis?.let {

                val isRecognizedCorrectly: Boolean = liepaHelper.isRecognized(liepaContext.currentPhraseText, it.hypstr)
                val correctCmdRatio: Int = liepaHelper.updateRecognitionResult(liepaContext, isRecognizedCorrectly)

                ui_recognition_result_view.progress = correctCmdRatio
                ui_result_stat.text = "%d/%d".format(liepaContext.phrasesCorrectNum, liepaContext.phrasesTestedNum)


                val timeCpuRatio = (timeFromStartMs-this.uttStartTime)/((samplesSeqNo-this.uttStartFromSampelNo)/16.0)

                Log.i(TAG, "[${this.uttno}][onResult] xRT:   $timeCpuRatio = ${(timeFromStartMs-this.uttStartTime)}/${(samplesSeqNo-this.uttStartFromSampelNo)/16.0}")
//                if (liepaContext.lastRecognitionWordsFound) {
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


                liepaTransportHelper.processAudioFile(liepaContext, recognitionResult)
                ui_recognized_text.setText("${it.hypstr} (Prašyta: ${liepaContext.currentPhraseText})")
                liepaHelper.updateNextWord(liepaContext)
            }
            if (recognizer != null) switchRecordingMode(RecordStateRequest.RECORD, liepaContext, recognizer!!) //assume continue recording always
        }

        override fun onPartialResult(hypothesis: Hypothesis?, samplesSeqNo:Long, timeFromStartMs:Long) {
            hypothesis?.let {
                Log.i(TAG, "[${this.uttno}][onPartialResult]+++ audioSamples: ${samplesSeqNo-this.uttStartFromSampelNo} processingTime = ${timeFromStartMs-this.uttStartTime}; timeFromStartMs=$timeFromStartMs; uttStartTime=${this.uttStartTime}")
                ui_recognized_text.setText(it.hypstr)
                lastPartialResultTime = timeFromStartMs
            }

        }

        override fun onTimeout() {
            Log.i(TAG, "[${this.uttno}][onTimeout]+++")
            if (recognizer != null) switchRecordingMode(RecordStateRequest.PAUSE, liepaContext, recognizer!!) //pause to process results
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
            var numberWordsWoSil = 0
            recognizer?.decoder?.seg()?.forEach {
                numberWordsWoSil++
                Log.i(TAG, String.format("[${this.uttno}][onEndOfSpeech] Word %s[%d-%d]; ascore: %d; lback: %d; lscore:%d; prob: %d ", it.word, it.startFrame, it.endFrame, it.ascore, it.lback, it.lscore, it.prob))
            }
            liepaContext.lastRecognitionWordsFound = numberWordsWoSil > 2//do not count 2 segments of silence in beginning and end.

        }

        override fun onError(error: Exception?) {
            Log.i(TAG, "[${this.uttno}][onError]+++ " + error?.message, error)
            ui_pronounce_request_text.setText(error?.message)
        }

        override fun onFrameProcessedEvent(samplesSeqNo:Long, maxSampleValue:Int){
            ui_record_level.progress = maxSampleValue
            Log.d(TAG, "[onFrameProcessedEvent ] maxSampleValue=$maxSampleValue")
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.recognition_automatic_activity)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        Log.d(TAG, "[onCreate]+++")

        liepaContext = liepaHelper.loadContext()

        ui_record_indication_btn.setOnClickListener {
            if (recognizer != null) {
//                val requestRecordState = !liepaContext.isRecordingStarted
                if (liepaContext.isRecordingStarted){
                    switchRecordingMode(RecordStateRequest.STOP, liepaContext, recognizer!!)
                }else{
                    switchRecordingMode(RecordStateRequest.RECORD, liepaContext, recognizer!!)
                }

            }
        }

    }



    override fun onResume() {
        super.onResume()
        Log.i(TAG, "[onResume]+++")
        verificationProcedureBeforeLaunch()

    }

    private fun verificationProcedureBeforeLaunch() {
        Log.i(TAG, "[verificationProcedureBeforeLaunch]+++")
        if (!liepaContext.prefAgreeTermsInd) {
            finish()
            val intent = Intent(this, UserPreferenceActivity::class.java)
            startActivity(intent)
            return
        }

        if (checkPermissionForRecognition()) {
            Log.d(TAG, "[onSuccess] Checked permission+++")
            doRecognition()
        }//else wait till it will be called #onRequestPermissionsResult() and we will repeat this same logic once again
    }

    private fun doRecognition() {
        //update UI


        // Recognizer initialization is a time-consuming and it involves IO,
        // so we execute it in async task

        try {
            doAsync({
                Log.i(TAG, "[doRecognition] doAsync failed ", it)
                finish()
                //proceed with local stuff
            }) {

                ui_read_phrase_progress.visibility = View.VISIBLE
                Log.i(TAG, "[doRecognition] doAsync+++")
                onComplete {
                    Log.i(TAG, "[doRecognition] onComplete +++")
                    uiThread {
                        setupRecognizer(liepaContext)
                        if (recognizer != null) it.switchRecordingMode(RecordStateRequest.STOP, liepaContext, recognizer!!)
                        ui_read_phrase_progress.visibility = View.INVISIBLE
                        longToast("Liepa pasiruošusi")
                        Log.i(TAG, "[doRecognition] onComplete ---")
                    }
                }
                Log.i(TAG, "[doRecognition] doAsync---")
            }.get(3, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            throw IllegalArgumentException("Something bad happened",e)
        }

    }


    @Throws(IOException::class)
    private fun setupRecognizer(liepaContext: LiepaRecognitionContext) {
        Log.d(TAG, "[setupRecognizer]+++")
        val performedInMils = measureTimeMillis {

            this.recognizer = SpeechRecognizerSetup.defaultSetup()
//                    .setInt("-audiosource",MediaRecorder.AudioSource.MIC.toDouble())
                    .setInteger("-audiosource",MediaRecorder.AudioSource.VOICE_RECOGNITION)
                    .setAcousticModel(liepaHelper.findRecognitionAcousticModelFile(liepaContext))
                    .setDictionary(liepaHelper.findRecognitionDictionaryFile(liepaContext))
                    .setRawLogDir(liepaContext.audioDir)
                    .recognizer



            val languageModelFile = liepaHelper.findRecognitionLanguageModelFile(liepaContext)
            when (Type.valueOf(liepaContext.recognitionModelType)){
                Type.GRAM -> recognizer?.addGrammarSearch(LIEPA_CMD, languageModelFile)
                Type.LM -> recognizer?.addNgramSearch(LIEPA_CMD, languageModelFile)
//                Type.REMOTE -> recognizer?.addGrammarSearch(LIEPA_CMD, liepaContext.liepaCommandsGrammar)
                else -> {
                    throw IllegalArgumentException("NOT Implemented")
                }
            }

            recognizer?.addListener(recognitionListenerImpl)
        }
        Log.d(TAG, "[setupRecognizer]--- performedInMils: $performedInMils")

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
        ui_read_phrase_progress.visibility = View.INVISIBLE
        countDownTimer?.cancel()
        recognizer?.let {
            it.cancel()
            it.shutdown()
            onRecordingStop()
            liepaContext.isRecordingStarted = false
            longToast("Liepa atpažintuvas atjungtas")
        }
    }


    private fun onRecordingStop() {
        ui_record_indication_btn.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_btn_speak_now, 0, 0, 0) //setColorFilter(Color.GRAY, PorterDuff.Mode.SRC_ATOP)
        ui_record_indication_btn.text = "Pradėk įrašymą"
        ui_user_instructions.visibility = View.INVISIBLE
        ui_pronounce_request_text.setBackgroundColor( ContextCompat.getColor(applicationContext, android.R.color.transparent) )
        ui_pronounce_request_text.setText("")
    }

    private fun onRecordingStart() {
        ui_record_indication_btn.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_delete, 0, 0, 0)
        ui_record_indication_btn.text = "Stok"
        ui_user_instructions.visibility = View.VISIBLE
    }


    private fun startRecordingWithDelay(recognizer: SpeechRecognizer, liepaContext: LiepaRecognitionContext) {


        val currentPhraseText = liepaContext.currentPhraseText
        ui_pronounce_request_text.setText(currentPhraseText)
        ui_user_instructions.text = "Persiskaityk tylai sau tekstą:"
        ui_user_instructions.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.presence_away, 0, 0, 0)


        Log.i(TAG, "[?${recognitionListenerImpl.uttno+1}][startRecordingWithDelay]+++ ")


        val requestPhaseDelayMsec = Math.max(3000L,liepaContext.requestPhaseDelayInSec * 1000L)

        ui_read_phrase_progress.max = requestPhaseDelayMsec.toInt()
        ui_read_phrase_progress.progress = 0
        ui_read_phrase_progress.visibility = View.VISIBLE

        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(requestPhaseDelayMsec, 100) {
            var isStarted:Boolean = false
            override fun onTick(millisUntilFinished: Long) {
                ui_read_phrase_progress.progress = requestPhaseDelayMsec.toInt()-millisUntilFinished.toInt()
                //start recording 0.4 second earlier than announced, to gather background noise
                if(millisUntilFinished<100 && !isStarted){
                    isStarted = true
                    Log.i(TAG, "[?${recognitionListenerImpl.uttno+1}][startRecordingWithDelay][onTick]  start recording $currentPhraseText")
                    recognizer.startListening(LIEPA_CMD, 2000)//wait till phrase is pronounced up to x second
                }
            }
            override fun onFinish() {
                //if something bad happens and recording did not started
                if(!isStarted){
                    isStarted = true
                    Log.i(TAG, "[?${recognitionListenerImpl.uttno+1}][startRecordingWithDelay][onFinish] This should never happen as recordingshould be started 2 sec earlier for $currentPhraseText")
                    recognizer.startListening(LIEPA_CMD, 2000)//wait till phrase is pronounced up to x second
                }
                Log.i(TAG, "[?${recognitionListenerImpl.uttno+1}][startRecordingWithDelay][onFinish] announce about recording $currentPhraseText")
                ui_read_phrase_progress.visibility = View.INVISIBLE
//                ui_record_indication_btn.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.presence_online, 0, 0, 0);
                ui_pronounce_request_text.setBackgroundColor( ContextCompat.getColor(applicationContext,  R.color.colorPrimary) )
                ui_user_instructions.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.presence_online, 0, 0, 0)
                ui_user_instructions.text = "Ištark garsiai tekstą:"


            }
        }.start()
        Log.i(TAG, "[?${recognitionListenerImpl.uttno+1}][startRecordingWithDelay]--- ")
    }


    private fun switchRecordingMode(recordStateRequest: RecordStateRequest, liepaContext: LiepaRecognitionContext, recognizer: SpeechRecognizer) {
//        val requestStateStr = if(requestRecordState) "Record" else "Stop"
        Log.i(TAG, "[${recognitionListenerImpl.uttno}][switchRecordingMode]+++ $recordStateRequest")


        //requested RECORD, but it is already RECORDing
        if (RecordStateRequest.RECORD == recordStateRequest && liepaContext.isRecordingStarted) {
            Log.i(TAG, "[${recognitionListenerImpl.uttno}][switchRecordingMode] requested RECORD, but it is already RECORDing. Stopping and restarting recognizer: $recordStateRequest")
            //for automatic continue recording
            recognizer.stop()
            onRecordingStop()
            startRecordingWithDelay(recognizer, liepaContext)
            onRecordingStart()
            //requested RECORD, but recording not started
        } else if (RecordStateRequest.RECORD == recordStateRequest && !liepaContext.isRecordingStarted) {
            Log.i(TAG, "[${recognitionListenerImpl.uttno}][switchRecordingMode] requested RECORD, but recording not started. Starting recognizer: $recordStateRequest")
            liepaContext.isRecordingStarted = true
            startRecordingWithDelay(recognizer, liepaContext)
            onRecordingStart()

            //requested pause RECORDing to process results
        } else if (RecordStateRequest.PAUSE == recordStateRequest && liepaContext.isRecordingStarted) {
            Log.i(TAG, "[${recognitionListenerImpl.uttno}][switchRecordingMode] requested pause RECORDing to process results. Stoping recognizer: $recordStateRequest")
            //it is recording and requested to stop
            liepaContext.isRecordingStarted = false
            recognizer.stop()
            onRecordingStop()
        } else if (RecordStateRequest.STOP == recordStateRequest && liepaContext.isRecordingStarted) {
            Log.i(TAG, "[${recognitionListenerImpl.uttno}][switchRecordingMode] requested stop and now system is ${liepaContext.isRecordingStarted}==recording. Stopping recognizer: $recordStateRequest")
            //it is recording and requested to stop
            liepaContext.isRecordingStarted = false
            recognizer.stop()
            onRecordingStop()
            //if requesting stop, remove waiting indicator and kill thread.
            ui_read_phrase_progress.visibility = View.INVISIBLE
            countDownTimer?.cancel()
            //clean it up
            liepaTransportHelper.prepareForRecognition(liepaContext.audioDir)
        }
        Log.i(TAG, "[${recognitionListenerImpl.uttno}][switchRecordingMode]--- $recordStateRequest")


    }


    /**
     * If access are granted it should  #onRequestPermissionsResult()
     */
    private fun checkPermissionForRecognition(): Boolean {
        Log.i(TAG, "[checkPermissionForRecognition]+++")


        val permissionAudioCheck = ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.RECORD_AUDIO)
        if (permissionAudioCheck != PackageManager.PERMISSION_GRANTED ) {
            ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    PERMISSIONS_REQUEST_RECORD_AUDIO)
            return false
        }
        val modifyAudioSettingCheck = ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.MODIFY_AUDIO_SETTINGS)
        if (modifyAudioSettingCheck != PackageManager.PERMISSION_GRANTED ) {
            ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.MODIFY_AUDIO_SETTINGS),
                    PERMISSIONS_REQUEST_RECORD_AUDIO)
            return false
        }
        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.i(TAG, "[onRequestPermissionsResult]+++" + requestCode)

        if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
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


