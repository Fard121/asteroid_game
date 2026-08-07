package dk.sdu.mmmi.cbse.scoring;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Scoring microservice - holds the authoritative score for the currently
 * running game session, out-of-process from the game itself. The game
 * (Core module's {@code ScoreClient}) pushes its locally-tracked score here
 * over HTTP whenever it changes.
 */
@RestController
@RequestMapping("/api/score")
public class ScoreController {

    private final AtomicInteger score = new AtomicInteger(0);

    @GetMapping
    public ScoreResponse getScore() {
        return new ScoreResponse(score.get());
    }

    @PostMapping
    public ScoreResponse setScore(@RequestBody ScoreUpdateRequest request) {
        score.set(request.score());
        return new ScoreResponse(score.get());
    }

    @PostMapping("/reset")
    public ScoreResponse reset() {
        score.set(0);
        return new ScoreResponse(score.get());
    }

    public record ScoreResponse(int score) {
    }

    public record ScoreUpdateRequest(int score) {
    }
}
