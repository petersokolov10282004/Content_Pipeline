package com.contentpipeline.pipeline.template.domain;

import com.contentpipeline.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "pipeline_step_definitions",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_template_step_order",
        columnNames = {"pipeline_template_id", "step_order"}
    )
)
public class PipelineStepDefinition extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pipeline_template_id", nullable = false)
    private PipelineTemplate pipelineTemplate;

    @Column(nullable = false)
    private Integer stepOrder;

    @Column(nullable = false, length = 100)
    private String stepHandlerKey;

    @Column(nullable = false)
    private String stepName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String configJson;

    @Column(nullable = false)
    private Boolean retryable = true;

    @Column(nullable = false)
    private Integer maxRetries = 3;

    public PipelineTemplate getPipelineTemplate() { return pipelineTemplate; }
    public void setPipelineTemplate(PipelineTemplate pipelineTemplate) { this.pipelineTemplate = pipelineTemplate; }
    public Integer getStepOrder() { return stepOrder; }
    public void setStepOrder(Integer stepOrder) { this.stepOrder = stepOrder; }
    public String getStepHandlerKey() { return stepHandlerKey; }
    public void setStepHandlerKey(String stepHandlerKey) { this.stepHandlerKey = stepHandlerKey; }
    public String getStepName() { return stepName; }
    public void setStepName(String stepName) { this.stepName = stepName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getConfigJson() { return configJson; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }
    public Boolean getRetryable() { return retryable; }
    public void setRetryable(Boolean retryable) { this.retryable = retryable; }
    public Integer getMaxRetries() { return maxRetries; }
    public void setMaxRetries(Integer maxRetries) { this.maxRetries = maxRetries; }
}
