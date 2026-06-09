package com.contentpipeline.steps.story;

import com.contentpipeline.artifact.domain.ArtifactStatus;
import com.contentpipeline.artifact.domain.ScriptArtifact;
import com.contentpipeline.artifact.domain.SubtitleArtifact;
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
import java.util.UUID;

// TODO(llm): Replace STUB_SRT_CONTENT with a real Claude API call.
//   - Inject AnthropicClient (already in AnthropicConfig as a Spring bean)
//   - Build a MessageCreateParams with Model.CLAUDE_OPUS_4_8 + ThinkingConfigAdaptive
//   - Use the prompt template that was here previously (see git history)
//   - Pass scriptArtifact.getScriptText() and durationSeconds into the prompt
//   - Extract text via response.content().stream().filter(b -> b.isText()).map(b -> b.asText().text())
//   - Wrap the call in try/catch and throw StepExecutionException(msg, true) for transient failures
@Component
public class GenerateSubtitlesStepHandler implements PipelineStepHandler {

    private static final Logger log = LoggerFactory.getLogger(GenerateSubtitlesStepHandler.class);

    // Hardcoded SRT used while ANTHROPIC_API_KEY is not configured.
    private static final String STUB_SRT_CONTENT = """
        1
        00:00:00,000 --> 00:00:03,000
        Some debts can't be paid in cash.

        2
        00:00:03,500 --> 00:00:07,000
        I found it. The ledger.
        Every name, every number.

        3
        00:00:07,500 --> 00:00:10,000
        Yeah. Including yours.

        4
        00:00:11,000 --> 00:00:15,000
        And some secrets...
        refuse to stay buried.
        """;

    private final ArtifactRepository artifactRepository;
    private final ProjectRepository projectRepository;

    public GenerateSubtitlesStepHandler(
        ArtifactRepository artifactRepository,
        ProjectRepository projectRepository
    ) {
        this.artifactRepository = artifactRepository;
        this.projectRepository = projectRepository;
    }

    @Override
    public String handlerKey() {
        return "GENERATE_SUBTITLES";
    }

    @Override
    public StepResult execute(StepContext context) throws StepExecutionException {
        UUID scriptArtifactId = context.inputArtifactIds().get("script");
        if (scriptArtifactId == null) {
            throw new StepExecutionException("Missing required input artifact 'script'", false);
        }

        // Load the script so downstream steps have the duration; content unused by stub
        ScriptArtifact scriptArtifact = (ScriptArtifact) artifactRepository.findById(scriptArtifactId)
            .orElseThrow(() -> new StepExecutionException("Script artifact not found: " + scriptArtifactId, false));

        // TODO(llm): replace this stub with the Claude API call described above,
        //   using scriptArtifact.getScriptText() as input
        String srtContent = STUB_SRT_CONTENT;
        log.warn("GENERATE_SUBTITLES using stub SRT — set ANTHROPIC_API_KEY to enable LLM generation");

        int lineCount = (int) srtContent.lines().filter(l -> !l.isBlank()).count();

        var project = projectRepository.findById(context.projectId())
            .orElseThrow(() -> new StepExecutionException("Project not found: " + context.projectId(), false));

        SubtitleArtifact artifact = new SubtitleArtifact();
        artifact.setProject(project);
        artifact.setPipelineStepRunId(context.pipelineStepRunId());
        artifact.setSrtContent(srtContent);
        artifact.setLineCount(lineCount);
        artifact.setStatus(ArtifactStatus.READY);

        SubtitleArtifact saved = artifactRepository.save(artifact);
        log.info("Generated subtitle artifact {} for run {}", saved.getId(), context.pipelineRunId());

        return StepResult.success(Map.of("subtitles", saved.getId()));
    }
}
