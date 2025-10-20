package gr.uom.java.ast.context;

import gr.uom.java.ast.*;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.eclipse.core.resources.IFile;

/**
 * Helper class that builds an ObjectContext from the currently loaded
 * JDeodorant SystemObject. Designed for testing and debugging before
 * integrating into an Eclipse Action.
 */
public class ContextBuilder {

    /**
     * Builds and populates the ObjectContext for the currently loaded project.
     * @return the populated ObjectContext, or null if no system is loaded.
     */
    public static ObjectContext buildCurrentProjectContext() {
        SystemObject system = ASTReader.getSystemObject();
        if (system == null) {
            System.out.println("[ContextBuilder] No SystemObject loaded. Run JDeodorant analysis first.");
            return null;
        }

        System.out.println("[ContextBuilder] Loaded " + system.getClassNumber() + " classes from active project.");

        ObjectContext objectContext = new ObjectContext(system);

        // attach source code for each class
        attachSourceCode(system, objectContext);

        System.out.println("[ContextBuilder] Context built successfully!");
        System.out.println(objectContext);

        return objectContext;
    }

    private static void attachSourceCode(SystemObject system, ObjectContext context) {
        for (ClassContext ctx : context.getClassContexts()) {
            ClassObject classObj = system.getClassObject(ctx.getClassName());
            if (classObj != null && classObj.getIFile() != null) {
                try (InputStream input = classObj.getIFile().getContents()) {
                    String source = new String(((IFile) input).readAllBytes(), StandardCharsets.UTF_8);
                    ctx.setSourceCode(source);
                } catch (Exception e) {
                    System.err.println("[ContextBuilder] Failed to read source for class: " + ctx.getClassName());
                }
            }
        }
    }

    /**
     * Simple entry point for testing when running JDeodorant as Eclipse app.
     */
    public static void testBuild() {
        ObjectContext context = buildCurrentProjectContext();
        if (context == null) {
            System.out.println("❌ Failed to build context (no project loaded or analyzed).");
            return;
        }

        for (ClassContext cls : context.getClassContexts()) {
            System.out.println("Class Name" + cls.getClassName());
            System.out.println("   Metrics: " + cls.getMetricsContext());
            System.out.println("   Methods: " + cls.getMethodContexts().size());
            System.out.println("   Fields: " + cls.getFieldContexts().size());
        }
    }
}

