package io.github.mondhs.poliepa.helper

class LiepaDictionaryGenerator{


    companion object {
        const val WORD_REPLACEMENT =
                """facebookas=feisbukas
unesco=junesko"""

//        const val GRAPHEME_GROUP_REPLACEMENT =
//                """^ie=jie
//sb=zb"""


        val wordReplaceMap = mutableMapOf<String, String>()
        val graphemeGroupReplacementMap = linkedMapOf<String, String>(
                "^ie" to "J. I E",
                "b$" to "P",
                "d$" to "T",
                "g$" to "K",//daug->dauk
                "z$" to "S",
                "ž$" to "S2",
                "dz$" to "C",
                "dž$" to "C2",
                "h$" to "DZ H",
                "iu" to "IU", //Svarbu jei be minkštumo
                "ių" to "IU_", //Svarbu jei be minkštumo
                "io" to "IO_", //Svarbu jei be minkštumo
                //"ui" to "UI",
                //"uo" to "UO",
                "ia" to "E",
                "ią" to "E_",
                //"tst" to "T S T", //atstatyk# nebėra versijoje z1.3
                //"ts" to "C", //atsakymą,atsiųsk# nebėra versijoje z1.3
                "tsi" to "C I", //atsisakyk
                "iau" to "E U",
                "ja" to "J. E", //jau, japonas
                "ją" to "J. E_", //naują


//Dantiniai priebalsiai {S, Z, C, DZ} prieš alveolinius {S2, Z2, C2, DZ2} keičiami atitinkamai į alveolinius {S2, Z2, C2, DZ2} (slenksčiai -> S L E N K S2 C2 E I).
                "sž" to "S2 Z2", //?
                "sč" to "S2 C2", //kunigaikštysčiu
                "zdž" to "Z2 DZ2", //vabzdžiai

// duslieji prieš skardžiuosius
                "pb" to "B B",
                "pg" to "B G", //apgadintas
                "pz" to "B Z",
                "pž" to "B Z2",
                "pdz" to "B DZ",
                "pdž" to "B DZ2",
                "pd" to "B D",
                "ph" to "B H",
                "tb" to "D B", //atbaidyti
                "tg" to "D G", //atgabenti
                "tz" to "D Z",
                "tž" to "D Z2", //atžvilgiu
                "tdz" to "D DZ",
                "tdž" to "D DZ2",
                "td" to "D D",
                "th" to "D H",
                "kb" to "G B",
                "kdz" to "G DZ",
                "kdž" to "G DZ2",
                "kd" to "G D", //atlikdavo
                "kg" to "G G",
                "kz" to "G Z",
                "kž" to "G Z2",

                "kh" to "G H",
                "sb" to "Z B", //feisbukas

                "sg" to "Z G",
                "sz" to "Z Z",
                "sž" to "Z Z2",
                "sdz" to "Z DZ",
                "sdž" to "Z DZ2",
                "sd" to "Z D", //kasdami
                "sh" to "Z H",
                "šb" to "Z2 B", //išbandyti
                "šg" to "Z2 G", //išgaubti
                "šz" to "Z2 Z",
                "šž" to "Z2 Z2",
                "šdz" to "Z2 DZ",
                "šdž" to "Z2 DZ2",
                "šd" to "Z2 D", //neišdildoma
                "šh" to "Z2 H",
                "cb" to "DZ B",
                "cg" to "DZ G",
                "cz" to "DZ Z",
                "cž" to "DZ Z2",
                "cdz" to "DZ DZ",
                "cdž" to "DZ DZ2",
                "cd" to "DZ D",
                "ch" to "DZ H",
                "čb" to "DZ2 B",
                "čg" to "DZ2 G",
                "čz" to "DZ2 Z",
                "čž" to "DZ2 Z2",
                "čdz" to "DZ2 DZ",
                "čdž" to "DZ2 DZ2",
                "čd" to "DZ2 D",
                "čh" to "DZ2 H",
                "chb" to "H B",
                "chg" to "H G",
                "chz" to "H Z",
                "chž" to "H Z2",
                "chdz" to "H DZ",
                "chdž" to "H DZ2",
                "chd" to "H D",
                "chh" to "H H",

                //skardieji prieš dusliuosius
                "bp" to "P P",
                "bt" to "P T",
                "bk" to "P K",
                "bs" to "P S",
                "bš" to "P S2",
                "bc" to "P C",
                "bč" to "P C2",
                "bch" to "P CH",

                "dp" to "T P",
                "dt" to "T T",
                "dk" to "T K",
                "ds" to "T S",
                "dš" to "T S2",
                "dch" to "T CH",
                "dc" to "T C",
                "dč" to "T C2",


                "gp" to "K P",
                "gt" to "K T", //vašingtonas, jungtinių
                "gk" to "K K", //angkoras -> A N K K O_ R A S
                "gs" to "K S",
                "gš" to "K S2",
                "gch" to "K CH",
                "gc" to "K C",
                "gč" to "K C2",

                "zp" to "S P",
                "zt" to "S T", //megztinis
                "zk" to "S K",
                "zs" to "S S",
                "zš" to "S S2",
                "zch" to "S CH",
                "zc" to "S C",
                "zč" to "S C2",

                "žp" to "S2 P",
                "žt" to "S2 T",
                "žk" to "S2 K", //grįžk
                "žs" to "S2 S",
                "žš" to "S2 S2",
                "žch" to "S2 CH",
                "žc" to "S2 C",
                "žč" to "S2 C2",

                "dzp" to "C P",
                "dzt" to "C T",
                "dzk" to "C K",
                "dzs" to "C S",
                "dzš" to "C S2",
                "dzch" to "C CH",
                "dzc" to "C C",
                "dzč" to "C C2",

                "džp" to "C2 P",
                "džt" to "C2 T",
                "džk" to "C2 K",
                "džs" to "C2 S",
                "džš" to "C2 S2",
                "džch" to "C2 CH",
                "džc" to "C2 C",
                "džč" to "C2 C2",

                "hp" to "CH P",
                "ht" to "CH T",
                "hk" to "CH K",
                "hs" to "CH S",
                "hš" to "CH S2",
                "hch" to "CH CH",
                "hc" to "CH C",
                "hč" to "CH C2",


                "ch" to "CH",
                "dž" to "DZ2",
                "dz" to "DZ",
                "a" to "A",
                "ą" to "A_",
                "b" to "B",
                "c" to "C",
                "č" to "C2",
                "d" to "D",
                "e" to "E",
                "ę" to "E_",
                "ė" to "E3_",
                "f" to "F",
                "g" to "G",
                "h" to "H",
                "i" to "I",
                "į" to "I_",
                "y" to "I_",
                "j" to "J.",
                "k" to "K",
                "l" to "L",
                "m" to "M",
                "n" to "N",
                "o" to "O_",
                "p" to "P",
                "r" to "R",
                "s" to "S",
                "š" to "S2",
                "t" to "T",
                "u" to "U",
                "ų" to "U_",
                "ū" to "U_",
                "v" to "V",
                "w" to "V",
                "z" to "Z",
                "ž" to "Z2"
        )
    }
//    val graphemeReplacementMap = mutableMapOf<Char,String>()

