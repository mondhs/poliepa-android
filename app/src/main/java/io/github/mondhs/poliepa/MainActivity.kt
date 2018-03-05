package io.github.mondhs.poliepa

import android.Manifest
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_main.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PorterDuff
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.widget.ArrayAdapter
import edu.cmu.pocketsphinx.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.longToast
import org.jetbrains.anko.uiThread
import java.io.File
import java.io.IOException
import java.lang.Exception


/* Named searches allow to quickly reconfigure the decoder */
private const val LIEPA_CMD = "liepa_commands"

/* Used to handle permission request */
private const val PERMISSIONS_REQUEST_RECORD_AUDIO = 1

class MainActivity : AppCompatActivity(),RecognitionListener {

    private var recognizer: SpeechRecognizer? = null

    private var liepaContext = LiepaRecognitionContext()

    private val liepaHelper = LiepaRecognitionHelper()

    private val resultArray = mutableListOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        caption_text.setText("Ruošiamas atpažintuvas")

        resultList.adapter = ArrayAdapter<String>(this,android.R.layout.simple_list_item_1, this.resultArray)

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
        doRecognition()
    }

    private fun doRecognition() {
        doAsync {
            uiThread {
                with(it) {
                    val assets = Assets(it)
                    val assetDir = assets.syncAssets()
                    it.setupRecognizer(assetDir)
                    it.switchSearch(LIEPA_CMD)
                    val currentWord = liepaHelper.nextWord(liepaContext)
                    caption_text.setText(currentWord)
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

        recognizer?.addListener(this)

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
            it.cancel()
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
                finish()
            }
        }
    }

    private fun switchSearch(searchName: String) {
        recordIndication.setColorFilter(Color.GRAY, PorterDuff.Mode.SRC_ATOP)
        recognizer?.stop()
        recognizer?.startListening(searchName, 10000)
        val currentWord = liepaHelper.nextWord(liepaContext)
        caption_text.setText(currentWord)
    }

    /*************************
     * RecognitionListener
     */
    override fun onResult(hypothesis: Hypothesis?) {
        result_text.setText("")
        hypothesis?.let {

            val correctCmdRatio:Int = liepaHelper.checkRecognition(liepaContext, it.hypstr)

            val outText = "%s = %s (tikimybė: %d; geriausias balas: %d)\n"
                    .format(it.hypstr, liepaContext.previousWord, it.prob, it.bestScore )

            recognitionResultVIew.progress = correctCmdRatio
            resultStat.setText("%d/%d".format(liepaContext.correctCommands, liepaContext.testedCommands))
            this.resultArray.add(0, outText)
            val adapter =  resultList.adapter
            if (adapter is ArrayAdapter<*>) {
                adapter.notifyDataSetChanged()
            }
        }
    }

    override fun onPartialResult(hypothesis: Hypothesis?) {
        hypothesis?.let{
            result_text.setText(it.hypstr)
        }

    }

    override fun onTimeout() {
        switchSearch(LIEPA_CMD)
    }

    override fun onBeginningOfSpeech() {
        recordIndication.setColorFilter(Color.RED, PorterDuff.Mode.SRC_ATOP)
    }

    override fun onEndOfSpeech() {
        switchSearch(LIEPA_CMD)

    }

    override fun onError(error: Exception?) {
        caption_text.setText(error?.message)
    }


}


