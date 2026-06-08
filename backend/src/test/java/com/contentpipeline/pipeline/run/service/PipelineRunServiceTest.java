package com.contentpipeline.pipeline.run.service;

import com.contentpipeline.asset.domain.Asset;
import com.contentpipeline.asset.repository.AssetRepository;
import com.contentpipeline.common.exception.PipelineException;
import com.contentpipeline.common.exception.ResourceNotFoundException;
import com.contentpipeline.pipeline.run.api.dto.CreatePipelineRunRequest;
import com.contentpipeline.pipeline.run.api.dto.PipelineRunResponse;
import com.contentpipeline.pipeline.run.domain.PipelineRun;
import com.contentpipeline.pipeline.run.domain.PipelineRunStatus;
import com.contentpipeline.pipeline.run.domain.PipelineStepRun;
import com.contentpipeline.pipeline.run.domain.StepRunStatus;
import com.contentpipeline.pipeline.run.repository.PipelineRunRepository;
import com.contentpipeline.pipeline.template.domain.PipelineStepDefinition;
import com.contentpipeline.pipeline.template.domain.PipelineTemplate;
import com.contentpipeline.pipeline.template.repository.PipelineTemplateRepository;
import com.contentpipeline.project.domain.Project;
import com.contentpipeline.project.service.ProjectService;
import com.contentpipeline.workflow.WorkflowInput;
import com.contentpipeline.workflow.WorkflowStarter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Thorough coverage of the run-creation feature ({@link PipelineRunService#create}),
 * which is the heart of the pipeline orchestration: it must materialize a run plus a
 * PENDING step-run per template step — each with a stable id — and only then hand off
 * to Temporal, so the workflow always finds the ids it is told to drive.
 *
 * Collaborators (repositories, ProjectService, WorkflowStarter) are mocked. A *real*
 * {@link ObjectMapper} is used so input-param serialization is genuinely exercised.
 * Because the repository is mocked, the {@code save} stub stamps ids onto the run and
 * its step-runs via reflection to imitate the ids JPA would assign on persist.
 */
@ExtendWith(MockitoExtension.class)
class PipelineRunServiceTest {

    @Mock private PipelineRunRepository runRepository;
    @Mock private PipelineTemplateRepository templateRepository;
    @Mock private AssetRepository assetRepository;
    @Mock private ProjectService projectService;
    @Mock private WorkflowStarter workflowStarter;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private PipelineRunService service;

    private final String userId = "dev-user-001";
    private final UUID projectId = UUID.randomUUID();
    private Project project;

    @BeforeEach
    void setUp() {
        service = new PipelineRunService(
            runRepository, templateRepository, assetRepository,
            projectService, workflowStarter, objectMapper);

        project = new Project();
        project.setOwnerIdentifier(userId);
        setId(project, projectId);
    }

    // ---------------------------------------------------------------------
    // create() — happy path
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("create: materializes one PENDING step-run per template step, persists the run, then starts Temporal")
    void createMaterializesStepRunsAndStartsWorkflow() {
        PipelineTemplate template = activeTemplate("Story Pipeline", 3);
        stubOwnedProjectAndTemplate(template);
        stubSaveStampingIds();
        when(workflowStarter.start(any(WorkflowInput.class))).thenReturn("story-pipeline-abc");

        CreatePipelineRunRequest request = new CreatePipelineRunRequest(
            template.getId(), null, params("a haunted lighthouse"), "My run");

        PipelineRunResponse response = service.create(userId, projectId, request);

        // One step-run per template step, each PENDING, in template order.
        ArgumentCaptor<PipelineRun> runCaptor = ArgumentCaptor.forClass(PipelineRun.class);
        verify(runRepository).save(runCaptor.capture());
        PipelineRun saved = runCaptor.getValue();
        assertThat(saved.getStepRuns()).hasSize(3);
        assertThat(saved.getStepRuns())
            .allSatisfy(sr -> assertThat(sr.getStatus()).isEqualTo(StepRunStatus.PENDING));
        assertThat(saved.getStepRuns()).extracting(PipelineStepRun::getStepOrder)
            .containsExactly(1, 2, 3);
        // Every step-run points back at the owning run.
        assertThat(saved.getStepRuns())
            .allSatisfy(sr -> assertThat(sr.getPipelineRun()).isSameAs(saved));

        // The run flips to RUNNING and adopts the real Temporal workflow id.
        assertThat(saved.getStatus()).isEqualTo(PipelineRunStatus.RUNNING);
        assertThat(saved.getTemporalWorkflowId()).isEqualTo("story-pipeline-abc");

        // The response mirrors the persisted run.
        assertThat(response.status()).isEqualTo(PipelineRunStatus.RUNNING);
        assertThat(response.templateName()).isEqualTo("Story Pipeline");
        assertThat(response.steps()).hasSize(3);
    }

    @Test
    @DisplayName("create: persists the run (with its step-runs) BEFORE starting Temporal, and feeds those ids into the workflow input")
    void createPersistsBeforeStartingTemporalWithStableIds() {
        PipelineTemplate template = activeTemplate("Story Pipeline", 2);
        stubOwnedProjectAndTemplate(template);
        stubSaveStampingIds();
        when(workflowStarter.start(any(WorkflowInput.class))).thenReturn("story-pipeline-xyz");

        CreatePipelineRunRequest request = new CreatePipelineRunRequest(
            template.getId(), null, params("a prompt"), null);

        service.create(userId, projectId, request);

        // Ordering invariant: the DB save happens strictly before the Temporal handoff.
        var ordered = inOrder(runRepository, workflowStarter);
        ordered.verify(runRepository).save(any(PipelineRun.class));
        ordered.verify(workflowStarter).start(any(WorkflowInput.class));

        // The workflow input carries the run id and the ordered, non-null step-run ids.
        ArgumentCaptor<WorkflowInput> inputCaptor = ArgumentCaptor.forClass(WorkflowInput.class);
        verify(workflowStarter).start(inputCaptor.capture());
        WorkflowInput input = inputCaptor.getValue();

        ArgumentCaptor<PipelineRun> runCaptor = ArgumentCaptor.forClass(PipelineRun.class);
        verify(runRepository).save(runCaptor.capture());
        List<UUID> expectedStepRunIds = runCaptor.getValue().getStepRuns().stream()
            .map(PipelineStepRun::getId).toList();

        assertThat(input.pipelineRunId()).isEqualTo(runCaptor.getValue().getId());
        assertThat(input.stepRunIds())
            .doesNotContainNull()
            .containsExactlyElementsOf(expectedStepRunIds);
    }

    @Test
    @DisplayName("create: serializes the input params into inputParamsJson")
    void createSerializesInputParams() {
        PipelineTemplate template = activeTemplate("Story Pipeline", 1);
        stubOwnedProjectAndTemplate(template);
        stubSaveStampingIds();
        when(workflowStarter.start(any(WorkflowInput.class))).thenReturn("wf");

        CreatePipelineRunRequest request = new CreatePipelineRunRequest(
            template.getId(), null,
            new CreatePipelineRunRequest.InputParams("a quiet town", "horror", "tense"),
            null);

        service.create(userId, projectId, request);

        ArgumentCaptor<PipelineRun> runCaptor = ArgumentCaptor.forClass(PipelineRun.class);
        verify(runRepository).save(runCaptor.capture());
        assertThat(runCaptor.getValue().getInputParamsJson())
            .contains("\"prompt\":\"a quiet town\"")
            .contains("\"genre\":\"horror\"")
            .contains("\"tone\":\"tense\"");
    }

    // ---------------------------------------------------------------------
    // create() — input asset resolution
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("create: resolves named input assets and forwards the role→assetId map to the workflow input")
    void createResolvesInputAssets() {
        PipelineTemplate template = activeTemplate("Story Pipeline", 1);
        stubOwnedProjectAndTemplate(template);
        stubSaveStampingIds();
        when(workflowStarter.start(any(WorkflowInput.class))).thenReturn("wf");

        UUID assetId = UUID.randomUUID();
        Asset asset = assetOwnedBy(project, assetId);
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));

        CreatePipelineRunRequest request = new CreatePipelineRunRequest(
            template.getId(), Map.of("gameplay", assetId), params("p"), null);

        service.create(userId, projectId, request);

        ArgumentCaptor<WorkflowInput> inputCaptor = ArgumentCaptor.forClass(WorkflowInput.class);
        verify(workflowStarter).start(inputCaptor.capture());
        assertThat(inputCaptor.getValue().inputAssets()).containsExactly(Map.entry("gameplay", assetId));

        // The asset is also attached to the run's input-asset set.
        ArgumentCaptor<PipelineRun> runCaptor = ArgumentCaptor.forClass(PipelineRun.class);
        verify(runRepository).save(runCaptor.capture());
        assertThat(runCaptor.getValue().getInputAssets()).containsExactly(asset);
    }

    @Test
    @DisplayName("create: a null-valued asset entry is skipped rather than looked up")
    void createSkipsNullAssetEntries() {
        PipelineTemplate template = activeTemplate("Story Pipeline", 1);
        stubOwnedProjectAndTemplate(template);
        stubSaveStampingIds();
        when(workflowStarter.start(any(WorkflowInput.class))).thenReturn("wf");

        Map<String, UUID> assets = new HashMap<>();
        assets.put("gameplay", null); // present key, no id — must be ignored
        CreatePipelineRunRequest request = new CreatePipelineRunRequest(
            template.getId(), assets, params("p"), null);

        service.create(userId, projectId, request);

        verify(assetRepository, never()).findById(any());
        ArgumentCaptor<WorkflowInput> inputCaptor = ArgumentCaptor.forClass(WorkflowInput.class);
        verify(workflowStarter).start(inputCaptor.capture());
        assertThat(inputCaptor.getValue().inputAssets()).isEmpty();
    }

    @Test
    @DisplayName("create: an input asset from another project is reported as 404 and no workflow is started")
    void createRejectsCrossProjectAsset() {
        PipelineTemplate template = activeTemplate("Story Pipeline", 1);
        stubOwnedProjectAndTemplate(template);

        Project otherProject = new Project();
        setId(otherProject, UUID.randomUUID());
        UUID assetId = UUID.randomUUID();
        Asset foreignAsset = assetOwnedBy(otherProject, assetId);
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(foreignAsset));

        CreatePipelineRunRequest request = new CreatePipelineRunRequest(
            template.getId(), Map.of("gameplay", assetId), params("p"), null);

        assertThatThrownBy(() -> service.create(userId, projectId, request))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining(assetId.toString());
        verify(runRepository, never()).save(any());
        verify(workflowStarter, never()).start(any());
    }

    @Test
    @DisplayName("create: a missing input asset is reported as 404 and no workflow is started")
    void createRejectsMissingAsset() {
        PipelineTemplate template = activeTemplate("Story Pipeline", 1);
        stubOwnedProjectAndTemplate(template);

        UUID assetId = UUID.randomUUID();
        when(assetRepository.findById(assetId)).thenReturn(Optional.empty());

        CreatePipelineRunRequest request = new CreatePipelineRunRequest(
            template.getId(), Map.of("gameplay", assetId), params("p"), null);

        assertThatThrownBy(() -> service.create(userId, projectId, request))
            .isInstanceOf(ResourceNotFoundException.class);
        verify(workflowStarter, never()).start(any());
    }

    // ---------------------------------------------------------------------
    // create() — template & ownership guards
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("create: an inactive template is rejected with PipelineException before any run is saved")
    void createRejectsInactiveTemplate() {
        PipelineTemplate template = activeTemplate("Story Pipeline", 2);
        template.setActive(false);
        when(projectService.requireOwnedProject(userId, projectId)).thenReturn(project);
        when(templateRepository.findById(template.getId())).thenReturn(Optional.of(template));

        CreatePipelineRunRequest request = new CreatePipelineRunRequest(
            template.getId(), null, params("p"), null);

        assertThatThrownBy(() -> service.create(userId, projectId, request))
            .isInstanceOf(PipelineException.class)
            .hasMessageContaining("is not active");
        verify(runRepository, never()).save(any());
        verify(workflowStarter, never()).start(any());
    }

    @Test
    @DisplayName("create: an unknown template id is reported as 404")
    void createRejectsUnknownTemplate() {
        UUID templateId = UUID.randomUUID();
        when(projectService.requireOwnedProject(userId, projectId)).thenReturn(project);
        when(templateRepository.findById(templateId)).thenReturn(Optional.empty());

        CreatePipelineRunRequest request = new CreatePipelineRunRequest(
            templateId, null, params("p"), null);

        assertThatThrownBy(() -> service.create(userId, projectId, request))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("PipelineTemplate");
        verify(workflowStarter, never()).start(any());
    }

    @Test
    @DisplayName("create: an unowned project short-circuits with 404 before the template is even loaded")
    void createRejectsUnownedProject() {
        when(projectService.requireOwnedProject(userId, projectId))
            .thenThrow(new ResourceNotFoundException("Project", projectId));

        CreatePipelineRunRequest request = new CreatePipelineRunRequest(
            UUID.randomUUID(), null, params("p"), null);

        assertThatThrownBy(() -> service.create(userId, projectId, request))
            .isInstanceOf(ResourceNotFoundException.class);
        verify(templateRepository, never()).findById(any());
        verify(workflowStarter, never()).start(any());
    }

    @Test
    @DisplayName("create: a JSON serialization failure surfaces as PipelineException")
    void createWrapsSerializationFailure() throws JsonProcessingException {
        ObjectMapper failing = mock(ObjectMapper.class);
        when(failing.writeValueAsString(any()))
            .thenThrow(new JsonProcessingException("boom") {});
        PipelineRunService failingService = new PipelineRunService(
            runRepository, templateRepository, assetRepository,
            projectService, workflowStarter, failing);

        PipelineTemplate template = activeTemplate("Story Pipeline", 1);
        when(projectService.requireOwnedProject(userId, projectId)).thenReturn(project);
        when(templateRepository.findById(template.getId())).thenReturn(Optional.of(template));

        CreatePipelineRunRequest request = new CreatePipelineRunRequest(
            template.getId(), null, params("p"), null);

        assertThatThrownBy(() -> failingService.create(userId, projectId, request))
            .isInstanceOf(PipelineException.class)
            .hasMessageContaining("Failed to serialize params");
        verify(workflowStarter, never()).start(any());
    }

    // ---------------------------------------------------------------------
    // requireOwnedRun()
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("requireOwnedRun: returns the run when it belongs to the caller's project")
    void requireOwnedRunReturnsOwnedRun() {
        UUID runId = UUID.randomUUID();
        PipelineRun run = new PipelineRun();
        run.setProject(project);
        setId(run, runId);
        when(projectService.requireOwnedProject(userId, projectId)).thenReturn(project);
        when(runRepository.findById(runId)).thenReturn(Optional.of(run));

        assertThat(service.requireOwnedRun(userId, projectId, runId)).isSameAs(run);
    }

    @Test
    @DisplayName("requireOwnedRun: a run under a different project is reported as 404, not leaked")
    void requireOwnedRunRejectsCrossProjectRun() {
        UUID runId = UUID.randomUUID();
        Project otherProject = new Project();
        setId(otherProject, UUID.randomUUID());
        PipelineRun run = new PipelineRun();
        run.setProject(otherProject);
        setId(run, runId);
        when(projectService.requireOwnedProject(userId, projectId)).thenReturn(project);
        when(runRepository.findById(runId)).thenReturn(Optional.of(run));

        assertThatThrownBy(() -> service.requireOwnedRun(userId, projectId, runId))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining(runId.toString());
    }

    @Test
    @DisplayName("requireOwnedRun: an unknown run id is reported as 404")
    void requireOwnedRunRejectsUnknownRun() {
        UUID runId = UUID.randomUUID();
        when(projectService.requireOwnedProject(userId, projectId)).thenReturn(project);
        when(runRepository.findById(runId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireOwnedRun(userId, projectId, runId))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    private void stubOwnedProjectAndTemplate(PipelineTemplate template) {
        when(projectService.requireOwnedProject(userId, projectId)).thenReturn(project);
        when(templateRepository.findById(template.getId())).thenReturn(Optional.of(template));
    }

    /** Mimics JPA assigning ids on persist: stamp the run and each cascaded step-run. */
    private void stubSaveStampingIds() {
        when(runRepository.save(any(PipelineRun.class))).thenAnswer(inv -> {
            PipelineRun run = inv.getArgument(0);
            setId(run, UUID.randomUUID());
            run.getStepRuns().forEach(sr -> setId(sr, UUID.randomUUID()));
            return run;
        });
    }

    private CreatePipelineRunRequest.InputParams params(String prompt) {
        return new CreatePipelineRunRequest.InputParams(prompt, null, null);
    }

    private PipelineTemplate activeTemplate(String name, int stepCount) {
        PipelineTemplate template = new PipelineTemplate();
        setId(template, UUID.randomUUID());
        template.setName(name);
        template.setActive(true);
        for (int i = 1; i <= stepCount; i++) {
            PipelineStepDefinition def = new PipelineStepDefinition();
            setId(def, UUID.randomUUID());
            def.setPipelineTemplate(template);
            def.setStepOrder(i);
            def.setStepHandlerKey("STEP_" + i);
            def.setStepName("Step " + i);
            template.getSteps().add(def);
        }
        return template;
    }

    private Asset assetOwnedBy(Project owner, UUID assetId) {
        Asset asset = new Asset();
        setId(asset, assetId);
        asset.setProject(owner);
        return asset;
    }

    private static void setId(Object entity, UUID id) {
        ReflectionTestUtils.setField(entity, "id", id);
    }
}
