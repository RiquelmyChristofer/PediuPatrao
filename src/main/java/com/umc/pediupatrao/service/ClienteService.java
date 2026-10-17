package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.CampoAuditado;
import com.umc.pediupatrao.repository.ClienteRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class ClienteService {
    private final ClienteRepository clienteRepository;
    private final AuditoriaService auditoriaService;

    public ClienteService(ClienteRepository clienteRepository, AuditoriaService auditoriaService) {
        this.clienteRepository = clienteRepository;
        this.auditoriaService = auditoriaService;
    }

    public Cliente novoCliente(Cliente cliente) { return salvar(cliente); }
    public List<Cliente> listarClientes() { return clienteRepository.findAll(); }

    public void excluir(String id) {
        Cliente existente = clienteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cliente não encontrado."));
        clienteRepository.deleteById(id);
        auditoriaService.registrar("EXCLUSAO", "CLIENTE", id,
                "Cadastro do cliente removido.", diferencas(existente, new Cliente()));
    }

    public Optional<Cliente> buscarPorId(String id) { return clienteRepository.findById(id); }

    public Cliente salvar(Cliente cliente) {
        if (cliente == null || cliente.getNome() == null || cliente.getNome().isBlank()) {
            throw new IllegalArgumentException("O nome do cliente é obrigatório.");
        }
        if (cliente.getTelefone() == null || cliente.getTelefone().isBlank()) {
            throw new IllegalArgumentException("O telefone do cliente é obrigatório.");
        }
        cliente.setNome(cliente.getNome().trim());
        cliente.setTelefone(cliente.getTelefone().trim());
        boolean atualizacao = cliente.getId() != null;
        Cliente anterior = null;
        if (atualizacao) {
            anterior = clienteRepository.findById(cliente.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Cliente não encontrado para atualização."));
        }
        Cliente salvo = clienteRepository.save(cliente);
        List<CampoAuditado> alteracoes = diferencas(anterior, salvo);
        auditoriaService.registrar(atualizacao ? "ATUALIZACAO" : "CRIACAO", "CLIENTE",
                salvo.getId(), atualizacao ? "Dados cadastrais do cliente alterados."
                        : "Novo cadastro de cliente.", alteracoes);
        return salvo;
    }

    private List<CampoAuditado> diferencas(Cliente antes, Cliente depois) {
        List<CampoAuditado> alteracoes = new ArrayList<>();
        adicionarMudanca(alteracoes, "nome", antes == null ? null : antes.getNome(), depois.getNome());
        adicionarMudanca(alteracoes, "telefone", antes == null ? null : antes.getTelefone(), depois.getTelefone());
        adicionarMudanca(alteracoes, "cep", antes == null ? null : antes.getCep(), depois.getCep());
        adicionarMudanca(alteracoes, "logradouro", antes == null ? null : antes.getLogradouro(), depois.getLogradouro());
        adicionarMudanca(alteracoes, "numero", antes == null ? null : antes.getNumero(), depois.getNumero());
        adicionarMudanca(alteracoes, "bairro", antes == null ? null : antes.getBairro(), depois.getBairro());
        adicionarMudanca(alteracoes, "cidade", antes == null ? null : antes.getCidade(), depois.getCidade());
        adicionarMudanca(alteracoes, "estado", antes == null ? null : antes.getEstado(), depois.getEstado());
        adicionarMudanca(alteracoes, "complemento", antes == null ? null : antes.getComplemento(), depois.getComplemento());
        adicionarMudanca(alteracoes, "enderecoLegado", antes == null ? null : antes.getEndereco(), depois.getEndereco());
        return alteracoes;
    }

    private void adicionarMudanca(List<CampoAuditado> alteracoes, String campo, Object antes, Object depois) {
        if (!Objects.equals(antes, depois)) {
            alteracoes.add(new CampoAuditado(campo, antes, depois));
        }
    }
}
