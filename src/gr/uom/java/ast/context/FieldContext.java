package gr.uom.java.ast.context;

import gr.uom.java.ast.FieldObject;
import java.util.*;

/**
 * A wrapper around JDeodorant's FieldObject.
 * This class only stores relationships to other contextual elements
 * (readByMethods, writterByMethods).
 */
public class FieldContext {

    private final FieldObject fieldObject;
    private final Set<MethodContext> readByMethods = new HashSet<>();
    private final Set<MethodContext> writtenByMethods = new HashSet<>();

    public FieldContext(FieldObject fieldObject) {
        this.fieldObject = fieldObject;
    }

    public FieldObject getFieldObject() {
        return fieldObject;
    }

    public Set<MethodContext> getReadByMethods() {
        return readByMethods;
    }

    public Set<MethodContext> getWrittenByMethods() {
        return writtenByMethods;
    }

    public void addReadByMethod(MethodContext method) {
        readByMethods.add(method);
    }

    public void addWrittenByMethod(MethodContext method) {
        writtenByMethods.add(method);
    }

    @Override
    public String toString() {
        return "FieldContext[" + fieldObject.getName() + "]";
    }
}
