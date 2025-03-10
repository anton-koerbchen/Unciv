//  Taken from https://github.com/TomGrill/gdx-testing
package com.unciv.testing

import com.badlogic.gdx.Gdx
import com.unciv.UncivGame
import com.unciv.models.metadata.GameSettings
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.translations.*
import com.unciv.utils.debug
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.OutputStream
import java.io.PrintStream

@RunWith(GdxTestRunner::class)
class TranslationTests {
    private var translations = Translations()
    private var ruleset = Ruleset()

    @Before
    fun loadTranslations() {
        // Since the ruleset and translation loader have their own output,
        // We 'disable' the output stream for their outputs, and only enable it for the test itself.
        val outputChannel = System.out
        System.setOut(PrintStream(object : OutputStream() {
            override fun write(b: Int) {}
        }))
        translations.readAllLanguagesTranslation()
        RulesetCache.loadRulesets()
        ruleset = RulesetCache.getVanillaRuleset()
        System.setOut(outputChannel)
    }

    @Test
    fun translationsLoad() {
        Assert.assertTrue("This test will only pass there are translations",
                translations.size > 0)
    }


    // This test is incorrectly defined: it should read from the template.properties file and not fro the final translation files.
//    @Test
//    fun allUnitActionsHaveTranslation() {
//        val actions: MutableSet<String> = HashSet()
//        for (action in UnitActionType.values()) {
//            actions.add(
//                when(action) {
//                    UnitActionType.Upgrade -> "Upgrade to [unitType] ([goldCost] gold)"
//                    UnitActionType.Create -> "Create [improvement]"
//                    UnitActionType.SpreadReligion -> "Spread [religionName]"
//                    else -> action.value
//                }
//            )
//        }
//        val allUnitActionsHaveTranslation = allStringAreTranslated(actions)
//        Assert.assertTrue("This test will only pass when there is a translation for all unit actions",
//                allUnitActionsHaveTranslation)
//    }
//
//    private fun allStringAreTranslated(strings: Set<String>): Boolean {
//        var allStringsHaveTranslation = true
//        for (entry in strings) {
//            val key = if (entry.contains('[')) entry.replace(squareBraceRegex, "[]") else entry
//            if (!translations.containsKey(key)) {
//                allStringsHaveTranslation = false
//                println("$entry not translated!")
//            }
//        }
//        return allStringsHaveTranslation
//    }

    @Test
    fun translationsFromJSONsCanBeGenerated() {
        // it triggers generation of the translation's strings
        val stringsSize = TranslationFileWriter.getGeneratedStringsSize()

        Assert.assertTrue("This test will only pass when all .json files are serializable",
                stringsSize > 0)
    }

    /** For every translatable string find its placeholders and check if all translations have them */
    @Test
    fun allTranslationsHaveCorrectPlaceholders() {
        var allTranslationsHaveCorrectPlaceholders = true
        val languages = translations.getLanguages()
        for (key in translations.keys) {
            val translationEntry = translations[key]!!.entry
            val placeholders = squareBraceRegex.findAll(translationEntry)
                    .map { it.value }.toList()
            for (language in languages) {
                val output = translations.getText(key, language)
                if (output == key) continue // the language doesn't have the required translation, so we got back the key
                for (placeholder in placeholders) {
                    if (!output.contains(placeholder)) {
                        allTranslationsHaveCorrectPlaceholders = false
                        debug("Placeholder `%s` not found in `%s` for entry `%s`", placeholder, language, translationEntry)
                    }
                }
            }
        }
        Assert.assertTrue(
                "This test will only pass when all translations' placeholders match those of the key",
                allTranslationsHaveCorrectPlaceholders
        )
    }

    @Test
    fun allPlaceholderKeysMatchEntry() {
        var allPlaceholderKeysMatchEntry = true
        for (key in translations.keys) {
            if (!key.contains('[') || key.contains('<')) continue
            val translationEntry = translations[key]!!.entry
            val keyFromEntry = translationEntry.replace(squareBraceRegex, "[]")
            if (key != keyFromEntry) {
                allPlaceholderKeysMatchEntry = false
                debug("Entry %s found under bad key %s", translationEntry, key)
                break
            }
        }
        Assert.assertTrue(
                "This test will only pass when all placeholder translations'keys match their entry with shortened placeholders",
                allPlaceholderKeysMatchEntry
        )
    }

    @Test
    fun noTwoPlaceholdersAreTheSame() {
        var noTwoPlaceholdersAreTheSame = true
        for (translationEntry in translations.values) {
            val placeholders = squareBraceRegex.findAll(translationEntry.entry)
                    .map { it.value }.toList()

            for (placeholder in placeholders)
                if (placeholders.count { it == placeholder } > 1) {
                    noTwoPlaceholdersAreTheSame = false
                    debug("Entry %s has the parameter %s more than once", translationEntry, placeholder)
                    break
                }
        }
        Assert.assertTrue(
                "This test will only pass when no translation keys have the same parameter twice",
                noTwoPlaceholdersAreTheSame
        )
    }

