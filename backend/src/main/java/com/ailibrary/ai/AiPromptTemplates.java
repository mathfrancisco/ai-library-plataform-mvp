package com.ailibrary.ai;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class AiPromptTemplates {
    private static final PromptTemplate GROUNDED_QA = PromptTemplate.builder()
            .template("""
                    QUESTION:
                    {question}

                    CONTEXT:
                    {context}
                    """)
            .build();

    public String groundedQuestion(String question, String context) {
        return GROUNDED_QA.render(Map.of("question", question, "context", context));
    }
}
