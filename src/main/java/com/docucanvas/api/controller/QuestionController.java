package com.docucanvas.api.controller;

import com.docucanvas.api.dto.request.QuestionRequest;
import com.docucanvas.api.dto.response.QuestionResponse;
import com.docucanvas.application.service.QuestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/questions")
@RequiredArgsConstructor
public class QuestionController {

    private final QuestionService questionService;

    @PostMapping
    public ResponseEntity<QuestionResponse> ask(
            @Valid @RequestBody QuestionRequest request) {
        return ResponseEntity.ok(questionService.answer(request));
    }
}
