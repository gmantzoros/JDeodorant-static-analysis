package gr.uom.java.ast.context;

import gr.uom.java.ast.ClassObject;
import java.util.*;

/**
 * A wrapper around JDeodorant's ClassObject.
 * Stores field/method contexts and inter-class dependency relationships with their types.
 */
public class ClassContext {

    private final ClassObject classObject;

    // Internal elements
    private final List<FieldContext> fieldContexts = new ArrayList<>();
    private final List<MethodContext> methodContexts = new ArrayList<>();

    // Inter-class relationships
    private final List<DependencyRelation> dependencyRelations = new ArrayList<>();
    private final List<DependencyRelation> dependentRelations = new ArrayList<>();

    // Metrics & source
    private MetricsContext metricsContext;
    private String sourceCode;
    
    //Workflow Map
    private List<String> workflowRoots = new ArrayList<>();
    private Map<String, List<String>> workflowMembership = new LinkedHashMap<>();

    // Constructor
    public ClassContext(ClassObject classObject) {
        this.classObject = classObject;
    }

    // Dependency management
    public void addDependency(ClassContext target, String type) {
        dependencyRelations.add(new DependencyRelation(target, type));
    }

    public void addDependent(ClassContext source, String type) {
        dependentRelations.add(new DependencyRelation(source, type));
    }

    public List<DependencyRelation> getDependencyRelations() {
        return Collections.unmodifiableList(dependencyRelations);
    }

    public List<DependencyRelation> getDependentRelations() {
        return Collections.unmodifiableList(dependentRelations);
    }

    public List<String> getWorkflowRoots() {
		return workflowRoots;
	}

	public void setWorkflowRoots(List<String> workflowRoots) {
		this.workflowRoots = workflowRoots;
	}

	public Map<String, List<String>> getWorkflowMembership() {
		return workflowMembership;
	}

	public void setWorkflowMembership(Map<String, List<String>> workflowMembership) {
		this.workflowMembership = workflowMembership;
	}

	// Element access
    public List<FieldContext> getFieldContexts() { return fieldContexts; }
    public List<MethodContext> getMethodContexts() { return methodContexts; }

    // Metrics
    public MetricsContext getMetricsContext() { return metricsContext; }
    public void setMetricsContext(MetricsContext metricsContext) { this.metricsContext = metricsContext; }

    // Source code
    public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }
    public String getSourceCode() { return sourceCode; }

    // ClassObject & name
    public ClassObject getClassObject() { return classObject; }
    public String getClassName() { return classObject.getName(); }

    @Override
    public String toString() {
        return "ClassContext[" +
                classObject.getName() +
                ", methods=" + methodContexts.size() +
                ", fields=" + fieldContexts.size() +
                ", dependsOn=" + dependencyRelations.size() +
                "]";
    }
}