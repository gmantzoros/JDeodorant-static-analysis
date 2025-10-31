package gr.uom.java.ast.context;

import gr.uom.java.ast.ClassObject;
import java.util.*;

/**
 * A wrapper around JDeodorant's ClassObject.
 * This class only stores relationships to other contextual elements
 * (methods, fields, metrics, and inter-class dependencies).
 */
public class ClassContext {

    private final ClassObject classObject;

    // Internal element contexts
    private final List<FieldContext> fieldContexts = new ArrayList<>();
    private final List<MethodContext> methodContexts = new ArrayList<>();

    // Relationships between classes
    private final Set<ClassContext> dependencyClasses = new HashSet<>();  // classes this one depends on
    private final Set<ClassContext> dependentClasses = new HashSet<>();   // classes depending on this one

    // Associated metrics
    private MetricsContext metricsContext;

    // Raw source code (attached later)
    private String sourceCode;

    public ClassContext(ClassObject classObject) {
        this.classObject = classObject;
    }

    // --- Relationship management ---
    public void addDependency(ClassContext dependency) {
        dependencyClasses.add(dependency);
    }

    public void addDependent(ClassContext dependent) {
        dependentClasses.add(dependent);
    }

    public Set<ClassContext> getDependencyClasses() {
        return Collections.unmodifiableSet(dependencyClasses);
    }

    public Set<ClassContext> getDependentClasses() {
        return Collections.unmodifiableSet(dependentClasses);
    }

    // --- Internal element access ---
    public List<FieldContext> getFieldContexts() {
        return fieldContexts;
    }

    public List<MethodContext> getMethodContexts() {
        return methodContexts;
    }

    public ClassObject getClassObject() {
        return classObject;
    }

    public String getClassName() {
        return classObject.getName();
    }

    // --- Metrics ---
    public MetricsContext getMetricsContext() {
        return metricsContext;
    }

    public void setMetricsContext(MetricsContext metricsContext) {
        this.metricsContext = metricsContext;
    }

    // --- Source code ---
    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    @Override
    public String toString() {
        return "ClassContext[" +
                classObject.getName() +
                ", methods=" + methodContexts.size() +
                ", fields=" + fieldContexts.size() +
                ", dependsOn=" + dependencyClasses.size() +
                "]";
    }
}
