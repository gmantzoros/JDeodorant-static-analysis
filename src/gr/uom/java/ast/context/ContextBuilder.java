package gr.uom.java.ast.context;

import gr.uom.java.ast.*;
import java.io.InputStream;
import org.eclipse.core.resources.IFile;
import org.eclipse.jdt.core.ITypeRoot;

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

    public static void attachSourceCode(SystemObject system, ObjectContext context) {
        for (ClassContext ctx : context.getClassContexts()) {
            ClassObject classObj = system.getClassObject(ctx.getClassName());
            if (classObj == null) continue;

            // Preferred: JDT buffer from ITypeRoot
            try {
                ITypeRoot typeRoot = classObj.getITypeRoot();
                if (typeRoot != null && typeRoot.getBuffer() != null) {
                    String src = typeRoot.getBuffer().getContents();
                    if (src != null) {
                        ctx.setSourceCode(src);
                        continue; // done for this class
                    }
                }
            } catch (Exception ignore) {
                // fall through to IFile
            }

            // Fallback: read contents from the IFile
            try {
                IFile file = classObj.getIFile();
                if (file != null && file.exists()) {
                    try (InputStream in = file.getContents(true)) {
                        String src = new String(readAllBytesCompat(in), java.nio.charset.StandardCharsets.UTF_8);
                        ctx.setSourceCode(src);
                    }
                }
            } catch (Exception e) {
                System.err.println("[ContextBuilder] Failed to read source for class: " + ctx.getClassName());
            }
        }
    }

    // Java 8–compatible readAllBytes for plug-ins
    private static byte[] readAllBytesCompat(InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            baos.write(buf, 0, n);
        }
        return baos.toByteArray();
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

