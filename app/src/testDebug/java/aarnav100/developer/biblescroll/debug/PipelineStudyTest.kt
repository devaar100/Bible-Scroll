package aarnav100.developer.biblescroll.debug

import aarnav100.developer.biblescroll.core.content.model.CrossReferenceSource
import aarnav100.developer.biblescroll.core.content.model.Language
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Debug-only: pipeline study files (schema/study.json v3) -> the app's published Study (like publish.public_study). */
class PipelineStudyTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun pipelineStudy(status: String = "auto_checked", model: String? = "claude-haiku-5-5", meaning: String = "God loves.") = """
        {"id":"JHN.1.12","schemaVersion":3,"contentVersion":"60a7844d3ae1",
         "originalLanguage":{"language":"greek","textBasis":"critical","words":[
           {"surface":"ἔλαβον","lemma":"λαμβάνω","transliteration":"e.labon","strongs":"G2983","gloss":"received","morphology":"V-2AAI-3P"}]},
         "crossReferences":[{"ref":"GAL.3.26","source":"OpenBible"},{"ref":"1JN.3.1","source":"TSK","note":"sons"}],
         "meaning":"$meaning","historicalContext":"h","lifeInBibleTimes":"l","theology":"t","didYouKnow":"d","applyIt":"a",
         "citations":[{"source":"STEP Bible","licence":"CC BY 4.0","url":"https://stepbible.github.io/STEPBible-Data/"}],
         "status":"$status",
         "meta":{"model":${model?.let { "\"$it\"" } ?: "null"},"generatedAt":"2026-11-18T22:53:45+00:00","promptVersion":"62631d6c8e7e","reviewedBy":"Private Name"},
         "checks":{"checkedAt":"2026-11-18T22:54:00+00:00","checkerVersion":"3","reasons":[]}}
    """.trimIndent()

    @Test
    fun writtenStudyBecomesThePublishedShape() {
        val study = PipelineStudy.toAppStudy(pipelineStudy(), json)!!
        assertEquals("JHN.1.12", study.id)
        assertEquals("60a7844d3ae1", study.contentVersion)
        assertEquals(Language.GREEK, study.originalLanguage.language)
        assertEquals(CrossReferenceSource.TSK, study.crossReferences[1].source)
        assertEquals("God loves.", study.meaning)
        assertEquals("claude-haiku-5-5", study.model)
        assertEquals("62631d6c8e7e", study.promptVersion)
        assertFalse(study.reviewed)
    }

    @Test
    fun reviewedOnlyWhenTheStatusIsReviewed() {
        assertTrue(PipelineStudy.toAppStudy(pipelineStudy(status = "reviewed"), json)!!.reviewed)
        assertFalse(PipelineStudy.toAppStudy(pipelineStudy(status = "flagged"), json)!!.reviewed)
    }

    @Test
    fun unwrittenStudiesAreSkipped() {
        assertNull(PipelineStudy.toAppStudy(pipelineStudy(status = "generated", model = null), json))
        assertNull(PipelineStudy.toAppStudy(pipelineStudy(meaning = ""), json))
    }
}
