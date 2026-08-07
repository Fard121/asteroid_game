package dk.sdu.mmmi.cbse.main;

import org.springframework.web.client.RestTemplate;

/**
 * Pushes the game's current score to the standalone Scoring microservice
 * (see the {@code Scoring} module) over HTTP via {@link RestTemplate}.
 *
 * <p>This is a hard runtime dependency, by design (per the Microservices
 * Lab spec) - there is no local fallback or swallowed exception here. If
 * the Scoring microservice isn't running on {@code localhost:8081}, {@link
 * #push} throws {@link org.springframework.web.client.RestClientException},
 * same as any other unreachable HTTP call.
 */
class ScoreClient {

    private static final String SCORE_URL = "http://localhost:8081/api/score";

    private final RestTemplate restTemplate = new RestTemplate();

    void push(int score) {
        restTemplate.postForObject(SCORE_URL, new ScoreUpdateRequest(score), ScoreResponse.class);
    }

    private static class ScoreUpdateRequest {
        private int score;

        ScoreUpdateRequest(int score) {
            this.score = score;
        }

        public int getScore() {
            return score;
        }

        public void setScore(int score) {
            this.score = score;
        }
    }

    private static class ScoreResponse {
        private int score;

        public int getScore() {
            return score;
        }

        public void setScore(int score) {
            this.score = score;
        }
    }
}
