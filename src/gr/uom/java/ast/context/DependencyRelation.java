package gr.uom.java.ast.context;

/**
 * Represents a dependency relationship between classes.
 * A dependency relation captures how one class depends on another class (the target),
 * along with the nature of that dependency (the type).
 * 
 * @see ClassContext
 */
public class DependencyRelation {
    private final ClassContext target;
    private final String type; // e.g., "extends", "implements", "field", "parameter", "return", "method call", "object creation"

    public DependencyRelation(ClassContext target, String type) {
        this.target = target;
        this.type = type;
    }

    public ClassContext getTarget() { return target; }
    public String getType() { return type; }
}
