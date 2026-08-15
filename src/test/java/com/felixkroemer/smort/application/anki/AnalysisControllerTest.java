package com.felixkroemer.smort.application.anki;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.felixkroemer.smort.application.anki.mapping.AnalysisRestMapper;
import com.felixkroemer.smort.application.anki.mapping.AnkiNoteRestMapper;
import com.felixkroemer.smort.application.anki.mapping.BulkFormatRestMapper;
import com.felixkroemer.smort.application.chat.mapping.ChatMessageRestMapper;
import com.felixkroemer.smort.domain.anki.AnalysisService;
import com.felixkroemer.smort.domain.anki.AnkiNoteService;
import com.felixkroemer.smort.domain.anki.AnkiNoteTypeService;
import com.felixkroemer.smort.domain.anki.BulkFormatService;
import com.felixkroemer.smort.domain.chat.ChatOrchestrationService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

class AnalysisControllerTest {

  @Test
  void cancelBulkFormatInvokesServiceAndReturns202() throws Exception {
    var bulkFormatService = mock(BulkFormatService.class);
    var controller =
        new AnalysisController(
            mock(AnalysisService.class),
            mock(AnkiNoteService.class),
            mock(ChatOrchestrationService.class),
            mock(AnkiNoteTypeService.class),
            bulkFormatService,
            mock(AnalysisRestMapper.class),
            mock(AnkiNoteRestMapper.class),
            mock(BulkFormatRestMapper.class),
            mock(ChatMessageRestMapper.class));

    var method = AnalysisController.class.getMethod("cancelBulkFormat", UUID.class);
    var responseStatus = method.getAnnotation(ResponseStatus.class);
    assertEquals(HttpStatus.ACCEPTED, responseStatus.value());

    var analysisId = UUID.randomUUID();
    controller.cancelBulkFormat(analysisId);
    verify(bulkFormatService).cancelBulkFormat(analysisId);
  }
}
