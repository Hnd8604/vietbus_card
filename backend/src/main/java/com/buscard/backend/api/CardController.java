package com.buscard.backend.api;

import com.buscard.backend.domain.CardEntity;
import com.buscard.backend.service.CardService;
import com.buscard.common.CardConstants.CardState;
import com.buscard.common.CardConstants.Category;
import com.buscard.common.CardConstants.LockReason;
import com.buscard.common.CardConstants.PassType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cards")
public class CardController {

    private final CardService cardService;

    public CardController(CardService cardService) {
        this.cardService = cardService;
    }

    public record RegisterCardRequest(
            @NotBlank String cardId,
            @NotBlank String publicKeyBase64,
            @NotNull Category category,
            @NotNull LocalDate cardExpiry) {
    }

    public record SyncCardRequest(
            @NotNull CardState state,
            @NotNull LockReason lockReason,
            @NotNull Category category,
            @NotNull LocalDate cardExpiry,
            long balance,
            @NotNull PassType passType,
            int passRoute,
            LocalDate passExpiry,
            long lastTxCounter) {
    }

    @PostMapping
    public CardEntity register(@Validated @RequestBody RegisterCardRequest req) {
        return cardService.registerCard(req.cardId(), req.publicKeyBase64(), req.category(), req.cardExpiry());
    }

    @GetMapping
    public List<CardEntity> list() {
        return cardService.listCards();
    }

    @GetMapping("/{cardId}")
    public CardEntity get(@PathVariable String cardId) {
        return cardService.getCard(cardId);
    }

    @PutMapping("/{cardId}/sync")
    public CardEntity sync(@PathVariable String cardId, @Validated @RequestBody SyncCardRequest req) {
        return cardService.syncCard(
                cardId,
                req.state(),
                req.lockReason(),
                req.category(),
                req.cardExpiry(),
                req.balance(),
                req.passType(),
                req.passRoute(),
                req.passExpiry(),
                req.lastTxCounter()
        );
    }
}
