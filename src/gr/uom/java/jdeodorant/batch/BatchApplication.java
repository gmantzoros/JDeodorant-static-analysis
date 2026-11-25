package gr.uom.java.jdeodorant.batch;

import gr.uom.java.ast.CompilationErrorDetectedException;
import gr.uom.java.jdeodorant.preferences.PreferenceConstants;
import gr.uom.java.jdeodorant.refactoring.Activator;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Platform;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.jface.preference.IPreferenceStore;
import org.osgi.framework.Bundle;

/**
 * Headless entry point: analyses one project with JDeodorant and writes all refactoring
 * suggestions to a JSON file. Run with a fresh workspace each time, for example:
 * <pre>jdeodorant-batch -data /tmp/ws --project /path/to/project --out result.json</pre>
 */
public class BatchApplication implements IApplication {

	static final String RUNNER_VERSION = "0.1.0";
	static final int EXIT_USAGE = 1;
	static final int EXIT_IMPORT = 2;
	static final int EXIT_COMPILE = 3;
	static final int EXIT_INTERNAL = 4;
	private static final int MAX_ERRORS_LISTED = 100;

	@Override
	public Object start(IApplicationContext context) throws Exception {
		String[] args = (String[]) context.getArguments().get(IApplicationContext.APPLICATION_ARGS);
		Options options;
		try {
			options = Options.parse(args == null ? new String[0] : args);
		} catch (IllegalArgumentException e) {
			System.err.println("Error: " + e.getMessage());
			System.err.println(Options.USAGE);
			return EXIT_USAGE;
		}
		if (options.help) {
			System.out.println(Options.USAGE);
			return IApplication.EXIT_OK;
		}

		long started = System.currentTimeMillis();
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("format_version", 1);
		result.put("started_at", Instant.ofEpochMilli(started).toString());
		result.put("tool", toolInfo());
		int exit = IApplication.EXIT_OK;
		try {
			result.put("settings", applySettings(options.prefs));
			result.put("detectors", List.copyOf(options.detectors));
			ProjectImporter.Imported imported = ProjectImporter.importProject(options);
			result.put("project", imported.describe());
			List<Map<String, Object>> errors = ProjectImporter.compileErrors(imported);
			if (!errors.isEmpty()) {
				exit = EXIT_COMPILE;
				result.put("status", "compile_errors");
				result.put("compile_error_count", errors.size());
				result.put("compile_errors", errors.subList(0, Math.min(errors.size(), MAX_ERRORS_LISTED)));
			} else {
				Map<String, Object> found = new Detectors(imported, options).run();
				result.put("status", "ok");
				result.putAll(found);
			}
		} catch (ProjectImporter.ImportException e) {
			exit = EXIT_IMPORT;
			result.put("status", "import_failed");
			result.put("error", e.getMessage());
		} catch (CompilationErrorDetectedException e) {
			exit = EXIT_COMPILE;
			result.put("status", "compile_errors");
			result.put("compile_error_count", e.getMarkers().size());
		} catch (Exception | LinkageError e) {
			exit = EXIT_INTERNAL;
			result.put("status", "internal_error");
			result.put("error", e.toString());
			e.printStackTrace();
		}
		result.put("duration_ms", System.currentTimeMillis() - started);
		Json.write(result, options.out);
		System.out.println("JDeodorant batch: status=" + result.get("status") + ", output=" + options.out);
		try {
			ResourcesPlugin.getWorkspace().save(true, null);
		} catch (Exception e) {
			// not important for a throw-away workspace
		}
		return exit;
	}

	@Override
	public void stop() {
	}

	private static Map<String, Object> toolInfo() {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("name", "JDeodorant");
		m.put("version", bundleVersion(Activator.PLUGIN_ID));
		m.put("runner_version", RUNNER_VERSION);
		m.put("jdt_core_version", bundleVersion("org.eclipse.jdt.core"));
		m.put("java_runtime", System.getProperty("java.version"));
		return m;
	}

	private static String bundleVersion(String id) {
		Bundle b = Platform.getBundle(id);
		return b == null ? null : b.getVersion().toString();
	}

	/**
	 * Turns usage reporting off, applies --pref overrides and returns every JDeodorant
	 * preference value used for the run.
	 */
	private static Map<String, Object> applySettings(Map<String, String> overrides) throws IllegalAccessException {
		IPreferenceStore store = Activator.getDefault().getPreferenceStore();
		store.setValue(PreferenceConstants.P_ENABLE_USAGE_REPORTING, false);
		store.setValue(PreferenceConstants.P_ENABLE_SOURCE_CODE_REPORTING, false);
		Map<String, String> keys = new LinkedHashMap<>();
		for (Field f : PreferenceConstants.class.getFields()) {
			if (Modifier.isStatic(f.getModifiers()) && f.getType() == String.class && f.getName().startsWith("P_")) {
				keys.put(f.getName().substring(2), (String) f.get(null));
			}
		}
		for (Map.Entry<String, String> o : overrides.entrySet()) {
			String key = keys.get(o.getKey());
			if (key == null) {
				throw new IllegalArgumentException("Unknown preference '" + o.getKey() + "'. Known: " + String.join(", ", keys.keySet()));
			}
			store.setValue(key, o.getValue());
		}
		Map<String, Object> snapshot = new LinkedHashMap<>();
		for (Map.Entry<String, String> k : keys.entrySet()) {
			snapshot.put(k.getKey(), store.getString(k.getValue()));
		}
		return snapshot;
	}
}
