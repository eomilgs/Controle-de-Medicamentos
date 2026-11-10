package br.edu.projeto.medicamentos;

import java.math.BigDecimal;

public record Medicamento(String rid, String nome, String concentracao,
                          String fabricante, BigDecimal preco) {
}
