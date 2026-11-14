package com.mohistmc.academy.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

class ConfigSaveTest {
    @Test
    void savingBeforeFmlAttachesAFileIsANoOp() {
        assertDoesNotThrow(ACConfig.Client::save);
        assertDoesNotThrow(ACConfig.Server::save);
    }
}
