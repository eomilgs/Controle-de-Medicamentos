package br.edu.projeto.medicamentos;

import java.math.BigDecimal;
import java.util.List;
import java.util.Scanner;

public class Main {
    private static final Scanner teclado = new Scanner(System.in);
    private static final MedicamentoRepositorio repositorio =
            new MedicamentoRepositorio(new ConexaoArcadeDB());

    public static void main(String[] args) {
        System.out.println("Controle de Medicamentos - ArcadeDB");
        try {
            repositorio.inicializarEstrutura();
            System.out.println("Conectado. Medicamentos cadastrados: " + repositorio.listar().size());
        } catch (Exception erro) {
            System.out.println("Não foi possível conectar ao banco ControleMedicamentos.");
            System.out.println("Confira se o ArcadeDB está ligado em localhost:2480, se o banco ControleMedicamentos existe e se a senha do usuário root é 12345678.");
            System.out.println("Detalhe: " + erro.getMessage());
            return;
        }

        int opcao;
        do {
            mostrarMenu();
            opcao = lerInteiro("Opção: ");
            try {
                switch (opcao) {
                    case 1 -> listarMedicamentos();
                    case 2 -> cadastrarMedicamento();
                    case 3 -> atualizarMedicamento();
                    case 4 -> excluirMedicamento();
                    case 5 -> listarRelacoes();
                    case 6 -> criarBackup();
                    case 7 -> exportarDump();
                    case 8 -> explicarBusca();
                    case 0 -> System.out.println("Programa encerrado.");
                    default -> System.out.println("Opção inválida.");
                }
            } catch (Exception erro) {
                System.out.println("Erro: " + erro.getMessage());
            }
        } while (opcao != 0);
    }

    private static void mostrarMenu() {
        System.out.println("\n1 - Listar medicamentos");
        System.out.println("2 - Cadastrar medicamento");
        System.out.println("3 - Atualizar medicamento");
        System.out.println("4 - Excluir medicamento");
        System.out.println("5 - Consultar relações do grafo");
        System.out.println("6 - Fazer backup manual do banco");
        System.out.println("7 - Exportar dump JSONL do banco");
        System.out.println("8 - Ver plano de busca por nome");
        System.out.println("0 - Sair");
    }

    private static void listarMedicamentos() {
        List<Medicamento> medicamentos = repositorio.listar();
        if (medicamentos.isEmpty()) {
            System.out.println("Nenhum medicamento cadastrado.");
            return;
        }
        System.out.println("@rid | Nome | Concentração | Fabricante | Preço");
        for (Medicamento medicamento : medicamentos) {
            System.out.printf("%s | %s | %s | %s | R$ %s%n",
                    medicamento.rid(), medicamento.nome(), medicamento.concentracao(),
                    medicamento.fabricante(), medicamento.preco());
        }
    }

    private static void cadastrarMedicamento() {
        System.out.println("\n--- DADOS DO MEDICAMENTO ---");
        Medicamento medicamento = lerMedicamento();
        String categoria = lerTexto("Categoria (ex.: analgésico): ");
        System.out.println("\n--- DADOS DO LOTE ---");
        String codigoLote = lerTexto("Código do lote: ");
        String validade = lerTexto("Validade (ex.: 12/2027): ");
        int quantidade = lerInteiroPositivo("Quantidade em estoque: ");
        System.out.println("\n--- LOCAL DE ARMAZENAMENTO ---");
        String farmacia = lerTexto("Nome da farmácia: ");
        String endereco = lerTexto("Endereço da farmácia: ");
        repositorio.cadastrarCompleto(medicamento, categoria, codigoLote, validade,
                quantidade, farmacia, endereco);
        System.out.println("\nCadastro concluído! Medicamento, categoria, fabricante, lote e farmácia foram ligados automaticamente no grafo.");
        System.out.println("Escolha a opção 5 para visualizar todas as informações e relações.");
    }

