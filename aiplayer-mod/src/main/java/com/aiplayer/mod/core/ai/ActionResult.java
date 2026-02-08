package com.aiplayer.mod.core.ai;

public record ActionResult(boolean success, boolean completed, String message) {
    public static ActionResult success(String message) {
        return new ActionResult(true, true, message);
    }

    public static ActionResult failure(String message) {
        return new ActionResult(false, true, message);
    }

    public static ActionResult running(String message) {
        return new ActionResult(true, false, message);
    }
}