package gr.uom.java.jdeodorant.refactoring.export;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;

import gr.uom.java.distance.ExtractClassCandidateGroup;
import gr.uom.java.distance.ExtractClassCandidateRefactoring;

public final class IdentifyPreviewExporter {

    private IdentifyPreviewExporter() {}

    /**
     * (Backward compatible) Export a single candidate to JSON.
     * @throws IOException 
     */
    public static void exportCandidate(Object candidateNode) throws CoreException, IOException {
        Candidate cand = Candidate.from(candidateNode);
        if (cand == null) {
            throw new IllegalArgumentException("Unsupported selection type for Identify preview.");
        }

        // Build JSON for one candidate (kept for compatibility)
        String json = buildJson(cand);

        // File: <project>/jdeodorant_cards/<ClassSimpleName>_identify_preview.json
        // (Overwrites on purpose; prefer exportGroup(...) in UI to include all candidates)
        IProject project = cand.sourceFile.getProject();
        IFolder folder = project.getFolder("jdeodorant_cards");
        if (!folder.exists()) {
            folder.create(true, true, null);
        }
        IFile out = folder.getFile(cand.sourceTypeName + "_identify_preview.json");

        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            if (out.exists()) {
                out.setContents(in, IResource.FORCE | IResource.KEEP_HISTORY, null);
            } else {
                out.create(in, true, null);
            }
        }
        out.refreshLocal(IResource.DEPTH_ZERO, null);
    }

    /**
     * Export ONE JSON per CLASS (aggregates all candidates in the class).
     * Call this from the GodClass view (toolbar/context menu) after Identify.
     * @throws IOException 
     */
    public static void exportGroup(ExtractClassCandidateGroup group) throws CoreException, IOException {
        if (group == null || group.getCandidates().isEmpty()) {
            throw new IllegalArgumentException("Empty candidate group.");
        }

        // Use first candidate to resolve the type/file/folder
        ExtractClassCandidateRefactoring first = group.getCandidates().get(0);
        TypeDeclaration td = first.getSourceClassTypeDeclaration();
        if (td == null) throw new IllegalArgumentException("Missing source class type.");

        CompilationUnit cu = (CompilationUnit) td.getRoot();
        ICompilationUnit icu = (ICompilationUnit) cu.getJavaElement();
        IFile sourceFile = (IFile) icu.getUnderlyingResource();
        IProject project = sourceFile.getProject();

        // Wrap all group candidates
        List<Candidate> candidates = new ArrayList<>();
        for (ExtractClassCandidateRefactoring cnode : group.getCandidates()) {
            Candidate c = Candidate.from(cnode);
            if (c != null) candidates.add(c);
        }
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("No valid candidates in group.");
        }

        // Build aggregated JSON (ONE file with an array of candidates)
        String json = buildMinimalJson(td, candidates);

        // Write under <project>/jdeodorant_cards/<Class>_identify_preview.json
        IFolder folder = project.getFolder("jdeodorant_cards");
        if (!folder.exists()) folder.create(true, true, null);

        String sourceTypeName = td.getName().getIdentifier();
        IFile out = folder.getFile(sourceTypeName + "_identify_preview.json");

        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            if (out.exists()) {
                out.setContents(in, IResource.FORCE | IResource.KEEP_HISTORY, null);
            } else {
                out.create(in, true, null);
            }
        }
        out.refreshLocal(IResource.DEPTH_ZERO, null);
    }

    // ---------------- JSON for a single candidate (compat) ----------------

    private static String buildJson(Candidate c) {
        String nl = "\n";
        StringBuilder sb = new StringBuilder();
        sb.append("{").append(nl);

        // intent
        key(sb, "intent").append("{").append(nl);
        kvStr(sb, "technique", "Extract Class (candidate)", true);
        kvStr(sb, "source_type_name", c.sourceTypeName, true);
        kvStr(sb, "detection_stage", "identify", false);
        sb.append("},").append(nl);

        // candidate
        key(sb, "candidate").append("{").append(nl);
        kvStr(sb, "id", c.id, true);
        kvNum(sb, "score", c.score, true);

        key(sb, "selection").append("{").append(nl);
        key(sb, "methods").append(listOfObjects(c.methodDescriptors())).append(",").append(nl);
        key(sb, "fields").append(listOfObjects(c.fieldDescriptors())).append(nl);
        sb.append("},").append(nl);

        key(sb, "sizes").append("{").append(nl);
        kvNum(sb, "cluster_methods", c.clusterMethodKeys.size(), true);
        kvNum(sb, "cluster_fields", c.clusterFieldKeys.size(), true);
        kvNum(sb, "source_total_methods", c.totalMethods, true);
        kvNum(sb, "source_total_fields", c.totalFields, false);
        sb.append("}").append(nl);

        sb.append("},").append(nl);

        // relationships
        key(sb, "relationships_preview").append("{").append(nl);
        key(sb, "internal_calls").append(listOfObjects(c.internalCalls)).append(",").append(nl);
        key(sb, "external_calls_in_source").append(listOfObjects(c.externalCallsInSource)).append(",").append(nl);
        key(sb, "field_accesses").append(listOfObjects(c.fieldAccesses)).append(nl);
        sb.append("},").append(nl);

        // type references
        key(sb, "type_references").append("{").append(nl);
        key(sb, "referenced_types_in_cluster").append(stringArray(c.referencedTypesInCluster)).append(",").append(nl);
        key(sb, "external_types").append(stringArray(c.externalTypes)).append(nl);
        sb.append("},").append(nl);

        // simple metrics
        key(sb, "metrics").append("{").append(nl);
        kvNum(sb, "distinct_source_dependencies", c.distinctSourceDeps, true);
        kvNum(sb, "distinct_target_dependencies", c.distinctTargetDeps, false);
        sb.append("}").append(nl);

        sb.append("}").append(nl);
        return sb.toString();
    }
    
    private static String buildMinimalJson(TypeDeclaration td, List<Candidate> candidates) {
        String nl = "\n";
        StringBuilder sb = new StringBuilder();

        sb.append("{").append(nl);
        sb.append("\"source_type_name\": \"").append(td.getName().getIdentifier()).append("\",").append(nl);
        sb.append("\"candidates\": [").append(nl);

        for (int i = 0; i < candidates.size(); i++) {
            Candidate c = candidates.get(i);
            sb.append("  {").append(nl);
            sb.append("    \"methods\": [");

            List<String> methodNames = new ArrayList<>();
            for (MethodDeclaration m : c.methods) {
                methodNames.add("\"" + m.getName().getIdentifier() + "\"");
            }

            sb.append(String.join(", ", methodNames));
            sb.append("]").append(nl);
            sb.append("  }");

            if (i < candidates.size() - 1) sb.append(",");
            sb.append(nl);
        }

        sb.append("]}").append(nl);
        return sb.toString();
    }

    // ---------------- JSON for the whole class (one file) ----------------

    private static String buildGroupJson(TypeDeclaration td, List<Candidate> candidates) {
        String nl = "\n";
        StringBuilder sb = new StringBuilder();
        String sourceTypeName = td.getName().getIdentifier();

        sb.append("{").append(nl);

        // intent
        key(sb, "intent").append("{").append(nl);
        kvStr(sb, "technique", "Extract Class (identify)", true);
        kvStr(sb, "source_type_name", sourceTypeName, true);
        kvStr(sb, "detection_stage", "identify", false);
        sb.append("},").append(nl);

        // class summary
        key(sb, "class_summary").append("{").append(nl);
        kvNum(sb, "total_candidates", candidates.size(), true);
        kvNum(sb, "source_total_methods", td.getMethods().length, true);
        kvNum(sb, "source_total_fields", td.getFields().length, false);
        sb.append("},").append(nl);

        // full list of candidates
        key(sb, "candidates").append("[").append(nl);
        for (int i = 0; i < candidates.size(); i++) {
            Candidate c = candidates.get(i);
            sb.append(candidateSectionJson(c));
            if (i < candidates.size() - 1) sb.append(",").append(nl);
            else sb.append(nl);
        }
        sb.append("]").append(nl);

        sb.append("}").append(nl);
        return sb.toString();
    }

    private static String candidateSectionJson(Candidate c) {
        String nl = "\n";
        StringBuilder sb = new StringBuilder();

        sb.append("{").append(nl);
        kvStr(sb, "id", c.id, true);
        kvNum(sb, "score", c.score, true);

        key(sb, "selection").append("{").append(nl);
        key(sb, "methods").append(listOfObjects(c.methodDescriptors())).append(",").append(nl);
        key(sb, "fields").append(listOfObjects(c.fieldDescriptors())).append(nl);
        sb.append("},").append(nl);

        key(sb, "sizes").append("{").append(nl);
        kvNum(sb, "cluster_methods", c.clusterMethodKeys.size(), true);
        kvNum(sb, "cluster_fields", c.clusterFieldKeys.size(), false);
        sb.append("},").append(nl);

        key(sb, "relationships_preview").append("{").append(nl);
        key(sb, "internal_calls").append(listOfObjects(c.internalCalls)).append(",").append(nl);
        key(sb, "external_calls_in_source").append(listOfObjects(c.externalCallsInSource)).append(",").append(nl);
        key(sb, "field_accesses").append(listOfObjects(c.fieldAccesses)).append(nl);
        sb.append("},").append(nl);

        key(sb, "type_references").append("{").append(nl);
        key(sb, "referenced_types_in_cluster").append(stringArray(c.referencedTypesInCluster)).append(",").append(nl);
        key(sb, "external_types").append(stringArray(c.externalTypes)).append(nl);
        sb.append("},").append(nl);

        key(sb, "metrics").append("{").append(nl);
        kvNum(sb, "distinct_source_dependencies", c.distinctSourceDeps, true);
        kvNum(sb, "distinct_target_dependencies", c.distinctTargetDeps, false);
        sb.append("}").append(nl);

        sb.append("}");
        return sb.toString();
    }

    // ---------------- Candidate adapter ----------------

    private static final class Candidate {
        final IFile sourceFile;
        final String sourceTypeName;
        final String id;
        final double score; // placeholder (0.0) at Identify
        final Set<MethodDeclaration> methods;
        final Set<VariableDeclaration> fields;
        final int totalMethods, totalFields;
        final Set<String> clusterMethodKeys, clusterFieldKeys;

        // relationships
        final List<String> internalCalls = new ArrayList<>();
        final List<String> externalCallsInSource = new ArrayList<>();
        final List<String> fieldAccesses = new ArrayList<>();
        // types
        final SortedSet<String> referencedTypesInCluster = new TreeSet<>();
        final SortedSet<String> externalTypes = new TreeSet<>();
        // simple metrics (if available)
        final int distinctSourceDeps;
        final int distinctTargetDeps;

        static Candidate from(Object node) {
            try {
                if (!(node instanceof ExtractClassCandidateRefactoring)) {
                    return null;
                }
                ExtractClassCandidateRefactoring cnode = (ExtractClassCandidateRefactoring) node;

                Set<MethodDeclaration> ms = cnode.getExtractedMethods();
                Set<VariableDeclaration> fs = cnode.getExtractedFieldFragments();
                TypeDeclaration td = cnode.getSourceClassTypeDeclaration();
                if (td == null) return null;

                CompilationUnit cu = (CompilationUnit) td.getRoot();
                ICompilationUnit icu = (ICompilationUnit) cu.getJavaElement();
                IFile file = (IFile) icu.getUnderlyingResource();

                // keys for cluster members
                Set<String> mKeys = new HashSet<>();
                if (ms != null) {
                    for (MethodDeclaration m : ms) {
                        IMethodBinding b = m.resolveBinding();
                        if (b != null) mKeys.add(b.getKey());
                    }
                }
                Set<String> fKeys = new HashSet<>();
                if (fs != null) {
                    for (VariableDeclaration v : fs) {
                        IVariableBinding vb = v.resolveBinding();
                        if (vb != null) fKeys.add(vb.getKey());
                    }
                }

                // metrics if exposed
                int srcDeps = 0, tgtDeps = 0;
                try {
                    srcDeps = cnode.getDistinctSourceDependencies();
                    tgtDeps = cnode.getDistinctTargetDependencies();
                } catch (Throwable ignore) { /* not exposed on all branches */ }

                Candidate c = new Candidate(
                    file,
                    td.getName().getIdentifier(),
                    td.getName().getIdentifier() + "#" + Math.abs(node.hashCode()),
                    0.0, // score placeholder at Identify-stage
                    ms != null ? ms : Collections.emptySet(),
                    fs != null ? fs : Collections.emptySet(),
                    td.getMethods().length,
                    td.getFields().length,
                    mKeys, fKeys,
                    srcDeps, tgtDeps
                );

                // Collect relationships & types
                c.scan(td);
                return c;
            } catch (Throwable t) {
                return null;
            }
        }

        private Candidate(IFile sourceFile, String sourceTypeName, String id, double score,
                          Set<MethodDeclaration> methods, Set<VariableDeclaration> fields,
                          int totalMethods, int totalFields,
                          Set<String> clusterMethodKeys, Set<String> clusterFieldKeys,
                          int distinctSourceDeps, int distinctTargetDeps) {
            this.sourceFile = sourceFile;
            this.sourceTypeName = sourceTypeName;
            this.id = id;
            this.score = score;
            this.methods = methods;
            this.fields = fields;
            this.totalMethods = totalMethods;
            this.totalFields = totalFields;
            this.clusterMethodKeys = clusterMethodKeys;
            this.clusterFieldKeys = clusterFieldKeys;
            this.distinctSourceDeps = distinctSourceDeps;
            this.distinctTargetDeps = distinctTargetDeps;
        }

        private void scan(TypeDeclaration td) {
            ITypeBinding sourceTypeBinding = td.resolveBinding();

            // (1) Signatures / types from declarations
            for (MethodDeclaration m : methods) addMethodSignatureTypes(m);
            for (VariableDeclaration f : fields) {
                FieldDeclaration fd = (FieldDeclaration) f.getParent();
                addTypeIfAny(fd.getType());
            }

            // (2) Body relationships and external types
            Set<String> classMethodKeys = new HashSet<>();
            for (MethodDeclaration md : td.getMethods()) {
                IMethodBinding b = md.resolveBinding();
                if (b != null) classMethodKeys.add(b.getKey());
            }

            for (MethodDeclaration m : methods) {
                final String callerSig = methodSignature(m);
                final Set<String> reads = new TreeSet<>();
                final Set<String> writes = new TreeSet<>();

                m.accept(new ASTVisitor() {
                    @Override
                    public boolean visit(MethodInvocation node) {
                        IMethodBinding mb = node.resolveMethodBinding();
                        if (mb != null) {
                            ITypeBinding decl = mb.getDeclaringClass();
                            // types referenced via call sites
                            if (decl != null) addTypeQN(decl.getErasure(), referencedTypesInCluster);

                            String calleeSig = methodSignature(mb);
                            String calleeKey = mb.getMethodDeclaration().getKey();

                            if (decl != null && sourceTypeBinding != null && decl.isEqualTo(sourceTypeBinding)) {
                                // call to a method in the same source class
                                if (clusterMethodKeys.contains(calleeKey)) {
                                    internalCalls.add(obj("caller", callerSig, "callee", calleeSig));
                                } else if (classMethodKeys.contains(calleeKey)) {
                                    externalCallsInSource.add(obj("caller", callerSig, "callee", calleeSig));
                                }
                            } else {
                                // external type → collect declaring type
                                addTypeQN(decl != null ? decl.getErasure() : null, externalTypes);
                            }
                        }
                        return true;
                    }

                    @Override
                    public boolean visit(ClassInstanceCreation node) {
                        addTypeIfAny(node.getType());
                        IMethodBinding mb = node.resolveConstructorBinding();
                        if (mb != null) addTypeQN(mb.getDeclaringClass(), externalTypes);
                        return true;
                    }

                    @Override
                    public boolean visit(CastExpression node) {
                        addTypeIfAny(node.getType());
                        return true;
                    }

                    @Override
                    public boolean visit(SimpleName node) {
                        IBinding b = node.resolveBinding();
                        if (b instanceof IVariableBinding) {
                            IVariableBinding vb = (IVariableBinding) b;
                            if (vb.isField()) {
                                String key = vb.getVariableDeclaration().getKey();
                                if (clusterFieldKeys.contains(key)) {
                                    reads.add(vb.getName());
                                } else if (vb.getDeclaringClass() != null &&
                                           sourceTypeBinding != null &&
                                           vb.getDeclaringClass().isEqualTo(sourceTypeBinding)) {
                                    // read of another field in the same source class
                                    reads.add(vb.getName());
                                }
                            }
                        }
                        return true;
                    }

                    @Override
                    public boolean visit(Assignment node) {
                        Expression lhs = node.getLeftHandSide();
                        IVariableBinding vb = resolveFieldBinding(lhs);
                        if (vb != null) {
                            String key = vb.getVariableDeclaration().getKey();
                            if (clusterFieldKeys.contains(key) ||
                               (vb.getDeclaringClass() != null &&
                                sourceTypeBinding != null &&
                                vb.getDeclaringClass().isEqualTo(sourceTypeBinding))) {
                                writes.add(vb.getName());
                            }
                        }
                        return true;
                    }

                    @Override
                    public boolean visit(PrefixExpression node) {
                        if (isIncDec(node.getOperator())) {
                            IVariableBinding vb = resolveFieldBinding(node.getOperand());
                            if (vb != null) writes.add(vb.getName());
                        }
                        return true;
                    }

                    @Override
                    public boolean visit(PostfixExpression node) {
                        if (isIncDec(node.getOperator())) {
                            IVariableBinding vb = resolveFieldBinding(node.getOperand());
                            if (vb != null) writes.add(vb.getName());
                        }
                        return true;
                    }
                });

                // Proper JSON object for field accesses
                String fa = new StringBuilder()
                    .append("{\"method\":\"").append(esc(callerSig)).append("\",")
                    .append("\"reads\":").append(stringArray(reads)).append(",")
                    .append("\"writes\":").append(stringArray(writes)).append("}")
                    .toString();
                fieldAccesses.add(fa);
            }
        }

        // ----- descriptors for JSON -----

        List<String> methodDescriptors() {
            List<String> out = new ArrayList<>();
            for (MethodDeclaration m : methods) {
                int mod = m.getModifiers();
                String vis = visibility(mod);
                String isStatic = (Modifier.isStatic(mod) ? "true" : "false");
                String isFinal  = (Modifier.isFinal(mod) ? "true" : "false");
                String ret = (m.getReturnType2() != null && m.getReturnType2().resolveBinding() != null)
                        ? qn(m.getReturnType2().resolveBinding())
                        : (m.isConstructor() ? "<ctor>" : "void");

                out.add("{"
                    + "\"signature\":\"" + esc(methodSignature(m)) + "\","
                    + "\"visibility\":\"" + vis + "\","
                    + "\"static\":" + isStatic + ","
                    + "\"final\":" + isFinal + ","
                    + "\"return_type\":\"" + esc(ret) + "\""
                    + "}");
            }
            return out;
        }

        List<String> fieldDescriptors() {
            List<String> out = new ArrayList<>();
            for (VariableDeclaration v : fields) {
                FieldDeclaration fd = (FieldDeclaration) v.getParent();
                int mod = fd.getModifiers();
                String vis = visibility(mod);
                String isStatic = (Modifier.isStatic(mod) ? "true" : "false");
                String isFinal  = (Modifier.isFinal(mod) ? "true" : "false");
                String type = (fd.getType() != null && fd.getType().resolveBinding() != null)
                        ? qn(fd.getType().resolveBinding()) : "java.lang.Object";

                out.add("{"
                    + "\"name\":\"" + esc(v.getName().getIdentifier()) + "\","
                    + "\"type\":\"" + esc(type) + "\","
                    + "\"visibility\":\"" + vis + "\","
                    + "\"static\":" + isStatic + ","
                    + "\"final\":" + isFinal
                    + "}");
            }
            return out;
        }

        // ----- helpers -----

        private void addMethodSignatureTypes(MethodDeclaration m) {
            if (m.getReturnType2() != null) addTypeIfAny(m.getReturnType2());
            @SuppressWarnings("unchecked")
            List<SingleVariableDeclaration> ps = m.parameters();
            for (SingleVariableDeclaration p : ps) addTypeIfAny(p.getType());
            @SuppressWarnings("unchecked")
            List<Type> thrown = m.thrownExceptionTypes();
            for (Type t : thrown) addTypeIfAny(t);
        }

        private static IVariableBinding resolveFieldBinding(Expression e) {
            if (e == null) return null;
            if (e instanceof SimpleName) {
                IBinding b = ((SimpleName) e).resolveBinding();
                if (b instanceof IVariableBinding && ((IVariableBinding) b).isField()) return (IVariableBinding) b;
            } else if (e instanceof FieldAccess) {
                IVariableBinding vb = ((FieldAccess) e).resolveFieldBinding();
                if (vb != null && vb.isField()) return vb;
            } else if (e instanceof QualifiedName) {
                IBinding b = ((QualifiedName) e).resolveBinding();
                if (b instanceof IVariableBinding && ((IVariableBinding) b).isField()) return (IVariableBinding) b;
            }
            return null;
        }

        private static boolean isIncDec(PrefixExpression.Operator op) {
            return op == PrefixExpression.Operator.INCREMENT || op == PrefixExpression.Operator.DECREMENT;
        }
        private static boolean isIncDec(PostfixExpression.Operator op) {
            return op == PostfixExpression.Operator.INCREMENT || op == PostfixExpression.Operator.DECREMENT;
        }

        private void addTypeIfAny(Type t) {
            if (t == null) return;
            ITypeBinding b = t.resolveBinding();
            if (b != null) addTypeQN(b.getErasure(), referencedTypesInCluster);
        }

        private static void addTypeQN(ITypeBinding b, SortedSet<String> bag) {
            if (b == null) return;
            String qn = qn(b);
            if (qn.startsWith("java.lang")) return; // reduce noise
            bag.add(qn);
        }

        private static String methodSignature(MethodDeclaration m) {
            IMethodBinding b = m.resolveBinding();
            return (b != null) ? methodSignature(b) : m.getName().getIdentifier();
        }

        private static String methodSignature(IMethodBinding b) {
            String name = b.isConstructor() ? b.getDeclaringClass().getName() : b.getName();
            String params = Arrays.stream(b.getParameterTypes())
                    .map(pt -> qn(pt.getErasure()))
                    .collect(Collectors.joining(", "));
            return name + "(" + params + ")";
        }

        private static String qn(ITypeBinding b) {
            if (b == null) return "java.lang.Object";
            String q = b.getQualifiedName();
            return (q == null || q.isEmpty()) ? b.getName() : q;
        }

        private static String visibility(int modifiers) {
            if (Modifier.isPublic(modifiers)) return "public";
            if (Modifier.isProtected(modifiers)) return "protected";
            if (Modifier.isPrivate(modifiers)) return "private";
            return "package";
        }

        private String obj(String k1, String v1, String k2, String v2) {
            return "{\"" + esc(k1) + "\":\"" + esc(v1) + "\",\"" + esc(k2) + "\":\"" + esc(v2) + "\"}";
        }
    }

    // ---------------- tiny JSON utils ----------------

    private static StringBuilder key(StringBuilder sb, String key) {
        return sb.append("\"").append(esc(key)).append("\": ");
    }
    private static void kvStr(StringBuilder sb, String key, String val, boolean comma) {
        key(sb, key).append("\"").append(esc(val)).append("\"");
        if (comma) sb.append(",");
        sb.append("\n");
    }
    private static void kvNum(StringBuilder sb, String key, Number val, boolean comma) {
        key(sb, key).append(val);
        if (comma) sb.append(",");
        sb.append("\n");
    }
    private static String listOfObjects(Collection<String> objs) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        boolean first = true;
        for (String o : new TreeSet<>(objs)) {
            if (!first) sb.append(", ");
            sb.append(o);
            first = false;
        }
        sb.append("]");
        return sb.toString();
    }
    private static String stringArray(Collection<String> items) {
        return "[" + new TreeSet<>(items).stream()
                .map(s -> "\"" + esc(s) + "\"")
                .collect(Collectors.joining(", ")) + "]";
    }
    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"","\\\"")
                .replace("\n","\\n").replace("\r","\\r").replace("\t","\\t");
    }
}