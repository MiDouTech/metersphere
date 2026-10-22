package io.metersphere.system.security;

import java.lang.annotation.*;

/** Marks interactive platform model operations, never external MCP or historical reads. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresLlmFeature { }
