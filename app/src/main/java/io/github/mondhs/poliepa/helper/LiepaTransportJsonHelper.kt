package io.github.mondhs.poliepa.helper

import android.util.Base64
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import java.io.*

class TransportRecognitionResult(
        var requestedText: String ="",
        var recognizedText:String="",
        var isRecognized:Boolean=false,
        var uttno:Int=0,
        var timeCpuRatio:Double=0.0,
        var wordRegions:String = "{}"){
    fun getUttid(): Int {
        return uttno-1
    }
}


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

    fun processAudioFile(ctx: LiepaRecognitionContext, recognitionResult:TransportRecognitionResult) {
        val audioDir = ctx.audioDir

        Log.i(TAG,"processAudioFile+++ internet enabled: ${ctx.internetEnabled}")
        var fileCount = 0
        require(audioDir.exists() && audioDir.isDirectory)

        audioDir.walk().filter { it.name.endsWith("raw") }.sortedByDescending { it.name}.forEach { Log.d(TAG, "File: ${it.name}") }

        //look at all files thats ends with .raw. sort higher number first.
        audioDir.walk().filter { it.name.endsWith("raw") }.sortedByDescending { it.name}.forEach {rawFile ->
            fileCount++
            if(ctx.internetEnabled && ctx.prefSendToCloud && fileCount==1) {
                pushDataToInternet(ctx, recognitionResult, rawFile)
            }else{
                rawFile.delete()
                Log.i( TAG, "[processAudioFile]Deleted: $rawFile; uttid:${recognitionResult.getUttid()}"  )
            }


        }
    }

    private fun pushDataToInternet(ctx: LiepaRecognitionContext, recognitionResult:TransportRecognitionResult, rawFile: File) {

        val audioDir = ctx.audioDir
        Log.i( TAG, "[processAudioFile]Sending: $rawFile (uttid: ${recognitionResult.getUttid()})"  )
        val url = URL("https://kdxqfng2d0.execute-api.eu-north-1.amazonaws.com/dev/record")
        val workingFile = File(audioDir, UUID.randomUUID().toString() + ".audio")
        Log.i(TAG, "Renaming file from ${rawFile.name} to ${workingFile.name} (uttid: ${recognitionResult.getUttid()})")
        rawFile.renameTo(workingFile)
        Thread {
            val requestJson =JSONObject()
            val metaDataJson =JSONObject()
            requestJson.put("metadata",metaDataJson)

            metaDataJson.put(LiepaRecognitionContext.KEY_RECOGN_REQUESTED_TEXT, recognitionResult.requestedText)
            metaDataJson.put(LiepaRecognitionContext.KEY_RECOGN_RECOGNIZED_TEXT, recognitionResult.recognizedText)
            metaDataJson.put(LiepaRecognitionContext.KEY_RECOGN_IS_RECOGNIZED, recognitionResult.isRecognized.toString())
            metaDataJson.put(LiepaRecognitionContext.KEY_PHONE_ID, ctx.phoneId)
            metaDataJson.put(LiepaRecognitionContext.KEY_PHONE_MODEL, ctx.phoneModel)
            metaDataJson.put(LiepaRecognitionContext.KEY_USER_NAME, ctx.userName)
            metaDataJson.put(LiepaRecognitionContext.KEY_USER_GENDER, ctx.userGender )
            metaDataJson.put(LiepaRecognitionContext.KEY_USER_AGE_GROUP, ctx.userAgeGroup)
            metaDataJson.put(LiepaRecognitionContext.KEY_RECOGN_ACOUSTIC, ctx.recognitionAcoustic)
            metaDataJson.put(LiepaRecognitionContext.KEY_RECOGN_MODEL, ctx.recognitionModel)
            metaDataJson.put(LiepaRecognitionContext.KEY_RECOGN_MODEL_TYPE, ctx.recognitionModelType)
            metaDataJson.put(LiepaRecognitionContext.KEY_RECOGN_WORD_REGIONS, recognitionResult.wordRegions)
            metaDataJson.put(LiepaRecognitionContext.KEY_RECOGN_TIME_CPU_RATIO, recognitionResult.timeCpuRatio)
            metaDataJson.put(LiepaRecognitionContext.KEY_RECOGN_UTTID, recognitionResult.getUttid())


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
                    Log.i(TAG, "[processAudioFile] (uttid: ${recognitionResult.getUttid()}) uploaded successfully File($workingFile)  $response")
                } else {
                    Log.i(TAG, "[processAudioFile] (uttid: ${recognitionResult.getUttid()}) uploaded successfully File($workingFile)  $status: ${conn.responseMessage}")
                }

            } catch (e: IOException) {
                Log.e(TAG, "Upload file crashed: $e")
            }


            Log.i( TAG, "[processAudioFile]Uploaded and deleted: $workingFile uttid:${recognitionResult.getUttid()}"  )
            workingFile.delete()
        }.start()
    }

}
