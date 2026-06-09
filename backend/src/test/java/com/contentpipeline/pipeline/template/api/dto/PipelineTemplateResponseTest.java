package com.contentpipeline.pipeline.template.api.dto;

import com.contentpipeline.pipeline.template.api.dto.PipelineTemplateResponse.StepDefinitionResponse;
import com.contentpipeline.pipeline.template.domain.PipelineStepDefinition;
import com.contentpipeline.pipeline.template.domain.PipelineTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins down the {@link PipelineTemplateResponse#from} entity→DTO mapping. Like
 * {@code PipelineRunResponse}, this mapper is a lazy-loading hazard under
 * {@code open-in-view: false}: {@code PipelineTemplateController} maps straight from a
 * repository result, so it relies on an {@code @EntityGraph} finder having initialized
 * {@code steps} (CLAUDE.md "Lazy-loading rule"). Here we feed a fully-initialized graph
 * and assert every field crosses and step ordering is preserved — catching a dropped or
 * mis-mapped field without a database. The {@code @OrderBy("stepOrder ASC")} ordering is
 * a DB-load concern; the mapper itself preserves the list as given, which is what we test.
 */
class PipelineTemplateResponseTest {

    @Test
    @DisplayName("from: copies every template field and maps its step definitions in list order")
    void mapsFullTemplateGraph() {
        UUID templateId = UUID.randomUUID();

        PipelineTemplate template = new PipelineTemplate();
        setId(template, templateId);
        template.setName("Story Pipeline");
        template.setDescription("Prompt to published short");
        template.setVersion(3);
        template.setActive(true);

        template.getSteps().add(stepDef(template, 1, "GENERATE_STORY", "Generate story", "AI script"));
        template.getSteps().add(stepDef(template, 2, "RENDER_VIDEO", "Render video", "FFmpeg burn-in"));

        PipelineTemplateResponse response = PipelineTemplateResponse.from(template);

        assertThat(response.id()).isEqualTo(templateId);
        assertThat(response.name()).isEqualTo("Story Pipeline");
        assertThat(response.description()).isEqualTo("Prompt to published short");
        assertThat(response.version()).isEqualTo(3);
        assertThat(response.active()).isTrue();

        assertThat(response.steps()).hasSize(2);
        assertThat(response.steps()).extracting(StepDefinitionResponse::stepOrder).containsExactly(1, 2);
        assertThat(response.steps()).extracting(StepDefinitionResponse::stepHandlerKey)
            .containsExactly("GENERATE_STORY", "RENDER_VIDEO");
        assertThat(response.steps().get(0).stepName()).isEqualTo("Generate story");
        assertThat(response.steps().get(0).description()).isEqualTo("AI script");
    }

    @Test
    @DisplayName("from: an inactive template with no steps maps to active=false and an empty (non-null) steps list")
    void mapsTemplateWithNoSteps() {
        PipelineTemplate template = new PipelineTemplate();
        setId(template, UUID.randomUUID());
        template.setName("Empty");
        template.setActive(false);

        PipelineTemplateResponse response = PipelineTemplateResponse.from(template);

        assertThat(response.active()).isFalse();
        assertThat(response.steps()).isEmpty();
    }

    private PipelineStepDefinition stepDef(PipelineTemplate template, int order, String key,
                                           String name, String description) {
        PipelineStepDefinition def = new PipelineStepDefinition();
        setId(def, UUID.randomUUID());
        def.setPipelineTemplate(template);
        def.setStepOrder(order);
        def.setStepHandlerKey(key);
        def.setStepName(name);
        def.setDescription(description);
        return def;
    }

    private static void setId(Object entity, UUID id) {
        ReflectionTestUtils.setField(entity, "id", id);
    }
}
