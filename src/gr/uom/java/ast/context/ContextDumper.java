package gr.uom.java.ast.context;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.*;

/**
 * Utility class that dumps the relationship graph from ObjectContext
 * into a text file for debugging and analysis.
 *
 * - Focuses on relationships between classes, methods, and fields
 * - Includes metrics (fan-in/out, LCOM, CBO, Connectivity, etc.)
 * - Displays field read/write access details
 */
public class ContextDumper {

    public static void dump(ObjectContext objectContext, String projectName) {
        Path outputPath = Paths.get(System.getProperty("user.home"), "jdeodorant_context_relationships_dump.txt");

        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(outputPath))) {
            out.println("=== JDEODORANT RELATIONSHIP DUMP ===");
            out.println("Project: " + projectName);
            out.println("Classes: " + objectContext.getTotalClasses());
            out.println("Methods: " + objectContext.getTotalMethods());
            out.println("Fields: " + objectContext.getTotalFields());
            out.println("====================================\n");

            // --------------------------------------------------
            // Per-Class Relationships
            // --------------------------------------------------
            for (ClassContext cls : objectContext.getClassContexts()) {
                out.println("CLASS: " + cls.getClassName());

                // --- Metrics section ---
                MetricsContext metrics = cls.getMetricsContext();
                if (metrics != null) {
                    out.println("  [Metrics]");
                    out.println("    - NOM  (Number of Methods):      " + metrics.getNom());
                    out.println("    - NOC  (Number of Children):     " + metrics.getNoc());
                    out.println("    - CBO  (Coupling Between Obj.):  " + String.format("%.3f", metrics.getCbo()));
                    out.println("    - LCOM (Lack of Cohesion):       " + String.format("%.3f", metrics.getLcom()));
                    out.println("    - CONN (Connectivity Metric):    " + String.format("%.3f", metrics.getConnectivity()));
                    out.println("    - Fan-In (Dependents):           " + metrics.getFanIn());
                    out.println("    - Fan-Out (Dependencies):        " + metrics.getFanOut());
                }

                // --- Class dependencies ---
                out.println("  Depends on (" + cls.getDependencyClasses().size() + "):");
                for (ClassContext dep : cls.getDependencyClasses()) {
                    out.println("    → " + dep.getClassName());
                }

                out.println("  Depended by (" + cls.getDependentClasses().size() + "):");
                for (ClassContext dep : cls.getDependentClasses()) {
                    out.println("    ← " + dep.getClassName());
                }

                // --- Fields ---
                if (!cls.getFieldContexts().isEmpty()) {
                    out.println("  Fields:");
                    for (FieldContext field : cls.getFieldContexts()) {
                        out.println("    - " + field.getFieldObject().getName());

                        if (!field.getReadByMethods().isEmpty()) {
                            out.println("       Read by methods:");
                            for (MethodContext m : field.getReadByMethods()) {
                                out.println("         ← [R] " + m.getMethodObject().getClassName() + "." + m.getMethodObject().getName());
                            }
                        }

                        if (!field.getWrittenByMethods().isEmpty()) {
                            out.println("       Written by methods:");
                            for (MethodContext m : field.getWrittenByMethods()) {
                                out.println("         ← [W] " + m.getMethodObject().getClassName() + "." + m.getMethodObject().getName());
                            }
                        }
                    }
                }

                // --- Methods ---
                if (!cls.getMethodContexts().isEmpty()) {
                    out.println("\n  Methods:");
                    for (MethodContext method : cls.getMethodContexts()) {
                        String methodName = method.getMethodObject().getName();
                        out.println("    METHOD: " + methodName);

                        if (!method.getCalledMethods().isEmpty()) {
                            out.println("      Calls (" + method.getCalledMethods().size() + "):");
                            for (MethodContext called : method.getCalledMethods()) {
                                out.println("         → " + called.getMethodObject().getClassName() + "." + called.getMethodObject().getName());
                            }
                        }

                        if (!method.getCallerMethods().isEmpty()) {
                            out.println("      Called by (" + method.getCallerMethods().size() + "):");
                            for (MethodContext caller : method.getCallerMethods()) {
                                out.println("         ← " + caller.getMethodObject().getClassName() + "." + caller.getMethodObject().getName());
                            }
                        }
                    }
                }

                out.println("----------------------------------------------------------------\n");
            }

            // --------------------------------------------------
            // Global Summary
            // --------------------------------------------------
            out.println("\n====================");
            out.println("PROJECT RELATIONSHIP SUMMARY");
            out.println("====================\n");

            out.println("Total Classes: " + objectContext.getTotalClasses());
            out.println("Total Methods: " + objectContext.getTotalMethods());
            out.println("Total Fields: " + objectContext.getTotalFields());
            out.println("\n=== END OF RELATIONSHIP DUMP ===");
            out.flush();

            System.out.println("[ContextDumper] Relationship dump saved to " + outputPath);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
