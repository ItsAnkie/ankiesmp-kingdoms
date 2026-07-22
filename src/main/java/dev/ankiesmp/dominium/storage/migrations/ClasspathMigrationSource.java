package dev.ankiesmp.dominium.storage.migrations;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@link MigrationSource} die migraties uit de classpath leest via een
 * <b>expliciete geordende lijst</b> van resourcepaden. Bewust géén
 * directory-enumeratie: {@code ClassLoader.getResources("db/migrations")}
 * gedraagt zich verschillend voor {@code file:} vs {@code jar:} en
 * levert bij shaded plugin-jars vaak niets op omdat directory-entries
 * niet in het zip-archief zitten.
 *
 * <p>Als een geregistreerd pad niet bestaat of als er twee entries
 * hetzelfde {@code Vn} versienummer hebben, gooit deze source een
 * {@link IllegalStateException} tijdens startup. Zo faalt de plugin
 * hard i.p.v. stilletjes een migratie over te slaan.
 */
public final class ClasspathMigrationSource implements MigrationSource {

    private static final Pattern FILE_PATTERN = Pattern.compile("V(\\d+)__(.+)\\.sql");

    private final ClassLoader classLoader;
    private final List<String> resourcePaths;

    public ClasspathMigrationSource(ClassLoader classLoader, List<String> resourcePaths) {
        this.classLoader = Objects.requireNonNull(classLoader, "classLoader");
        this.resourcePaths = List.copyOf(resourcePaths);
    }

    public static ClasspathMigrationSource standard(ClassLoader classLoader) {
        return new ClasspathMigrationSource(classLoader, MigrationRegistry.RESOURCE_PATHS);
    }

    @Override
    public List<Migration> load() {
        Set<Integer> seenVersions = new HashSet<>();
        List<Migration> out = new ArrayList<>(resourcePaths.size());

        for (String path : resourcePaths) {
            String leaf = leafOf(path);
            Matcher matcher = FILE_PATTERN.matcher(leaf);
            if (!matcher.matches()) {
                throw new IllegalStateException(
                        "migration resource does not match V{n}__name.sql pattern: " + path);
            }
            int version = Integer.parseInt(matcher.group(1));
            String description = matcher.group(2).replace('_', ' ');

            if (!seenVersions.add(version)) {
                throw new IllegalStateException(
                        "duplicate migration version V" + version + " detected in registry");
            }

            String sql = readResource(path);
            out.add(new Migration(version, description, sql));
        }

        out.sort((a, b) -> Integer.compare(a.version(), b.version()));
        return Collections.unmodifiableList(out);
    }

    private String readResource(String path) {
        try (InputStream stream = classLoader.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException(
                        "migration resource missing on classpath: " + path
                                + " (classloader=" + classLoader + ")");
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int n;
            while ((n = stream.read(chunk)) != -1) buffer.write(chunk, 0, n);
            return buffer.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("failed to read migration resource: " + path, e);
        }
    }

    private static String leafOf(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }
}
