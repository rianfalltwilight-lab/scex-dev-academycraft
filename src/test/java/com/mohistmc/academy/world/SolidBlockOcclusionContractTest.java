package com.mohistmc.academy.world;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that solid opaque full-cube blocks (especially worldgen ores and solid machines)
 * do not set {@code .noOcclusion()}, which would otherwise prevent Minecraft from culling
 * all 6 adjacent block faces and inflate chunk mesh geometry.
 * Also guards that non-full-cube / multiblock blocks retain {@code .noOcclusion()} to avoid
 * X-ray culling glitches.
 */
class SolidBlockOcclusionContractTest {

    private static final Path BLOCK_DIR = Path.of("src/main/java/com/mohistmc/academy/world/block");
    private static final Path ACADEMY_BLOCKS = Path.of("src/main/java/com/mohistmc/academy/world/AcademyBlocks.java");

    @Test
    void solidOresAndStandaloneFullCubeBlocksMustNotCallNoOcclusion() throws IOException {
        List<String> solidBlockFiles = List.of(
                "ConstraintMetal.java",
                "CrystalOre.java",
                "ResoOre.java",
                "ImagsilOre.java",
                "MachineFrame.java",
                "AbilityInterferer.java",
                "MetalFomer.java"
        );

        for (String fileName : solidBlockFiles) {
            String src = Files.readString(BLOCK_DIR.resolve(fileName));
            assertFalse(
                    src.contains(".noOcclusion()"),
                    () -> fileName + " is a solid full-cube block and must not call .noOcclusion()"
            );
        }
    }

    @Test
    void academyBlocksSolidMachinesMustNotCallNoOcclusion() throws IOException {
        String src = Files.readString(ACADEMY_BLOCKS);
        List<String> solidRegistrations = List.of(
                "IMAG_FUSOR",
                "NODE_BASIC",
                "NODE_STANDARD",
                "NODE_ADVANCED",
                "RF_INPUT",
                "RF_OUTPUT"
        );

        for (String field : solidRegistrations) {
            String decl = extractFieldDeclaration(src, field);
            assertFalse(
                    decl.contains(".noOcclusion()"),
                    () -> "AcademyBlocks." + field + " is a solid full-cube block and must not call .noOcclusion()"
            );
        }
    }

    @Test
    void nonFullCubeAndMultiblockRegistrationsKeepNoOcclusion() throws IOException {
        String src = Files.readString(ACADEMY_BLOCKS);
        List<String> nonOpaqueRegistrations = List.of(
                "CAT_ENGINE",
                "DEV_NORMAL",
                "DEV_NORMAL_SUB",
                "DEV_ADVANCED",
                "DEV_ADVANCED_SUB",
                "MATRIX",
                "MATRIX_SUB",
                "PHASE_GEN",
                "SOLAR_GEN",
                "WINDGEN_BASE",
                "WIND_GEN_BASE_SUB",
                "WINDGEN_MAIN",
                "WINDGEN_PILLAR",
                "WINDGEN_FAN"
        );

        for (String field : nonOpaqueRegistrations) {
            String decl = extractFieldDeclaration(src, field);
            assertTrue(
                    decl.contains(".noOcclusion()"),
                    () -> "AcademyBlocks." + field + " is non-full-cube/multiblock and must retain .noOcclusion()"
            );
        }
    }

    private static String extractFieldDeclaration(String source, String fieldName) {
        Pattern p = Pattern.compile("DeferredBlock<Block>\\s+" + Pattern.quote(fieldName) + "\\s*=.*?;", Pattern.DOTALL);
        Matcher m = p.matcher(source);
        assertTrue(m.find(), "Expected field declaration for " + fieldName + " in AcademyBlocks.java");
        return m.group();
    }
}
