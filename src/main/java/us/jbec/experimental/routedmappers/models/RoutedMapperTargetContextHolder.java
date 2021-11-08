package us.jbec.experimental.routedmappers.models;

import org.springframework.util.Assert;

public class RoutedMapperTargetContextHolder {

    private static final ThreadLocal<RoutedMapperTarget> CONTEXT = new ThreadLocal<>();

    public static void setRoutedMapperTarget(RoutedMapperTarget routedMapperTarget) {
        Assert.notNull(routedMapperTarget, "Target Routed Mapper Cannot Be Null");
        CONTEXT.set(routedMapperTarget);
    }

    public static RoutedMapperTarget getRoutedMapperTarget() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
