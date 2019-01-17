package io.github.mondhs.poliepa

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import io.github.mondhs.poliepa.helper.LiepaContextHelper
import kotlinx.android.synthetic.main.user_preference_activity.*
import org.jetbrains.anko.sdk27.coroutines.onCheckedChange
import android.widget.ArrayAdapter
import io.github.mondhs.poliepa.helper.LiepaRecognitionContext
import io.github.mondhs.poliepa.helper.RecognitionModel


class UserPreferenceActivity : AppCompatActivity() {

    private val liepaHelper = LiepaContextHelper()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.user_preference_activity)


        // Get the Intent that started this activity and extract the string
//        val liepaContext = intent.getSerializableExtra(LiepaRecognitionContext.LIEPA_CONTEXT_KEY) as LiepaRecognitionContext


        val liepaContext = liepaHelper.loadContext()

        val res: Resources = resources
        val genderArray = res.getStringArray(R.array.gender_array)
        val ageGroupArray = res.getStringArray(R.array.age_group_array)

        var genderIdx = genderArray.indexOf(liepaContext.userGender)
        genderIdx = if (genderIdx == -1) 3 else genderIdx// 3 == I will not say
        var ageGroupIdx = ageGroupArray.indexOf(liepaContext.userAgeGroup)
        ageGroupIdx = if (ageGroupIdx == -1) 11 else ageGroupIdx// 11 == I will not say

        ui_agree_terms_ind.isChecked = liepaContext.prefAgreeTermsInd
        ui_send_to_cloud_ind.isChecked = liepaContext.prefSendToCloud
        ui_user_name.setText(liepaContext.userName)
        ui_user_gender.setSelection(genderIdx)
        ui_user_age_group.setSelection(ageGroupIdx)
        ui_show_recognition_ind.isChecked = liepaContext.prefRecognitionAutomationInd
        ui_requestPhaseDelayInSecSeekBar.progress = liepaContext.requestPhaseDelayInSec



        ui_show_recognition_ind.onCheckedChange { _, isChecked ->
            run {
                ui_send_to_cloud_ind.isChecked=if (!isChecked) true else ui_send_to_cloud_ind.isChecked
                ui_automaticSettingsSection.visibility = if (isChecked) View.VISIBLE else View.INVISIBLE
            }
        }
        ui_automaticSettingsSection.visibility = if (ui_show_recognition_ind.isChecked) View.VISIBLE else View.INVISIBLE



        val recognitionModelList = liepaHelper.retrieveAvailableRecognitionModes(liepaContext)
        val availableRecognitionModelsAdapter = ArrayAdapter(this, R.layout.recognition_model_layout, recognitionModelList)
        ui_recognition_models.adapter = availableRecognitionModelsAdapter
        val selectedModelIndex = recognitionModelList.indexOfFirst {
            it.acoustic == liepaContext.recognitionAcoustic &&
                    it.model == liepaContext.recognitionModel &&
                    it.modelType == RecognitionModel.Type.valueOf(liepaContext.recognitionModelType)
        }
        ui_recognition_models.setSelection(if(selectedModelIndex>-1)selectedModelIndex else 0)

    }

    override fun onResume() {
        super.onResume()
    }

    override fun onBackPressed() {
//        super.onBackPressed();
        navigateToNextScreen()
    }

    private fun save() {

        val liepaContext = liepaHelper.loadContext()
        val preferences = this.getSharedPreferences(LiepaRecognitionContext.LIEPA_CONTEXT_KEY,Context.MODE_PRIVATE)

        val userNameStr = ui_user_name.text
        var isValid = true
        if (userNameStr.isBlank()) {
            ui_user_name.error = getString(R.string.validation_empty)
            isValid = false
        } else {
            if (!ui_agree_terms_ind.isChecked) {
                ui_agree_terms_ind.error = getString(R.string.validation_empty)
                isValid = false
            }
        }

        if (isValid) {
            liepaContext.prefAgreeTermsInd = ui_agree_terms_ind.isChecked
            liepaContext.userName = ui_user_name.text.toString()
            liepaContext.userGender = ui_user_gender.selectedItem.toString()
            liepaContext.userAgeGroup = ui_user_age_group.selectedItem.toString()
            liepaContext.prefRecognitionAutomationInd = ui_show_recognition_ind.isChecked
            liepaContext.prefSendToCloud = ui_send_to_cloud_ind.isChecked
            liepaContext.requestPhaseDelayInSec = ui_requestPhaseDelayInSecSeekBar.progress

            //Recognition model selection
            val recognitionModel = ui_recognition_models.selectedItem as RecognitionModel
            liepaContext.recognitionAcoustic = recognitionModel.acoustic
            liepaContext.recognitionModel= recognitionModel.model
            liepaContext.recognitionModelType=recognitionModel.modelType.name
            //reset recognition stat
            liepaContext.phrasesCorrectNum = 0
            liepaContext.phrasesTestedNum = 0

            LiepaContextHelper().writeContext(liepaContext, preferences)

            navigateToNextScreen()


        }
    }

    private fun navigateToNextScreen(){
        finish()
        val liepaContext = liepaHelper.loadContext()
        if(liepaContext.prefRecognitionAutomationInd){
            val intent = Intent(this, RecognitionAutomaticActivity::class.java)
            startActivity(intent)
        }else{
            val intent = Intent(this, RecognitionManualActivity::class.java)
            startActivity(intent)
        }
    }

    /// Menu logic
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu to use in the action bar
        val inflater = menuInflater
        inflater.inflate(R.menu.user_preference_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle presses on the action bar menu items
        when (item.itemId) {
            R.id.action_save -> {
                save()
                return true
            }
            R.id.home -> {

                navigateToNextScreen()
                return true
            }

        }
        return super.onOptionsItemSelected(item)
    }


}