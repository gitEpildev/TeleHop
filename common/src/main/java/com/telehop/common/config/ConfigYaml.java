package com.telehop.common.config;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Restricted YAML documents shared by both platforms. Parser exceptions may include secret values. */
public final class ConfigYaml {
    private ConfigYaml() {}

    public static Map<String, Object> read(Path path) throws IOException {
        return parse(checkedText(path), path.toString());
    }

    /** Read and validate once before passing the document to a platform parser that retains comments. */
    public static String readText(Path path) throws IOException {
        String content = checkedText(path);
        parse(content, path.toString());
        return content;
    }

    private static String checkedText(Path path) throws IOException {
        if (!Files.isRegularFile(path) || Files.isSymbolicLink(path) || Files.size(path) > 2_000_000) {
            throw new IOException("Expected a regular configuration file of up to 2 MB at " + path);
        }
        return Files.readString(path);
    }

    public static Map<String, Object> parse(String text, String source) {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(0);
        options.setNestingDepthLimit(40);
        options.setCodePointLimit(2_000_000);
        try {
            Object value = new Yaml(new SafeConstructor(options)).load(text);
            if (value == null) return new LinkedHashMap<>();
            if (!(value instanceof Map<?, ?> mapping)) throw new IllegalArgumentException();
            validate(mapping);
            Map<String, Object> result = new LinkedHashMap<>();
            mapping.forEach((key, entry) -> result.put((String) key, entry));
            return result;
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("Invalid or unsupported YAML in " + source);
        }
    }

    private static void validate(Object value) {
        if (value instanceof Map<?, ?> map) {
            for (var entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key) || key.equals("==")) throw new IllegalArgumentException();
                validate(entry.getValue());
            }
        } else if (value instanceof List<?> list) {
            list.forEach(ConfigYaml::validate);
        } else if (value != null && !(value instanceof String || value instanceof Number || value instanceof Boolean)) {
            throw new IllegalArgumentException();
        }
    }

    public static String dump(Map<String, ?> map) {
        var options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        return new Yaml(options).dump(map);
    }

    public static Object get(Map<?, ?> map, String path, Object fallback) {
        Object node = map;
        for (String key : path.split("\\.")) {
            if (!(node instanceof Map<?, ?> section)) throw new IllegalArgumentException(path + " requires a mapping");
            if (!section.containsKey(key)) return fallback;
            node = section.get(key);
        }
        return node;
    }

    public static boolean flag(Map<?, ?> map, String path, boolean fallback) {
        if (get(map, path, fallback) instanceof Boolean answer) return answer;
        throw new IllegalArgumentException(path + " must be true or false");
    }
}
