package com.priyanshu.index.indexing_service.service;

import com.priyanshu.index.indexing_service.dto.AiAnalysis;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiAnalysisIsolationTest {
    @Test
    void analysisDoesNotShareMutableTags() {
        var tags = new ArrayList<>(List.of("cloud"));
        var analysis = new AiAnalysis("summary", tags, "COMPLETED", null);
        tags.clear();
        assertEquals(List.of("cloud"), analysis.tags());
        assertThrows(UnsupportedOperationException.class, () -> analysis.tags().clear());
    }

    @Test
    void optionalNullTagsRemainNull() {
        assertNull(new AiAnalysis(null, null, "FAILED", "unavailable").tags());
    }
}
