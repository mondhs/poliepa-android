package io.github.mondhs.poliepa

import android.Manifest
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_main.*
import android.content.pm.PackageManager
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import edu.cmu.pocketsphinx.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.longToast
import org.jetbrains.anko.uiThread
import java.io.File
import java.io.IOException
import java.lang.Exception



class MainActivity : AppCompatActivity(),RecognitionListener {

    /* Named searches allow to quickly reconfigure the decoder */
    private val LIEPA_CMD = "liepa_commands"

    /* Used to handle permission request */
    private val PERMISSIONS_REQUEST_RECORD_AUDIO = 1

    private var recognizer: SpeechRecognizer? = null

    private var liepaContext = LiepaRecognitionContext()

    private val liepaHelper = LiepaRecognitionHelper()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        caption_text.text = "Ruošiamas atpažintuvas"
        val permissionCheck = ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.RECORD_AUDIO)
        if (permissionCheck != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), PERMISSIONS_REQUEST_RECORD_AUDIO)
            return
        }
    }

    override fun onResume() {
        super.onResume()
        // Recognizer initialization is a time-consuming and it involves IO,
        // so we execute it in async task
        doRecognition();
    }

    private fun doRecognition() {
        doAsync {
            uiThread {
                with(it) {
                    val assets = Assets(it)
                    val assetDir = assets.syncAssets()
                    it.setupRecognizer(assetDir);
                    it.switchSearch(LIEPA_CMD);
                    var currentWord = liepaHelper.nextWord(liepaContext)
                    caption_text.text = currentWord
                    liepaContext.isRecogntionActive =true
                    longToast("Liepa klauso")
                }
            }

        }
    }

    @Throws(IOException::class)
    private fun setupRecognizer(assetsDir: File) {
        this.recognizer = SpeechRecognizerSetup.defaultSetup()
                .setAcousticModel(File(assetsDir, "lt-lt-ptm"))
                .setDictionary(File(assetsDir, "liepa-lt-lt.dict"))
                .recognizer

        recognizer?.addListener(this);

        // Create grammar-based search for digit recognition
        val liepaCommandFile = File(assetsDir, "liepa_commands.gram")
        recognizer?.addGrammarSearch(LIEPA_CMD, liepaCommandFile)
        //initialize recognition result tracking context
        liepaContext = liepaHelper.initConxet(liepaCommandFile.readText())
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
        recognizer?.let {
            it.cancel();
            it.shutdown()
            liepaContext.isRecogntionActive = false
            longToast("Liepa nebegirdi")
        }
    }



    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) {
            if (grantResults.isNotEmpty()  && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Recognizer initialization is a time-consuming and it involves IO,
                // so we execute it in async task
                doRecognition()
            } else {
                finish();
            }
        }
    }

    private fun switchSearch(searchName: String) {
        recognizer?.stop()
        recognizer?.startListening(searchName, 10000)
        var currentWord = liepaHelper.nextWord(liepaContext)
        caption_text.text = currentWord
    }

    /*************************
     * RecognitionListener
     */
    override fun onResult(hypothesis: Hypothesis?) {
        result_text.text = ""
        hypothesis?.let {

            val correctCmdRatio:Int = liepaHelper.checkRecognition(liepaContext, it.hypstr)

            val outText = "%s - %s \n\t tikimybė: %d \n\t geriausias balas: %d\n Bendras rezultastas %d(%d/%d)"
                    .format(it.hypstr, liepaContext.previousWord, it.prob, it.bestScore, correctCmdRatio, liepaContext.correctCommands, liepaContext.testedCommands )

            recognitionResultVIew.progress = correctCmdRatio
            longToast(outText)
        }
    }

    override fun onPartialResult(hypothesis: Hypothesis?) {
        hypothesis?.let{
            result_text.text = "%s".format(it.hypstr)
        }

    }

    override fun onTimeout() {
        switchSearch(LIEPA_CMD);
    }

    override fun onBeginningOfSpeech() {
    }

    override fun onEndOfSpeech() {
        switchSearch(LIEPA_CMD);
    }

    override fun onError(error: Exception?) {
        caption_text.text = error?.message
    }


}
