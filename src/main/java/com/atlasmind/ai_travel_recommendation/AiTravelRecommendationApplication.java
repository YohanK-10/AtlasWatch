package com.atlasmind.ai_travel_recommendation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AiTravelRecommendationApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiTravelRecommendationApplication.class, args);
    }

}
