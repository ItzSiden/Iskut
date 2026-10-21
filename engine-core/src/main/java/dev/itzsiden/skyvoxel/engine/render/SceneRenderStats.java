package dev.itzsiden.skyvoxel.engine.render;

/** Per-frame CPU-side scene submission counters. */
public record SceneRenderStats(
    int totalObjects,
    int visibleObjects,
    int frustumCulledObjects,
    int hiddenObjects,
    int drawCalls,
    int trianglesSubmitted
) {}
