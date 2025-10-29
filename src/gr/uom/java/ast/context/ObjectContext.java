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

    // Optional dependency maps (can be filled by builder later)
    private final Map<String, Set<String>> classDependencies;
    private final Map<String, Set<String>> classDependents;
    private final Map<String, Integer> fanInMap;
    private final Map<String, Integer> fanOutMap;

    public ObjectContext(SystemObject systemObject) {
        this.systemObject = systemObject;
        this.classContexts = new ArrayList<>();
        this.classDependencies = new HashMap<>();
        this.classDependents = new HashMap<>();
        this.fanInMap = new HashMap<>();
        this.fanOutMap = new HashMap<>();
    }

    // --- Setters (used by ContextBuilder) ---

    public void setClassContexts(List<ClassContext> classContexts) {
        this.classContexts = classContexts;
    }

    public void setClassDependencies(Map<String, Set<String>> classDependencies) {
        this.classDependencies.clear();
        this.classDependencies.putAll(classDependencies);
    }

    public void setClassDependents(Map<String, Set<String>> classDependents) {
        this.classDependents.clear();
        this.classDependents.putAll(classDependents);
    }

    public void setFanInMap(Map<String, Integer> fanInMap) {
        this.fanInMap.clear();
        this.fanInMap.putAll(fanInMap);
    }

    public void setFanOutMap(Map<String, Integer> fanOutMap) {
        this.fanOutMap.clear();
        this.fanOutMap.putAll(fanOutMap);
    }

    // --- Getters ---

    public SystemObject getSystemObject() {
        return systemObject;
    }

    public List<ClassContext> getClassContexts() {
        return Collections.unmodifiableList(classContexts);
    }

    public Map<String, Set<String>> getClassDependencies() {
        return Collections.unmodifiableMap(classDependencies);
    }

    public Map<String, Set<String>> getClassDependents() {
        return Collections.unmodifiableMap(classDependents);
    }

    public Map<String, Integer> getFanInMap() {
        return Collections.unmodifiableMap(fanInMap);
    }

    public Map<String, Integer> getFanOutMap() {
        return Collections.unmodifiableMap(fanOutMap);
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
