package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.MDBListRatings
import com.nuvio.tv.domain.model.MetaPreview

internal fun MetaPreview.withMdbRatings(ratings: MDBListRatings, order: List<String>): MetaPreview {
    if (mdbListRatings == ratings && mdbListRatingOrder == order &&
        (ratings.imdb == null || imdbRating == ratings.imdb.toFloat())) return this
    return copy(
        mdbListRatings = ratings,
        mdbListRatingOrder = order,
        imdbRating = ratings.imdb?.toFloat() ?: imdbRating
    )
}

/** Preserve unchanged identities so receiving one ratings batch does not invalidate every row. */
internal fun mergeMdbRatingsRows(
    rows: List<CatalogRow>,
    ratingsById: Map<String, MDBListRatings>,
    order: List<String>
): List<CatalogRow> {
    if (ratingsById.isEmpty()) return rows
    var anyChanged = false
    val result = rows.map { row ->
        var rowChanged = false
        val items = row.items.map itemLoop@ { item ->
            val ratings = ratingsById[item.id] ?: return@itemLoop item
            val updated = item.withMdbRatings(ratings, order)
            if (updated !== item) rowChanged = true
            updated
        }
        if (rowChanged) {
            anyChanged = true
            row.copy(items = items)
        } else row
    }
    return if (anyChanged) result else rows
}
