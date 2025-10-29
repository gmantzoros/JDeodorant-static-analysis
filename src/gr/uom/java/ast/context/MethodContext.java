package gr.uom.java.ast.context;

import gr.uom.java.ast.MethodObject;
import java.util.HashSet;
import java.util.Set;

/**
 * A representation of a method that captures only
 * inter-method relationships while delegating all other details
 * to MethodObject.
 */
public class MethodContext {

    private final MethodObject methodObject;
    private final Set<MethodContext> calledMethods = new HashSet<>();
    private final Set<MethodContext> callerMethods = new HashSet<>();

    public MethodContext(MethodObject methodObject) {
        this.methodObject = methodObject;
    }

    public MethodObject getMethodObject() {
        return methodObject;
    }

    public Set<MethodContext> getCalledMethods() {
        return calledMethods;
    }

    public Set<MethodContext> getCallerMethods() {
        return callerMethods;
    }

    @Override
    public String toString() {
        return methodObject.getClassName() + "." + methodObject.getName();
    }
}
