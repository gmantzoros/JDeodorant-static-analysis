package gr.uom.java.ast.context;

import gr.uom.java.ast.*;
import gr.uom.java.ast.decomposition.cfg.PlainVariable;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;
import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.MethodDeclaration;

public class ContextBuilder {

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
        buildClassContexts(objectContext);
        buildGlobalDependencies(objectContext);

        System.out.println("[ContextBuilder] Created " + objectContext.getClassContexts().size() + " ClassContexts (lazy).");
        return objectContext;
    }

    // --------------------------------------------------------------------
    // LAZY ENRICH
    // --------------------------------------------------------------------

    public static void enrichClassContext(ObjectContext objectContext, ClassContext target) {
        if (objectContext == null || target == null) return;

        SystemObject system = objectContext.getSystemObject();
        ClassObject cls = system.getClassObject(target.getClassName());
        if (cls == null) return;

        System.out.println("[ContextBuilder] Enriching class: " + target.getClassName());

        try {
            attachSourceCodeForClass(cls, target);
            attachSourceCodeForMethods(cls, target);

            if (target.getMetricsContext() == null)
                target.setMetricsContext(new MetricsContext(cls));

            buildLocalRelationships(objectContext, target);

            // NEW: compute workflow roots
            computeWorkflowRootAnalysis(target);

            System.out.println("[ContextBuilder] Class enriched successfully: " + target.getClassName());
        } catch (Exception e) {
            System.err.println("[ContextBuilder] Error enriching class: " + target.getClassName());
            e.printStackTrace();
        }
    }

    // --------------------------------------------------------------------
    // LOCAL RELATIONSHIPS (calls + fields)
    // --------------------------------------------------------------------

    private static void buildLocalRelationships(ObjectContext objectContext, ClassContext target) {
        Map<String, FieldContext> fieldMap = new HashMap<>();
        for (FieldContext f : target.getFieldContexts()) {
            fieldMap.put(f.getFieldObject().getName(), f);
        }

        Map<String, ClassContext> classMap = new HashMap<>();
        for (ClassContext ctx : objectContext.getClassContexts()) {
            classMap.put(ctx.getClassName(), ctx);
        }

        for (MethodContext m : target.getMethodContexts()) {
            MethodObject mo = m.getMethodObject();

            for (PlainVariable usedVar : mo.getUsedFieldsThroughThisReference()) {
                FieldContext f = fieldMap.get(usedVar.getVariableName());
                if (f != null) f.addReadByMethod(m);
            }

            for (PlainVariable writtenVar : mo.getDefinedFieldsThroughThisReference()) {
                FieldContext f = fieldMap.get(writtenVar.getVariableName());
                if (f != null) f.addWrittenByMethod(m);
            }

            for (MethodInvocationObject mio : mo.getMethodInvocations()) {
                String originClass = mio.getOriginClassName();

                // internal call
                if (originClass == null || originClass.equals(target.getClassName())) {
                    for (MethodContext targetMethod : target.getMethodContexts()) {
                        if (targetMethod.getMethodObject().getSignature().equals(mio.getSignature())) {

                            m.getCalledMethods().add(targetMethod);
                            targetMethod.getCallerMethods().add(m);
                            break;
                        }
                    }
                }

                // external call
                else {
                    ClassContext externalClass = classMap.get(originClass);
                    if (externalClass != null && !originClass.equals(target.getClassName())) {
                        for (MethodContext targetMethod : externalClass.getMethodContexts()) {
                            if (targetMethod.getMethodObject().getSignature().equals(mio.getSignature())) {

                                m.getCalledMethods().add(targetMethod);
                                targetMethod.getCallerMethods().add(m);

                                //mark external caller
                                targetMethod.getExternalCallers().add(m);

                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    // --------------------------------------------------------------------
    // WORKFLOW ROOT ANALYSIS 
    // --------------------------------------------------------------------

    private static void computeWorkflowRootAnalysis(ClassContext classCtx) {
        // Compute workflow roots for each method
        for (MethodContext m : classCtx.getMethodContexts()) {
            Set<MethodContext> roots = new HashSet<>();
            findWorkflowRootsRecursive(m, roots);
            m.setWorkflowRoots(roots);
        }

        // Build class-level summary: which methods are root entrypoints
        List<String> rootNames = new ArrayList<>();
        Map<String, List<String>> membership = new LinkedHashMap<>();

        for (MethodContext m : classCtx.getMethodContexts()) {

            // direct external callers → this method is a workflow entry
            if (!m.getExternalCallers().isEmpty()) {
                rootNames.add(m.getMethodObject().getName());
            }

            List<String> rootList = m.getWorkflowRoots().stream()
                    .map(x -> x.getMethodObject().getName())
                    .sorted()
                    .collect(Collectors.toList());

            membership.put(m.getMethodObject().getName(), rootList);
        }

        classCtx.setWorkflowRoots(rootNames);
        classCtx.setWorkflowMembership(membership);
    }

    private static void findWorkflowRootsRecursive(MethodContext m, Set<MethodContext> roots) {
        for (MethodContext caller : m.getCallerMethods()) {

            String callerClass = caller.getMethodObject().getClassName();
            String calleeClass = m.getMethodObject().getClassName();

            // external caller → workflow root
            if (!callerClass.equals(calleeClass)) {
                roots.add(caller);
            } else {
                // internal caller → keep climbing
                findWorkflowRootsRecursive(caller, roots);
            }
        }
    }

    // --------------------------------------------------------------------
    // DEPENDENCIES 
    // --------------------------------------------------------------------

    public static void buildGlobalDependencies(ObjectContext objectContext) {
        if (objectContext == null) return;

        SystemObject system = objectContext.getSystemObject();
        Map<String, ClassContext> classMap = new HashMap<>();
        for (ClassContext ctx : objectContext.getClassContexts()) {
            classMap.put(ctx.getClassName(), ctx);
        }

        for (ClassContext sourceCtx : objectContext.getClassContexts()) {
            ClassObject cls = sourceCtx.getClassObject();

            Map<String, Set<String>> depTypes = new LinkedHashMap<>();

            if (cls.getSuperclass() != null)
                depTypes.computeIfAbsent(cls.getSuperclass().getClassType(), k -> new LinkedHashSet<>()).add("extends");

            ListIterator<TypeObject> itf = cls.getInterfaceIterator();
            while (itf.hasNext()) {
                depTypes.computeIfAbsent(itf.next().getClassType(), k -> new LinkedHashSet<>()).add("implements");
            }

            ListIterator<FieldObject> fields = cls.getFieldIterator();
            while (fields.hasNext()) {
                FieldObject f = fields.next();
                if (f.getType() != null)
                    depTypes.computeIfAbsent(f.getType().getClassType(), k -> new LinkedHashSet<>()).add("field");
            }

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

            for (Map.Entry<String, Set<String>> entry : depTypes.entrySet()) {
                String depName = entry.getKey();
                ClassContext targetCtx = classMap.get(depName);

                if (targetCtx != null && !depName.equals(sourceCtx.getClassName())) {
                    for (String type : entry.getValue()) {
                        boolean exists = sourceCtx.getDependencyRelations().stream()
                                .anyMatch(r -> r.getTarget() == targetCtx && r.getType().equals(type));

                        if (!exists) {
                            sourceCtx.addDependency(targetCtx, type);
                            targetCtx.addDependent(sourceCtx, type);
                        }
                    }
                }
            }
        }

        System.out.println("[ContextBuilder] Built global dependency graph for all classes (deduplicated per type).");
    }

    // --------------------------------------------------------------------
    // INITIALIZATION
    // --------------------------------------------------------------------

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

            classContexts.add(classCtx);
        }

        objectContext.setClassContexts(classContexts);
    }

    // --------------------------------------------------------------------
    // SOURCE CODE
    // --------------------------------------------------------------------

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
        if (classSource == null) return;

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

    // --------------------------------------------------------------------
    // UTILITIES
    // --------------------------------------------------------------------

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