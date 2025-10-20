package gr.uom.java.ast.context;

import gr.uom.java.ast.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.*;
import java.util.Map;
import java.util.Set;

/**
 * Utility class to deeply dump an ObjectContext hierarchy
 * into a text file for inspection and debugging.
 */
public class ContextDumper {

    public static void dump(ObjectContext objectContext, String projectName) {
        Path outputPath = Paths.get(System.getProperty("user.home"), "jdeodorant_context_full_dump.txt");

        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(outputPath))) {
            out.println("=== FULL JDEODORANT CONTEXT DUMP ===");
            out.println("Project: " + projectName);
            out.println("Classes: " + objectContext.getTotalClasses());
            out.println("Methods: " + objectContext.getTotalMethods());
            out.println("Fields: " + objectContext.getTotalFields());
            out.println("====================================\n");

            // --------------------------------------------------
            // Per-Class Details
            // --------------------------------------------------
            for (ClassContext cls : objectContext.getClassContexts()) {
                out.println("CLASS: " + cls.getClassName());
                out.println("  Superclass: " + cls.getSuperclassName());
                out.println("  Interfaces: " + cls.getImplementedInterfaces());
                out.println("  Abstract: " + cls.isAbstract() +
                        ", Interface: " + cls.isInterface() +
                        ", Enum: " + cls.isEnum() +
                        ", Static: " + cls.isStatic());
                out.println();

                // === Fields ===
                out.println("  [FIELDS]");
                for (FieldContext field : cls.getFieldContexts()) {
                    out.println("    - " + field);
                    if (!field.getComments().isEmpty()) {
                        out.println("       Comments:");
                        field.getComments().forEach(c -> out.println("         " + c));
                    }
                }

                // === Methods ===
                out.println("\n  [METHODS]");
                for (MethodContext method : cls.getMethodContexts()) {
                    out.println("    METHOD: " + method.getName() + " :: " + method.getSignature());
                    out.println("      Access: " + method.getMethodObject().getAccess());
                    out.println("      Modifiers: "
                            + (method.getMethodObject().isStatic() ? "static " : "")
                            + (method.getMethodObject().isAbstract() ? "abstract " : "")
                            + (method.getMethodObject().isSynchronized() ? "synchronized " : "")
                            + (method.getMethodObject().isNative() ? "native " : ""));
                    out.println("      Return Type: " + method.getMethodObject().getReturnType());
                    out.println("      Parameters:");
                    for (ParameterObject param : method.getParameters()) {
                        out.println("         - " + param.getType() + " " + param.getName());
                    }

                    // Accessed fields
                    out.println("      Accessed Fields:");
                    for (FieldInstructionObject f : method.getAccessedFields()) {
                        out.println("         -> " + f.getOwnerClass() + "." + f.getName() +
                                " : " + f.getType());
                    }

                    // Called methods
                    out.println("      Called Methods:");
                    for (MethodInvocationObject m : method.getCalledMethods()) {
                        int argCount = 0;
                        try {
                            if (m.getParameterList() != null) {
                                argCount = m.getParameterList().size();
                            } else if (m.getParameterTypeList() != null) {
                                argCount = m.getParameterTypeList().size();
                            }
                        } catch (Throwable ignored) {}
                        out.println("         -> " + m.getOriginClassName() + "." + m.getMethodName()
                                + " (" + argCount + " args)  [signature: " + m.getSignature() + "]");
                    }

                    // Super calls
                    if (!method.getSuperCalls().isEmpty()) {
                        out.println("      Super Calls:");
                        for (SuperMethodInvocationObject sm : method.getSuperCalls()) {
                            out.println("         -> " + sm.getOriginClassName() + "." + sm.getMethodName());
                        }
                    }

                    // Constructor calls
                    if (!method.getConstructorCalls().isEmpty()) {
                        out.println("      Constructor Calls:");
                        for (ConstructorInvocationObject ci : method.getConstructorCalls()) {
                            out.println("         -> " + ci.getOriginClassName() + "." + ci.getType());
                        }
                    }

                    // Created objects
                    if (!method.getCreatedObjects().isEmpty()) {
                        out.println("      Created Objects:");
                        for (CreationObject c : method.getCreatedObjects()) {
                            out.println("         -> new " + c.getType().getClassType());
                        }
                    }

                    // Locals
                    out.println("      Declared Locals: " + method.getDeclaredLocals().size());
                    out.println("      Used Locals: " + method.getUsedLocals().size());
                    out.println("      Defined Locals: " + method.getDefinedLocals().size());

                    // Behavioral flags
                    out.println("      isGetter: " + method.isGetter() +
                            ", isSetter: " + method.isSetter() +
                            ", isDelegate: " + method.isDelegate() +
                            ", isCollectionAdder: " + method.isCollectionAdder());
                    out.println("      Overrides method: " + method.getMethodObject().overridesMethod());

                    // External relationships
                    out.println("      Invoked External Classes: " + method.getInvokedExternalClasses());
                    out.println("      Accessed Field Classes: " + method.getAccessedFieldClasses());
                    out.println();
                }

                // === Metrics ===
                out.println("  [METRICS]");
                out.println("    " + cls.getMetricsContext());
                out.println();
                out.println("----------------------------------------------------------------\n");
            }

            // --------------------------------------------------
            // Global (Project-Level) Dependency Summary
            // --------------------------------------------------
            out.println("\n====================");
            out.println("PROJECT-LEVEL RELATIONSHIPS");
            out.println("====================\n");

            // --- Class Dependencies ---
            out.println("[CLASS DEPENDENCIES]");
            for (Map.Entry<String, Set<String>> entry : objectContext.getClassDependencies().entrySet()) {
                String className = entry.getKey();
                Set<String> deps = entry.getValue();
                out.println("  " + className + " depends on:");
                for (String dep : deps) {
                    out.println("    → " + dep);
                }
            }

            // --- Class Dependents ---
            out.println("\n[CLASS DEPENDENTS]");
            for (Map.Entry<String, Set<String>> entry : objectContext.getClassDependents().entrySet()) {
                String className = entry.getKey();
                Set<String> dependents = entry.getValue();
                out.println("  " + className + " is used by:");
                for (String dep : dependents) {
                    out.println("    ← " + dep);
                }
            }

            // --- Fan-in / Fan-out summary ---
            out.println("\n[FAN-IN / FAN-OUT]");
            for (ClassContext cls : objectContext.getClassContexts()) {
                String name = cls.getClassName();
                int fanIn = objectContext.getFanIn(name);
                int fanOut = objectContext.getFanOut(name);
                out.println("  " + name + "  fanIn=" + fanIn + "  fanOut=" + fanOut);
            }

            out.println("\n=== END OF CONTEXT DUMP ===");
            out.flush();
            System.out.println("[ContextDumper] Full dump saved to " + outputPath);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
