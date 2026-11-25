package gr.uom.java.jdeodorant.batch;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceDescription;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaModelMarker;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.launching.JavaRuntime;

/**
 * Creates a Java project in the (fresh) workspace for the project under analysis. Source
 * folders are linked, not copied, and nothing is written into the analysed project.
 */
final class ProjectImporter {

	static final class ImportException extends Exception {
		private static final long serialVersionUID = 1L;

		ImportException(String message) {
			super(message);
		}
	}

	static final class Imported {
		final IJavaProject javaProject;
		final Path root;
		final List<Path> sources;
		final List<String> classpath;
		final int missingClasspathEntries;
		final String javaLevel;
		final String gitCommit;

		Imported(IJavaProject javaProject, Path root, List<Path> sources, List<String> classpath,
				int missingClasspathEntries, String javaLevel, String gitCommit) {
			this.javaProject = javaProject;
			this.root = root;
			this.sources = sources;
			this.classpath = classpath;
			this.missingClasspathEntries = missingClasspathEntries;
			this.javaLevel = javaLevel;
			this.gitCommit = gitCommit;
		}

		Map<String, Object> describe() {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("path", root.toString());
			m.put("git_commit", gitCommit);
			List<String> rel = new ArrayList<>();
			for (Path s : sources) {
				rel.add(Detectors.relative(root, s));
			}
			m.put("source_dirs", rel);
			m.put("classpath_entries", classpath.size());
			m.put("missing_classpath_entries", missingClasspathEntries);
			m.put("java_compliance", javaLevel);
			return m;
		}
	}

	private ProjectImporter() {
	}

	static Imported importProject(Options o) throws ImportException, CoreException {
		Path root = o.projectDir;
		if (!Files.isDirectory(root)) {
			throw new ImportException("Project folder not found: " + root);
		}
		List<Path> sources = new ArrayList<>();
		for (String s : o.sources) {
			Path p = root.resolve(s).normalize();
			if (!Files.isDirectory(p)) {
				throw new ImportException("Source folder not found: " + p);
			}
			sources.add(p);
		}

		List<String> classpath = readClasspath(o, root);
		List<String> existing = new ArrayList<>();
		for (String entry : classpath) {
			if (Files.exists(Path.of(entry))) {
				existing.add(entry);
			}
		}
		int missing = classpath.size() - existing.size();

		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		IWorkspaceDescription wd = workspace.getDescription();
		wd.setAutoBuilding(false);
		workspace.setDescription(wd);

		String name = "jd-" + root.getFileName().toString().replaceAll("[^A-Za-z0-9._-]", "_");
		IProject project = workspace.getRoot().getProject(name);
		if (project.exists()) {
			throw new ImportException("The workspace already contains a project named " + name
					+ ". Pass an empty folder with -data.");
		}
		project.create(null);
		project.open(null);
		IProjectDescription description = project.getDescription();
		description.setNatureIds(new String[] { JavaCore.NATURE_ID });
		project.setDescription(description, null);

		List<IClasspathEntry> entries = new ArrayList<>();
		for (int i = 0; i < sources.size(); i++) {
			IFolder folder = project.getFolder(i == 0 ? "src" : "src" + i);
			folder.createLink(org.eclipse.core.runtime.Path.fromOSString(sources.get(i).toString()), IResource.NONE, null);
			entries.add(JavaCore.newSourceEntry(folder.getFullPath()));
		}
		for (String entry : existing) {
			entries.add(JavaCore.newLibraryEntry(org.eclipse.core.runtime.Path.fromOSString(entry), null, null));
		}
		entries.add(JavaRuntime.getDefaultJREContainerEntry());
		IFolder bin = project.getFolder("bin");
		if (!bin.exists()) {
			bin.create(true, true, null);
		}

		IJavaProject javaProject = JavaCore.create(project);
		javaProject.setRawClasspath(entries.toArray(new IClasspathEntry[0]), bin.getFullPath(), null);
		String level = o.javaLevel != null ? o.javaLevel : defaultJavaLevel();
		Map<String, String> options = new HashMap<>(javaProject.getOptions(true));
		JavaCore.setComplianceOptions(level, options);
		javaProject.setOptions(options);

		return new Imported(javaProject, root, sources, existing, missing, level, gitCommit(root));
	}

