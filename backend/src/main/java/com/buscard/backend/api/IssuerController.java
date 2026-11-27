package com.buscard.backend.api;

import com.buscard.backend.service.ApiException;
import com.buscard.backend.service.IssuerService;
import jakarta.validation.constraints.NotBlank;
import java.util.Base64;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/issuer")
public class IssuerController {

    private final IssuerService issuerService;

    public IssuerController(IssuerService issuerService) {
        this.issuerService = issuerService;
    }

    public record IssuerPublicKeyDto(String publicKeyBase64) {
    }

    public record SignCommandRequest(
            @NotBlank String cardId,
            int ins,
            @NotBlank String challengeBase64,
            String payloadBase64) {
    }

    public record SignCommandResponse(String signatureBase64) {
    }

    @GetMapping("/public-key")
    public IssuerPublicKeyDto publicKey() {
        return new IssuerPublicKeyDto(issuerService.getPublicKeyBase64());
    }

    @PostMapping("/sign")
    public SignCommandResponse sign(@Validated @RequestBody SignCommandRequest req) {
        byte[] challenge;
        byte[] payload;
        try {
            challenge = Base64.getDecoder().decode(req.challengeBase64());
            payload = (req.payloadBase64() == null || req.payloadBase64().isEmpty())
                    ? new byte[0]
                    : Base64.getDecoder().decode(req.payloadBase64());
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Malformed Base64 input");
        }
        byte[] signature = issuerService.signCommand(req.cardId(), req.ins(), challenge, payload);
        return new SignCommandResponse(Base64.getEncoder().encodeToString(signature));
    }
}
