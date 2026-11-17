package com.nuvio.tv.core.poster

import org.junit.Assert.*
import org.junit.Test

class PosterConsistencyIdsTest {
    private val pattern = "https://example.test/poster?tmdb_id={tmdb_id?}&imdb_id={imdb_id?}&stremio_id={id}&type={type}&shape={shape}&sm=notch&mo=genre,rating,year"

    @Test fun `IMDb episode suffix never reaches title poster service`() {
        val ids = CustomPosterUrlResolver.extractIds("tt26743760:3:8")
        assertEquals("tt26743760", ids.id)
        assertEquals("tt26743760", ids.imdbId)
        val url = CustomPosterUrlResolver.resolve(pattern, ids, "series")!!
        assertTrue(url.contains("imdb_id=tt26743760&stremio_id=tt26743760&"))
        assertFalse(url.contains(":3:8"))
        assertTrue(url.endsWith("shape=poster&sm=notch&mo=genre,rating,year"))
    }
    @Test fun `numeric episode suffixes normalize for all supported namespaces`() {
        for (prefix in listOf("tmdb", "tvdb", "kitsu", "anilist", "mal", "anidb")) {
            assertEquals("$prefix:123", CustomPosterUrlResolver.extractIds("$prefix:123:2:8").id)
        }
        assertEquals("220542", CustomPosterUrlResolver.extractIds("tmdb:220542:3:8").tmdbId)
    }
    @Test fun `opaque IDs and nonnumeric suffixes retain exact identity`() {
        for (id in listOf("server:account:item:123", "custom:anime:abc", "kitsu:123:alternate", "tt26743760:edition")) {
            assertEquals(id, CustomPosterUrlResolver.extractIds(id).id)
        }
    }
    @Test fun `anime ID retains tracking identity while explicit IMDb and TMDB reach poster service`() {
        val ids = CustomPosterUrlResolver.extractIds("kitsu:123", "tt26743760", 220542)
        val url = CustomPosterUrlResolver.resolve(pattern, ids, "series")!!
        assertTrue(url.contains("tmdb_id=220542&imdb_id=tt26743760&stremio_id=kitsu:123&"))
        assertEquals("kitsu:123", ids.id)
        assertEquals("123", ids.kitsuId)
    }
    @Test fun `explicit IMDb aliases normalize episode IDs too`() {
        assertEquals("tt26743760", CustomPosterUrlResolver.extractIds("kitsu:123", "tt26743760:3:8").imdbId)
    }
    @Test fun `invalid explicit TMDB does not erase a valid parsed ID`() {
        assertEquals("220542", CustomPosterUrlResolver.extractIds("tmdb:220542", explicitTmdbId = 0).tmdbId)
        assertEquals("220542", CustomPosterUrlResolver.extractIds("tmdb:220542", explicitTmdbId = -1).tmdbId)
    }
}
