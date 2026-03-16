package com.sefira.omer

object OmerHelper {

    private val dayNames = arrayOf(
        "one", "two", "three", "four", "five", "six", "seven",
        "eight", "nine", "ten", "eleven", "twelve", "thirteen",
        "fourteen", "fifteen", "sixteen", "seventeen", "eighteen",
        "nineteen", "twenty", "twenty-one", "twenty-two", "twenty-three",
        "twenty-four", "twenty-five", "twenty-six", "twenty-seven",
        "twenty-eight", "twenty-nine", "thirty", "thirty-one", "thirty-two",
        "thirty-three", "thirty-four", "thirty-five", "thirty-six",
        "thirty-seven", "thirty-eight", "thirty-nine", "forty", "forty-one",
        "forty-two", "forty-three", "forty-four", "forty-five", "forty-six",
        "forty-seven", "forty-eight", "forty-nine"
    )

    private val hebrewDayNames = arrayOf(
        "אחד", "שניים", "שלושה", "ארבעה", "חמישה", "שישה", "שבעה",
        "שמונה", "תשעה", "עשרה", "אחד עשר", "שנים עשר", "שלושה עשר",
        "ארבעה עשר", "חמישה עשר", "שישה עשר", "שבעה עשר", "שמונה עשר",
        "תשעה עשר", "עשרים", "עשרים ואחד", "עשרים ושניים", "עשרים ושלושה",
        "עשרים וארבעה", "עשרים וחמישה", "עשרים ושישה", "עשרים ושבעה",
        "עשרים ושמונה", "עשרים ותשעה", "שלושים", "שלושים ואחד",
        "שלושים ושניים", "שלושים ושלושה", "שלושים וארבעה", "שלושים וחמישה",
        "שלושים ושישה", "שלושים ושבעה", "שלושים ושמונה", "שלושים ותשעה",
        "ארבעים", "ארבעים ואחד", "ארבעים ושניים", "ארבעים ושלושה",
        "ארבעים וארבעה", "ארבעים וחמישה", "ארבעים ושישה", "ארבעים ושבעה",
        "ארבעים ושמונה", "ארבעים ותשעה"
    )

    private val hebrewWeekNames = arrayOf(
        "", "שבוע אחד", "שני שבועות", "שלושה שבועות",
        "ארבעה שבועות", "חמישה שבועות", "שישה שבועות", "שבעה שבועות"
    )

    private val hebrewWeekDayNames = arrayOf(
        "", "ויום אחד", "ושני ימים", "ושלושה ימים",
        "וארבעה ימים", "וחמישה ימים", "וששה ימים"
    )

    /** Returns English counting text for the given day (1–49). */
    fun getEnglishText(day: Int): String {
        if (day < 1 || day > 49) return ""
        val weeks = day / 7
        val rem = day % 7
        val dayWord = if (day == 1) "one day" else "${dayNames[day - 1]} days"

        return if (weeks == 0) {
            "Today is $dayWord of the Omer"
        } else {
            val weekWord = if (weeks == 1) "one week" else "${dayNames[weeks - 1]} weeks"
            val remWord = when {
                rem == 0 -> ""
                rem == 1 -> " and one day"
                else -> " and ${dayNames[rem - 1]} days"
            }
            "Today is $dayWord, which is $weekWord$remWord of the Omer"
        }
    }

    /** Returns traditional Hebrew counting text (היום X ימים לעומר). */
    fun getHebrewText(day: Int): String {
        if (day < 1 || day > 49) return ""
        val weeks = day / 7
        val rem = day % 7

        return if (weeks == 0) {
            if (day == 1) "הַיּוֹם יוֹם אֶחָד לָעֹמֶר"
            else "הַיּוֹם ${hebrewDayNames[day - 1]} יָמִים לָעֹמֶר"
        } else {
            val remPart = if (rem == 0) "" else " ${hebrewWeekDayNames[rem]}"
            if (day == 7) "הַיּוֹם שִׁבְעָה יָמִים שֶׁהֵם שָׁבוּעַ אֶחָד לָעֹמֶר"
            else "הַיּוֹם ${hebrewDayNames[day - 1]} יָמִים שֶׁהֵם ${hebrewWeekNames[weeks]}$remPart לָעֹמֶר"
        }
    }

    /** Day number label (e.g. "Day 3 of 49"). */
    fun getDayLabel(day: Int) = "Day $day of 49"
}
