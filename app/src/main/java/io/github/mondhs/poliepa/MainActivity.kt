package io.github.mondhs.poliepa

import android.content.Context
import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.widget.ArrayAdapter
import edu.cmu.pocketsphinx.Assets
import io.github.mondhs.poliepa.helper.LiepaContextHelper
import io.github.mondhs.poliepa.helper.LiepaRecognitionContext
import io.github.mondhs.poliepa.helper.RecognitionModel
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.user_preference_activity.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.sdk25.coroutines.onClick

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val assets = Assets(this)
        val assetsDir = assets.syncAssets()
        val liepaHelper = LiepaContextHelper()
        val sharedPrefs = this.getSharedPreferences(LiepaRecognitionContext.LIEPA_CONTEXT_KEY,Context.MODE_PRIVATE)
        val aLiepaCtx =liepaHelper.initContext( assetsDir,sharedPrefs)

        val recognitionModelList = liepaHelper.retrieveAvailableRecognitionModes(aLiepaCtx)
        val availableRecognitionModelsAdapter = ArrayAdapter(this, R.layout.recognition_model_layout, recognitionModelList)
        ui_main_recognition_models.adapter = availableRecognitionModelsAdapter
        val selectedModelIndex = recognitionModelList.indexOfFirst {
            it.acoustic == aLiepaCtx.recognitionAcoustic &&
                    it.model == aLiepaCtx.recognitionModel &&
                    it.modelType == RecognitionModel.Type.valueOf(aLiepaCtx.recognitionModelType)
        }
        ui_main_recognition_models.setSelection(if(selectedModelIndex>-1)selectedModelIndex else 0)

        if(!aLiepaCtx.prefAgreeTermsInd){
            Handler().postDelayed({
                navigateToNextScreen(aLiepaCtx)
            }, 300)
        }else{
            continueBtn.onClick {
                //Recognition model selection
                val recognitionModel = ui_main_recognition_models.selectedItem as RecognitionModel
                aLiepaCtx.recognitionAcoustic = recognitionModel.acoustic
                aLiepaCtx.recognitionModel= recognitionModel.model
                aLiepaCtx.recognitionModelType=recognitionModel.modelType.name

                aLiepaCtx.phrasesCorrectNum = 0
                aLiepaCtx.phrasesTestedNum = 0
                LiepaContextHelper().writeContext(aLiepaCtx, sharedPrefs)
                navigateToNextScreen(aLiepaCtx)
            }
        }
        doAsync {
//            liepaHelper.updateCtxWithRemotePhrase(liepaContext, liepaContext.assetsDir)
        }


    }

    private fun navigateToNextScreen(liepaContext:LiepaRecognitionContext){
        finish()
        if(!liepaContext.prefAgreeTermsInd){
            val intent = Intent(this, UserPreferenceActivity::class.java)
            startActivity(intent)
        }else if(!liepaContext.prefRecognitionAutomationInd){
            val intent = Intent(this, RecognitionManualActivity::class.java)
            startActivity(intent)
        }else{
            val intent = Intent(this, RecognitionAutomaticActivity::class.java)
            startActivity(intent)
        }
    }
}
