package gr.uom.java.ast.context;

import gr.uom.java.ast.*;
import gr.uom.java.ast.decomposition.cfg.PlainVariable;

import java.io.InputStream;
import java.util.*;
import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.*;
import org.eclipse.jdt.core.*;

/**
 * Builds a high-level ObjectContext model from JDeodorant's AST representation.
 * Automatically constructs the AST for the active Eclipse project.
 */
public class ContextBuilder {

	public static ObjectContext buildProjectContext(IJavaProject javaProject) {
	    if (javaProject == null) {
	        System.out.println("[ContextBuilder] Provided Java project is null.");
	        return null;
	    }

	    SystemObject system = null;

	    try {
	        System.out.println("[ContextBuilder] Starting AST build for: " + javaProject.getElementName());
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
	    buildMethodRelationships(objectContext);
	    buildFieldAccessRelationships(objectContext);
	    buildClassRelationships(objectContext);
	    attachSourceCode(system, objectContext);
	    computeMetrics(objectContext);

	    System.out.println("[ContextBuilder] Context fully built for project: " + javaProject.getElementName());
	    return objectContext;
	}

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
                } else {
                    System.out.println("   [SKIP NON-SOURCE ROOT]: " + root.getElementName());
                }
            }
        } catch (Exception e) {
            System.err.println("[ContextBuilder] Failed to list project structure: " + e.getMessage());
        }
    }

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

            classCtx.setMetricsContext(new MetricsContext(cls));
            classContexts.add(classCtx);
        }

        objectContext.setClassContexts(classContexts);
        System.out.println("[ContextBuilder] Created " + classContexts.size() + " ClassContexts.");
    }

    private static void buildMethodRelationships(ObjectContext objectContext) {
        Map<String, MethodContext> methodMap = new HashMap<>();

        for (ClassContext classCtx : objectContext.getClassContexts()) {
            for (MethodContext methodCtx : classCtx.getMethodContexts()) {
                MethodObject mo = methodCtx.getMethodObject();
                methodMap.put(buildMethodKey(mo.getClassName(), mo.getSignature()), methodCtx);
            }
        }

        for (MethodContext callerCtx : methodMap.values()) {
            MethodObject caller = callerCtx.getMethodObject();
            for (MethodInvocationObject invocation : caller.getMethodInvocations()) {
                String targetClass = invocation.getOriginClassName();
                String targetSignature = invocation.getSignature();
                MethodContext calleeCtx = methodMap.get(buildMethodKey(targetClass, targetSignature));
                if (calleeCtx != null) {
                    callerCtx.getCalledMethods().add(calleeCtx);
                    calleeCtx.getCallerMethods().add(callerCtx);
                }
            }
        }

        System.out.println("[ContextBuilder] Built method relationships for " + methodMap.size() + " methods.");
    }

    private static String buildMethodKey(String className, String signature) {
        return className + "#" + signature;
    }

    // ---------------------------------------------------------------
    // BUILD FIELD ACCESS RELATIONSHIPS (read/write)
    // ---------------------------------------------------------------
    private static void buildFieldAccessRelationships(ObjectContext objectContext) {
        Map<String, FieldContext> fieldMap = new HashMap<>();

        for (ClassContext classCtx : objectContext.getClassContexts()) {
            for (FieldContext fieldCtx : classCtx.getFieldContexts()) {
                String key = buildFieldKey(classCtx.getClassName(), fieldCtx.getFieldObject().getName());
                fieldMap.put(key, fieldCtx);
            }
        }

        for (ClassContext classCtx : objectContext.getClassContexts()) {
            for (MethodContext methodCtx : classCtx.getMethodContexts()) {
                MethodObject methodObj = methodCtx.getMethodObject();

                // READS: fields used through this reference
                for (PlainVariable usedVar : methodObj.getUsedFieldsThroughThisReference()) {
                    String key = buildFieldKey(classCtx.getClassName(), usedVar.getVariableName());
                    FieldContext fieldCtx = fieldMap.get(key);
                    if (fieldCtx != null) {
                        fieldCtx.addReadByMethod(methodCtx);
                    }
                }

                // WRITES: fields defined through this reference
                for (PlainVariable writtenVar : methodObj.getDefinedFieldsThroughThisReference()) {
                    String key = buildFieldKey(classCtx.getClassName(), writtenVar.getVariableName());
                    FieldContext fieldCtx = fieldMap.get(key);
                    if (fieldCtx != null) {
                        fieldCtx.addWrittenByMethod(methodCtx);
                    }
                }
            }
        }

        System.out.println("[ContextBuilder] Built field access relationships (read/write via this reference).");
    }

    private static String buildFieldKey(String className, String fieldName) {
        return className + "#" + fieldName;
    }

    private static void buildClassRelationships(ObjectContext objectContext) {
        Map<String, ClassContext> classMap = new HashMap<>();
        for (ClassContext ctx : objectContext.getClassContexts()) {
            classMap.put(ctx.getClassName(), ctx);
        }

        for (ClassContext sourceCtx : objectContext.getClassContexts()) {
            ClassObject cls = sourceCtx.getClassObject();
            String sourceName = cls.getName();
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
                    if (mio.getOriginClassName() != null && !mio.getOriginClassName().equals(sourceName))
                        deps.add(mio.getOriginClassName());
                }

                for (CreationObject c : m.getCreations()) {
                    if (c.getType() != null)
                        deps.add(c.getType().getClassType());
                }
            }

            for (String target : deps) {
                ClassContext targetCtx = classMap.get(target);
                if (targetCtx != null && !target.equals(sourceName)) {
                    sourceCtx.addDependency(targetCtx);
                    targetCtx.addDependent(sourceCtx);
                }
            }
        }

        System.out.println("[ContextBuilder] Built class dependency relationships.");
    }

    private static void computeMetrics(ObjectContext objectContext) {
        for (ClassContext cls : objectContext.getClassContexts()) {
            MetricsContext metrics = cls.getMetricsContext();
            if (metrics == null) continue;

            metrics.setFanIn(cls.getDependentClasses().size());
            metrics.setFanOut(cls.getDependencyClasses().size());
        }

        System.out.println("[ContextBuilder] Attached fan-in/fan-out metrics to all ClassContexts.");
    }

    public static void attachSourceCode(SystemObject system, ObjectContext context) {
        for (ClassContext ctx : context.getClassContexts()) {
            ClassObject classObj = system.getClassObject(ctx.getClassName());
            if (classObj == null) continue;

            try {
                ITypeRoot typeRoot = classObj.getITypeRoot();
                if (typeRoot != null && typeRoot.getBuffer() != null) {
                    String src = typeRoot.getBuffer().getContents();
                    if (src != null) {
                        ctx.setSourceCode(src);
                        continue;
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
