package de.example.reports.integrity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

public final class IntegrityManifest {
    private static final String MANIFEST_ENTRY = "META-INF/reports-integrity.sha256";

    private IntegrityManifest() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 2 && "verify".equals(args[0])) {
            verify(Path.of(args[1]));
            System.out.println("Plugin JAR integrity verified.");
            return;
        }
        if (args.length != 3 || !"generate".equals(args[0])) {
            throw new IllegalArgumentException(
                    "Usage: generate <plugin.jar> <output-manifest> | verify <plugin.jar>"
            );
        }

        Path jarPath = Path.of(args[1]);
        Path manifestPath = Path.of(args[2]);
        Map<String, String> hashes = hashes(jarPath);
        String contents = manifestContents(hashes);

        Path manifestParent = manifestPath.toAbsolutePath().getParent();
        if (manifestParent != null) {
            Files.createDirectories(manifestParent);
        }
        Files.writeString(manifestPath, contents, StandardCharsets.UTF_8);

        Path jarParent = jarPath.toAbsolutePath().getParent();
        Path temporaryJar = Files.createTempFile(jarParent, "reports-integrity-", ".jar");
        try {
            addManifestToJar(jarPath, temporaryJar, contents);
            try {
                Files.move(temporaryJar, jarPath, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporaryJar, jarPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporaryJar);
        }
    }

    public static void verify(Path jarPath) throws IOException {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            JarEntry manifestEntry = jar.getJarEntry(MANIFEST_ENTRY);
            if (manifestEntry == null) {
                throw new IOException("Embedded integrity manifest is missing.");
            }

            Map<String, String> expected = new TreeMap<>();
            try (BufferedReader reader = new BufferedReader(
                    new java.io.InputStreamReader(jar.getInputStream(manifestEntry), StandardCharsets.UTF_8))) {
                String line;
                int lineNumber = 0;
                while ((line = reader.readLine()) != null) {
                    lineNumber++;
                    if (line.isBlank() || line.startsWith("#")) {
                        continue;
                    }
                    int separator = line.indexOf("  ");
                    if (separator != 64 || line.length() <= separator + 2) {
                        throw new IOException("Malformed integrity manifest at line " + lineNumber + ".");
                    }
                    String hash = line.substring(0, separator);
                    String entryName = line.substring(separator + 2);
                    if (!hash.matches("[0-9a-f]{64}") || expected.put(entryName, hash) != null) {
                        throw new IOException("Invalid or duplicate integrity entry at line " + lineNumber + ".");
                    }
                }
            }

            Map<String, String> actual = hashes(jar);
            if (!expected.keySet().equals(actual.keySet())) {
                throw new IOException("Plugin JAR file entries do not match the integrity manifest.");
            }
            for (Map.Entry<String, String> entry : expected.entrySet()) {
                if (!entry.getValue().equals(actual.get(entry.getKey()))) {
                    throw new IOException("Integrity check failed for JAR entry: " + entry.getKey());
                }
            }
        }
    }

    private static Map<String, String> hashes(Path jarPath) throws IOException {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            return hashes(jar);
        }
    }

    private static Map<String, String> hashes(JarFile jar) throws IOException {
        Map<String, String> hashes = new TreeMap<>();
        var entries = jar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            if (entry.isDirectory() || MANIFEST_ENTRY.equals(entry.getName())) {
                continue;
            }
            try (InputStream input = jar.getInputStream(entry)) {
                hashes.put(entry.getName(), sha256(input));
            }
        }
        return hashes;
    }

    private static String sha256(InputStream input) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable.", e);
        }
    }

    private static String manifestContents(Map<String, String> hashes) {
        StringBuilder contents = new StringBuilder("# SHA-256 of every file entry except this manifest\n");
        hashes.forEach((name, hash) -> contents.append(hash).append("  ").append(name).append('\n'));
        return contents.toString();
    }

    private static void addManifestToJar(Path sourcePath, Path outputPath, String contents) throws IOException {
        try (JarFile source = new JarFile(sourcePath.toFile());
             JarOutputStream output = new JarOutputStream(Files.newOutputStream(outputPath))) {
            var entries = source.entries();
            byte[] buffer = new byte[8192];
            while (entries.hasMoreElements()) {
                JarEntry sourceEntry = entries.nextElement();
                if (MANIFEST_ENTRY.equals(sourceEntry.getName())) {
                    continue;
                }
                JarEntry outputEntry = new JarEntry(sourceEntry.getName());
                outputEntry.setTime(sourceEntry.getTime());
                output.putNextEntry(outputEntry);
                if (!sourceEntry.isDirectory()) {
                    try (InputStream input = source.getInputStream(sourceEntry)) {
                        int read;
                        while ((read = input.read(buffer)) != -1) {
                            output.write(buffer, 0, read);
                        }
                    }
                }
                output.closeEntry();
            }

            output.putNextEntry(new JarEntry(MANIFEST_ENTRY));
            output.write(contents.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
    }
}
