package gr.uom.java.jdeodorant.batch;

import gr.uom.java.ast.ASTReader;
import gr.uom.java.ast.ClassObject;
import gr.uom.java.ast.CompilationErrorDetectedException;
import gr.uom.java.ast.CompilationUnitCache;
import gr.uom.java.ast.MethodObject;
import gr.uom.java.ast.SystemObject;
import gr.uom.java.ast.decomposition.cfg.CFG;
import gr.uom.java.ast.decomposition.cfg.PDG;
import gr.uom.java.ast.decomposition.cfg.PDGObjectSliceUnion;
import gr.uom.java.ast.decomposition.cfg.PDGObjectSliceUnionCollection;
import gr.uom.java.ast.decomposition.cfg.PDGSliceUnion;
import gr.uom.java.ast.decomposition.cfg.PDGSliceUnionCollection;
import gr.uom.java.ast.decomposition.cfg.PlainVariable;
import gr.uom.java.ast.util.StatementExtractor;
import gr.uom.java.distance.DistanceMatrix;
import gr.uom.java.distance.ExtractClassCandidateGroup;
import gr.uom.java.distance.ExtractClassCandidateRefactoring;
import gr.uom.java.distance.ExtractedConcept;
import gr.uom.java.distance.MoveMethodCandidateRefactoring;
import gr.uom.java.distance.MySystem;
import gr.uom.java.jdeodorant.preferences.PreferenceConstants;
import gr.uom.java.jdeodorant.refactoring.Activator;
import gr.uom.java.jdeodorant.refactoring.manipulators.ASTSlice;
import gr.uom.java.jdeodorant.refactoring.manipulators.ASTSliceGroup;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.eclipse.core.resources.IFile;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

/**
 * Runs JDeodorant's God Class, Feature Envy and Long Method detectors on the whole project.
 * The detection code mirrors the plug-in's views (GodClass, FeatureEnvy, LongMethod) rather than
 * gr.uom.java.ast.Standalone, because Standalone collects results in TreeSets whose compareTo can
 * treat distinct candidates as equal and drop them.
 */
final class Detectors {

	private final ProjectImporter.Imported imported;
	private final Options options;

	Detectors(ProjectImporter.Imported imported, Options options) {
		this.imported = imported;
		this.options = options;
	}

	Map<String, Object> run() throws CompilationErrorDetectedException {
		CompilationUnitCache.getInstance().clearCache();
		new ASTReader(imported.javaProject, null);
		SystemObject systemObject = ASTReader.getSystemObject();

		Set<ClassObject> examined = new LinkedHashSet<>();
		Set<String> examinedNames = new LinkedHashSet<>();
		int methods = 0;
		for (ClassObject c : systemObject.getClassObjects()) {
			if (!c.isEnum() && !c.isInterface() && !c.isGeneratedByParserGenenator()) {
				examined.add(c);
				examinedNames.add(c.getName());
				methods += c.getNumberOfMethods();
			}
		}

		Map<String, Object> result = new LinkedHashMap<>();
		Map<String, Object> summary = new LinkedHashMap<>();
		summary.put("classes_parsed", systemObject.getClassObjects().size());
		summary.put("classes_examined", examined.size());
		summary.put("methods_examined", methods);
		result.put("summary", summary);

		if (options.detectors.contains(Options.GOD_CLASS)) {
			long t = System.currentTimeMillis();
			List<Object> godClass = godClass(systemObject, examinedNames);
			summary.put("god_class_source_classes", godClass.size());
			summary.put("god_class_ms", System.currentTimeMillis() - t);
			result.put("god_class", godClass);
		}
		if (options.detectors.contains(Options.FEATURE_ENVY)) {
			long t = System.currentTimeMillis();
			List<Object> featureEnvy = featureEnvy(systemObject, examinedNames);
			summary.put("feature_envy_candidates", featureEnvy.size());
			summary.put("feature_envy_ms", System.currentTimeMillis() - t);
			result.put("feature_envy", featureEnvy);
		}
		if (options.detectors.contains(Options.LONG_METHOD)) {
			long t = System.currentTimeMillis();
			List<Object> longMethod = longMethod(examined);
			summary.put("long_method_groups", longMethod.size());
			summary.put("long_method_ms", System.currentTimeMillis() - t);
			result.put("long_method", longMethod);
		}
		return result;
	}

	// ---- God Class -> Extract Class (mirrors views.GodClass) ----

