package br.edu.projeto.medicamentos;

import com.arcadedb.query.sql.executor.Result;
import com.arcadedb.query.sql.executor.ResultSet;
import com.arcadedb.remote.RemoteDatabase;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.arcadedb.serializer.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class MedicamentoRepositorio {
    private final ConexaoArcadeDB conexao;

    public MedicamentoRepositorio(ConexaoArcadeDB conexao) {
        this.conexao = conexao;
    }

    public void inicializarEstrutura() {
        try (RemoteDatabase banco = conexao.abrir()) {
            banco.command("sql", "CREATE VERTEX TYPE Medicamento IF NOT EXISTS");
        }
    }

    public List<Medicamento> listar() {
        List<Medicamento> medicamentos = new ArrayList<>();
        try (RemoteDatabase banco = conexao.abrir();
             ResultSet resultados = banco.query("sql", "SELECT @rid AS rid, nome, concentracao, fabricante, preco FROM Medicamento")) {
            while (resultados.hasNext()) {
                medicamentos.add(converter(resultados.next()));
            }
        }
        return medicamentos;
    }

    public void cadastrar(Medicamento medicamento) {
        String sql = "CREATE VERTEX Medicamento SET nome = :nome, "
                + "concentracao = :concentracao, fabricante = :fabricante, preco = :preco";
        try (RemoteDatabase banco = conexao.abrir()) {
            banco.command("sql", sql, parametros(medicamento));
        }
    }

    public boolean atualizar(String rid, Medicamento medicamento) {
        String ridValido = validarRid(rid);
        String sql = "UPDATE Medicamento SET nome = :nome, concentracao = :concentracao, "
                + "fabricante = :fabricante, preco = :preco WHERE @rid = " + ridValido;
        try (RemoteDatabase banco = conexao.abrir()) {
            if (!existe(banco, ridValido)) return false;
            banco.command("sql", sql, parametros(medicamento));
            return true;
        }
    }

    public boolean excluir(String rid) {
        String ridValido = validarRid(rid);
        try (RemoteDatabase banco = conexao.abrir()) {
            if (!existe(banco, ridValido)) return false;
            banco.command("sql", "DELETE VERTEX FROM Medicamento WHERE @rid = " + ridValido);
            return true;
        }
    }

    public List<String> listarRelacoes() {
        List<String> relacoes = new ArrayList<>();
        try (RemoteDatabase banco = conexao.abrir()) {
            adicionarRelacoes(banco, relacoes, "Medicamento", "nome", "PertenceA", "Categoria", "nome");
            adicionarRelacoes(banco, relacoes, "Medicamento", "nome", "FornecidoPor", "Fornecedor", "nome");
            adicionarRelacoes(banco, relacoes, "Medicamento", "nome", "PossuiLote", "Lote", "codigo");
            adicionarRelacoes(banco, relacoes, "Lote", "codigo", "ArmazenadoEm", "Farmacia", "nome");
        }
        return relacoes;
    }

    private void adicionarRelacoes(RemoteDatabase banco, List<String> relacoes,
                                   String origem, String campoOrigem, String aresta,
                                   String destino, String campoDestino) {
        String sql = "MATCH {type: " + origem + ", as: origem}.out('" + aresta + "')"
                + "{type: " + destino + ", as: destino} "
                + "RETURN origem." + campoOrigem + " AS origem, destino." + campoDestino + " AS destino";
        try (ResultSet resultados = banco.query("sql", sql)) {
            while (resultados.hasNext()) {
                Result linha = resultados.next();
                relacoes.add(linha.getProperty("origem") + " -> " + aresta + " -> "
                        + linha.getProperty("destino"));
            }
        }
    }

    public void criarBackup() {
        try (RemoteDatabase banco = conexao.abrir()) {
            banco.command("sql", "BACKUP DATABASE");
        }
    }

    public String exportarDump() {
        String nomeArquivo = "ControleMedicamentos-"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                + ".jsonl.tgz";
        try (RemoteDatabase banco = conexao.abrir()) {
            banco.command("sql", "EXPORT DATABASE file://" + nomeArquivo);
        }
        return nomeArquivo;
    }

    public List<String> explicarBuscaPorNome(String nome) {
        String bancoCodificado = URLEncoder.encode(
                conexao.banco(), StandardCharsets.UTF_8
        ).replace("+", "%20");

        String url = "http://" + conexao.host() + ":" + conexao.porta()
                + "/api/v1/query/" + bancoCodificado;

        String credenciais = conexao.usuario() + ":" + conexao.senha();
        String autorizacao = "Basic " + java.util.Base64.getEncoder()
                .encodeToString(credenciais.getBytes(StandardCharsets.UTF_8));

        JSONObject corpo = new JSONObject()
                .put("language", "sql")
                .put("command", "EXPLAIN SELECT FROM Medicamento WHERE nome = :nome")
                .put("params", Map.of("nome", nome));

        HttpRequest requisicao = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", autorizacao)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(corpo.toString()))
                .build();

        try {
            HttpResponse<String> resposta = HttpClient.newHttpClient()
                    .send(requisicao, HttpResponse.BodyHandlers.ofString());

            if (resposta.statusCode() < 200 || resposta.statusCode() >= 300) {
                throw new IllegalStateException(
                        "O servidor respondeu com HTTP " + resposta.statusCode()
                                + ": " + resposta.body()
                );
            }

            JSONObject resultado = new JSONObject(resposta.body());
            String plano = resultado.getString("explain");

            if (plano == null || plano.isBlank()) {
                throw new IllegalStateException(
                        "O servidor não enviou o plano da consulta."
                );
            }

            return List.of(plano.split("\\R"));
        } catch (IOException erro) {
            throw new IllegalStateException(
                    "Não foi possível receber o plano do ArcadeDB: "
                            + erro.getMessage(),
                    erro
            );
        } catch (InterruptedException erro) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "A consulta do plano foi interrompida.",
                    erro
            );
        }
    }

    private boolean existe(RemoteDatabase banco, String rid) {
        try (ResultSet resultados = banco.query("sql",
                "SELECT @rid FROM Medicamento WHERE @rid = " + rid)) {
            return resultados.hasNext();
        }
    }

    private String validarRid(String rid) {
        if (rid == null || !rid.matches("#[0-9]+:[0-9]+")) {
            throw new IllegalArgumentException("Identificador inválido. Use o @rid mostrado na listagem.");
        }
        return rid;
    }

    private Map<String, Object> parametros(Medicamento medicamento) {
        Map<String, Object> valores = new HashMap<>();
        valores.put("nome", medicamento.nome());
        valores.put("concentracao", medicamento.concentracao());
        valores.put("fabricante", medicamento.fabricante());
        valores.put("preco", medicamento.preco());
        return valores;
    }

    private Medicamento converter(Result resultado) {

        Object rid = resultado.getProperty("rid");
        if (rid == null) {
            rid = resultado.getIdentity().map(Object::toString).orElse(null);
        }
        Object preco = resultado.getProperty("preco");
        return new Medicamento(
                rid == null ? "(RID não retornado pelo servidor)" : rid.toString(),
                textoOuVazio(resultado.getProperty("nome")),
                textoOuVazio(resultado.getProperty("concentracao")),
                textoOuVazio(resultado.getProperty("fabricante")),
                preco == null ? null : new BigDecimal(preco.toString()));
    }

    private String textoOuVazio(Object valor) {
        return valor == null ? "" : valor.toString();
    }
}
