package gr.uom.java.ast.context;

import gr.uom.java.ast.*;
import java.util.*;

/**
 * ObjectContext provides a global view of a system analyzed by JDeodorant.
 * It aggregates ClassContext objects and captures inter-class relationships,
 * allowing higher-level reasoning about dependencies and refactoring opportunities.
 */
public class ObjectContext {

    private final SystemObject systemObject;
    private final List<ClassContext> classContexts;
    private final Map<String, Set<String>> classDependencies; // class -> classes it depends on
    private final Map<String, Set<String>> classDependents;   // class -> classes depending on it
    private final Map<String, Integer> fanInMap;
    private final Map<String, Integer> fanOutMap;

    private final int totalClasses;
    private final int totalMethods;
    private final int totalFields;

    public ObjectContext(SystemObject systemObject) {
        this.systemObject = systemObject;
        this.classContexts = new ArrayList<>();
        this.classDependencies = new HashMap<>();
        this.classDependents = new HashMap<>();
        this.fanInMap = new HashMap<>();
        this.fanOutMap = new HashMap<>();

        extractClassContexts();
        analyzeDependencies();

        // Inject fan-in/out values into each class metrics
        for (ClassContext ctx : classContexts) {
            MetricsContext metrics = ctx.getMetricsContext();
            metrics.setFanIn(getFanIn(ctx.getClassName()));
            metrics.setFanOut(getFanOut(ctx.getClassName()));
        }

        this.totalClasses = classContexts.size();
        this.totalMethods = classContexts.stream()
                .mapToInt(c -> c.getMethodContexts().size())
                .sum();
        this.totalFields = classContexts.stream()
                .mapToInt(c -> c.getFieldContexts().size())
                .sum();
    }

    // --- Extraction phase ---

    private void extractClassContexts() {
        for (ClassObject cls : systemObject.getClassObjects()) {
            ClassContext ctx = new ClassContext(cls);
            classContexts.add(ctx);
        }
    }

    // --- Relationship analysis ---

    private void analyzeDependencies() {
        for (ClassObject source : systemObject.getClassObjects()) {
            String sourceName = source.getName();
            Set<String> deps = new HashSet<>();

            // Add superclass and interfaces
            if (source.getSuperclass() != null)
                deps.add(source.getSuperclass().getClassType());
            ListIterator<TypeObject> itfIter = source.getInterfaceIterator();
            while (itfIter.hasNext())
                deps.add(itfIter.next().getClassType());

            // Check field types
            ListIterator<FieldObject> fieldIt = source.getFieldIterator();
            while (fieldIt.hasNext()) {
                FieldObject f = fieldIt.next();
                deps.add(f.getType().getClassType());
            }

            // Check method parameter and return types
            ListIterator<MethodObject> methodIt = source.getMethodIterator();
            while (methodIt.hasNext()) {
                MethodObject m = methodIt.next();
                if (m.getReturnType() != null)
                    deps.add(m.getReturnType().getClassType());
                ListIterator<ParameterObject> paramIt = m.getParameterListIterator();
                while (paramIt.hasNext())
                    deps.add(paramIt.next().getType().getClassType());
            }

            // Record dependencies
            classDependencies.put(sourceName, deps);
            for (String target : deps) {
                classDependents.computeIfAbsent(target, k -> new HashSet<>()).add(sourceName);
            }
        }

        // Compute fan-in and fan-out
        for (String className : classDependencies.keySet()) {
            Set<String> deps = classDependencies.getOrDefault(className, Collections.emptySet());
            fanOutMap.put(className, deps.size());
        }
        for (String className : classDependents.keySet()) {
            Set<String> dependents = classDependents.getOrDefault(className, Collections.emptySet());
            fanInMap.put(className, dependents.size());
        }
    }

    // --- Getters ---

    public List<ClassContext> getClassContexts() {
        return Collections.unmodifiableList(classContexts);
    }

    public Map<String, Set<String>> getClassDependencies() {
        return Collections.unmodifiableMap(classDependencies);
    }

    public Map<String, Set<String>> getClassDependents() {
        return Collections.unmodifiableMap(classDependents);
    }

    public int getFanIn(String className) {
        return fanInMap.getOrDefault(className, 0);
    }

    public int getFanOut(String className) {
        return fanOutMap.getOrDefault(className, 0);
    }

    public int getTotalClasses() {
        return totalClasses;
    }

    public int getTotalMethods() {
        return totalMethods;
    }

    public int getTotalFields() {
        return totalFields;
    }

    @Override
    public String toString() {
        return "ObjectContext[" +
                "classes=" + totalClasses +
                ", methods=" + totalMethods +
                ", fields=" + totalFields +
                "]";
    }
}

