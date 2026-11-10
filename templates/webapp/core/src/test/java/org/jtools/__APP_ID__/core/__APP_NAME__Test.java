package org.jtools.__APP_ID__.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class __APP_NAME__Test {

    @Test
    void saysWhatItIs() {
        __APP_NAME__.About about = new __APP_NAME__().about();
        assertEquals(__APP_NAME__.NAME, about.name());
        assertFalse(about.version().isBlank());
    }
}
