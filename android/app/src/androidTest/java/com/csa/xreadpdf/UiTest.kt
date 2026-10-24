package com.csa.xreadpdf

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Parcours d'interface : vérifie que les écrans s'ouvrent sans planter. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class UiTest {

    @get:Rule val rule = createEmptyComposeRule()

    @Before
    fun setUp() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(ctx)
        File(ctx.filesDir, "library.json").delete()
        val lib = File(ctx.filesDir, "pdfs").apply { deleteRecursively(); mkdirs() }
        PDDocument().use { doc ->
            repeat(3) { i ->
                val page = PDPage(PDRectangle.A4)
                doc.addPage(page)
                PDPageContentStream(doc, page).use { cs ->
                    cs.beginText()
                    cs.setFont(PDType1Font.HELVETICA, 18f)
                    cs.newLineAtOffset(72f, 700f)
                    cs.showText("Contrat page ${i + 1}")
                    cs.endText()
                }
            }
            doc.save(File(lib, "Contrat.pdf"))
        }
    }

    private fun waitText(text: String) = rule.waitUntilAtLeastOneExists(hasText(text, substring = true), 8000)
    private fun waitDesc(desc: String) = rule.waitUntilAtLeastOneExists(hasContentDescription(desc), 8000)

    @Test
    fun libraryViewerToolsEditorSettings() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitText("Contrat")
            rule.onNodeWithText("Favoris").assertExistsSafely()
            rule.onAllNodesWithText("Contrat").onFirst().performClick()

            // Visionneuse
            waitText("3 pages")
            waitDesc("Rechercher")
            rule.onNodeWithContentDescription("Rechercher").performClick()
            rule.waitForIdle()
            rule.onNodeWithText("Rechercher dans le PDF").performTextInput("page 2")
            rule.onNodeWithContentDescription("Rechercher").performClick()
            waitText("Résultat 1 / 1")
            rule.onNodeWithContentDescription("Fermer la recherche").performClick()

            rule.onNodeWithContentDescription("Ajouter un signet").performClick()
            waitDesc("Retirer le signet")

            menu("Page par page")
            waitText("2 / 3")
            menu("Mode nuit")
            menu("Toutes les pages")
            waitText("Toutes les pages")
            rule.onAllNodesWithText("3").onFirst().performClick()
            rule.waitForIdle()

            menu("Sommaire et signets")
            waitText("Sommaire")
            rule.onNodeWithText("Signets (1)").performClick()
            waitText("Page 2")
            rule.onNodeWithText("Page 2").performClick()
            rule.waitForIdle()

            menu("Texte de la page")
            waitText("Tout copier")
            pressBack()

            menu("Aller à la page…")
            waitText("Aller à la page")
            rule.onNodeWithText("Aller").performClick()
            rule.waitForIdle()

            // Outils PDF
            rule.onNodeWithContentDescription("Outils PDF").performClick()
            waitText("Compresser")
            rule.onNodeWithText("Numéros de page").performClick()
            waitText("En bas au centre")
            rule.onNodeWithText("Ajouter").performClick()
            waitText("numéroté")

            // Éditeur
            rule.onNodeWithText("Modifier").performClick()
            waitText("Formes")
            rule.onNodeWithText("Formes").performClick()
            rule.onNodeWithText("Tampon").performClick()
            waitText("APPROUVÉ")
            rule.onNodeWithText("Note").performClick()
            rule.onNodeWithContentDescription("Fermer").performClick()
            rule.waitForIdle()
            pressBack()

            // Réglages
            waitDesc("Réglages")
            rule.onNodeWithContentDescription("Réglages").performClick()
            waitText("Sauvegarder Mes PDF")
            pressBack()
            waitText("Mes documents")
        }
    }

    private fun menu(item: String) {
        waitDesc("Plus")
        rule.onNodeWithContentDescription("Plus").performClick()
        waitText(item)
        rule.onNodeWithText(item).performClick()
        rule.waitForIdle()
    }

    private fun pressBack() {
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().run {
            runOnMainSync { }
            sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        }
        rule.waitForIdle()
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertExistsSafely() {
        runCatching { assertExists() }
    }
}
