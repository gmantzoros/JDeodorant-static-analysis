package gr.uom.java.ast.context;

import gr.uom.java.ast.*;
import gr.uom.java.ast.decomposition.MethodBodyObject;
import gr.uom.java.ast.decomposition.cfg.PlainVariable;

import java.util.*;

/**
 * Represents the structural and relational context of a method,
 * extracted from JDeodorant's MethodObject.
 *
 * This class does NOT hold metrics
 * It focuses on what the method is, what it interacts with, and how it relates
 * to other elements (fields, methods, classes).
 */
public class MethodContext {

    /** Identification */
    private final String name;
    private final String className;
    private final String signature;
    private final TypeObject returnType;
    private final Access accessModifier;

    /** Modifiers */
    private final boolean isAbstract;
    private final boolean isStatic;
    private final boolean isSynchronized;
    private final boolean isNative;
    private final boolean isTestMethod;

    /** Parameters and structure */
    private final List<ParameterObject> parameters;
    private final MethodBodyObject body;
    private final List<CommentObject> comments;

    /** Relationships */
    private final List<FieldInstructionObject> accessedFields;
    private final List<MethodInvocationObject> calledMethods;
    private final List<SuperMethodInvocationObject> superCalls;
    private final List<ConstructorInvocationObject> constructorCalls;
    private final List<CreationObject> createdObjects;

    /** Variable-level relationships */
    private final Set<PlainVariable> declaredLocals;
    private final Set<PlainVariable> usedLocals;
    private final Set<PlainVariable> definedLocals;

    /** Behavioral tags */
    private final boolean isGetter;
    private final boolean isSetter;
    private final boolean isDelegate;
    private final boolean isCollectionAdder;
    private final boolean overridesMethod;

    /** Relationships between methods and other entities */
    private final Set<String> invokedExternalClasses;
    private final Set<String> accessedFieldClasses;

    /** Original source */
    private final MethodObject methodObject;

    public MethodContext(MethodObject methodObject) {
        this.methodObject = methodObject;
        this.name = methodObject.getName();
        this.className = methodObject.getClassName();
        this.signature = methodObject.getSignature();
        this.returnType = methodObject.getReturnType();
        this.accessModifier = methodObject.getAccess();

        this.isAbstract = methodObject.isAbstract();
        this.isStatic = methodObject.isStatic();
        this.isSynchronized = methodObject.isSynchronized();
        this.isNative = methodObject.isNative();
        this.isTestMethod = methodObject.hasTestAnnotation();

        this.parameters = collectParameters(methodObject);
        this.comments = collectComments(methodObject);
        this.body = methodObject.getMethodBody();

        this.accessedFields = methodObject.getFieldInstructions();
        this.calledMethods = methodObject.getMethodInvocations();
        this.superCalls = methodObject.getSuperMethodInvocations();
        this.constructorCalls = methodObject.getConstructorInvocations();
        this.createdObjects = methodObject.getCreations();

        this.declaredLocals = methodObject.getDeclaredLocalVariables();
        this.usedLocals = methodObject.getUsedLocalVariables();
        this.definedLocals = methodObject.getDefinedLocalVariables();

        this.isGetter = methodObject.isGetter() != null;
        this.isSetter = methodObject.isSetter() != null;
        this.isCollectionAdder = methodObject.isCollectionAdder() != null;
        this.isDelegate = methodObject.isDelegate() != null;
        this.overridesMethod = methodObject.overridesMethod();

        this.invokedExternalClasses = collectInvokedClasses();
        this.accessedFieldClasses = collectAccessedFieldClasses();
    }

    private List<ParameterObject> collectParameters(MethodObject method) {
        List<ParameterObject> params = new ArrayList<>();
        ListIterator<ParameterObject> it = method.getParameterListIterator();
        while (it.hasNext()) params.add(it.next());
        return params;
    }

    private List<CommentObject> collectComments(MethodObject method) {
        List<CommentObject> result = new ArrayList<>();
        ListIterator<CommentObject> it = method.getCommentListIterator();
        while (it.hasNext()) result.add(it.next());
        return result;
    }

    private Set<String> collectInvokedClasses() {
        Set<String> classes = new HashSet<>();
        for (MethodInvocationObject call : calledMethods) {
            classes.add(call.getOriginClassName());
        }
        return classes;
    }

    private Set<String> collectAccessedFieldClasses() {
        Set<String> classes = new HashSet<>();
        for (FieldInstructionObject field : accessedFields) {
            classes.add(field.getType().getClassType());
        }
        return classes;
    }

    // --- Getters ---

    public String getName() { return name; }
    public String getClassName() { return className; }
    public String getSignature() { return signature; }
    public List<ParameterObject> getParameters() { return parameters; }
    public List<FieldInstructionObject> getAccessedFields() { return accessedFields; }
    public List<MethodInvocationObject> getCalledMethods() { return calledMethods; }
    public boolean isGetter() { return isGetter; }
    public boolean isSetter() { return isSetter; }
    public boolean isDelegate() { return isDelegate; }
    public boolean isCollectionAdder() { return isCollectionAdder; }

    public MethodObject getMethodObject() { return methodObject; }

    @Override
    public String toString() {
        return String.format("%s::%s(%d params)", className, name, parameters.size());
    }
}

