package com.ailibrary.ai;

import java.util.Map;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Component;

@Component
public class AiPromptTemplates {
    private static final PromptTemplate GROUNDED_QA = PromptTemplate.builder()
            .template(
                    """
                    <question>
                    {question}
                    </question>

                    <context>
                    {context}
                    </context>
                    """)
            .build();

    public String groundedQuestion(String question, String context) {
        return GROUNDED_QA.render(Map.of("question", question, "context", context));
    }
}
