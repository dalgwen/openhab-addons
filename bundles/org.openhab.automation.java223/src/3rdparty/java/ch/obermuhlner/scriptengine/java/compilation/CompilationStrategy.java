package ch.obermuhlner.scriptengine.java.compilation;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import javax.tools.JavaFileObject;

/**
 * This strategy is used to decide what to compile
 */
public interface CompilationStrategy {

    /**
     * Generate a list of JavaFileObject to compile
     *
     * @param simpleClassName the class name of the script
     * @param currentSource The current source script we want to execute
     * @return
     */
    List<JavaFileObject> getJavaFileObjectsToCompile(String simpleClassName, String currentSource);

    /**
     * Get all paths to JAR file to include
     *
     * @return
     */
    default List<Path> getJarsPath() {
        return Collections.emptyList();
    }

    /**
     * Help the compiler to see if the classloader should be rebuild with the jar inside
     *
     * @return true if the jar list changed since the last modification
     */
    default boolean needRebuild() {
        return false;
    }

    /**
     * Event if the class loader has been rebuild, including all JARs
     */
    default void rebuildDone() {
    }

    /**
     * As the script is compiled, this is an opportunity to see if we still want to
     * keep it.
     *
     * @param clazz
     */
    default void compilationResult(Class<?> clazz) {
    }

}