    private static void atualizarMedicamento() {
        System.out.println("\n--- ATUALIZAR MEDICAMENTO E TODAS AS RELAÇÕES ---");
        String rid = lerTexto("@rid do medicamento (consulte a opção 1): ");

        System.out.println("\n--- DADOS DO MEDICAMENTO ---");
        Medicamento medicamento = lerMedicamento();
        String categoria = lerTexto("Categoria: ");

        System.out.println("\n--- DADOS DO LOTE ---");
        String codigoLote = lerTexto("Código do lote: ");
        String validade = lerTexto("Validade (ex.: 12/2027): ");
        int quantidade = lerInteiroPositivo("Quantidade em estoque: ");

        System.out.println("\n--- LOCAL DE ARMAZENAMENTO ---");
        String farmacia = lerTexto("Nome da farmácia: ");
        String endereco = lerTexto("Endereço da farmácia: ");

        boolean atualizado = repositorio.atualizarCompleto(rid, medicamento, categoria,
                codigoLote, validade, quantidade, farmacia, endereco);
        System.out.println(atualizado
                ? "Medicamento, lote, categoria, fabricante e farmácia atualizados com sucesso!"
                : "Não encontrei medicamento com esse @rid.");
    }

    private static void excluirMedicamento() {
        String rid = lerTexto("@rid do medicamento a excluir: ");
        if (!confirmar("Confirma a exclusão deste medicamento? (s/n): ")) return;
        boolean excluido = repositorio.excluir(rid);
        System.out.println(excluido ? "Medicamento excluído." : "Não encontrei esse medicamento.");
    }

    private static void listarRelacoes() {
        List<String> relacoes = repositorio.listarRelacoes();
        if (relacoes.isEmpty()) {
            System.out.println("Ainda não há relações cadastradas para mostrar.");
            return;
        }
        for (String relacao : relacoes) {
            System.out.println(relacao);
        }
    }

    private static void criarBackup() {
        repositorio.criarBackup();
        System.out.println("Backup criado pelo ArcadeDB. O arquivo fica na pasta de backups do servidor.");
    }

    private static void exportarDump() {
        String arquivo = repositorio.exportarDump();
        System.out.println("Exportação criada: exports/" + arquivo + " na pasta do servidor ArcadeDB.");
    }

    private static void explicarBusca() {
        String nome = lerTexto("Nome para analisar a busca: ");
        List<String> plano = repositorio.explicarBuscaPorNome(nome);
        if (plano.isEmpty()) {
            System.out.println("O ArcadeDB não retornou o plano da consulta.");
            return;
        }
        for (String linha : plano) {
            System.out.println(linha);
        }
    }

    private static Medicamento lerMedicamento() {
        String nome = lerTexto("Nome: ");
        String concentracao = lerTexto("Concentração: ");
        String fabricante = lerTexto("Fabricante: ");
        BigDecimal preco = lerPreco();
        return new Medicamento(null, nome, concentracao, fabricante, preco);
    }

    private static BigDecimal lerPreco() {
        while (true) {
            String valor = lerTexto("Preço (ex.: 12,50): ").replace(',', '.');
            try {
                BigDecimal preco = new BigDecimal(valor);
                if (preco.signum() > 0) return preco;
            } catch (NumberFormatException ignored) {
            }
            System.out.println("Digite um preço maior que zero.");
        }
    }

    private static String lerTexto(String mensagem) {
        System.out.print(mensagem);
        return teclado.nextLine().trim();
    }

    private static int lerInteiro(String mensagem) {
        try {
            return Integer.parseInt(lerTexto(mensagem));
        } catch (NumberFormatException erro) {
            return -1;
        }
    }

    private static int lerInteiroPositivo(String mensagem) {
        while (true) {
            int valor = lerInteiro(mensagem);
            if (valor > 0) return valor;
            System.out.println("Digite uma quantidade inteira maior que zero.");
        }
    }

    private static boolean confirmar(String mensagem) {
        return lerTexto(mensagem).equalsIgnoreCase("s");
    }
}
