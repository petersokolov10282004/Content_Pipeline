package com.contentpipeline.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
//Will we have a public api key for you to steal????? maybe 

//This is needed because claude api is used for script generation
//And subtitles
/*Idk why make subtittles api call because we can easily calualte
them from speech speed or and script  */
@Configuration
public class AnthropicConfig {

    @Value("${anthropic.api-key:}")
    private String apiKey;

    @Bean
    public AnthropicClient anthropicClient() {
        return AnthropicOkHttpClient.builder()
            .apiKey(apiKey)
            .build();
    }
}
