package gr.uom.java.ast.context;

import gr.uom.java.ast.*;
import gr.uom.java.ast.decomposition.cfg.PlainVariable;
import java.io.InputStream;
import java.util.*;
import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.*;
import org.eclipse.jdt.core.*;

/**
 * Lazily builds a high-level ObjectContext model from JDeodorant's AST representation.
 * - Initial build parses classes only (fast)
 * - Relationships, metrics, and source code are computed on demand
 */
public class ContextBuilder {

    /**
     * Builds the initial ObjectContext for a given Java project.
     * Only parses classes and creates minimal contexts (lazy approach).
     */
    public static ObjectContext buildProjectContext(IJavaProject javaProject) {
        if (javaProject == null) {
            System.out.println("[ContextBuilder] Provided Java project is null.");
            return null;
        }

        SystemObject system = null;

        try {
            System.out.println("[ContextBuilder] Starting LAZY AST build for: " + javaProject.getElementName());
            logProjectStructure(javaProject);

            ASTReader reader = new ASTReader(javaProject, new NullProgressMonitor());
            system = ASTReader.getSystemObject();

            if (system == null || system.getClassNumber() == 0) {
                System.out.println("[ContextBuilder] ASTReader returned no classes for project: " + javaProject.getElementName());
                return null;
            }

            System.out.println("[ContextBuilder] Parsed " + system.getClassNumber() + " classes successfully.");
        } catch (Exception e) {
            System.err.println("[ContextBuilder] Exception while building AST: " + e.getMessage());
            e.printStackTrace();
            return null;
        }

        ObjectContext objectContext = new ObjectContext(system);
        buildClassContexts(objectContext); // only build minimal class list

        System.out.println("[ContextBuilder] Created " + objectContext.getClassContexts().size() + " ClassContexts (lazy).");
        return objectContext;
    }

    // -----------------------------------------------------------------------
    // LAZY ENRICHMENT
    // -----------------------------------------------------------------------

    /**
     * Enriches a single ClassContext on demand — computes metrics, relationships, and source code.
     */
    public static void enrichClassContext(ObjectContext objectContext, ClassContext target) {
        if (objectContext == null || target == null) return;

        SystemObject system = objectContext.getSystemObject();
        ClassObject cls = system.getClassObject(target.getClassName());
        if (cls == null) return;

        System.out.println("[ContextBuilder] Enriching class: " + target.getClassName());

        try {
            // Attach source code
            attachSourceCodeForClass(cls, target);

            // Add metrics if not already set
            if (target.getMetricsContext() == null)
                target.setMetricsContext(new MetricsContext(cls));

            // Build local relationships only for this class
            buildLocalRelationships(objectContext, target);

            // Compute fan-in/out metrics for dependencies
            computeLocalDependencies(objectContext, target);

            System.out.println("[ContextBuilder] Class enriched successfully: " + target.getClassName());
        } catch (Exception e) {
            System.err.println("[ContextBuilder] Error enriching class: " + target.getClassName());
            e.printStackTrace();
        }
    }

    private static void buildLocalRelationships(ObjectContext objectContext, ClassContext target) {
        // Map field names for quick lookup
        Map<String, FieldContext> fieldMap = new HashMap<>();
        for (FieldContext f : target.getFieldContexts()) {
            fieldMap.put(f.getFieldObject().getName(), f);
        }

        // Method-level relationships
        for (MethodContext m : target.getMethodContexts()) {
            MethodObject mo = m.getMethodObject();

            // Field reads
            for (PlainVariable usedVar : mo.getUsedFieldsThroughThisReference()) {
                FieldContext f = fieldMap.get(usedVar.getVariableName());
                if (f != null) f.addReadByMethod(m);
            }

            // Field writes
            for (PlainVariable writtenVar : mo.getDefinedFieldsThroughThisReference()) {
                FieldContext f = fieldMap.get(writtenVar.getVariableName());
                if (f != null) f.addWrittenByMethod(m);
            }

            // Local method calls
            for (MethodInvocationObject mio : mo.getMethodInvocations()) {
                for (MethodContext targetMethod : target.getMethodContexts()) {
                    if (targetMethod.getMethodObject().getSignature().equals(mio.getSignature())) {
                        m.getCalledMethods().add(targetMethod);
                        targetMethod.getCallerMethods().add(m);
                    }
                }
            }
        }
    }