	/**
	 * The running JRE's version, capped at the newest level the compiler supports. The project is
	 * compiled against the running JRE's class library, which cannot provide a newer release.
	 */
	private static String defaultJavaLevel() {
		int runtime = Runtime.version().feature();
		String latest = JavaCore.latestSupportedJavaVersion();
		try {
			int latestFeature = Integer.parseInt(latest.startsWith("1.") ? latest.substring(2) : latest);
			return Integer.toString(Math.min(runtime, latestFeature));
		} catch (NumberFormatException e) {
			return Integer.toString(runtime);
		}
	}

	/** Builds the project and returns its compile and build-path errors (empty when it compiles). */
	static List<Map<String, Object>> compileErrors(Imported imported) throws CoreException {
		IProject project = imported.javaProject.getProject();
		project.refreshLocal(IResource.DEPTH_INFINITE, null);
		project.build(IncrementalProjectBuilder.FULL_BUILD, null);
		List<Map<String, Object>> errors = new ArrayList<>();
		for (String type : new String[] { IJavaModelMarker.BUILDPATH_PROBLEM_MARKER, IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER }) {
			for (IMarker marker : project.findMarkers(type, true, IResource.DEPTH_INFINITE)) {
				if (marker.getAttribute(IMarker.SEVERITY, -1) != IMarker.SEVERITY_ERROR) {
					continue;
				}
				Map<String, Object> e = new LinkedHashMap<>();
				IResource resource = marker.getResource();
				String file = null;
				if (resource != null && resource.getLocation() != null) {
					file = Detectors.relative(imported.root, resource.getLocation().toFile().toPath());
				}
				e.put("file", file);
				int line = marker.getAttribute(IMarker.LINE_NUMBER, -1);
				e.put("line", line < 0 ? null : line);
				e.put("message", marker.getAttribute(IMarker.MESSAGE, ""));
				errors.add(e);
			}
		}
		return errors;
	}

	private static List<String> readClasspath(Options o, Path root) throws ImportException {
		Path file = o.classpathFile;
		if (file == null) {
			if (!Files.exists(root.resolve("pom.xml"))) {
				return List.of();
			}
			file = runMaven(o.mvn, root);
		}
		try {
			String text = Files.readString(file, StandardCharsets.UTF_8).trim();
			List<String> entries = new ArrayList<>();
			if (!text.isEmpty()) {
				for (String e : text.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
					if (!e.isBlank()) {
						entries.add(e.trim());
					}
				}
			}
			return entries;
		} catch (IOException e) {
			throw new ImportException("Cannot read classpath file " + file + ": " + e.getMessage());
		}
	}

	private static Path runMaven(String mvn, Path root) throws ImportException {
		try {
			Path out = Files.createTempFile("jdeodorant-classpath", ".txt");
			List<String> command = new ArrayList<>();
			if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
				command.add("cmd");
				command.add("/c");
			}
			command.add(mvn);
			command.add("-q");
			command.add("-B");
			command.add("dependency:build-classpath");
			command.add("-Dmdep.outputFile=" + out);
			Process process = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).start();
			String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			if (!process.waitFor(30, TimeUnit.MINUTES)) {
				process.destroyForcibly();
				throw new ImportException("Maven timed out while resolving the classpath");
			}
			if (process.exitValue() != 0) {
				String tail = output.length() > 2000 ? output.substring(output.length() - 2000) : output;
				throw new ImportException("Maven failed to resolve the classpath (exit " + process.exitValue() + "):\n" + tail);
			}
			return out;
		} catch (IOException e) {
			throw new ImportException("Cannot run Maven ('" + mvn + "'): " + e.getMessage());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new ImportException("Interrupted while running Maven");
		}
	}

	private static String gitCommit(Path root) {
		try {
			Process process = new ProcessBuilder("git", "-C", root.toString(), "rev-parse", "HEAD")
					.redirectErrorStream(true).start();
			String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
			if (process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0 && output.matches("[0-9a-f]{40}")) {
				return output;
			}
		} catch (IOException e) {
			// git not installed or not a repository
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		return null;
	}
}
