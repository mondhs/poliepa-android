package io.github.mondhs.poliepa

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Color
import android.graphics.PorterDuff
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import edu.cmu.pocketsphinx.Assets
import edu.cmu.pocketsphinx.Hypothesis
import edu.cmu.pocketsphinx.RecognitionListener
import edu.cmu.pocketsphinx.SpeechRecognizer
import edu.cmu.pocketsphinx.SpeechRecognizerSetup
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.longToast
import org.jetbrains.anko.uiThread
import java.io.File
import java.io.IOException
import java.lang.Exception
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.dialog_user_preference.view.*


/* Named searches allow to quickly reconfigure the decoder */
private const val LIEPA_CMD = "liepa_commands"

/* Used to handle permission request */
private const val PERMISSIONS_REQUEST_RECORD_AUDIO = 1


class MainActivity : AppCompatActivity(), RecognitionListener, AnkoLogger {

    private val TAG = "MainActivity"


    private var recognizer: SpeechRecognizer? = null

    private var liepaContext = LiepaRecognitionContext()

    private val liepaHelper = LiepaRecognitionHelper()

    private val liepaTransportHelper = LiepaTransportHelper()

    private val resultArray = mutableListOf("")


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        Log.e(TAG, "[onCreate]+++")
        val aLiepaCtx = initContext()
        liepaContext = aLiepaCtx

