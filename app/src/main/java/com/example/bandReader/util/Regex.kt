package com.example.bandReader.util

val regexList = mapOf(
   "chapter" to  Regex("""(.{0,20})第(.{1,10})(章|卷)(.{0,30})"""),
    //类似 Unit 1 I have a pen
    "unit" to Regex("""(.{0,20})(Unit|unit|Lesson|lesson)(.{0,30})"""),
    "isInt" to Regex("""\d+"""),
)