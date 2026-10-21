package dev.itzsiden.skyvoxel.engine.items;

/** Result returned by item-use handlers so the UI can show clear feedback without owning gameplay rules. */
public record ItemUseResult(boolean consumed, String message) {
    public ItemUseResult { message = message == null ? "" : message; }
    public static ItemUseResult failed(String message) { return new ItemUseResult(false, message); }
    public static ItemUseResult success(String message) { return new ItemUseResult(true, message); }
}
