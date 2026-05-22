package br.com.middleware.lifecycle;

import java.lang.annotation.ElementType;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Lifecycle {
    LifecycleMode value() default LifecycleMode.STATIC;
    int poolSize() default 5;
}
