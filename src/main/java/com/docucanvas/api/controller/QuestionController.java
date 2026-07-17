package com.docucanvas.api.controller;

import com.docucanvas.api.dto.request.QuestionRequest;
import com.docucanvas.api.dto.response.CitationDTO;
import com.docucanvas.api.dto.response.QuestionResponse;
import com.docucanvas.application.question.AnswerQuestionCommand;
import com.docucanvas.application.question.AnswerResult;
import com.docucanvas.application.question.Citation;
import com.docucanvas.application.service.QuestionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/questions")
public class QuestionController {

    private final QuestionService questionService;

    public QuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    @PostMapping("/ask")
    public ResponseEntity<QuestionResponse> ask(@Valid @RequestBody QuestionRequest request) {
        AnswerResult result = questionService.answer(toCommand(request));
        return ResponseEntity.ok(toResponse(result));
    }

    private AnswerQuestionCommand toCommand(QuestionRequest request) {
        return new AnswerQuestionCommand(request.question(), request.maxChunks(), request.documentId());
    }

    private QuestionResponse toResponse(AnswerResult result) {
        List<CitationDTO> citations = result.citations().stream()
                .map(this::toCitationDto)
                .toList();
        return new QuestionResponse(
                result.question(),
                result.answer(),
                citations,
                result.chunkCount(),
                result.imageUrl(),
                result.retrievalTimeMs(),
                result.generationTimeMs(),
                result.imageTimeMs());
    }

    private CitationDTO toCitationDto(Citation c) {
        return new CitationDTO(c.source(), c.content(), c.score());
    }
}
