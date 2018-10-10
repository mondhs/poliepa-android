package io.github.mondhs.poliepa.pocketsphinx

import android.content.Context
import edu.cmu.pocketsphinx.Assets
import io.github.mondhs.poliepa.helper.LiepaDictionaryGenerator
import io.github.mondhs.poliepa.helper.LiepaGrammarGenerator
import java.io.File

class RemoteAssets(context: Context) : Assets(context) {

    override fun syncAssets(): File {
        val asset = super.syncAssets()
        return asset
    }



    override fun getItems(): MutableMap<String, String> {
        val items = super.getItems()

        return items
    }

    override fun getExternalItems(): MutableMap<String, String> {
        val externalItems = super.getExternalItems()
        return externalItems
    }
}