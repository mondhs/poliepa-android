package io.github.mondhs.poliepa.helper

class RecognitionModel(val acoustic:String, val model:String, val modelType:Type) {

    enum class Type{
        GRAM, LM, REMOTE, NONE
    }



    override fun toString(): String {
        return "$acoustic/$model/$modelType"
    }

//    fun getDictionaryFileName(): String {
//        return "$dictionary.dict"
//    }
//
//    fun getRecognitionLanguageModelName(): String {
//        when (recognitionLanguageModelType) {
//            LanguageModelType.GRAM -> "return  $dictionary.gram"
//            LanguageModelType.LM -> return "$dictionary.lm"
//            LanguageModelType.REMOTE -> return "" // should be generated in the memory no files needed
//            LanguageModelType.NONE -> return "SHOULD NEVER HAPPEN " // should be generated in the memory no files needed
//        }
//        return "SHOULD NEVER HAPPEN TOO"
//
//    }

}