	private List<Object> godClass(SystemObject systemObject, Set<String> classNames) {
		MySystem system = new MySystem(systemObject, true);
		DistanceMatrix distanceMatrix = new DistanceMatrix(system);
		List<ExtractClassCandidateRefactoring> candidates = new ArrayList<>(
				distanceMatrix.getExtractClassCandidateRefactorings(classNames, null));

		Map<String, ExtractClassCandidateGroup> groups = new TreeMap<>();
		for (ExtractClassCandidateRefactoring candidate : candidates) {
			groups.computeIfAbsent(candidate.getSourceEntity(), ExtractClassCandidateGroup::new).addCandidate(candidate);
		}
		List<Object> out = new ArrayList<>();
		for (ExtractClassCandidateGroup group : groups.values()) {
			group.groupConcepts();
			List<ExtractClassCandidateRefactoring> ranked = new ArrayList<>(group.getCandidates());
			Collections.sort(ranked);
			Map<ExtractClassCandidateRefactoring, Integer> rankOf = new HashMap<>();

			Map<String, Object> g = new LinkedHashMap<>();
			ExtractClassCandidateRefactoring first = ranked.get(0);
			g.put("source_class", typeName(first.getSourceClassTypeDeclaration(), group.getSource()));
			g.put("file", file(first.getSourceIFile()));
			List<Object> list = new ArrayList<>();
			for (int i = 0; i < ranked.size(); i++) {
				ExtractClassCandidateRefactoring c = ranked.get(i);
				rankOf.put(c, i + 1);
				Map<String, Object> m = new LinkedHashMap<>();
				m.put("rank", i + 1);
				m.put("methods", methods(c.getExtractedMethods()));
				m.put("fields", fields(c.getExtractedFieldFragments()));
				m.put("delegate_methods", methods(c.getDelegateMethods()));
				m.put("distinct_source_dependencies", c.getDistinctSourceDependencies());
				m.put("distinct_target_dependencies", c.getDistinctTargetDependencies());
				m.put("topics", c.getTopics() == null ? List.of() : new ArrayList<>(c.getTopics()));
				list.add(m);
			}
			g.put("candidates", list);
			List<Object> concepts = new ArrayList<>();
			for (ExtractedConcept concept : group.getExtractedConcepts()) {
				Map<String, Object> m = new LinkedHashMap<>();
				m.put("topics", concept.getTopics() == null ? List.of() : new ArrayList<>(concept.getTopics()));
				List<Integer> ranks = new ArrayList<>();
				for (ExtractClassCandidateRefactoring c : concept.getConceptClusters()) {
					Integer r = rankOf.get(c);
					if (r != null) {
						ranks.add(r);
					}
				}
				Collections.sort(ranks);
				m.put("candidate_ranks", ranks);
				concepts.add(m);
			}
			g.put("concepts", concepts);
			out.add(g);
		}
		return out;
	}

	// ---- Feature Envy -> Move Method (mirrors views.FeatureEnvy) ----

	private List<Object> featureEnvy(SystemObject systemObject, Set<String> classNames) {
		MySystem system = new MySystem(systemObject, false);
		DistanceMatrix distanceMatrix = new DistanceMatrix(system);
		List<MoveMethodCandidateRefactoring> candidates = new ArrayList<>(
				distanceMatrix.getMoveMethodCandidateRefactoringsByAccess(classNames, null));
		Collections.sort(candidates);
		List<Object> out = new ArrayList<>();
		for (int i = 0; i < candidates.size(); i++) {
			MoveMethodCandidateRefactoring c = candidates.get(i);
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("rank", i + 1);
			m.put("source_class", typeName(c.getSourceClassTypeDeclaration(), c.getSourceClass().getName()));
			m.put("source_file", file(c.getSourceIFile()));
			m.put("method", method(c.getSourceMethodDeclaration()));
			m.put("target_class", typeName(c.getTargetClassTypeDeclaration(), c.getTargetClass().getName()));
			m.put("target_file", file(c.getTargetIFile()));
			m.put("distinct_envied_elements", c.getNumberOfDistinctEnviedElements());
			m.put("distinct_source_dependencies", c.getDistinctSourceDependencies());
			m.put("distinct_target_dependencies", c.getDistinctTargetDependencies());
			List<Object> extra = new ArrayList<>();
			for (MethodDeclaration md : c.getAdditionalMethodsToBeMoved().values()) {
				extra.add(method(md));
			}
			m.put("additional_methods_moved", extra);
			out.add(m);
		}
		return out;
	}

	// ---- Long Method -> Extract Method (mirrors views.LongMethod) ----

