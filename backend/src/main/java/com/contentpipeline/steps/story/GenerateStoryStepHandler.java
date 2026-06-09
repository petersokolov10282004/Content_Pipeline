package com.contentpipeline.steps.story;

import com.contentpipeline.artifact.domain.ArtifactStatus;
import com.contentpipeline.artifact.domain.ScriptArtifact;
import com.contentpipeline.artifact.repository.ArtifactRepository;
import com.contentpipeline.common.exception.StepExecutionException;
import com.contentpipeline.pipeline.handler.PipelineStepHandler;
import com.contentpipeline.pipeline.handler.StepContext;
import com.contentpipeline.pipeline.handler.StepResult;
import com.contentpipeline.project.repository.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

// TODO(llm): Replace STUB_SCRIPT_TEXT with a real Claude API call.
//   - Inject AnthropicClient (already in AnthropicConfig as a Spring bean)
//   - Build a MessageCreateParams with Model.CLAUDE_OPUS_4_8 + ThinkingConfigAdaptive
//   - Use the prompt template that was here previously (see git history)
//   - The targetDurationSeconds from stepConfig should drive the prompt
//   - Extract text via response.content().stream().filter(b -> b.isText()).map(b -> b.asText().text())
//   - Wrap the call in try/catch and throw StepExecutionException(msg, true) for transient failures
@Component
public class GenerateStoryStepHandler implements PipelineStepHandler {

    private static final Logger log = LoggerFactory.getLogger(GenerateStoryStepHandler.class);

    // Hardcoded script used while ANTHROPIC_API_KEY is not configured.
    private static final String STUB_SCRIPT_TEXT = """
        [OPEN on a dimly lit alley. Rain falls steadily.]

        NARRATOR: Some debts can't be paid in cash.

        [A figure — MARA, 30s, leather jacket — steps into a puddle of light.]

        MARA: (into phone) I found it. The ledger. Every name, every number.

        [Beat. She glances over her shoulder.]

        MARA: Yeah. Including yours.

        [She ends the call. Footsteps echo behind her.]

        NARRATOR: And some secrets... refuse to stay buried.

        [SMASH CUT TO BLACK.]
        """;

    private final ArtifactRepository artifactRepository;
    private final ProjectRepository projectRepository;

    public GenerateStoryStepHandler(
        ArtifactRepository artifactRepository,
        ProjectRepository projectRepository
    ) {
        this.artifactRepository = artifactRepository;
        this.projectRepository = projectRepository;
    }

    @Override
    public String handlerKey() {
        return "GENERATE_STORY";
    }

    @Override
    public StepResult execute(StepContext context) throws StepExecutionException {
        int targetDuration = parseInt(context.stepConfig().get("targetDurationSeconds"), 60);

        // TODO(llm): replace this stub with the Claude API call described above
        String scriptText = STUB_SCRIPT_TEXT;
        log.warn("GENERATE_STORY using stub script — set ANTHROPIC_API_KEY to enable LLM generation");

        var project = projectRepository.findById(context.projectId())
            .orElseThrow(() -> new StepExecutionException("Project not found: " + context.projectId(), false));

        ScriptArtifact artifact = new ScriptArtifact();
        artifact.setProject(project);
        artifact.setPipelineStepRunId(context.pipelineStepRunId());
        artifact.setScriptText(scriptText);
        artifact.setTitle("Generated Story Script");
        artifact.setEstimatedDurationSeconds(targetDuration);
        artifact.setStatus(ArtifactStatus.READY);

        ScriptArtifact saved = artifactRepository.save(artifact);
        log.info("Generated script artifact {} for run {}", saved.getId(), context.pipelineRunId());

        return StepResult.success(Map.of("script", saved.getId()));
    }

    private int parseInt(String value, int defaultValue) {
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
