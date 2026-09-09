package com.maxieruff.dynasty.world;

import com.badlogic.gdx.graphics.Color;

/** Prototype palette; these colors can later be replaced with texture-atlas regions. */
public enum BlockType {
    AIR(null),
    GRASS(new Color(0.28f, 0.64f, 0.20f, 1f)),
    DIRT(new Color(0.45f, 0.27f, 0.12f, 1f)),
    STONE(new Color(0.45f, 0.47f, 0.50f, 1f));

    private final Color color;

    BlockType(Color color) { this.color = color; }
    public boolean isSolid() { return this != AIR; }
    public Color getColor() { return color; }

    public static BlockType fromHotbarSlot(int slot) {
        return switch (slot) {
            case 0 -> GRASS;
            case 1 -> DIRT;
            default -> STONE;
        };
    }
}
