package com.seuoj.seuojbackend.service.contest;

import com.seuoj.seuojbackend.exception.BadRequestException;
import org.springframework.stereotype.Component;

@Component
public class ContestRankingStrategyFactory {

    private final AcmRankingStrategy acmStrategy;
    private final OiRankingStrategy oiStrategy;
    private final IoiRankingStrategy ioiStrategy;
    private final CustomScriptRankingStrategy customStrategy;

    public ContestRankingStrategyFactory(AcmRankingStrategy acmStrategy,
                                         OiRankingStrategy oiStrategy,
                                         IoiRankingStrategy ioiStrategy,
                                         CustomScriptRankingStrategy customStrategy) {
        this.acmStrategy = acmStrategy;
        this.oiStrategy = oiStrategy;
        this.ioiStrategy = ioiStrategy;
        this.customStrategy = customStrategy;
    }

    public ContestRankingStrategy getStrategy(String ruleType) {
        return switch (ruleType) {
            case "ACM" -> acmStrategy;
            case "NOI" -> oiStrategy;
            case "IOI" -> ioiStrategy;
            case "CUSTOM" -> customStrategy;
            default -> throw new BadRequestException("未知赛制: " + ruleType);
        };
    }
}
