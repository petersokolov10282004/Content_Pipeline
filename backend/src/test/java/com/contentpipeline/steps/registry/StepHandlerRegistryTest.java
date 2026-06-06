package com.contentpipeline.steps.registry;

import com.contentpipeline.common.exception.PipelineException;
import com.contentpipeline.pipeline.handler.PipelineStepHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The registry is constructed from the List of handler beans Spring discovers.
 * We feed it mocked handlers (same as runtime) and verify indexing + lookup.
 */
class StepHandlerRegistryTest {

    @Test
    @DisplayName("indexes every handler by its handlerKey() and resolves them")
    void indexesAndResolvesByKey() {
        PipelineStepHandler story = mock(PipelineStepHandler.class);
        when(story.handlerKey()).thenReturn("GENERATE_STORY");
        PipelineStepHandler render = mock(PipelineStepHandler.class);
        when(render.handlerKey()).thenReturn("RENDER_VIDEO");

        StepHandlerRegistry registry = new StepHandlerRegistry(List.of(story, render));

        assertThat(registry.hasHandler("GENERATE_STORY")).isTrue();
        assertThat(registry.hasHandler("RENDER_VIDEO")).isTrue();
        assertThat(registry.getRequired("GENERATE_STORY")).isSameAs(story);
        assertThat(registry.getRequired("RENDER_VIDEO")).isSameAs(render);
    }

    @Test
    @DisplayName("getRequired() throws PipelineException for an unknown key")
    void getRequiredThrowsForUnknownKey() {
        StepHandlerRegistry registry = new StepHandlerRegistry(List.of());

        assertThat(registry.hasHandler("MISSING")).isFalse();
        assertThatThrownBy(() -> registry.getRequired("MISSING"))
            .isInstanceOf(PipelineException.class)
            .hasMessageContaining("No step handler registered for key: MISSING");
    }
}
