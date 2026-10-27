package io.metersphere.sdk.exception;

/** Typed domain conflict shared by web and Agent adapters, independent of translated text. */
public class ModuleNameConflictException extends MSException {
    public ModuleNameConflictException(String message) { super(message); }
}