	private List<Object> longMethod(Set<ClassObject> classes) {
		List<ASTSliceGroup> groups = new ArrayList<>();
		for (ClassObject classObject : classes) {
			ListIterator<MethodObject> it = classObject.getMethodIterator();
			while (it.hasNext()) {
				processMethod(groups, classObject, it.next());
			}
		}
		List<Object> out = new ArrayList<>();
		for (ASTSliceGroup group : groups) {
			Map<String, Object> g = new LinkedHashMap<>();
			g.put("class", typeName(group.getSourceTypeDeclaration(), null));
			ASTSlice any = group.getCandidates().iterator().next();
			g.put("file", file(any.getIFile()));
			g.put("method", method(group.getSourceMethodDeclaration()));
			VariableDeclaration criterion = group.getLocalVariableCriterion();
			g.put("criterion_variable", criterion == null ? null : criterion.getName().getIdentifier());
			List<Object> list = new ArrayList<>();
			for (ASTSlice slice : group.getCandidates()) {
				Map<String, Object> m = new LinkedHashMap<>();
				m.put("extracted_method_name", slice.getExtractedMethodName());
				m.put("object_slice", slice.isObjectSlice());
				m.put("method_size", slice.getMethodSize());
				m.put("slice_statements", slice.getNumberOfSliceStatements());
				m.put("duplicated_statements", slice.getNumberOfDuplicatedStatements());
				m.put("slice_statement_lines", statementLines(slice.getSliceStatements()));
				m.put("removable_statement_lines", statementLines(slice.getRemovableStatements()));
				list.add(m);
			}
			g.put("candidates", list);
			out.add(g);
		}
		return out;
	}

	/** Copy of LongMethod.processMethod (view) so the runner finds the same candidates as the UI. */
	private static void processMethod(List<ASTSliceGroup> extractedSliceGroups, ClassObject classObject, MethodObject methodObject) {
		if (methodObject.getMethodBody() == null) {
			return;
		}
		int minimumMethodSize = Activator.getDefault().getPreferenceStore().getInt(PreferenceConstants.P_MINIMUM_METHOD_SIZE);
		StatementExtractor statementExtractor = new StatementExtractor();
		int numberOfStatements = statementExtractor.getTotalNumberOfStatements(methodObject.getMethodBody().getCompositeStatement().getStatement());
		if (numberOfStatements < minimumMethodSize) {
			return;
		}
		ITypeRoot typeRoot = classObject.getITypeRoot();
		CompilationUnitCache.getInstance().lock(typeRoot);
		try {
			CFG cfg = new CFG(methodObject);
			PDG pdg = new PDG(cfg, classObject.getIFile(), classObject.getFieldsAccessedInsideMethod(methodObject), null);
			for (VariableDeclaration declaration : pdg.getVariableDeclarationsInMethod()) {
				PlainVariable variable = new PlainVariable(declaration);
				PDGSliceUnionCollection collection = new PDGSliceUnionCollection(pdg, variable);
				List<ASTSlice> slices = new ArrayList<>();
				for (PDGSliceUnion sliceUnion : collection.getSliceUnions()) {
					slices.add(new ASTSlice(sliceUnion));
				}
				addGroup(extractedSliceGroups, slices, collection.getSliceUnions().size());
			}
			for (VariableDeclaration declaration : pdg.getVariableDeclarationsAndAccessedFieldsInMethod()) {
				PlainVariable variable = new PlainVariable(declaration);
				PDGObjectSliceUnionCollection collection = new PDGObjectSliceUnionCollection(pdg, variable);
				List<ASTSlice> slices = new ArrayList<>();
				for (PDGObjectSliceUnion sliceUnion : collection.getSliceUnions()) {
					slices.add(new ASTSlice(sliceUnion));
				}
				addGroup(extractedSliceGroups, slices, collection.getSliceUnions().size());
			}
		} finally {
			CompilationUnitCache.getInstance().releaseLock();
		}
	}

