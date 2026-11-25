package com.minelauncher.app.game;

/**
 * Block ids and their texture tiles inside the procedural 4x4 atlas.
 */
public final class BlockTypes {

    public static final byte AIR = 0;
    public static final byte GRASS = 1;
    public static final byte DIRT = 2;
    public static final byte STONE = 3;
    public static final byte SAND = 4;
    public static final byte LOG = 5;
    public static final byte LEAVES = 6;
    public static final byte WATER = 7;
    public static final byte PLANKS = 8;
    public static final byte COBBLE = 9;
    public static final byte GLASS = 10;
    public static final byte BEDROCK = 11;
    public static final byte SNOW = 12;
    public static final byte BRICK = 13;

    /** Blocks available in the in-game hotbar. */
    public static final byte[] HOTBAR = {GRASS, DIRT, STONE, COBBLE, SAND, LOG, PLANKS, GLASS, BRICK};

    private BlockTypes() {
    }

    public static boolean isSolid(int id) {
        return id != AIR && id != WATER;
    }

    public static boolean isOpaque(int id) {
        return id != AIR && id != WATER && id != GLASS;
    }

    public static String name(int id) {
        switch (id) {
            case GRASS: return "Трава";
            case DIRT: return "Земля";
            case STONE: return "Камень";
            case SAND: return "Песок";
            case LOG: return "Бревно";
            case LEAVES: return "Листва";
            case WATER: return "Вода";
            case PLANKS: return "Доски";
            case COBBLE: return "Булыжник";
            case GLASS: return "Стекло";
            case BEDROCK: return "Бедрок";
            case SNOW: return "Снег";
            case BRICK: return "Кирпич";
            default: return "Воздух";
        }
    }

    /** Preview colour used by the hotbar buttons. */
    public static int color(int id) {
        switch (id) {
            case GRASS: return 0xFF6EBB43;
            case DIRT: return 0xFF8B5E34;
            case STONE: return 0xFF8F8F8F;
            case SAND: return 0xFFE2D6A2;
            case LOG: return 0xFF6B4A26;
            case LEAVES: return 0xFF3E7A2A;
            case WATER: return 0xFF2F63C4;
            case PLANKS: return 0xFFB08A54;
            case COBBLE: return 0xFF777777;
            case GLASS: return 0xFFCDE8F2;
            case BEDROCK: return 0xFF4A4A4A;
            case SNOW: return 0xFFF2F7FA;
            case BRICK: return 0xFFA64B3B;
            default: return 0xFF000000;
        }
    }

    public static int tileTop(int id) {
        switch (id) {
            case GRASS: return 0;
            case DIRT: return 2;
            case STONE: return 3;
            case SAND: return 5;
            case LOG: return 7;
            case LEAVES: return 8;
            case WATER: return 9;
            case PLANKS: return 10;
            case GLASS: return 11;
            case BEDROCK: return 12;
            case SNOW: return 13;
            case BRICK: return 14;
            case COBBLE: return 4;
            default: return 15;
        }
    }

    public static int tileBottom(int id) {
        if (id == GRASS) return 2;
        return tileTop(id);
    }

    public static int tileSide(int id) {
        switch (id) {
            case GRASS: return 1;
            case LOG: return 6;
            default: return tileTop(id);
        }
    }
}
