package gr.uom.java.ast.context;

import gr.uom.java.ast.*;
import java.util.*;

/**
 * ObjectContext provides a lightweight container for all contextual information
 * derived from JDeodorant’s AST analysis.
 */
public class ObjectContext {

    private final SystemObject systemObject;
    private List<ClassContext> classContexts;

    public ObjectContext(SystemObject systemObject) {
        this.systemObject = systemObject;
        this.classContexts = new ArrayList<>();
    }

    // --- Setters (used by ContextBuilder) ---

    public void setClassContexts(List<ClassContext> classContexts) {
        this.classContexts = classContexts;
    }

    // --- Getters ---

    public SystemObject getSystemObject() {
        return systemObject;
    }

    public List<ClassContext> getClassContexts() {
        return Collections.unmodifiableList(classContexts);
    }

    // --- Convenience methods ---

    public int getTotalClasses() {
        return classContexts.size();
    }

    public int getTotalMethods() {
        return classContexts.stream()
                .mapToInt(c -> c.getMethodContexts().size())
                .sum();
    }

    public int getTotalFields() {
        return classContexts.stream()
                .mapToInt(c -> c.getFieldContexts().size())
                .sum();
    }

    @Override
    public String toString() {
        return "ObjectContext[" +
                "classes=" + getTotalClasses() +
                ", methods=" + getTotalMethods() +
                ", fields=" + getTotalFields() +
                "]";
    }
}
