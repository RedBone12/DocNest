package com.priyanshu.documents.search_service.service;

import com.priyanshu.documents.search_service.dto.SearchResponse;
import com.priyanshu.documents.search_service.dto.SearchResultItem;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SearchDtoIsolationTest {
    @Test
    void responsesKeepIndependentImmutableCollections() {
        var highlights = new ArrayList<>(List.of("matched text"));
        var tags = new ArrayList<>(List.of("cloud"));
        var item = new SearchResultItem("id", "title", "", 1L, "READY", "",
                highlights, "", tags, "COMPLETED", null);
        var items = new ArrayList<>(List.of(item));
        var response = new SearchResponse(1, items);
        highlights.clear();
        tags.clear();
        items.clear();
        assertEquals(List.of("matched text"), item.contentHighlights());
        assertEquals(List.of("cloud"), item.aiTags());
        assertEquals(List.of(item), response.results());
        assertThrows(UnsupportedOperationException.class, () -> response.results().clear());
    }

    @Test
    void optionalNullCollectionsRemainNull() {
        var item = new SearchResultItem("id", "", "", null, "", "", null, null, null, null, null);
        assertNull(item.contentHighlights());
        assertNull(item.aiTags());
        assertNull(new SearchResponse(0, null).results());
    }
}
