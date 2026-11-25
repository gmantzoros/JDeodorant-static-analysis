package gr.uom.java.jdeodorant.batch;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Command-line options of the batch runner. */
final class Options {

	static final String GOD_CLASS = "god-class";
	static final String FEATURE_ENVY = "feature-envy";
	static final String LONG_METHOD = "long-method";
	static final List<String> ALL_DETECTORS = List.of(GOD_CLASS, FEATURE_ENVY, LONG_METHOD);

	static final String USAGE = String.join("\n",
			"Usage: jdeodorant-batch -data <empty workspace dir> --project <dir> --out <file.json> [options]",
			"",
			"Options:",
			"  --project <dir>          Project to analyse (required)",
			"  --out <file>             JSON file to write (required)",
			"  --source <dir>           Source folder, relative to the project; repeatable (default: src/main/java)",
			"  --classpath-file <file>  Library jars, separated by the platform path separator,",
			"                           as written by 'mvn dependency:build-classpath'",
			"  --mvn <command>          Maven command used when there is no classpath file and the project",
			"                           has a pom.xml (default: mvn)",
			"  --java <level>           Java compliance level, e.g. 17 (default: the running Java version)",
			"  --detectors <list>       Comma-separated: god-class, feature-envy, long-method (default: all)",
			"  --pref NAME=VALUE        Override a JDeodorant preference; NAME is a PreferenceConstants field",
			"                           without the P_ prefix, e.g. MINIMUM_METHOD_SIZE=10; repeatable",
			"  --help                   Show this help",
			"",
			"Exit codes: 0 ok, 1 bad arguments, 2 project import failed, 3 compile errors, 4 internal error");

	Path projectDir;
	Path out;
	final List<String> sources = new ArrayList<>();
	Path classpathFile;
	String mvn = "mvn";
	String javaLevel;
	final Set<String> detectors = new LinkedHashSet<>();
	final Map<String, String> prefs = new LinkedHashMap<>();
	boolean help;

	static Options parse(String[] args) {
		Options o = new Options();
		for (int i = 0; i < args.length; i++) {
			String a = args[i];
			switch (a) {
			case "--help", "-h" -> o.help = true;
			case "--project" -> o.projectDir = Paths.get(value(args, ++i, a)).toAbsolutePath().normalize();
			case "--out" -> o.out = Paths.get(value(args, ++i, a)).toAbsolutePath().normalize();
			case "--source" -> o.sources.add(value(args, ++i, a));
			case "--classpath-file" -> o.classpathFile = Paths.get(value(args, ++i, a)).toAbsolutePath().normalize();
			case "--mvn" -> o.mvn = value(args, ++i, a);
			case "--java" -> o.javaLevel = value(args, ++i, a);
			case "--detectors" -> {
				for (String d : value(args, ++i, a).split(",")) {
					String name = d.trim();
					if (!ALL_DETECTORS.contains(name)) {
						throw new IllegalArgumentException("Unknown detector '" + name + "'. Use: " + String.join(", ", ALL_DETECTORS));
					}
					o.detectors.add(name);
				}
			}
			case "--pref" -> {
				String kv = value(args, ++i, a);
				int eq = kv.indexOf('=');
				if (eq <= 0) {
					throw new IllegalArgumentException("--pref needs NAME=VALUE, got '" + kv + "'");
				}
				o.prefs.put(kv.substring(0, eq).trim(), kv.substring(eq + 1).trim());
			}
			default -> {
				// Eclipse launcher arguments such as -consoleLog can reach the application; ignore them.
				if (a.startsWith("--")) {
					throw new IllegalArgumentException("Unknown option '" + a + "'");
				}
			}
			}
		}
		if (o.help) {
			return o;
		}
		if (o.projectDir == null) {
			throw new IllegalArgumentException("--project is required");
		}
		if (o.out == null) {
			throw new IllegalArgumentException("--out is required");
		}
		if (o.sources.isEmpty()) {
			o.sources.add("src/main/java");
		}
		if (o.detectors.isEmpty()) {
			o.detectors.addAll(ALL_DETECTORS);
		}
		return o;
	}

	private static String value(String[] args, int i, String option) {
		if (i >= args.length || args[i].startsWith("--")) {
			throw new IllegalArgumentException(option + " needs a value");
		}
		return args[i];
	}
}
