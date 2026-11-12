package br.edu.projeto.medicamentos;

import com.arcadedb.query.sql.executor.Result;
import com.arcadedb.query.sql.executor.ResultSet;
import com.arcadedb.remote.RemoteDatabase;
import com.arcadedb.serializer.json.JSONObject;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MedicamentoRepositorio {
    private final ConexaoArcadeDB conexao;

    public MedicamentoRepositorio(ConexaoArcadeDB conexao) { this.conexao = conexao; }

    public void inicializarEstrutura() {
        try (RemoteDatabase banco = conexao.abrir()) {
            banco.command("sql", "CREATE VERTEX TYPE Medicamento IF NOT EXISTS");
            banco.command("sql", "CREATE VERTEX TYPE Categoria IF NOT EXISTS");
            banco.command("sql", "CREATE VERTEX TYPE Fornecedor IF NOT EXISTS");
            banco.command("sql", "CREATE VERTEX TYPE Lote IF NOT EXISTS");
            banco.command("sql", "CREATE VERTEX TYPE Farmacia IF NOT EXISTS");
            banco.command("sql", "CREATE EDGE TYPE PertenceA IF NOT EXISTS");
            banco.command("sql", "CREATE EDGE TYPE FornecidoPor IF NOT EXISTS");
            banco.command("sql", "CREATE EDGE TYPE PossuiLote IF NOT EXISTS");
            banco.command("sql", "CREATE EDGE TYPE ArmazenadoEm IF NOT EXISTS");
        }
    }

    public List<Medicamento> listar() {
        List<Medicamento> lista = new ArrayList<>();
        try (RemoteDatabase banco = conexao.abrir();
             ResultSet rs = banco.query("sql", "SELECT @rid AS rid, nome, concentracao, fabricante, preco FROM Medicamento")) {
            while (rs.hasNext()) lista.add(converter(rs.next()));
        }
        return lista;
    }

    public void cadastrarCompleto(Medicamento medicamento, String categoria, String codigoLote,
                                  String validade, int quantidade, String farmacia, String endereco) {
        try (RemoteDatabase banco = conexao.abrir()) {
            String ridCategoria = buscarOuCriar(banco, "Categoria", "nome", categoria);
            String ridFornecedor = buscarOuCriar(banco, "Fornecedor", "nome", medicamento.fabricante());
            String ridFarmacia = buscarOuCriarFarmacia(banco, farmacia, endereco);

            String chaveMedicamento = UUID.randomUUID().toString();
            Map<String, Object> dados = parametros(medicamento);
            dados.put("chave", chaveMedicamento);
            banco.command("sql", "CREATE VERTEX Medicamento SET nome=:nome, concentracao=:concentracao, fabricante=:fabricante, preco=:preco, chaveCadastro=:chave", dados);
            String ridMedicamento = buscarRidPorChave(banco, "Medicamento", chaveMedicamento);

            String chaveLote = UUID.randomUUID().toString();
            Map<String, Object> lote = new HashMap<>();
            lote.put("codigo", codigoLote);
            lote.put("validade", validade);
            lote.put("quantidade", quantidade);
            lote.put("chave", chaveLote);
            banco.command("sql", "CREATE VERTEX Lote SET codigo=:codigo, validade=:validade, quantidade=:quantidade, chaveCadastro=:chave", lote);
            String ridLote = buscarRidPorChave(banco, "Lote", chaveLote);

            criarAresta(banco, "PertenceA", ridMedicamento, ridCategoria);
            criarAresta(banco, "FornecidoPor", ridMedicamento, ridFornecedor);
            criarAresta(banco, "PossuiLote", ridMedicamento, ridLote);
            criarAresta(banco, "ArmazenadoEm", ridLote, ridFarmacia);
        }
    }

    public void cadastrar(Medicamento medicamento) {
        cadastrarCompleto(medicamento, "Não informada", "LOTE-" + UUID.randomUUID().toString().substring(0, 8),
                "Não informada", 0, "Farmácia não informada", "Endereço não informado");
    }

    public boolean atualizarCompleto(String rid, Medicamento medicamento, String categoria,
                                     String codigoLote, String validade, int quantidade,
                                     String farmacia, String endereco) {
        String id = validarRid(rid);
        try (RemoteDatabase banco = conexao.abrir()) {
            if (!existe(banco, id)) return false;

            banco.command("sql", "UPDATE Medicamento SET nome=:nome, concentracao=:concentracao, fabricante=:fabricante, preco=:preco WHERE @rid = " + id,
                    parametros(medicamento));

            String ridCategoria = buscarOuCriar(banco, "Categoria", "nome", categoria);
            banco.command("sql", "DELETE FROM PertenceA WHERE @out = " + id);
            criarAresta(banco, "PertenceA", id, ridCategoria);

            String ridFornecedor = buscarOuCriar(banco, "Fornecedor", "nome", medicamento.fabricante());
            banco.command("sql", "DELETE FROM FornecidoPor WHERE @out = " + id);
            criarAresta(banco, "FornecidoPor", id, ridFornecedor);

            String ridLote = primeiroDestinoRid(banco, id, "PossuiLote");
            if (ridLote == null) {
                String chaveLote = UUID.randomUUID().toString();
                Map<String, Object> dadosLote = new HashMap<>();
                dadosLote.put("codigo", codigoLote);
                dadosLote.put("validade", validade);
                dadosLote.put("quantidade", quantidade);
                dadosLote.put("chave", chaveLote);
                banco.command("sql", "CREATE VERTEX Lote SET codigo=:codigo, validade=:validade, quantidade=:quantidade, chaveCadastro=:chave", dadosLote);
                ridLote = buscarRidPorChave(banco, "Lote", chaveLote);
                criarAresta(banco, "PossuiLote", id, ridLote);
            } else {
                Map<String, Object> dadosLote = new HashMap<>();
                dadosLote.put("codigo", codigoLote);
                dadosLote.put("validade", validade);
                dadosLote.put("quantidade", quantidade);
                banco.command("sql", "UPDATE Lote SET codigo=:codigo, validade=:validade, quantidade=:quantidade WHERE @rid = " + validarRid(ridLote), dadosLote);
            }

            String ridFarmacia = buscarOuCriarFarmacia(banco, farmacia, endereco);
            banco.command("sql", "DELETE FROM ArmazenadoEm WHERE @out = " + validarRid(ridLote));
            criarAresta(banco, "ArmazenadoEm", ridLote, ridFarmacia);
            return true;
        }
    }

    public boolean atualizar(String rid, Medicamento medicamento) {
        return atualizarCompleto(rid, medicamento, "Não informada",
                "Não informado", "Não informada", 1,
                "Farmácia não informada", "Endereço não informado");
    }

    private String primeiroDestinoRid(RemoteDatabase banco, String origem, String aresta) {
        try (ResultSet rs = banco.query("sql", "SELECT expand(out('" + aresta + "')) FROM " + validarRid(origem))) {
            if (rs.hasNext()) return rs.next().getIdentity().map(Object::toString).orElse(null);
        }
        return null;
    }

    public boolean excluir(String rid) {
        String id = validarRid(rid);
        try (RemoteDatabase banco = conexao.abrir()) {
            if (!existe(banco, id)) return false;
            try (ResultSet rs = banco.query("sql", "SELECT expand(out('PossuiLote')) FROM " + id)) {
                List<String> lotes = new ArrayList<>();
                while (rs.hasNext()) rs.next().getIdentity().ifPresent(identity -> lotes.add(identity.toString()));
                for (String loteRid : lotes) {
                    banco.command("sql", "DELETE FROM ArmazenadoEm WHERE @out = " + loteRid);
                    banco.command("sql", "DELETE FROM Lote WHERE @rid = " + loteRid);
                }
            }
            banco.command("sql", "DELETE FROM PertenceA WHERE @out = " + id);
            banco.command("sql", "DELETE FROM FornecidoPor WHERE @out = " + id);
            banco.command("sql", "DELETE FROM PossuiLote WHERE @out = " + id);
            banco.command("sql", "DELETE FROM Medicamento WHERE @rid = " + id);
            return true;
        }
    }

    public List<String> listarRelacoes() {
        List<String> saida = new ArrayList<>();
        try (RemoteDatabase banco = conexao.abrir();
             ResultSet medicamentos = banco.query("sql", "SELECT @rid AS rid, nome, concentracao, fabricante, preco FROM Medicamento ORDER BY nome")) {
            while (medicamentos.hasNext()) {
                Result m = medicamentos.next();
                String rid = m.getProperty("rid").toString();
                saida.add("============================================================");
                saida.add("MEDICAMENTO: " + valor(m, "nome") + " | " + valor(m, "concentracao"));
                saida.add("Fabricante: " + valor(m, "fabricante") + " | Preço: R$ " + valor(m, "preco"));
                saida.add("@rid: " + rid);
                saida.add("  CATEGORIA (PertenceA): " + destinos(banco, rid, "PertenceA", "nome"));
                saida.add("  FABRICANTE (FornecidoPor): " + destinos(banco, rid, "FornecidoPor", "nome"));
                List<String> lotes = consultarLotes(banco, rid);
                if (lotes.isEmpty()) saida.add("  LOTES: nenhum lote vinculado");
                else for (String lote : lotes) saida.add(lote);
            }
        }
        return saida;
    }

    private List<String> consultarLotes(RemoteDatabase banco, String ridMedicamento) {
        List<String> linhas = new ArrayList<>();
        try (ResultSet rs = banco.query("sql", "SELECT expand(out('PossuiLote')) FROM " + ridMedicamento)) {
            while (rs.hasNext()) {
                Result lote = rs.next();
                String ridLote = lote.getIdentity().map(Object::toString).orElse("");
                linhas.add("  LOTE: " + valor(lote, "codigo") + " | Validade: " + valor(lote, "validade") + " | Quantidade: " + valor(lote, "quantidade"));
                if (!ridLote.isBlank()) {
                    linhas.add("    ARMAZENADO EM (ArmazenadoEm): " + destinos(banco, ridLote, "ArmazenadoEm", "nome/endereco"));
                } else {
                    linhas.add("    ARMAZENADO EM: não foi possível recuperar o identificador do lote");
                }
            }
        }
        return linhas;
    }

    private String destinos(RemoteDatabase banco, String origem, String aresta, String campos) {
        List<String> valores = new ArrayList<>();
        String sql = "SELECT expand(out('" + aresta + "')) FROM " + origem;
        try (ResultSet rs = banco.query("sql", sql)) {
            while (rs.hasNext()) {
                Result r = rs.next();
                if ("nome/endereco".equals(campos)) valores.add(valor(r, "nome") + " — " + valor(r, "endereco"));
                else valores.add(valor(r, campos));
            }
        }
        return valores.isEmpty() ? "não informado" : String.join(", ", valores);
    }

    private String buscarOuCriar(RemoteDatabase banco, String tipo, String campo, String valor) {
        String sql = "SELECT @rid AS rid FROM " + tipo + " WHERE " + campo + " = :valor LIMIT 1";
        try (ResultSet rs = banco.query("sql", sql, Map.of("valor", valor))) {
            if (rs.hasNext()) return rs.next().getProperty("rid").toString();
        }
        String chave = UUID.randomUUID().toString();
        banco.command("sql", "CREATE VERTEX " + tipo + " SET " + campo + "=:valor, chaveCadastro=:chave",
                Map.of("valor", valor, "chave", chave));
        return buscarRidPorChave(banco, tipo, chave);
    }

    private String buscarOuCriarFarmacia(RemoteDatabase banco, String nome, String endereco) {
        try (ResultSet rs = banco.query("sql", "SELECT @rid AS rid FROM Farmacia WHERE nome=:nome AND endereco=:endereco LIMIT 1",
                Map.of("nome", nome, "endereco", endereco))) {
            if (rs.hasNext()) return rs.next().getProperty("rid").toString();
        }
        String chave = UUID.randomUUID().toString();
        banco.command("sql", "CREATE VERTEX Farmacia SET nome=:nome, endereco=:endereco, chaveCadastro=:chave",
                Map.of("nome", nome, "endereco", endereco, "chave", chave));
        return buscarRidPorChave(banco, "Farmacia", chave);
    }

    private String buscarRidPorChave(RemoteDatabase banco, String tipo, String chave) {
        try (ResultSet rs = banco.query("sql", "SELECT @rid AS rid FROM " + tipo + " WHERE chaveCadastro=:chave LIMIT 1", Map.of("chave", chave))) {
            if (rs.hasNext()) return rs.next().getProperty("rid").toString();
        }
        throw new IllegalStateException("Não foi possível recuperar o @rid criado para " + tipo + ".");
    }

    private void criarAresta(RemoteDatabase banco, String tipo, String origem, String destino) {
        banco.command("sql", "CREATE EDGE " + tipo + " FROM " + validarRid(origem) + " TO " + validarRid(destino));
    }

    public void criarBackup() { try (RemoteDatabase banco = conexao.abrir()) { banco.command("sql", "BACKUP DATABASE"); } }

    public String exportarDump() {
        String nome = "ControleMedicamentos-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".jsonl.tgz";
        try (RemoteDatabase banco = conexao.abrir()) { banco.command("sql", "EXPORT DATABASE file://" + nome); }
        return nome;
    }

    public List<String> explicarBuscaPorNome(String nome) {
        String db = URLEncoder.encode(conexao.banco(), StandardCharsets.UTF_8).replace("+", "%20");
        String url = "http://" + conexao.host() + ":" + conexao.porta() + "/api/v1/query/" + db;
        String auth = "Basic " + Base64.getEncoder().encodeToString((conexao.usuario() + ":" + conexao.senha()).getBytes(StandardCharsets.UTF_8));
        JSONObject corpo = new JSONObject().put("language", "sql").put("command", "EXPLAIN SELECT FROM Medicamento WHERE nome = :nome").put("params", Map.of("nome", nome));
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).header("Authorization", auth).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(corpo.toString())).build();
        try {
            HttpResponse<String> resp = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) throw new IllegalStateException("HTTP " + resp.statusCode() + ": " + resp.body());
            String plano = new JSONObject(resp.body()).getString("explain");
            return plano == null || plano.isBlank() ? List.of("Plano não retornado.") : List.of(plano.split("\\R"));
        } catch (IOException e) { throw new IllegalStateException("Falha ao consultar plano: " + e.getMessage(), e); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("Consulta interrompida.", e); }
    }

    private boolean existe(RemoteDatabase banco, String rid) {
        try (ResultSet rs = banco.query("sql", "SELECT @rid FROM Medicamento WHERE @rid = " + rid)) { return rs.hasNext(); }
    }
    private String validarRid(String rid) {
        if (rid == null || !rid.matches("#[0-9]+:[0-9]+")) throw new IllegalArgumentException("@rid inválido: " + rid);
        return rid;
    }
    private Map<String,Object> parametros(Medicamento m) {
        Map<String,Object> p = new HashMap<>(); p.put("nome",m.nome()); p.put("concentracao",m.concentracao()); p.put("fabricante",m.fabricante()); p.put("preco",m.preco()); return p;
    }
    private Medicamento converter(Result r) {
        Object rid = r.getProperty("rid"); Object preco = r.getProperty("preco");
        return new Medicamento(rid == null ? "(sem RID)" : rid.toString(), texto(r.getProperty("nome")), texto(r.getProperty("concentracao")), texto(r.getProperty("fabricante")), preco == null ? null : new BigDecimal(preco.toString()));
    }
    private String valor(Result r, String campo) { return texto(r.getProperty(campo)); }
    private String texto(Object o) { return o == null ? "não informado" : o.toString(); }
}
