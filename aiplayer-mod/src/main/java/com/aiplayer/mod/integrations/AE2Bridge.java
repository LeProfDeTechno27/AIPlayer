package com.aiplayer.mod.integrations;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.List;

public final class AE2Bridge {
    private static final int MIN_RADIUS = 2;
    private static final int MAX_RADIUS = 32;
    private static final int VERTICAL_SCAN = 6;

    private static final String[] AE2_API_PROBES = {
        "appeng.api.networking.IGrid",
        "appeng.api.networking.IGridNode",
        "appeng.api.networking.crafting.ICraftingService",
        "appeng.api.networking.storage.IStorageService"
    };

    public boolean isAvailable() {
        return ModList.get().isLoaded("ae2");
    }

    public AE2ApiProbeResult probeApi() {
        if (!isAvailable()) {
            return new AE2ApiProbeResult(false, 0, AE2_API_PROBES.length, List.of(), List.of(AE2_API_PROBES));
        }

        List<String> found = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        for (String className : AE2_API_PROBES) {
            try {
                Class.forName(className, false, getClass().getClassLoader());
                found.add(className);
            } catch (ClassNotFoundException ignored) {
                missing.add(className);
            }
        }

        boolean apiAccessible = !found.isEmpty();
        return new AE2ApiProbeResult(apiAccessible, found.size(), AE2_API_PROBES.length, found, missing);
    }

    public AE2ScanResult scan(ServerLevel level, BlockPos center, int radius) {
        int safeRadius = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, radius));
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        int scannedBlocks = 0;
        int ae2Blocks = 0;
        int controllers = 0;
        int drives = 0;
        int terminals = 0;
        int interfaces = 0;
        int crafters = 0;
        int energyCells = 0;

        int minY = Math.max(level.getMinBuildHeight(), center.getY() - VERTICAL_SCAN);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, center.getY() + VERTICAL_SCAN);

        for (int x = center.getX() - safeRadius; x <= center.getX() + safeRadius; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = center.getZ() - safeRadius; z <= center.getZ() + safeRadius; z++) {
                    scannedBlocks++;
                    cursor.set(x, y, z);

                    BlockState blockState = level.getBlockState(cursor);
                    ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(blockState.getBlock());
                    if (blockId == null || !"ae2".equals(blockId.getNamespace())) {
                        continue;
                    }

                    ae2Blocks++;
                    String path = blockId.getPath();

                    if (path.contains("controller")) {
                        controllers++;
                    }
                    if (path.contains("drive")) {
                        drives++;
                    }
                    if (path.contains("terminal")) {
                        terminals++;
                    }
                    if (path.contains("interface")) {
                        interfaces++;
                    }
                    if (path.contains("craft") || path.contains("molecular_assembler")) {
                        crafters++;
                    }
                    if (path.contains("energy") || path.contains("dense_cell") || path.contains("cell")) {
                        energyCells++;
                    }
                }
            }
        }

        return new AE2ScanResult(
            safeRadius,
            scannedBlocks,
            ae2Blocks,
            controllers,
            drives,
            terminals,
            interfaces,
            crafters,
            energyCells
        );
    }

    public record AE2ApiProbeResult(
        boolean accessible,
        int foundCount,
        int expectedCount,
        List<String> foundClasses,
        List<String> missingClasses
    ) {
        public String summary() {
            if (!accessible) {
                return "api=unavailable found=" + foundCount + "/" + expectedCount;
            }
            return "api=available found=" + foundCount + "/" + expectedCount;
        }
    }

    public record AE2ScanResult(
        int radius,
        int scannedBlocks,
        int ae2Blocks,
        int controllers,
        int drives,
        int terminals,
        int interfaces,
        int crafters,
        int energyCells
    ) {
        public String stage() {
            if (ae2Blocks == 0) {
                return "EMPTY";
            }
            if (controllers == 0) {
                return "NO_CONTROLLER";
            }
            if (drives == 0) {
                return "NO_STORAGE";
            }
            if (crafters == 0 || interfaces == 0) {
                return "STORAGE_READY";
            }
            return "AUTOCRAFT_READY";
        }

        public String nextActionHint() {
            return switch (stage()) {
                case "EMPTY" -> "Poser un Controller AE2, un Drive et une source d'energie.";
                case "NO_CONTROLLER" -> "Ajouter au moins 1 Controller AE2 pour stabiliser le reseau.";
                case "NO_STORAGE" -> "Ajouter un ME Drive + cellules de stockage.";
                case "STORAGE_READY" -> "Ajouter Interface + Molecular Assembler pour l'autocraft.";
                default -> "Reseau pret: commencer les recettes d'autocraft et l'integration MineColonies.";
            };
        }

        public String summary() {
            return "stage=" + stage()
                + " ae2=" + ae2Blocks
                + " ctrl=" + controllers
                + " drives=" + drives
                + " terminals=" + terminals
                + " interfaces=" + interfaces
                + " crafters=" + crafters
                + " cells=" + energyCells;
        }
    }
}