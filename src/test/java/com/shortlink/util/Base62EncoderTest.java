package com.shortlink.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class Base62EncoderTest {

    @Test
    void encodesZeroAsFirstAlphabetCharacter() {
        assertEquals("0", Base62Encoder.encode(0));
    }

    @Test
    void encodeIsDeterministic() {
        assertEquals(Base62Encoder.encode(123456), Base62Encoder.encode(123456));
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 1L, 61L, 62L, 125L, 999L, 123456789L, Long.MAX_VALUE})
    void decodeReversesEncodeForVariousMagnitudes(long id) {
        String code = Base62Encoder.encode(id);
        assertEquals(id, Base62Encoder.decode(code));
    }

    @Test
    void differentIdsProduceDifferentCodes() {
        assertNotEquals(Base62Encoder.encode(100), Base62Encoder.encode(101));
    }

    @Test
    void largerIdsProduceLongerOrEqualLengthCodes() {
        String small = Base62Encoder.encode(10);
        String large = Base62Encoder.encode(10_000_000_000L);
        assertEquals(true, large.length() >= small.length());
    }

    @Test
    void decodeRejectsInvalidCharacters() {
        try {
            Base62Encoder.decode("abc!@#");
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // pass
        }
    }
}
