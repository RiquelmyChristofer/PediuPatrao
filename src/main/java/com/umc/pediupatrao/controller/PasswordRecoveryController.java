package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.service.PasswordRecoveryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PasswordRecoveryController {
    private final PasswordRecoveryService passwordRecoveryService;

    public PasswordRecoveryController(PasswordRecoveryService passwordRecoveryService) {
        this.passwordRecoveryService = passwordRecoveryService;
    }

    @GetMapping("/esqueceu-senha")
    public String formularioSolicitacao() {
        return "esqueceu-senha";
    }

    @PostMapping("/esqueceu-senha")
    public String solicitarRedefinicao(@RequestParam String email, RedirectAttributes redirectAttributes) {
        passwordRecoveryService.solicitarRedefinicao(email);
        redirectAttributes.addFlashAttribute("solicitado", true);
        return "redirect:/esqueceu-senha";
    }

    @GetMapping("/redefinir-senha")
    public String formularioRedefinicao(@RequestParam(required = false) String token, Model model) {
        model.addAttribute("token", token);
        model.addAttribute("tokenValido", passwordRecoveryService.tokenValido(token));
        return "redefinir-senha";
    }

    @PostMapping("/redefinir-senha")
    public String redefinirSenha(
            @RequestParam String token,
            @RequestParam String senha,
            @RequestParam String confirmarSenha,
            Model model,
            RedirectAttributes redirectAttributes) {
        model.addAttribute("token", token);
        model.addAttribute("tokenValido", passwordRecoveryService.tokenValido(token));
        if (!senha.equals(confirmarSenha)) {
            model.addAttribute("erro", "As senhas informadas não coincidem.");
            return "redefinir-senha";
        }
        try {
            if (!passwordRecoveryService.redefinirSenha(token, senha)) {
                model.addAttribute("tokenValido", false);
                model.addAttribute("erro", "Este link é inválido, expirou ou já foi utilizado.");
                return "redefinir-senha";
            }
        } catch (IllegalArgumentException ex) {
            model.addAttribute("erro", ex.getMessage());
            return "redefinir-senha";
        }
        redirectAttributes.addAttribute("reset", "true");
        return "redirect:/login";
    }
}
