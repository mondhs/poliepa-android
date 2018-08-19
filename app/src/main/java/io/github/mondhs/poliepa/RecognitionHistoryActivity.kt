package io.github.mondhs.poliepa

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.widget.ArrayAdapter
import edu.cmu.pocketsphinx.Hypothesis
import io.github.mondhs.poliepa.helper.LiepaRecognitionContext
import kotlinx.android.synthetic.main.activity_recognition_history.*

class RecognitionHistoryActivity : AppCompatActivity() {

    private val resultArray = mutableListOf("")


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recognition_history)
        ui_result_list.adapter = ArrayAdapter<String>(this,android.R.layout.simple_list_item_1, this.resultArray)
    }

    fun pushResults(hypothesis: Hypothesis, liepaContext: LiepaRecognitionContext){
        val outText = "%s = %s (tikimybė: %d; geriausias balas: %d)\n"
                .format(hypothesis.hypstr, liepaContext.previousPhraseText, hypothesis.prob, hypothesis.bestScore )
        this.resultArray.add(0, outText)
        val adapter =  ui_result_list.adapter
        if (adapter is ArrayAdapter<*>) {
            adapter.notifyDataSetChanged()
        }
    }
}
