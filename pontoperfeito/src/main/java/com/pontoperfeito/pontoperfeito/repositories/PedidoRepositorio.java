package com.pontoperfeito.pontoperfeito.repositories;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.pontoperfeito.pontoperfeito.model.Pedido;
import com.pontoperfeito.pontoperfeito.model.StatusPagamento;
import com.pontoperfeito.pontoperfeito.model.StatusPedido;

import jakarta.annotation.PostConstruct;

import com.pontoperfeito.pontoperfeito.model.Cliente;
import com.pontoperfeito.pontoperfeito.model.Item;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Date;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class PedidoRepositorio {
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public PedidoRepositorio(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void seedPedidos() {
        String query = "SELECT COUNT(*) FROM pedidos";
        int rowCount = jdbcTemplate.queryForObject(query, Integer.class);
    
        if (rowCount == 0) {
        String sql = "INSERT INTO pedidos (id, id_cliente, data_pedido, estimativa_entrega, data_entrega, status_pedido, status_pagamento, observacao) VALUES " +
                "(1, 1, '2023-12-01', '2023-12-10', NULL, 'PENDENTE', 'PENDENTE', 'Observacao do Pedido 1')," +
                "(2, 2, '2023-12-02', '2023-12-11', NULL, 'PENDENTE', 'PENDENTE', 'Observacao do Pedido 2')," +
                "(3, 3, '2023-12-03', '2023-12-12', NULL, 'PRONTO', 'PAGO', 'Observacao do Pedido 3')," +
                "(4, 4, '2023-12-04', '2023-12-13', NULL, 'PRONTO', 'PAGO', 'Observacao do Pedido 4')," +
                "(5, 5, '2023-12-05', '2023-12-14', NULL, 'FINALIZADO', 'PAGO', 'Observacao do Pedido 5')," +
                "(6, 6, '2023-12-06', '2023-12-15', NULL, 'FINALIZADO', 'PAGO', 'Observacao do Pedido 6')," +
                "(7, 7, '2023-12-07', '2023-12-16', NULL, 'PENDENTE', 'PENDENTE', 'Observacao do Pedido 7')," +
                "(8, 8, '2023-12-08', '2023-12-17', NULL, 'PENDENTE', 'PENDENTE', 'Observacao do Pedido 8')," +
                "(9, 9, '2023-12-09', '2023-12-18', NULL, 'PRONTO', 'PAGO', 'Observacao do Pedido 9')," +
                "(10, 10, '2023-12-10', '2023-12-19', NULL, 'PRONTO', 'PAGO', 'Observacao do Pedido 10')";
    
        jdbcTemplate.execute(sql);
        }
    }

    public List<Pedido> buscarClientesPorNome(String nome) {
        String sql = "SELECT\r\n" + //
                "    pedidos.*,\r\n" + //
                "    (SELECT GROUP_CONCAT(CONCAT(itens.id, ' - ', itens.valor, ' - ', itens.nome) ORDER BY itens.id)\r\n"
                + //
                "     FROM itens_pedido\r\n" + //
                "     LEFT JOIN itens ON itens.id = itens_pedido.id_item\r\n" + //
                "     WHERE itens_pedido.id_pedido = pedidos.id) AS itens_do_pedido,\r\n" + //
                "    clientes.*\r\n" + //
                "FROM\r\n" + //
                "    pedidos\r\n" + //
                "INNER JOIN\r\n" + //
                "    clientes ON clientes.id = pedidos.id_cliente\r\n" + //
                "WHERE\r\n" + //
                "    clientes.nome LIKE ? \r\n" + //
                "GROUP BY\r\n" + //
                "    pedidos.id;";

        String parametroLike = "%" + nome + "%";
        List<Pedido> resultados = jdbcTemplate.query(sql, this::mapearPedido, parametroLike);

        return resultados.isEmpty() ? new ArrayList<>() : resultados;
    }

    private Pedido mapearPedido(ResultSet resultSet, int rowNum) throws SQLException {
        Long pedidoId = resultSet.getLong("id");

        Pedido pedido = new Pedido();
        pedido.setId(pedidoId);
        pedido.setEstimativa_entrega(resultSet.getDate("estimativa_entrega"));
        pedido.setData_entrega(resultSet.getDate("data_entrega"));
        pedido.setStatus_pedido(StatusPedido.valueOf(resultSet.getString("status_pedido")));
        pedido.setStatus_pagamento(StatusPagamento.valueOf(resultSet.getString("status_pagamento")));
        pedido.setObservacao(resultSet.getString("observacao"));

        Cliente cliente = new Cliente();
        cliente.setNome(resultSet.getString("nome"));

        pedido.setCliente(cliente);

        String itensConcatenados = resultSet.getString("itens_do_pedido");
        if (itensConcatenados != null) {
            Set<Item> itens = Arrays.stream(itensConcatenados.split(","))
                    .map(itemString -> {
                        String[] itemInfo = itemString.split(" - ");
                        if (itemInfo.length >= 3) {
                            Long itemId = Long.parseLong(itemInfo[0]); // Adicionado o ID
                            return new Item(itemId, itemInfo[2], Float.parseFloat(itemInfo[1]), itemInfo[0]);
                        } else {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            pedido.setItens(itens);
        }

        return pedido;
    }

    public List<Pedido> listarPedidos() {
        String sql = "SELECT p.*, GROUP_CONCAT(CONCAT(i.id, ' - ', i.valor, ' - ', i.nome) ORDER BY i.id) AS itens_do_pedido, " +
                     "c.* " +
                     "FROM pedidos p " +
                     "INNER JOIN (SELECT * FROM clientes) c ON c.id = p.id_cliente " +
                     "LEFT JOIN itens_pedido ip ON ip.id_pedido = p.id " +
                     "LEFT JOIN itens i ON i.id = ip.id_item " +
                     "GROUP BY p.id";
    
        return jdbcTemplate.query(sql, this::mapearPedido);
    }
    
    public void associarItensAoPedido(Long pedidoId, Set<Long> itens) {
        String sqlItensPedido = "INSERT INTO itens_pedido (id_pedido, id_item, quantidade) VALUES (?, ?, ?)";

        for (Long itemId : itens) {
            jdbcTemplate.update(sqlItensPedido, pedidoId, itemId, 1);
        }
    }

    public Pedido buscarPedidoPorId(Long id) {
        String sql = "SELECT * FROM pedidos WHERE id = ?";
        return jdbcTemplate.queryForObject(sql, new BeanPropertyRowMapper<>(Pedido.class), id);
    }

    public Pedido criarPedido(Pedido pedido) {
        Calendar calendar = Calendar.getInstance();
        java.sql.Date dataAtual = new java.sql.Date(calendar.getTime().getTime());

        String sql = "INSERT INTO pedidos (id_cliente, data_pedido, estimativa_entrega, data_entrega, status_pedido, status_pagamento, observacao) VALUES (?, ?, ?, ?, ?, ?, ?)";

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, pedido.getCliente().getId());
            ps.setDate(2, dataAtual);
            ps.setDate(3, new java.sql.Date(pedido.getEstimativa_entrega().getTime()));
            ps.setDate(4, null);
            ps.setString(5, pedido.getStatus_pedido().toString());
            ps.setString(6, pedido.getStatus_pagamento().toString());
            ps.setString(7, pedido.getObservacao().toString());
            return ps;
        }, keyHolder);

        Long idGerado = keyHolder.getKey().longValue();

        pedido.setId(idGerado);

        return pedido;
    }

    public Pedido atualizarPedido(Long id, Pedido pedidoAtualizado) {

        if (pedidoAtualizado.getStatus_pedido().toString() == "FINALIZADO") {
            Calendar calendar = Calendar.getInstance();
            java.sql.Date dataAtual = new java.sql.Date(calendar.getTime().getTime());

            String sql = "UPDATE pedidos SET estimativa_entrega = ?, data_entrega = ?, status_pedido = ?, status_pagamento = ?, observacao = ? WHERE id = ?";

            jdbcTemplate.update(
                    sql,
                    new java.sql.Date(pedidoAtualizado.getEstimativa_entrega().getTime()),
                    dataAtual,
                    pedidoAtualizado.getStatus_pedido().toString(),
                    pedidoAtualizado.getStatus_pagamento().toString(),
                    pedidoAtualizado.getObservacao(),
                    id);
        } else {
            String sql = "UPDATE pedidos SET estimativa_entrega = ?, status_pedido = ?, status_pagamento = ?, observacao = ? WHERE id = ?";

            jdbcTemplate.update(
                    sql,
                    new java.sql.Date(pedidoAtualizado.getEstimativa_entrega().getTime()),
                    pedidoAtualizado.getStatus_pedido().toString(),
                    pedidoAtualizado.getStatus_pagamento().toString(),
                    pedidoAtualizado.getObservacao(),
                    id);
        }

        return pedidoAtualizado;
    }

    public void atualizarItensDoPedido(Long pedidoId, Set<Long> itens) {
        String sqlRemoverItens = "DELETE FROM itens_pedido WHERE id_pedido = ?";
        jdbcTemplate.update(sqlRemoverItens, pedidoId);

        String sqlInserirItens = "INSERT INTO itens_pedido (id_pedido, id_item, quantidade) VALUES (?, ?, ?)";
        for (Long itemId : itens) {
            jdbcTemplate.update(sqlInserirItens, pedidoId, itemId, 1);
        }
    }

    public void excluirPedido(Long id) {
        String sqlItem = "DELETE FROM itens_pedido WHERE id_pedido = ?";
        jdbcTemplate.update(sqlItem, id);
        String sql = "DELETE FROM pedidos WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }

    public List<Pedido> buscarPedidosPorFiltros(String nomeCliente, String statusPagamento, String statusPedido,
            Date dataInicial, Date dataFinal) {
        StringBuilder sql = new StringBuilder("SELECT pedidos.*, " +
                "(SELECT GROUP_CONCAT(CONCAT(itens.id, ' - ', itens.valor, ' - ', itens.nome) ORDER BY itens.id) " +
                " FROM itens_pedido " +
                " INNER JOIN itens ON itens.id = itens_pedido.id_item " +
                " WHERE itens_pedido.id_pedido = pedidos.id) AS itens_do_pedido, " +
                "clientes.* " +
                "FROM pedidos " +
                "INNER JOIN clientes ON clientes.id = pedidos.id_cliente ");

        List<Object> params = new ArrayList<>();

        if (nomeCliente != null && !nomeCliente.isEmpty()) {
            sql.append("WHERE clientes.nome LIKE ? ");
            params.add("%" + nomeCliente + "%");
        }

        if (statusPagamento != null && !statusPagamento.isEmpty()) {
            if (params.isEmpty()) {
                sql.append("WHERE ");
            } else {
                sql.append("AND ");
            }
            sql.append("pedidos.status_pagamento = ? ");
            params.add(statusPagamento);
        }

        if (statusPedido != null && !statusPedido.isEmpty()) {
            if (params.isEmpty()) {
                sql.append("WHERE ");
            } else {
                sql.append("AND ");
            }
            sql.append("pedidos.status_pedido = ? ");
            params.add(statusPedido);
        }

        if (dataInicial != null && dataFinal != null) {
            if (params.isEmpty()) {
                sql.append("WHERE ");
            } else {
                sql.append("AND ");
            }
            sql.append("pedidos.estimativa_entrega BETWEEN ? AND ? ");
            params.add(new java.sql.Date(dataInicial.getTime()));
            params.add(new java.sql.Date(dataFinal.getTime()));
        } else if (dataInicial != null) {
            if (params.isEmpty()) {
                sql.append("WHERE ");
            } else {
                sql.append("AND ");
            }
            sql.append("pedidos.estimativa_entrega >= ? ");
            params.add(new java.sql.Date(dataInicial.getTime()));
        } else if (dataFinal != null) {
            if (params.isEmpty()) {
                sql.append("WHERE ");
            } else {
                sql.append("AND ");
            }
            sql.append("pedidos.estimativa_entrega <= ? ");
            params.add(new java.sql.Date(dataFinal.getTime()));
        }

        sql.append("GROUP BY pedidos.id");
        Object[] paramsArray = params.toArray();

        return jdbcTemplate.query(sql.toString(), paramsArray, (resultSet, rowNum) -> {
            Pedido pedido = new Pedido();
            // Preencha os campos do pedido com os dados do ResultSet
            pedido.setId(resultSet.getLong("id"));
            pedido.setEstimativa_entrega(resultSet.getDate("estimativa_entrega"));
            pedido.setData_entrega(resultSet.getDate("data_entrega"));
            pedido.setStatus_pedido(StatusPedido.valueOf(resultSet.getString("status_pedido")));
            pedido.setStatus_pagamento(StatusPagamento.valueOf(resultSet.getString("status_pagamento")));
            pedido.setObservacao(resultSet.getString("observacao"));

            Cliente cliente = new Cliente();
            cliente.setNome(resultSet.getString("nome"));
            pedido.setCliente(cliente);

            String itensConcatenados = resultSet.getString("itens_do_pedido");
            if (itensConcatenados != null) {
                Set<Item> itens = Arrays.stream(itensConcatenados.split(","))
                        .map(itemString -> {
                            String[] itemInfo = itemString.split(" - ");
                            if (itemInfo.length >= 3) {
                                Long itemId = Long.parseLong(itemInfo[0]);
                                return new Item(itemId, itemInfo[2], Float.parseFloat(itemInfo[1]), itemInfo[0]);
                            } else {
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

                pedido.setItens(itens);
            }

            return pedido;
        });
    }

}
