package com.jenkinschina.atom.filestool;

import com.jenkinschina.atom.sdk.AtomContext;
import com.jenkinschina.atom.sdk.AtomResult;
import com.jenkinschina.atom.sdk.AtomTask;
import com.jenkinschina.atom.sdk.ErrorType;
import com.jenkinschina.atom.sdk.Glob;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * The file-shaped steps of pipeline-utility-steps as one atom: touch, sha256, findFiles and readCSV.
 *
 * <p>Four operations that pipelines reach for constantly and that otherwise need a shell snippet each — with the same
 * workspace guard on every path.
 */
public class FilesToolAtom implements AtomTask {

    @Override
    public AtomResult run(AtomContext context) throws Exception {
        Path workspace = context.getWorkspace();
        String operation = context.getParam("operation", "sha256").trim().toLowerCase(Locale.ROOT);
        return switch (operation) {
            case "touch" -> touch(context, workspace);
            case "sha256" -> sha256(context, workspace);
            case "find" -> find(context, workspace);
            case "readcsv" -> readCsv(context, workspace);
            default -> AtomResult.error(ErrorType.USER, 40001,
                    "unknown operation '" + operation + "'; use touch, sha256, find or readcsv");
        };
    }

    /** touch: create the files (and their directories) with the current time. */
    private AtomResult touch(AtomContext context, Path workspace) throws Exception {
        String files = context.getParam("files", "");
        if (files.isBlank()) {
            return AtomResult.error(ErrorType.USER, 40002, "files is required for touch");
        }
        List<String> created = new ArrayList<>();
        for (String name : files.split(",")) {
            String trimmed = name.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            Path path = inside(workspace, trimmed);
            if (path == null) {
                return AtomResult.error(ErrorType.USER, 40003, "path outside the workspace: " + trimmed);
            }
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            if (!Files.exists(path)) {
                Files.createFile(path);
            } else {
                Files.setLastModifiedTime(path, java.nio.file.attribute.FileTime.from(Instant.now()));
            }
            created.add(trimmed);
        }
        return AtomResult.success().data("touched", String.join(",", created))
                .data("count", String.valueOf(created.size()));
    }

    /** sha256: checksums of the matched files, the way `sha256sum` would print them. */
    private AtomResult sha256(AtomContext context, Path workspace) throws Exception {
        String pattern = context.getParam("pattern", "**/*.jar");
        String output = context.getParam("output", "checksums.sha256");
        List<Path> files = match(workspace, pattern);
        if (files.isEmpty()) {
            return AtomResult.error(ErrorType.USER, 40004, "no file matched " + pattern);
        }
        StringBuilder lines = new StringBuilder();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (Path file : files) {
            byte[] hash = digest.digest(Files.readAllBytes(file));
            lines.append(HexFormat.of().formatHex(hash)).append("  ")
                    .append(workspace.relativize(file)).append('\n');
        }
        Path out = inside(workspace, output);
        if (out == null) {
            return AtomResult.error(ErrorType.USER, 40005, "output outside the workspace: " + output);
        }
        if (out.getParent() != null) {
            Files.createDirectories(out.getParent());
        }
        Files.writeString(out, lines.toString(), StandardCharsets.UTF_8);
        return AtomResult.success()
                .data("files", String.valueOf(files.size()))
                .data("output", output)
                .artifact("checksums", output);
    }

    /** find: the matched paths as an output, plus the count. */
    private AtomResult find(AtomContext context, Path workspace) throws Exception {
        String pattern = context.getParam("pattern", "**/*");
        List<Path> files = match(workspace, pattern);
        List<String> names = files.stream().map(path -> workspace.relativize(path).toString()).toList();
        return AtomResult.success()
                .data("count", String.valueOf(names.size()))
                .data("first", names.isEmpty() ? "" : names.get(0))
                .data("files", String.join(",", names.size() > 100 ? names.subList(0, 100) : names));
    }

    /** readcsv: one column of a CSV as a variable (first row) or as a comma separated list. */
    private AtomResult readCsv(AtomContext context, Path workspace) throws Exception {
        String file = context.getParam("file", "");
        String column = context.getParam("column", "");
        Path path = file.isBlank() ? null : inside(workspace, file);
        if (path == null || !Files.isRegularFile(path)) {
            return AtomResult.error(ErrorType.USER, 40006, "no such csv file: " + file);
        }
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.isEmpty()) {
            return AtomResult.error(ErrorType.USER, 40007, "the csv file is empty: " + file);
        }
        List<String> header = split(lines.get(0));
        int index = column.isBlank() ? 0 : header.indexOf(column);
        if (index < 0) {
            return AtomResult.error(ErrorType.USER, 40008, "no column '" + column + "' in " + header);
        }
        List<String> values = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            List<String> row = split(lines.get(i));
            if (row.size() > index) {
                values.add(row.get(index));
            }
        }
        return AtomResult.success()
                .data("rows", String.valueOf(values.size()))
                .data("first", values.isEmpty() ? "" : values.get(0))
                .data("values", String.join(",", values));
    }

    private static Path inside(Path workspace, String name) {
        Path path = workspace.resolve(name).normalize();
        return path.startsWith(workspace) ? path : null;
    }

    private static List<Path> match(Path workspace, String pattern) throws Exception {
        // Glob from the SDK: **/ means zero or more directories, so src/**/*.py finds src/calc.py
        return Glob.find(workspace, pattern);
    }

    private static List<String> split(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (char c : line.toCharArray()) {
            if (c == '"') {
                quoted = !quoted;
            } else if (c == ',' && !quoted) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        values.add(current.toString());
        return values;
    }
}
