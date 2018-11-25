package io.github.mondhs.poliepa.helper

import android.util.Log
import edu.cmu.pocketsphinx.Hypothesis
import java.io.File
import java.net.URL
import java.util.*

class LiepaTransportMultipartHelper {

    val TAG= LiepaTransportMultipartHelper::class.simpleName

    fun prepareForRecognition(audioDir: File){

        if (!(audioDir.exists() && audioDir.isDirectory)) {
            Log.i(TAG, "Dir does not exists: " + audioDir.absolutePath)
            return
        }

        audioDir.walk().filter { it.name.endsWith("raw") }.forEach {
            Log.i( TAG, "[prepareForRecognition]Deleted: $it"  )
            it.delete()
        }

    }

    fun processAudioFile(context: LiepaRecognitionContext, hypothesis: Hypothesis?, isRecognized:Boolean) {
        val audioDir = context.audioDir
        val requestedText  = context.previousPhraseText
        val recognizedText:String = hypothesis?.hypstr?.toString() ?: "";

        Log.i(TAG,"processAudioFile+++ internet enabled: ${context.internetEnabled}")
        var fileCount = 0
        require(audioDir.exists() && audioDir.isDirectory)
        //look at all files thats ends with .raw. sort higher number first. remove first as it is current wich system is working with
        audioDir.walk().filter { it.name.endsWith("raw") }.sortedByDescending { it.name}.drop(1).forEach {
            fileCount++
            if(context.internetEnabled && context.prefSendToCloud && fileCount==1) {
                Log.i( TAG, "[processAudioFile]Sending: $it"  )
                val url = URL("https://lieparinktuvas-1530815329656.appspot.com/upload")
                val workingFile = File(audioDir, UUID.randomUUID().toString() + ".audio")
                Log.i(TAG, "Renaming file from ${it.name} to ${workingFile.name}")
                it.renameTo(workingFile)
                Thread {
                    val multipart = Multipart(url)
                    multipart.addFormField(LiepaRecognitionContext.KEY_RECOGN_REQUESTED_TEXT, requestedText)
                    multipart.addFormField(LiepaRecognitionContext.KEY_RECOGN_RECOGNIZED_TEXT, recognizedText)
                    multipart.addFormField(LiepaRecognitionContext.KEY_RECOGN_IS_RECOGNIZED, isRecognized.toString())
                    multipart.addFormField(LiepaRecognitionContext.KEY_PHONE_ID, context.phoneId)
                    multipart.addFormField(LiepaRecognitionContext.KEY_PHONE_MODEL, context.phoneModel.toString())
                    multipart.addFormField(LiepaRecognitionContext.KEY_USER_NAME, context.userName)
                    multipart.addFormField(LiepaRecognitionContext.KEY_USER_GENDER, context.userGender )
                    multipart.addFormField(LiepaRecognitionContext.KEY_USER_AGE_GROUP, context.userAgeGroup.toString())

                    multipart.addFileFlacPart("file", workingFile, workingFile.name, "audio/raw")
                    multipart.upload(
                            onSuccess = { w ->
                                Log.i(TAG, "[processAudioFile] uploaded successfully File($workingFile)  $w")
                            },
                            onFailure = { e ->
                                Log.i(TAG, "[processAudioFile]upload failed File($workingFile)! $e")
                            })
                    Log.i( TAG, "[processAudioFile]Uploaded and deleted: $workingFile"  )
                    workingFile.delete()
                }.start()
            }else{
                it.delete()
                Log.i( TAG, "[processAudioFile]Deleted: $it"  )
            }


        }
    }

}
