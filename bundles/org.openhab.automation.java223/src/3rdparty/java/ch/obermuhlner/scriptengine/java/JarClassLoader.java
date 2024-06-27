package ch.obermuhlner.scriptengine.java;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NonNullByDefault
public class JarClassLoader extends ClassLoader {
    private final Logger logger = LoggerFactory.getLogger(JarClassLoader.class);

    public static final String CLASS_FILE_TYPE = ".class";

    private final Map<String, Path> availableClasses = new HashMap<>();

    public JarClassLoader(@Nullable ClassLoader parent) {
        super(parent);
    }

    public void addJar(Path path) {
        try (JarFile jarFile = new JarFile(path.toFile())) {
            jarFile.stream().map(JarEntry::getName).filter(p -> p.endsWith(CLASS_FILE_TYPE))
                    .forEach(className -> availableClasses.put(className, path));
        } catch (IOException e) {
            logger.warn("Failed to process '{}': {}", path, e.getMessage());
        }
    }

    @Override
    protected Class<?> findClass(@Nullable String name) throws ClassNotFoundException {
        if (name == null) {
            throw new ClassNotFoundException();
        }
        String path = name.replace('.', '/').concat(".class");
        Path jarPath = availableClasses.get(path);
        if (jarPath == null) {
            throw new ClassNotFoundException();
        }
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            JarEntry jarEntry = (JarEntry) jarFile.getEntry(path);
            if (jarEntry == null) {
                throw new FileNotFoundException();
            }
            byte[] clazzBytes = jarFile.getInputStream(jarEntry).readAllBytes();
            return defineClass(name, clazzBytes, 0, clazzBytes.length);
        } catch (IOException e) {
            logger.warn("Failed to load class '{}' from the stored location '{}': {}", name, jarPath, e.getMessage());
            throw new ClassNotFoundException();
        }
    }
}
