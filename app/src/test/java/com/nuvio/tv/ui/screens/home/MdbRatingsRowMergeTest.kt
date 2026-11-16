package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MDBListRatings
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import org.junit.Assert.*
import org.junit.Test

class MdbRatingsRowMergeTest {
    private val order = listOf("imdb", "trakt")
    private fun item(id: String) = MetaPreview(
        id = id, type = ContentType.MOVIE, name = id, poster = "https://example.test/$id.jpg",
        posterShape = PosterShape.POSTER, background = null, logo = null,
        description = null, releaseInfo = null, imdbRating = 7f, genres = emptyList()
    )
    private fun row(id: String, vararg items: MetaPreview) = CatalogRow(
        addonId = "addon", addonName = "Addon", addonBaseUrl = "https://example.test",
        catalogId = id, catalogName = id, type = ContentType.MOVIE, items = items.toList()
    )

    @Test fun `one batch updates all matching titles and preserves unrelated identities`() {
        val untouched = item("other")
        val a = item("a")
        val b = item("b")
        val unchangedRow = row("unrelated", untouched)
        val rows = listOf(row("first", a, b, untouched), row("second", a), unchangedRow)
        val ratings = mapOf("a" to MDBListRatings(imdb = 8.2), "b" to MDBListRatings(trakt = 90.0))
        val merged = mergeMdbRatingsRows(rows, ratings, order)
        assertEquals(8.2f, merged[0].items[0].imdbRating)
        assertEquals(ratings["b"], merged[0].items[1].mdbListRatings)
        assertEquals(ratings["a"], merged[1].items[0].mdbListRatings)
        assertSame(untouched, merged[0].items[2])
        assertSame(unchangedRow, merged[2])
        assertNull(a.mdbListRatings)
    }

    @Test fun `absent imdb rating preserves existing imdb and artwork`() {
        val original = item("a")
        val merged = original.withMdbRatings(MDBListRatings(tmdb = 82.0), order)
        assertEquals(7f, merged.imdbRating)
        assertEquals(original.poster, merged.poster)
        assertEquals(order, merged.mdbListRatingOrder)
    }

    @Test fun `identical or unrelated results do not trigger another catalog emission`() {
        val ratings = MDBListRatings(imdb = 8.0)
        val rows = listOf(row("first", item("a").withMdbRatings(ratings, order)))
        assertSame(rows, mergeMdbRatingsRows(rows, mapOf("a" to ratings), order))
        assertSame(rows, mergeMdbRatingsRows(rows, mapOf("missing" to ratings), order))
        assertSame(rows, mergeMdbRatingsRows(rows, emptyMap(), order))
    }
}
