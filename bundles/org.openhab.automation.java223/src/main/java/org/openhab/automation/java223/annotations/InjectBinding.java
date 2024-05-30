package org.openhab.automation.java223.annotations;

import static org.openhab.automation.java223.common.Java223Constants.ANNOTATION_DEFAULT;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link Inject}
 *
 * @author Gwendal Roulleau - tag field with an injection intention
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.FIELD, ElementType.PARAMETER })
@NonNullByDefault
public @interface InjectBinding {
    public String named() default ANNOTATION_DEFAULT;

    public boolean mandatory() default true;
}
