package br.edu.projeto.medicamentos;

import com.arcadedb.remote.RemoteDatabase;

public class ConexaoArcadeDB {
    private final String host = System.getenv().getOrDefault("ARCADEDB_HOST", "localhost");
    private final int porta = Integer.parseInt(System.getenv().getOrDefault("ARCADEDB_PORT", "2480"));
    private final String banco = System.getenv().getOrDefault("ARCADEDB_DATABASE", "ControleMedicamentos");
    private final String usuario = System.getenv().getOrDefault("ARCADEDB_USER", "root");

    public RemoteDatabase abrir() {
        String senha = System.getenv().getOrDefault("ARCADEDB_PASSWORD", "12345678");
        return new RemoteDatabase(host, porta, banco, usuario, senha);
    }
    public String host() {
        return host;
    }

    public int porta() {
        return porta;
    }

    public String banco() {
        return banco;
    }

    public String usuario() {
        return usuario;
    }

    public String senha() {
        return System.getenv().getOrDefault("ARCADEDB_PASSWORD", "12345678");
    }
}
