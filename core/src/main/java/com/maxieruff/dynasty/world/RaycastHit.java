package com.maxieruff.dynasty.world;

/** Result of a block raycast, including the outward face normal for placement. */
public record RaycastHit(int x, int y, int z, int normalX, int normalY, int normalZ) { }
