package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ProdutoService {
    private final ProdutoRepository produtoRepository;
    private final AuditoriaService auditoriaService;

    public ProdutoService(ProdutoRepository produtoRepository, AuditoriaService auditoriaService) {
        this.produtoRepository = produtoRepository;
        this.auditoriaService = auditoriaService;
    }

    public Produto novoProduto(Produto produto) { return salvar(produto); }
    public List<Produto> listarProdutos() { return produtoRepository.findAll(); }
    public Optional<Produto> buscarPorId(String id) { return produtoRepository.findById(id); }

    public void excluir(String id) {
        Produto existente = produtoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Produto não encontrado."));
        produtoRepository.deleteById(id);
        auditoriaService.registrar("EXCLUSAO", "PRODUTO", id, "Produto " + existente.getNome() + " excluído");
    }

    public Produto salvar(Produto produto) {
        if (produto == null || produto.getNome() == null || produto.getNome().isBlank()) {
            throw new IllegalArgumentException("O nome do produto é obrigatório.");
        }
        if (produto.getTipo() == null || produto.getTipo().isBlank()) {
            throw new IllegalArgumentException("O tipo do produto é obrigatório.");
        }
        if (produto.getPreco() == null || !Double.isFinite(produto.getPreco()) || produto.getPreco() <= 0) {
            throw new IllegalArgumentException("O preço do produto deve ser maior que zero.");
        }
        produto.setNome(produto.getNome().trim());
        produto.setTipo(produto.getTipo().trim());
        if (produto.getAtivo() == null) {
            produto.setAtivo(true);
        }
        boolean atualizacao = produto.getId() != null;
        Produto anterior = null;
        if (atualizacao) {
            anterior = produtoRepository.findById(produto.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Produto não encontrado para atualização."));
        }
        Produto salvo = produtoRepository.save(produto);
        String detalhes = atualizacao
                ? diferencas(anterior, salvo)
                : "Novo produto: " + salvo.getNome() + ", tipo=" + salvo.getTipo() + ", preço=" + salvo.getPreco();
        auditoriaService.registrar(atualizacao ? "ATUALIZACAO" : "CRIACAO", "PRODUTO", salvo.getId(), detalhes);
        return salvo;
    }

    private String diferencas(Produto antes, Produto depois) {
        StringBuilder sb = new StringBuilder("Campos alterados: ");
        adicionarMudanca(sb, "nome", antes.getNome(), depois.getNome());
        adicionarMudanca(sb, "tipo", antes.getTipo(), depois.getTipo());
        adicionarMudanca(sb, "preço", antes.getPreco(), depois.getPreco());
        adicionarMudanca(sb, "ativo", antes.getAtivo(), depois.getAtivo());
        if (sb.toString().endsWith("Campos alterados: ")) return "Cadastro salvo sem diferença detectável.";
        return sb.toString();
    }

    private void adicionarMudanca(StringBuilder sb, String campo, Object antes, Object depois) {
        if (!java.util.Objects.equals(antes, depois)) {
            sb.append(campo).append(" [").append(antes).append(" -> ").append(depois).append("]; ");
        }
    }
}
