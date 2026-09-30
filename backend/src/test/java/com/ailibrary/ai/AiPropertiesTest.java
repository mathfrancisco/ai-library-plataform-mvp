package com.ailibrary.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AiPropertiesTest {
    @Test
    void usesCurrentGroqDefaultsWhenModelsAreMissing() {
        var properties = new AiProperties(true, null, null, null, null);

        assertThat(properties.model(ModelTier.FAST)).isEqualTo("openai/gpt-oss-20b");
        assertThat(properties.model(ModelTier.SMART)).isEqualTo("openai/gpt-oss-120b");
    }

    @Test
    void preservesConfiguredModelOverrides() {
        var properties = new AiProperties(true, "groq", new AiProperties.Models("fast-custom", "smart-custom"), 20, 25);

        assertThat(properties.model(ModelTier.FAST)).isEqualTo("fast-custom");
        assertThat(properties.model(ModelTier.SMART)).isEqualTo("smart-custom");
    }
}
