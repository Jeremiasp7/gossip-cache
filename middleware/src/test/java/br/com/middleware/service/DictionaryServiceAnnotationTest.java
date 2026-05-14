package br.com.middleware.service;

import br.com.middleware.annotations.RemoteObject;
import br.com.middleware.annotations.MethodMapping;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DictionaryService Annotation Inspection Tests")
class DictionaryServiceAnnotationTest {

    @Test
    @DisplayName("Should have @RemoteObject annotation on class")
    void testClassHasRemoteObjectAnnotation() {
        Class<?> clazz = DictionaryService.class;

        assertTrue(clazz.isAnnotationPresent(RemoteObject.class),
            "DictionaryService should have @RemoteObject annotation");

        RemoteObject annotation = clazz.getAnnotation(RemoteObject.class);
        assertNotNull(annotation);
        assertEquals("DictionaryService", annotation.name());

        System.out.println("✓ Class annotation found: @RemoteObject(name = \"" + annotation.name() + "\")");
    }

    @Test
    @DisplayName("Should have @MethodMapping annotations on methods")
    void testMethodsHaveMethodMappingAnnotation() throws NoSuchMethodException {
        Class<?> clazz = DictionaryService.class;

        // Test GET method
        Method getMethod = clazz.getDeclaredMethod("get", String.class);
        assertTrue(getMethod.isAnnotationPresent(MethodMapping.class),
            "get() method should have @MethodMapping annotation");

        MethodMapping getMapping = getMethod.getAnnotation(MethodMapping.class);
        assertEquals("GET", getMapping.method().toString());
        assertEquals("/dictionary/get", getMapping.path());

        System.out.println("✓ Method annotation found: @MethodMapping(method = " +
            getMapping.method() + ", path = \"" + getMapping.path() + "\")");

        // Test POST method (saveLocalData)
        Method saveMethod = clazz.getDeclaredMethod("saveLocalData", String.class, String.class);
        assertTrue(saveMethod.isAnnotationPresent(MethodMapping.class));

        MethodMapping saveMapping = saveMethod.getAnnotation(MethodMapping.class);
        assertEquals("POST", saveMapping.method().toString());
        assertEquals("/dictionary/save", saveMapping.path());

        System.out.println("✓ Method annotation found: @MethodMapping(method = " +
            saveMapping.method() + ", path = \"" + saveMapping.path() + "\")");
    }

    @Test
    @DisplayName("Should inspect all annotated methods using reflection")
    void testInspectAllAnnotatedMethods() {
        Class<?> clazz = DictionaryService.class;
        Method[] methods = clazz.getDeclaredMethods();

        System.out.println("\nInspecting all methods of " + clazz.getSimpleName() + ":");

        for (Method method : methods) {
            if (method.isAnnotationPresent(MethodMapping.class)) {
                MethodMapping mapping = method.getAnnotation(MethodMapping.class);
                System.out.println("  - " + method.getName() + "() -> " +
                    mapping.method() + " " + mapping.path());
            }
        }
    }

    @Test
    @DisplayName("Should instantiate and inspect DictionaryService")
    void testInstantiateAndInspect() {
        DictionaryService service = new DictionaryService();
        Class<?> clazz = service.getClass();

        assertNotNull(service);
        assertEquals("DictionaryService", clazz.getSimpleName());

        if (clazz.isAnnotationPresent(RemoteObject.class)) {
            RemoteObject annotation = clazz.getAnnotation(RemoteObject.class);
            System.out.println("\nInstance of " + annotation.name() + " created successfully");
        }
    }
}