	private static void addGroup(List<ASTSliceGroup> groups, List<ASTSlice> slices, int groupSize) {
		double sumExtracted = 0.0;
		double sumDuplicated = 0.0;
		double sumRatio = 0.0;
		int maxExtracted = 0;
		ASTSliceGroup sliceGroup = new ASTSliceGroup();
		for (ASTSlice slice : slices) {
			if (slice.isVariableCriterionDeclarationStatementIsDeeperNestedThanExtractedMethodInvocationInsertionStatement()) {
				continue;
			}
			int extracted = slice.getNumberOfSliceStatements();
			int duplicated = slice.getNumberOfDuplicatedStatements();
			sumExtracted += extracted;
			sumDuplicated += duplicated;
			sumRatio += (double) duplicated / (double) extracted;
			maxExtracted = Math.max(maxExtracted, extracted);
			sliceGroup.addCandidate(slice);
		}
		if (!sliceGroup.getCandidates().isEmpty()) {
			sliceGroup.setAverageNumberOfExtractedStatementsInGroup(sumExtracted / groupSize);
			sliceGroup.setAverageNumberOfDuplicatedStatementsInGroup(sumDuplicated / groupSize);
			sliceGroup.setAverageDuplicationRatioInGroup(sumRatio / groupSize);
			sliceGroup.setMaximumNumberOfExtractedStatementsInGroup(maxExtracted);
			groups.add(sliceGroup);
		}
	}

	// ---- helpers ----

	private String file(IFile file) {
		if (file == null || file.getLocation() == null) {
			return null;
		}
		return relative(imported.root, file.getLocation().toFile().toPath());
	}

	static String relative(Path root, Path path) {
		Path p = path.toAbsolutePath().normalize();
		String s = p.startsWith(root) ? root.relativize(p).toString() : p.toString();
		return s.replace('\\', '/');
	}

	private static String typeName(TypeDeclaration td, String fallback) {
		if (td != null) {
			ITypeBinding binding = td.resolveBinding();
			if (binding != null) {
				return binding.getQualifiedName();
			}
			return td.getName().getIdentifier();
		}
		return fallback;
	}

	private static List<Object> methods(Set<MethodDeclaration> declarations) {
		List<MethodDeclaration> sorted = new ArrayList<>(declarations);
		sorted.sort(Comparator.comparingInt(ASTNode::getStartPosition));
		List<Object> out = new ArrayList<>();
		for (MethodDeclaration md : sorted) {
			out.add(method(md));
		}
		return out;
	}

	static Map<String, Object> method(MethodDeclaration md) {
		Map<String, Object> m = new LinkedHashMap<>();
		if (md == null) {
			return m;
		}
		m.put("name", md.getName().getIdentifier());
		m.put("signature", signature(md));
		m.put("line", line(md.getName()));
		m.put("line_start", line(md));
		m.put("line_end", endLine(md));
		return m;
	}

	static String signature(MethodDeclaration md) {
		StringBuilder sb = new StringBuilder(md.getName().getIdentifier()).append('(');
		List<?> parameters = md.parameters();
		for (int i = 0; i < parameters.size(); i++) {
			SingleVariableDeclaration p = (SingleVariableDeclaration) parameters.get(i);
			if (i > 0) {
				sb.append(", ");
			}
			sb.append(p.getType().toString());
			for (int d = 0; d < p.getExtraDimensions(); d++) {
				sb.append("[]");
			}
			if (p.isVarargs()) {
				sb.append("...");
			}
		}
		return sb.append(')').toString();
	}

	private static List<Object> fields(Set<VariableDeclaration> fragments) {
		List<VariableDeclaration> sorted = new ArrayList<>(fragments);
		sorted.sort(Comparator.comparingInt(ASTNode::getStartPosition));
		List<Object> out = new ArrayList<>();
		for (VariableDeclaration vd : sorted) {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("name", vd.getName().getIdentifier());
			String type = null;
			if (vd instanceof VariableDeclarationFragment && vd.getParent() instanceof FieldDeclaration fd) {
				type = fd.getType().toString();
				for (int d = 0; d < vd.getExtraDimensions(); d++) {
					type += "[]";
				}
			}
			m.put("type", type);
			m.put("line", line(vd));
			out.add(m);
		}
		return out;
	}

	private static List<Object> statementLines(Set<Statement> statements) {
		List<Statement> sorted = new ArrayList<>(statements);
		sorted.sort(Comparator.comparingInt(ASTNode::getStartPosition));
		List<Object> out = new ArrayList<>();
		for (Statement s : sorted) {
			out.add(java.util.Arrays.asList(line(s), endLine(s)));
		}
		return out;
	}

	private static Integer line(ASTNode node) {
		if (node != null && node.getRoot() instanceof CompilationUnit cu) {
			int l = cu.getLineNumber(node.getStartPosition());
			return l < 0 ? null : l;
		}
		return null;
	}

	private static Integer endLine(ASTNode node) {
		if (node != null && node.getRoot() instanceof CompilationUnit cu) {
			int l = cu.getLineNumber(node.getStartPosition() + Math.max(0, node.getLength() - 1));
			return l < 0 ? null : l;
		}
		return null;
	}
}
