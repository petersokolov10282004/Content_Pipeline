package com.contentpipeline.config;

import com.contentpipeline.pipeline.template.domain.PipelineStepDefinition;
import com.contentpipeline.pipeline.template.domain.PipelineTemplate;
import com.contentpipeline.pipeline.template.repository.PipelineTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the STORY_GAMEPLAY_VIDEO_V1 pipeline template on startup if it does not
 * already exist. Idempotent — safe to run on every boot.
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private static final String TEMPLATE_NAME = "STORY_GAMEPLAY_VIDEO_V1";

    private final PipelineTemplateRepository templateRepository;

    public DataInitializer(PipelineTemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (templateRepository.existsByName(TEMPLATE_NAME)) {
            log.debug("Pipeline template '{}' already exists — skipping seed", TEMPLATE_NAME);
            return;
        }

        PipelineTemplate template = new PipelineTemplate();
        template.setName(TEMPLATE_NAME);
        template.setDescription("Short-form story gameplay video: AI script → AI subtitles → FFmpeg render → YouTube upload");
        template.setVersion(1);
        template.setActive(true);

        template.getSteps().add(step(template, 1,
            "GENERATE_STORY",
            "Generate Story Script",
            "Calls Claude to write a short-form story script from the provided prompt.",
            """
            {
              "targetDurationSeconds": 60,
              "maxTokens": 4096
            }
            """));

        template.getSteps().add(step(template, 2,
            "GENERATE_SUBTITLES",
            "Generate Subtitles",
            "Calls Claude to produce SRT subtitles timed to the script duration.",
            """
            {
              "maxCharsPerLine": 40,
              "maxLinesPerCue": 2
            }
            """));

        template.getSteps().add(step(template, 3,
            "RENDER_VIDEO",
            "Render Video",
            "Runs FFmpeg synchronously to produce a 1080×1920 MP4 and uploads it to R2.",
            """
            {
              "width": 1080,
              "height": 1920,
              "fps": 30,
              "crf": 23,
              "preset": "fast"
            }
            """));

        template.getSteps().add(step(template, 4,
            "UPLOAD_VIDEO",
            "Upload to YouTube",
            "Uploads the rendered video to YouTube via the Data API v3 (mock in dev).",
            """
            {
              "privacyStatus": "PRIVATE"
            }
            """));

        templateRepository.save(template);
        log.info("Seeded pipeline template '{}'", TEMPLATE_NAME);
    }

    private static PipelineStepDefinition step(
        PipelineTemplate template,
        int order,
        String handlerKey,
        String name,
        String description,
        String configJson
    ) {
        PipelineStepDefinition step = new PipelineStepDefinition();
        step.setPipelineTemplate(template);
        step.setStepOrder(order);
        step.setStepHandlerKey(handlerKey);
        step.setStepName(name);
        step.setDescription(description);
        step.setConfigJson(configJson.strip());
        step.setRetryable(true);
        step.setMaxRetries(3);
        return step;
    }
}
