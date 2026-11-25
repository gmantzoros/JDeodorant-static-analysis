# JDeodorant with LLM context export

This is a fork of [JDeodorant](https://github.com/tsantalis/JDeodorant), based on the upstream `JLS8-support` branch at commit `f673c722` (release v5.0.85, 2025-01-05). It adds a layer that turns JDeodorant's static analysis of a class into a prompt for an LLM, and exports JDeodorant's Extract Class (God Class) candidates as JSON.

The tag `thesis-final` (commit `46cdb9e5`) marks the exact code and data used for a pilot study (October 2025 – February 2026). That study compared LLM Extract Class suggestions, made with and without this context, against JDeodorant's candidates.

## Batch runner

The `batch-runner` branch adds a command-line version of JDeodorant. It analyses a whole project without the Eclipse UI and writes every God Class, Feature Envy and Long Method suggestion to a JSON file.

**Build** (needs JDK 21 and Maven 3.9; no Eclipse IDE):

```
mvn -B -f releng/pom.xml clean verify
```

This produces a runnable headless Eclipse for Windows and Linux in `releng/product/target/products/`. The archives are `jdeodorant.batch-win32.win32.x86_64.zip` and `jdeodorant.batch-linux.gtk.x86_64.tar.gz`.

**Run** with an empty workspace folder each time:

```
jdeodorant-batchc -data <empty folder> --project <project folder> --out result.json
```

- Use `jdeodorant-batchc.exe` on Windows (console version) and `jdeodorant-batch` on Linux.
- Java 21 must be on the PATH.
- For a Maven project, the runner calls `mvn dependency:build-classpath` to find the library jars, so Maven must be on the PATH too. Or pass the jars with `--classpath-file`.
- Nothing is written into the analysed project.
- Run with `--help` for all options: source folders, Java level, which detectors, and preference overrides.

**Exit codes:** 0 ok, 1 bad arguments, 2 project import failed, 3 the project doesn't compile, 4 internal error. With exit code 3, the JSON lists the compile errors.

**Output.** The JSON records:
- the JDeodorant, JDT and Java versions;
- every JDeodorant preference used;
- the project's git commit;
- a summary.

It then lists the candidates of each detector:
- `god_class`: one entry per class, with ranked Extract Class candidates (methods with signatures and lines, fields, dependency counts) and JDeodorant's concept groups.
- `feature_envy`: ranked Move Method candidates with source and target class.
- `long_method`: Extract Method slice groups per method and variable, with the line ranges of the extracted statements.

The detection code mirrors the plug-in's views rather than `gr.uom.java.ast.Standalone`. Standalone keeps results in sorted sets that can drop distinct candidates which compare as equal.

**Known limits:**
- Single-module Maven projects or plain source folders only; no Gradle or multi-module support yet.
- No annotation processing, so Lombok projects don't compile.
- Long Method is slow: about 3 minutes for 450 methods.

## What this fork adds

Detection algorithms and parser settings are unchanged from upstream. The additions are:

- `src/gr/uom/java/ast/context/`: context objects built from JDeodorant's model. They hold class metrics, dependencies, which methods use each field, method calls and the class source.
- **Generate Context** (Bad Smells menu) and the **Context View**: render a prompt for a selected class from a FreeMarker template.
- **Export Identify JSON** (God Class view): writes `<project>/jdeodorant_cards/<ClassName>_identify_preview.json` with the method names of each candidate.
- `templates/`: 9 FreeMarker prompt templates, with and without context. `v0`–`v2` are earlier prompt iterations.
- `lib/freemarker.jar`: FreeMarker 2.3.34.
- `study/`: the pilot data for 55 classes from 8 project folders:
  - `jdeodorant/`: JDeodorant's candidates.
  - `context/` and `noContext/`: the LLM's answers with and without context.
  - `compare_clusters.py`: computes GRI, AGRI and BCubed-F1 for one class.
  - `results_jd.xlsx`: the collected scores.

## How the pilot was run

Everything was manual, one class at a time:

1. In Eclipse with PDE (plus Draw2d/GEF), import this repository and launch an "Eclipse Application".
2. In the runtime workspace, import the subject project. It must compile with no errors.
3. Run Bad Smells → God Class → Identify, select a candidate, then use Export Identify JSON.
4. Run Bad Smells → Generate Context, open the Context View, pick a class and a template, and copy the prompt.
5. Paste the prompt into an LLM chat and save the JSON answer under `study/{context,noContext}/<project>/<id>`.
6. Run `python study/compare_clusters.py <jdeodorant.json> <llm_no_context.json> <llm_context.json>` and copy the numbers into `results_jd.xlsx`.

## Not recorded

Fill these in when known:

- Eclipse version: TODO
- JDK version: TODO
- LLM used (model, version, date, settings): TODO
- Which template produced each file in `study/context/` and `study/noContext/`: TODO
- Commit of each subject project that was analysed: not recorded

## Known issues

- **Usage reporting is on by default.** JDeodorant sends project and class names to a Concordia University server. Turn it off in the JDeodorant preferences before analysing new code.
- **Templates are not packaged.** `build.properties` does not list `templates/` or `icons/`, so an exported plug-in jar would not contain them. Prompt generation only works when the plug-in is launched from source.
- **"CBO" is mislabelled.** The value shown as CBO in the prompts is the mean of JDeodorant's import coupling (`MMImportCoupling`), not Chidamber–Kemerer CBO. It falls back to 0.0 silently if the metric cannot be read.
- **Exported files can overwrite each other.** JSON files are named by simple class name, so classes with the same name in different packages collide.
- **`compare_clusters.py` has scoring limits.**
  - It scores only methods that appear in at least one of the three outputs.
  - It compares methods by simple name, so overloads merge.
  - BCubed keeps only the last overlapping JDeodorant candidate for each method.
- **Newer Java features may be missed.** JDeodorant does not model records, switch expressions or pattern matching. This is from reading the code and has not been tested.