    @Test
    fun allTranslationsEndWithASpace() {
        val templateLines = Gdx.files.internal(TranslationFileWriter.templateFileLocation).reader().readLines()
        var failed = false
        for (line in templateLines) {
            if (line.endsWith(" =")) {
                debug("%s ends without a space at the end", line)
                failed = true
            }
        }
        Assert.assertFalse(failed)
    }


    @Test
    fun allStringsTranslate() {
        // Needed for .tr() to work
        UncivGame.Current = UncivGame("")
        UncivGame.Current.settings = GameSettings()

        for ((key, value) in translations)
            UncivGame.Current.translations[key] = value

        var allWordsTranslatedCorrectly = true
        for (translationEntry in translations.values) {
            for ((language, _) in translationEntry) {
                UncivGame.Current.settings.language = language
                try {
                    translationEntry.entry.tr()
                } catch (ex: Exception) {
                    allWordsTranslatedCorrectly = false
                    debug("Crashed when translating %s to %s", translationEntry.entry, language)
                }
            }
        }
        Assert.assertTrue(
            "This test will only pass when all phrases properly translate to their language",
            allWordsTranslatedCorrectly
        )
    }

    @Test
    fun wordBoundaryTranslationIsFormattedCorrectly() {
        val translationEntry = translations["\" \""]!!

        var allTranslationsCheckedOut = true
        for ((language, translation) in translationEntry) {
            if (!translation.startsWith("\"")
                || !translation.endsWith("\"")
                || translation.count { it == '\"' } != 2
            ) {
                allTranslationsCheckedOut = false
                debug("Translation of the word boundary in %s was incorrectly formatted", language)
            }
        }

        Assert.assertTrue(
            "This test will only pass when the word boundrary translation succeeds",
            allTranslationsCheckedOut
        )
    }


    @Test
    fun translationParameterExtractionForNestedBracesWorks() {
        Assert.assertEquals(listOf("New [York]"),
            "The city of [New [York]]".getPlaceholderParametersIgnoringLowerLevelBraces())

        // Closing braces without a matching opening brace - 'level 0' - are ignored
        Assert.assertEquals(listOf("New [York]"),
            "The city of [New [York]]]".getPlaceholderParametersIgnoringLowerLevelBraces())

        // Opening braces without a matching closing brace mean that the term is never 'closed'
        // so there are no parameters
        Assert.assertEquals(listOf<String>(),
            "The city of [[New [York]".getPlaceholderParametersIgnoringLowerLevelBraces())

        // Supernesting
        val superNestedString = "The brother of [[my [best friend]] and [[America]'s greatest [Dad]]]"
        Assert.assertEquals(listOf("[my [best friend]] and [[America]'s greatest [Dad]]"),
                superNestedString.getPlaceholderParametersIgnoringLowerLevelBraces())
        Assert.assertEquals(listOf("my [best friend]", "[America]'s greatest [Dad]"),
        superNestedString.getPlaceholderParametersIgnoringLowerLevelBraces()[0]
            .getPlaceholderParametersIgnoringLowerLevelBraces())

        UncivGame.Current = UncivGame("")
        UncivGame.Current.settings = GameSettings()

        fun addTranslation(original:String, result:String){
            UncivGame.Current.translations[original.getPlaceholderText()] =TranslationEntry(original)
                .apply { this["English"] = result }
        }
        addTranslation("The brother of [person]", "The sibling of [person]")
        Assert.assertEquals("The sibling of bob", "The brother of [bob]".tr())


        addTranslation("[a] and [b]", "[a] and indeed [b]")
        addTranslation("my [whatever]", "mine own [whatever]")
        addTranslation("[place]'s greatest [job]", "the greatest [job] in [place]")
        addTranslation("Dad", "Father")
        addTranslation("best friend", "closest ally")
        addTranslation("America", "The old British colonies")

        debug("[Dad] and [my [best friend]]".getPlaceholderText())
        Assert.assertEquals(listOf("Dad","my [best friend]"),
            "[Dad] and [my [best friend]]".getPlaceholderParametersIgnoringLowerLevelBraces())
        Assert.assertEquals("Father and indeed mine own closest ally", "[Dad] and [my [best friend]]".tr())

        // Reminder: "The brother of [[my [best friend]] and [[America]'s greatest [Dad]]]"
        Assert.assertEquals("The sibling of mine own closest ally and indeed the greatest Father in The old British colonies",
            superNestedString.tr())
    }


//    @Test
//    fun allConditionalsAreContainedInConditionalOrderTranslation() {
//        val orderedConditionals = Translations.englishConditionalOrderingString
//        val orderedConditionalsSet = orderedConditionals.getConditionals().map { it.placeholderText }
//        val translationEntry = translations[orderedConditionals]!!
//
//        var allTranslationsCheckedOut = true
//        for ((language, translation) in translationEntry) {
//            val translationConditionals = translation.getConditionals().map { it.placeholderText }
//            if (translationConditionals.toHashSet() != orderedConditionalsSet.toHashSet()
//                || translationConditionals.count() != translationConditionals.distinct().count()
//            ) {
//                allTranslationsCheckedOut = false
//                println("Not all or double parameters found in the conditional ordering for $language")
//            }
//        }
//
//        Assert.assertTrue(
//            "This test will only pass when each of the conditionals exists exactly once in the translations for the conditional ordering",
//            allTranslationsCheckedOut
//        )
//    }
}
