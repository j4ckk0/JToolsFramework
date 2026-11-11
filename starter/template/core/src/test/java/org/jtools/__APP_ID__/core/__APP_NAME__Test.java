package org.jtools.__APP_ID__.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class __APP_NAME__Test {

    @Test
    void saysItIsReady() {
        assertEquals(__APP_NAME__.NAME + " is ready.", new __APP_NAME__().status().summary());
    }
}
