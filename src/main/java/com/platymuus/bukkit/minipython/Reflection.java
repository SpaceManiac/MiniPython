package com.platymuus.bukkit.minipython;

import java.lang.reflect.Field;

/**
 * Helper methods for reflecting on private members of Bukkit internals.
 */
public class Reflection {

    public static <T> Object getPrivateValue(Class<T> clazz, Object target, String field) {
        try {
            Field f = clazz.getDeclaredField(field);
            f.setAccessible(true);
            return f.get(target);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException("Error reflecting", ex);
        }
    }

    public static Object getPrivateValue(Object target, String field) {
        return getPrivateValue(target.getClass(), target, field);
    }

    public static <T> void setPrivateValue(Class<T> clazz, Object target, String field, Object value) {
        try {
            Field f = clazz.getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException("Error reflecting", ex);
        }
    }

    public static void setPrivateValue(Object target, String field, Object value) {
        setPrivateValue(target.getClass(), target, field, value);
    }

}
