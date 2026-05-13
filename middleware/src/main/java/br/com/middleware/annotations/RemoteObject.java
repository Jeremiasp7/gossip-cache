package br.com.middleware.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME) // the annotation can be processed in executation time
@Target(ElementType.TYPE)
public @interface RemoteObject {
    String name();
}
