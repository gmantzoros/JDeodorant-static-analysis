package gr.uom.java.ast.context;

import gr.uom.java.ast.*;
import gr.uom.java.ast.decomposition.cfg.PlainVariable;
import java.io.InputStream;
import java.util.*;
import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.MethodDeclaration;

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
        buildGlobalDependencies(objectContext); // Builds dependencies for all classes

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
            // Attach source code for class and method
            attachSourceCodeForClass(cls, target);
            attachSourceCodeForMethods(cls, target);

            // Add metrics if not already set
            if (target.getMetricsContext() == null)
                target.setMetricsContext(new MetricsContext(cls));

            // Build local relationships only for this class
            buildLocalRelationships(objectContext, target);

            System.out.println("[ContextBuilder] Class enriched successfully: " + target.getClassName());
        } catch (Exception e) {
            System.err.println("[ContextBuilder] Error enriching class: " + target.getClassName());
            e.printStackTrace();
        }
    }

    /**
     * Builds local relationships within a class, including field access and method calls.
     * <p>
     * Analyzes each method in the target class to identify:
     *   Field reads and writes
     *   Internal method calls (within the same class)
     *   External method calls (to other classes)
     *   
     * All relationships are registered bidirectionally between source and target elements.
     *
     * @param objectContext the context containing all classes in the system
     * @param target the class to analyze for local relationships
     */
    private static void buildLocalRelationships(ObjectContext objectContext, ClassContext target) {
        // Map field names for quick lookup
        Map<String, FieldContext> fieldMap = new HashMap<>();
        for (FieldContext f : target.getFieldContexts()) {
            fieldMap.put(f.getFieldObject().getName(), f);
        }

        // Access to all ClassContexts for cross-class calls
        Map<String, ClassContext> classMap = new HashMap<>();
        for (ClassContext ctx : objectContext.getClassContexts()) {
            classMap.put(ctx.getClassName(), ctx);
        }

        // --- Analyze each method ---
        for (MethodContext m : target.getMethodContexts()) {
            MethodObject mo = m.getMethodObject();

            // --- Field reads ---
            for (PlainVariable usedVar : mo.getUsedFieldsThroughThisReference()) {
                FieldContext f = fieldMap.get(usedVar.getVariableName());
                if (f != null) f.addReadByMethod(m);
            }

            // --- Field writes ---
            for (PlainVariable writtenVar : mo.getDefinedFieldsThroughThisReference()) {
                FieldContext f = fieldMap.get(writtenVar.getVariableName());
                if (f != null) f.addWrittenByMethod(m);
            }

            // --- Method calls (local + external) ---
            for (MethodInvocationObject mio : mo.getMethodInvocations()) {
                String originClass = mio.getOriginClassName();

                // Case 1: No origin (Same class)
                if (originClass == null || originClass.equals(target.getClassName())) {
                    for (MethodContext targetMethod : target.getMethodContexts()) {
                        if (targetMethod.getMethodObject().getSignature().equals(mio.getSignature())) {
                            // Symmetric internal link
                            m.getCalledMethods().add(targetMethod);
                            targetMethod.getCallerMethods().add(m);
                            break;
                        }
                    }
                }

                // Case 2: External class call
                else {
                    ClassContext externalClassCtx = classMap.get(originClass);
                    if (externalClassCtx != null && !originClass.equals(target.getClassName())) {
                        for (MethodContext targetMethod : externalClassCtx.getMethodContexts()) {
                            if (targetMethod.getMethodObject().getSignature().equals(mio.getSignature())) {
                                // --- Symmetric cross-class link ---
                                m.getCalledMethods().add(targetMethod);
                                targetMethod.getCallerMethods().add(m);
                                break;
                            }
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Builds a bidirectional dependency graph for all classes in the system.
     *
     * Analyzes each class to identify dependencies through inheritance, fields, 
     * method signatures, method calls, and object creation. Each dependency is 
     * categorized by type and registered bidirectionally between source and target classes.
     *
     * @param objectContext the context containing all classes to analyze; 
     *                      if null, the method returns immediately
     */
    public static void buildGlobalDependencies(ObjectContext objectContext) {
        if (objectContext == null) return;

        SystemObject system = objectContext.getSystemObject();
        Map<String, ClassContext> classMap = new HashMap<>();
        for (ClassContext ctx : objectContext.getClassContexts()) {
            classMap.put(ctx.getClassName(), ctx);
        }

        for (ClassContext sourceCtx : objectContext.getClassContexts()) {
            ClassObject cls = sourceCtx.getClassObject();

            // Each target class maps to a set of unique dependency types
            Map<String, Set<String>> depTypes = new LinkedHashMap<>();

            // --- Inheritance ---
            if (cls.getSuperclass() != null)
                depTypes.computeIfAbsent(cls.getSuperclass().getClassType(), k -> new LinkedHashSet<>()).add("extends");

            ListIterator<TypeObject> itf = cls.getInterfaceIterator();
            while (itf.hasNext()) {
                depTypes.computeIfAbsent(itf.next().getClassType(), k -> new LinkedHashSet<>()).add("implements");
            }

            // --- Fields ---
            ListIterator<FieldObject> fields = cls.getFieldIterator();
            while (fields.hasNext()) {
                FieldObject f = fields.next();
                if (f.getType() != null)
                    depTypes.computeIfAbsent(f.getType().getClassType(), k -> new LinkedHashSet<>()).add("field");
            }

            // --- Methods ---
            ListIterator<MethodObject> methods = cls.getMethodIterator();
            while (methods.hasNext()) {
                MethodObject m = methods.next();

                if (m.getReturnType() != null)
                    depTypes.computeIfAbsent(m.getReturnType().getClassType(), k -> new LinkedHashSet<>()).add("return type");

                ListIterator<ParameterObject> params = m.getParameterListIterator();
                while (params.hasNext()) {
                    ParameterObject p = params.next();
                    if (p.getType() != null)
                        depTypes.computeIfAbsent(p.getType().getClassType(), k -> new LinkedHashSet<>()).add("parameter");
                }

                for (MethodInvocationObject mio : m.getMethodInvocations()) {
                    String origin = mio.getOriginClassName();
                    if (origin != null && !origin.equals(cls.getName())) {
                        depTypes.computeIfAbsent(origin, k -> new LinkedHashSet<>()).add("method call");
                    }
                }

                for (CreationObject c : m.getCreations()) {
                    if (c.getType() != null)
                        depTypes.computeIfAbsent(c.getType().getClassType(), k -> new LinkedHashSet<>()).add("object creation");
                }
            }

            // --- Register unique relationships ---
            for (Map.Entry<String, Set<String>> entry : depTypes.entrySet()) {
                String depName = entry.getKey();
                ClassContext targetCtx = classMap.get(depName);

                if (targetCtx != null && !depName.equals(sourceCtx.getClassName())) {
                    for (String type : entry.getValue()) {
                        boolean alreadyExists = sourceCtx.getDependencyRelations().stream()
                            .anyMatch(r -> r.getTarget() == targetCtx && r.getType().equals(type));
                        if (!alreadyExists) {
                            sourceCtx.addDependency(targetCtx, type);
                            targetCtx.addDependent(sourceCtx, type);
                        }
                    }
                }
            }
        }

        System.out.println("[ContextBuilder] Built global dependency graph for all classes (deduplicated per type).");
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
    
    private static void attachSourceCodeForMethods(ClassObject classObj, ClassContext ctx) {
        String classSource = ctx.getSourceCode();
        if (classSource == null) return; // No point continuing if class source is missing

        try {
            for (MethodContext methodCtx : ctx.getMethodContexts()) {
                MethodObject methodObj = methodCtx.getMethodObject();
                MethodDeclaration decl = methodObj.getMethodDeclaration();

                if (decl != null) {
                    int start = decl.getStartPosition();
                    int end = start + decl.getLength();

                    if (start >= 0 && end <= classSource.length()) {
                        String snippet = classSource.substring(start, end);
                        methodCtx.setSourceCode(snippet);
                    } else {
                        methodCtx.setSourceCode("[Source unavailable]");
                    }
                } else {
                    methodCtx.setSourceCode("[No MethodDeclaration]");
                }
            }

            System.out.println("[ContextBuilder] Attached method-level source for class: " + ctx.getClassName());
        } catch (Exception e) {
            System.err.println("[ContextBuilder] Failed to attach method sources for " + ctx.getClassName());
            e.printStackTrace();
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
