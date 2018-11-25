package io.github.mondhs.poliepa.helper

import android.util.Base64
import android.util.Log
import edu.cmu.pocketsphinx.Hypothesis
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import java.io.*


class LiepaTransportJsonHelper {

    private val TAG= LiepaTransportJsonHelper::class.simpleName

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
        val recognizedText:String = hypothesis?.hypstr?.toString() ?: ""

        Log.i(TAG,"processAudioFile+++ internet enabled: ${context.internetEnabled}")
        var fileCount = 0
        require(audioDir.exists() && audioDir.isDirectory)
        //look at all files thats ends with .raw. sort higher number first. remove first as it is current wich system is working with
        audioDir.walk().filter { it.name.endsWith("raw") }.sortedByDescending { it.name}.drop(1).forEach {
            fileCount++
            if(context.internetEnabled && context.prefSendToCloud && fileCount==1) {
                Log.i( TAG, "[processAudioFile]Sending: $it"  )
                val url = URL("https://0247888z1g.execute-api.eu-central-1.amazonaws.com/dev/record")
                val workingFile = File(audioDir, UUID.randomUUID().toString() + ".audio")
                Log.i(TAG, "Renaming file from ${it.name} to ${workingFile.name}")
                it.renameTo(workingFile)
                Thread {
                    val requestJson =JSONObject()
                    val metaDataJson =JSONObject()
                    requestJson.put("metadata",metaDataJson)

                    metaDataJson.put(LiepaRecognitionContext.KEY_RECOGN_REQUESTED_TEXT, requestedText)
                    metaDataJson.put(LiepaRecognitionContext.KEY_RECOGN_RECOGNIZED_TEXT, recognizedText)
                    metaDataJson.put(LiepaRecognitionContext.KEY_RECOGN_IS_RECOGNIZED, isRecognized.toString())
                    metaDataJson.put(LiepaRecognitionContext.KEY_PHONE_ID, context.phoneId)
                    metaDataJson.put(LiepaRecognitionContext.KEY_PHONE_MODEL, context.phoneModel)
                    metaDataJson.put(LiepaRecognitionContext.KEY_USER_NAME, context.userName)
                    metaDataJson.put(LiepaRecognitionContext.KEY_USER_GENDER, context.userGender )
                    metaDataJson.put(LiepaRecognitionContext.KEY_USER_AGE_GROUP, context.userAgeGroup)

                    try {
                        val bytes = workingFile.readBytes()
                        requestJson.put("audio",Base64.encodeToString(bytes,Base64.DEFAULT))
                        val conn = url.openConnection() as HttpURLConnection
                        conn.requestMethod = "PUT"
                        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        val os = conn.outputStream
                        val writer = BufferedWriter(OutputStreamWriter(os, "UTF-8"))
                        writer.write(requestJson.toString())
                        writer.flush()
                        writer.close()
                        os.close()
                        conn.connect()
                        // checks server's status code first
                        val status = conn.responseCode
                        if (status == HttpURLConnection.HTTP_OK) {
                            val reader = BufferedReader(InputStreamReader(conn
                                    .inputStream))
                            val response = reader.use(BufferedReader::readText)
                            conn.disconnect()
                            Log.i(TAG, "[processAudioFile] uploaded successfully File($workingFile)  $response")
                        } else {
                            Log.i(TAG, "[processAudioFile] uploaded successfully File($workingFile)  $status: ${conn.responseMessage}")
                        }

                    } catch (e: IOException) {
                        Log.e(TAG, "Upload file crashed: $e")
                    }


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
