package gr.uom.java.ast.context;

import gr.uom.java.ast.*;
import java.util.*;

/**
 * ClassContext provides an enriched contextual representation of a single
 * ClassObject analyzed by JDeodorant. It gathers methods, fields, metrics,
 * and structural relationships of the class.
 */
public class ClassContext {

    private final ClassObject classObject;
    private final List<FieldContext> fieldContexts;
    private final List<MethodContext> methodContexts;
    private final MetricsContext metricsContext;

    private final String className;
    private final String superclassName;
    private final List<String> implementedInterfaces;
    private final boolean isAbstract;
    private final boolean isInterface;
    private final boolean isEnum;
    private final boolean isStatic;

    private String sourceCode;

    public ClassContext(ClassObject classObject) {
        this.classObject = classObject;
        this.className = classObject.getName();
        this.superclassName = (classObject.getSuperclass() != null)
                ? classObject.getSuperclass().getClassType()
                : null;
        this.implementedInterfaces = extractInterfaceNames(classObject);

        this.isAbstract = classObject.isAbstract();
        this.isInterface = classObject.isInterface();
        this.isEnum = classObject.isEnum();
        this.isStatic = classObject.isStatic();

        // Build internal contexts
        this.fieldContexts = extractFieldContexts(classObject);
        this.methodContexts = extractMethodContexts(classObject);

        // Compute metrics context
        this.metricsContext = new MetricsContext(classObject);
    }

    // --- Extraction helpers ---

    private List<FieldContext> extractFieldContexts(ClassObject classObject) {
        List<FieldContext> result = new ArrayList<>();
        ListIterator<FieldObject> fieldIterator = classObject.getFieldIterator();
        while (fieldIterator.hasNext()) {
            result.add(new FieldContext(fieldIterator.next()));
        }
        return result;
    }

    private List<MethodContext> extractMethodContexts(ClassObject classObject) {
        List<MethodContext> result = new ArrayList<>();
        ListIterator<MethodObject> methodIterator = classObject.getMethodIterator();
        while (methodIterator.hasNext()) {
            result.add(new MethodContext(methodIterator.next()));
        }
        return result;
    }

    private List<String> extractInterfaceNames(ClassObject classObject) {
        List<String> names = new ArrayList<>();
        ListIterator<TypeObject> iterator = classObject.getInterfaceIterator();
        while (iterator.hasNext()) {
            names.add(iterator.next().getClassType());
        }
        return names;
    }

    // --- Getters ---

    public String getClassName() {
        return className;
    }

    public String getSuperclassName() {
        return superclassName;
    }

    public List<String> getImplementedInterfaces() {
        return Collections.unmodifiableList(implementedInterfaces);
    }

    public List<FieldContext> getFieldContexts() {
        return Collections.unmodifiableList(fieldContexts);
    }

    public List<MethodContext> getMethodContexts() {
        return Collections.unmodifiableList(methodContexts);
    }

    public MetricsContext getMetricsContext() {
        return metricsContext;
    }

    public boolean isAbstract() {
        return isAbstract;
    }

    public boolean isInterface() {
        return isInterface;
    }

    public boolean isEnum() {
        return isEnum;
    }

    public boolean isStatic() {
        return isStatic;
    }

    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    @Override
    public String toString() {
        return "ClassContext[" + className +
                ", superclass=" + superclassName +
                ", methods=" + methodContexts.size() +
                ", fields=" + fieldContexts.size() + "]";
    }
}