        ui_caption_text.setText(getString(R.string.INIT_MESSAGE))

    }

    /**
     *
     */
    private fun initContext(): LiepaRecognitionContext {
        val assets = Assets(this)
        val assetsDir = assets.syncAssets()
        val aLiepaCtx =liepaHelper.initContext( assetsDir,this.getPreferences(Context.MODE_PRIVATE))
        ui_result_list.adapter = ArrayAdapter<String>(this,android.R.layout.simple_list_item_1, this.resultArray)
        return aLiepaCtx
    }


    override fun onResume() {
        super.onResume()
        Log.e(TAG, "[onResume]+++")
        verificationProcedureBeforeLounch()

    }

    private fun verificationProcedureBeforeLounch(){
        Log.e(TAG, "[verificationProcedureBeforeLounch]+++")
        if (!liepaContext.prefAgreeTermsInd){
            showRegistryDialog(liepaContext,this.getPreferences(Context.MODE_PRIVATE))//when is doen repeat this same logic again
            return
        }

        if(checkPermissionForRecognition()){
            Log.e(TAG, "[onSuccess] Checked permission+++")
            doRecognition()
        }//else wait till it will be called #onRequestPermissionsResult() and we will repeat this same logic once again
    }

    private fun doRecognition() {
        //update UI


        val isRecognitionResultVisible:Int = if(liepaContext.prefRecognitionAutomationInd) View.VISIBLE else View.INVISIBLE

        ui_recognition_result_view.visibility =isRecognitionResultVisible
        ui_result_stat.visibility = isRecognitionResultVisible
        ui_result_text.visibility = isRecognitionResultVisible
        ui_result_list.visibility = isRecognitionResultVisible
        ui_result_text_label.visibility = isRecognitionResultVisible



        // Recognizer initialization is a time-consuming and it involves IO,
        // so we execute it in async task
        doAsync {
            uiThread {
                with(it) {
                    Log.i(TAG, "[doRecognition]+++")
                    it.setupRecognizer(liepaContext.audioDir.parentFile)
                    it.switchSearch(LIEPA_CMD)
                    val currentWord = liepaHelper.nextWord(liepaContext)
                    ui_caption_text.setText(currentWord)
                    liepaContext.isRecognitionActive =true
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
                .setRawLogDir(liepaContext.audioDir)
//                .setString("-senlogdir",liepaContext.audioDir.path)
                .recognizer

        recognizer?.addListener(this)
//        recognizer?.decoder?.lattice.

        recognizer?.addGrammarSearch(LIEPA_CMD, liepaContext.liepaCommandFile)
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
        recognizer?.let {
            it.cancel()
            it.shutdown()
            liepaContext.isRecognitionActive = false
            longToast("Liepa nebegirdi")
        }
    }





    private fun switchSearch(searchName: String) {
        Log.i(TAG, "[switchSearch]+++")
        ui_record_indication.setColorFilter(Color.GRAY, PorterDuff.Mode.SRC_ATOP)
        recognizer?.stop()
        recognizer?.startListening(searchName, 10000)
        val currentWord = liepaHelper.nextWord(liepaContext)
        ui_caption_text.setText(currentWord)
    }

    /*************************
     * RecognitionListener
     */
    override fun onResult(hypothesis: Hypothesis?) {
        Log.i(TAG, "[onResult]+++")
        ui_result_text.setText("")
        hypothesis?.let {

            val recognizedCorrectly: Boolean = liepaHelper.isRecognized(liepaContext.previousPhraseText, it.hypstr)
            val correctCmdRatio:Int = liepaHelper.updateRecognitionResult(liepaContext, it.hypstr)

            val outText = "%s = %s (tikimybė: %d; geriausias balas: %d)\n"
                    .format(it.hypstr, liepaContext.previousPhraseText, it.prob, it.bestScore )

            ui_recognition_result_view.progress = correctCmdRatio
            ui_result_stat.setText("%d/%d".format(liepaContext.phrasesCorrectNum, liepaContext.phrasesTestedNum))
            if(liepaContext.lastRecognitionWordsFound) {
                liepaTransportHelper.processAudioFile(liepaContext, hypothesis, recognizedCorrectly);
            }
//            this.recognizer?.decoder?.lattice?.write(liepaContext.audioDir.path)
//            this.recognizer?.decoder?.lattice?.writeHtk(liepaContext.audioDir.path)
            this.resultArray.add(0, outText)
            val adapter =  ui_result_list.adapter
            if (adapter is ArrayAdapter<*>) {
                adapter.notifyDataSetChanged()
            }

        }
    }

    override fun onPartialResult(hypothesis: Hypothesis?) {
        hypothesis?.let{
            Log.i(TAG, "[onPartialResult]+++")
            ui_result_text.setText(it.hypstr)
        }

    }

    override fun onTimeout() {
        Log.i(TAG, "[onTimeout]+++")
        switchSearch(LIEPA_CMD)
    }

    override fun onBeginningOfSpeech() {
        Log.i(TAG, "[onBeginningOfSpeech]+++")
        ui_record_indication.setColorFilter(Color.RED, PorterDuff.Mode.SRC_ATOP)
    }

    override fun onEndOfSpeech() {
        Log.i(TAG, "[onEndOfSpeech]+++")
        var numberWordsWoSil = 0
        recognizer?.decoder?.seg()?.forEach {
            numberWordsWoSil++
            Log.i(TAG, String.format("Word %s[%d-%d]; ascore: %d; lback: %d; lscore:%d; prob: %d ",it.word, it.startFrame, it.endFrame, it.ascore, it.lback, it.lscore, it.prob))
        }
        liepaContext.lastRecognitionWordsFound = numberWordsWoSil > 2//do not count 2 segments of silence in beginning and end.
        switchSearch(LIEPA_CMD)

    }

    override fun onError(error: Exception?) {
        Log.i(TAG, "[onError]+++ " + error?.message, error)
        ui_caption_text.setText(error?.message)
    }

    private fun showRegistryDialog(liepaContext: LiepaRecognitionContext, preferences: SharedPreferences) {
        val context = this
        val builder = AlertDialog.Builder(context)
        builder
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, null)
                .setTitle("Liepa-2 Registracija")


        val res: Resources = resources
        val genenderArray = res.getStringArray(R.array.gender_array)
        val ageGroupArray = res.getStringArray(R.array.age_group_array)

        var genderIdx = genenderArray.indexOf(liepaContext.userGender)
        genderIdx = if (genderIdx == -1) 3 else genderIdx// 3 == I will not say
        var ageGroupIdx = ageGroupArray.indexOf(liepaContext.userAgeGroup)
        ageGroupIdx = if (ageGroupIdx == -1) 11 else ageGroupIdx// 11 == I will not say


        val view = layoutInflater!!.inflate(R.layout.dialog_user_preference, null)

        builder.setView(view);



        view.ui_agree_terms_ind.isChecked = liepaContext.prefAgreeTermsInd
        view.ui_user_name.setText(liepaContext.userName)
        view.ui_user_gender.setSelection(genderIdx)
        view.ui_user_age_group.setSelection(ageGroupIdx)
        view.ui_show_recognition_ind.isChecked = liepaContext.prefRecognitionAutomationInd




        val dialog = builder.show()

        //clicking outside alert do not close this
        dialog.setCancelable(false)

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
                View.OnClickListener() {
                    val userNameStr = view.ui_user_name.text
                    var isValid = true
                    if (userNameStr.isBlank()) {
                        view.ui_user_name.error = getString(R.string.validation_empty)
                        isValid = false
                    }else{
                        if (!view.ui_agree_terms_ind.isChecked) {
                            view.ui_agree_terms_ind.error = getString(R.string.validation_empty)
                            isValid = false
                        }
                    }

                    if (isValid) {
                        liepaContext.prefAgreeTermsInd = view.ui_agree_terms_ind.isChecked
                        liepaContext.userName = view.ui_user_name.text.toString()
                        liepaContext.userGender = view.ui_user_gender.selectedItem.toString()
                        liepaContext.userAgeGroup = view.ui_user_age_group.selectedItem.toString()
                        liepaContext.prefRecognitionAutomationInd = view.ui_show_recognition_ind.isChecked

                        LiepaRecognitionHelper().writeContext(liepaContext, preferences)

                        verificationProcedureBeforeLounch()

                        dialog.dismiss()
                    } else {
                        // do something
                    }
                }
        )
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(
                View.OnClickListener() {
                    longToast("Ačiū! Grįžktite jei norėsite prisidėti!")
                    finish()
                }
        )
    }

    /**
     * If access are granted it should  #onRequestPermissionsResult()
     */
    private fun checkPermissionForRecognition(): Boolean {
        Log.e(TAG, "[checkPermissionForRecognition]+++")

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
        Log.e(TAG, "[onRequestPermissionsResult]+++" + requestCode)

        if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) {
            if (grantResults.isNotEmpty()  && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                //it will call onResume() after this logic is done
                Log.e(TAG, "[onRequestPermissionsResult]+++Granted????" + requestCode)
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
        inflater.inflate(R.menu.toolbar_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle presses on the action bar menu items
        when (item.itemId) {
            R.id.action_preference -> {
                showRegistryDialog(liepaContext,this.getPreferences(Context.MODE_PRIVATE))
                shutdownRecognition()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }


}