    private static void computeLocalDependencies(ObjectContext objectContext, ClassContext target) {
        ClassObject cls = target.getClassObject();
        Set<String> deps = new HashSet<>();

        if (cls.getSuperclass() != null)
            deps.add(cls.getSuperclass().getClassType());

        ListIterator<TypeObject> itf = cls.getInterfaceIterator();
        while (itf.hasNext())
            deps.add(itf.next().getClassType());

        ListIterator<FieldObject> fields = cls.getFieldIterator();
        while (fields.hasNext()) {
            FieldObject f = fields.next();
            if (f.getType() != null)
                deps.add(f.getType().getClassType());
        }

        ListIterator<MethodObject> methods = cls.getMethodIterator();
        while (methods.hasNext()) {
            MethodObject m = methods.next();
            if (m.getReturnType() != null)
                deps.add(m.getReturnType().getClassType());

            ListIterator<ParameterObject> params = m.getParameterListIterator();
            while (params.hasNext()) {
                ParameterObject p = params.next();
                if (p.getType() != null)
                    deps.add(p.getType().getClassType());
            }

            for (MethodInvocationObject mio : m.getMethodInvocations()) {
                if (mio.getOriginClassName() != null && !mio.getOriginClassName().equals(cls.getName()))
                    deps.add(mio.getOriginClassName());
            }

            for (CreationObject c : m.getCreations()) {
                if (c.getType() != null)
                    deps.add(c.getType().getClassType());
            }
        }

        // Link to other known ClassContexts
        Map<String, ClassContext> classMap = new HashMap<>();
        for (ClassContext ctx : objectContext.getClassContexts()) {
            classMap.put(ctx.getClassName(), ctx);
        }

        for (String depName : deps) {
            ClassContext depCtx = classMap.get(depName);
            if (depCtx != null && !depName.equals(target.getClassName())) {
                target.addDependency(depCtx);
                depCtx.addDependent(target);
            }
        }

        MetricsContext metrics = target.getMetricsContext();
        if (metrics != null) {
            metrics.setFanIn(target.getDependentClasses().size());
            metrics.setFanOut(target.getDependencyClasses().size());
        }
    }

    // -----------------------------------------------------------------------
    // BASIC CONTEXT INITIALIZATION
    // -----------------------------------------------------------------------

    private static void buildClassContexts(ObjectContext objectContext) {
        SystemObject system = objectContext.getSystemObject();
        Set<ClassObject> classes = system.getClassObjects();
        List<ClassContext> classContexts = new ArrayList<>();

        for (ClassObject cls : classes) {
            ClassContext classCtx = new ClassContext(cls);

            ListIterator<FieldObject> fieldIterator = cls.getFieldIterator();
            while (fieldIterator.hasNext()) {
                classCtx.getFieldContexts().add(new FieldContext(fieldIterator.next()));
            }

            for (MethodObject method : cls.getMethodList()) {
                classCtx.getMethodContexts().add(new MethodContext(method));
            }

            // NOTE: Metrics and relationships will be filled lazily
            classContexts.add(classCtx);
        }

        objectContext.setClassContexts(classContexts);
    }

    // -----------------------------------------------------------------------
    // SOURCE CODE
    // -----------------------------------------------------------------------

    private static void attachSourceCodeForClass(ClassObject classObj, ClassContext ctx) {
        try {
            ITypeRoot typeRoot = classObj.getITypeRoot();
            if (typeRoot != null && typeRoot.getBuffer() != null) {
                String src = typeRoot.getBuffer().getContents();
                if (src != null) {
                    ctx.setSourceCode(src);
                    return;
                }
            }
        } catch (Exception ignore) {}

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

    // -----------------------------------------------------------------------
    // UTILITIES
    // -----------------------------------------------------------------------

    private static void logProjectStructure(IJavaProject javaProject) {
        try {
            System.out.println("[ContextBuilder] Inspecting project structure...");
            for (IPackageFragmentRoot root : javaProject.getPackageFragmentRoots()) {
                if (root.getKind() == IPackageFragmentRoot.K_SOURCE) {
                    System.out.println("   SOURCE ROOT: " + root.getPath());
                    for (IJavaElement pkg : root.getChildren()) {
                        if (pkg instanceof IPackageFragment) {
                            IPackageFragment fragment = (IPackageFragment) pkg;
                            for (ICompilationUnit unit : fragment.getCompilationUnits()) {
                                System.out.println("      Found unit: " + unit.getElementName());
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[ContextBuilder] Failed to list project structure: " + e.getMessage());
        }
    }

    private static byte[] readAllBytesCompat(InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            baos.write(buf, 0, n);
        }
        return baos.toByteArray();
    }
}