    constructor(){
        for (pairStr in WORD_REPLACEMENT.split("\n".toRegex())){
            var pairArr = pairStr.split("=")
            wordReplaceMap.put(pairArr[0],pairArr[1])
        }


    }

    public fun transcribeDictionary(words:Set<String>):Map<String,String>{
        var dictionary = mutableListOf<Pair<String,String>>()
        for (word in words){
            dictionary.add(Pair(word, transcribeWord(word)))
        }
//        var result = hashMapOf("labas" to "L A B A S", "rytas" to "R I_ T A S")
        return dictionary.map { it.first to it.second }.sortedBy { it.first }.toMap()
    }

    public fun transcribeDictionary(inputWords:String):Map<String,String>{
        var dictionary = mutableListOf<Pair<String,String>>()
        var words = inputWords.toLowerCase()
        for (word in words.split(" ".toRegex())){
            dictionary.add(Pair(word, transcribeWord(word)))
        }
//        var result = hashMapOf("labas" to "L A B A S", "rytas" to "R I_ T A S")
        return dictionary.map { it.first to it.second }.sortedBy { it.first }.toMap()
    }

    fun dictionaryToString(dictionary:Map<String,String>):String{
        val result = dictionary.entries.joinToString("\n") { "%s\t%s".format(it.key, it.value)}
        return result + "\n"
    }

    private fun transcribeWord(word:String): String{
        //1.Word replacement: correct spelling
        var spelledWord = wordReplaceMap.get(word) ?: word
        //2 group of grapheme fixing spelling
        graphemeGroupReplacementMap.forEach { t, u -> spelledWord  = spelledWord .replace(t.toRegex(), u + " ") }
        return spelledWord.trim()
    }
}