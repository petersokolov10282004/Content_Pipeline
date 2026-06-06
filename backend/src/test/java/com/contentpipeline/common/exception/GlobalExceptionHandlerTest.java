package com.contentpipeline.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the exception → HTTP status contract. Lives in the same package as the
 * handler so the package-private handler methods can be invoked directly (no web
 * server, no Spring context — just the mapping logic).
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("ResourceNotFoundException → 404 with the exception message as detail")
    void notFoundMapsTo404() {
        ProblemDetail pd = handler.handleNotFound(new ResourceNotFoundException("Project", "abc-123"));

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(pd.getDetail()).isEqualTo("Project not found: abc-123");
    }

    @Test
    @DisplayName("PipelineException → 422 Unprocessable Entity")
    void pipelineMapsTo422() {
        ProblemDetail pd = handler.handlePipeline(new PipelineException("template is not active"));

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.value());
        assertThat(pd.getDetail()).isEqualTo("template is not active");
    }

    @Test
    @DisplayName("IllegalArgumentException → 400 Bad Request")
    void illegalArgumentMapsTo400() {
        ProblemDetail pd = handler.handleBadArg(new IllegalArgumentException("bad input"));

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(pd.getDetail()).isEqualTo("bad input");
    }
}
