package com.buscard.backend.api;

import com.buscard.backend.domain.TransactionEntity;
import com.buscard.backend.service.TransactionService;
import com.buscard.common.CardConstants.PassType;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    public record RecordTxRequest(
            @NotBlank String rawBase64,
            @NotBlank String signatureBase64,
            PassType passType,
            Integer passRoute,
            LocalDate passExpiry) {
    }

    @PostMapping
    public TransactionEntity record(@Validated @RequestBody RecordTxRequest req) {
        return transactionService.recordTransaction(
                req.rawBase64(),
                req.signatureBase64(),
                req.passType(),
                req.passRoute(),
                req.passExpiry()
        );
    }

    @GetMapping
    public List<TransactionEntity> listRecent() {
        return transactionService.listRecent();
    }

    @GetMapping("/card/{cardId}")
    public List<TransactionEntity> listByCard(@PathVariable String cardId) {
        return transactionService.listByCard(cardId);
    }
}
