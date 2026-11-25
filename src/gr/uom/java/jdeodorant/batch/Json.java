package gr.uom.java.jdeodorant.batch;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON writer for the batch runner. Supports null, String, Number,
 * Boolean, Map (string keys, insertion order kept) and List.
 */
final class Json {

	private Json() {
	}

	static void write(Object value, Path file) throws IOException {
		Path parent = file.toAbsolutePath().getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
			write(value, w, 0);
			w.write('\n');
		}
	}

	private static void write(Object value, Writer w, int indent) throws IOException {
		if (value == null) {
			w.write("null");
		} else if (value instanceof String s) {
			writeString(s, w);
		} else if (value instanceof Double d && (d.isNaN() || d.isInfinite())) {
			w.write("null");
		} else if (value instanceof Number || value instanceof Boolean) {
			w.write(value.toString());
		} else if (value instanceof Map<?, ?> map) {
			if (map.isEmpty()) {
				w.write("{}");
				return;
			}
			w.write("{\n");
			Iterator<? extends Map.Entry<?, ?>> it = map.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<?, ?> e = it.next();
				pad(w, indent + 1);
				writeString(String.valueOf(e.getKey()), w);
				w.write(": ");
				write(e.getValue(), w, indent + 1);
				if (it.hasNext()) {
					w.write(',');
				}
				w.write('\n');
			}
			pad(w, indent);
			w.write('}');
		} else if (value instanceof List<?> list) {
			if (list.isEmpty()) {
				w.write("[]");
				return;
			}
			boolean simple = list.stream().allMatch(v -> v == null || v instanceof Number || v instanceof Boolean);
			if (simple) {
				w.write('[');
				for (int i = 0; i < list.size(); i++) {
					if (i > 0) {
						w.write(", ");
					}
					write(list.get(i), w, indent);
				}
				w.write(']');
				return;
			}
			w.write("[\n");
			for (int i = 0; i < list.size(); i++) {
				pad(w, indent + 1);
				write(list.get(i), w, indent + 1);
				if (i < list.size() - 1) {
					w.write(',');
				}
				w.write('\n');
			}
			pad(w, indent);
			w.write(']');
		} else {
			writeString(value.toString(), w);
		}
	}

	private static void pad(Writer w, int indent) throws IOException {
		for (int i = 0; i < indent; i++) {
			w.write("  ");
		}
	}

	private static void writeString(String s, Writer w) throws IOException {
		w.write('"');
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
			case '"' -> w.write("\\\"");
			case '\\' -> w.write("\\\\");
			case '\n' -> w.write("\\n");
			case '\r' -> w.write("\\r");
			case '\t' -> w.write("\\t");
			case '\b' -> w.write("\\b");
			case '\f' -> w.write("\\f");
			default -> {
				if (c < 0x20) {
					w.write(String.format("\\u%04x", (int) c));
				} else {
					w.write(c);
				}
			}
			}
		}
		w.write('"');
	}
}
