/* Dashboard administrativo — consome /api/dashboard/** (Chart.js 2.x). */
(function () {
    'use strict';

    var API = '/api/dashboard';
    var CORES = ['#0d6efd', '#198754', '#fd7e14', '#6f42c1', '#dc3545', '#20c997', '#6c757d', '#ffc107'];
    var CORES_STATUS = {
        RECEBIDO: '#0d6efd', EM_PREPARACAO: '#fd7e14', PRONTO: '#20c997', SAIU_PARA_ENTREGA: '#6f42c1',
        RETIRADO: '#0dcaf0', FINALIZADO: '#198754', CANCELADO: '#dc3545'
    };
    var ROTULO_STATUS = {
        RECEBIDO: 'Recebido', EM_PREPARACAO: 'Em preparação', PRONTO: 'Pronto',
        SAIU_PARA_ENTREGA: 'Saiu p/ entrega', RETIRADO: 'Retirado', FINALIZADO: 'Finalizado', CANCELADO: 'Cancelado'
    };
    var moeda = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
    var inteiro = new Intl.NumberFormat('pt-BR');
    var graficos = {};
    var timer = null;
    var carregando = false;

    function $(id) { return document.getElementById(id); }

    function iso(d) {
        var m = String(d.getMonth() + 1).padStart(2, '0');
        var dia = String(d.getDate()).padStart(2, '0');
        return d.getFullYear() + '-' + m + '-' + dia;
    }

    function hoje() { return new Date(); }
    function diasAtras(n) { var d = new Date(); d.setDate(d.getDate() - n); return d; }

    function definirPeriodo(inicio, fim) {
        $('f-inicio').value = iso(inicio);
        $('f-fim').value = iso(fim);
    }

    function filtrosAtuais() {
        var p = new URLSearchParams();
        if ($('f-inicio').value) p.set('inicio', $('f-inicio').value);
        if ($('f-fim').value) p.set('fim', $('f-fim').value);
        if ($('f-status').value) p.set('status', $('f-status').value);
        if ($('f-categoria').value) p.set('categoria', $('f-categoria').value);
        return p;
    }

    function validarFiltros() {
        var erro = $('f-erro');
        var ini = $('f-inicio').value, fim = $('f-fim').value;
        var msg = '';
        if (!ini || !fim) msg = 'Informe a data inicial e a data final.';
        else if (ini > fim) msg = 'A data inicial não pode ser posterior à data final.';
        else if ((new Date(fim) - new Date(ini)) / 86400000 + 1 > 366) msg = 'O período máximo é de 366 dias.';
        erro.textContent = msg;
        erro.classList.toggle('d-none', !msg);
        return !msg;
    }

    function buscar(caminho, extras) {
        var p = filtrosAtuais();
        if (extras) Object.keys(extras).forEach(function (k) { p.set(k, extras[k]); });
        var qs = p.toString();
        return fetch(API + caminho + (qs ? '?' + qs : ''), {
            credentials: 'same-origin',
            headers: { 'Accept': 'application/json' }
        }).then(function (resp) {
            var tipo = resp.headers.get('content-type') || '';
            if (resp.redirected || tipo.indexOf('application/json') === -1) {
                throw new Error('Sua sessão expirou. Faça login novamente.');
            }
            return resp.json().then(function (corpo) {
                if (!resp.ok) throw new Error((corpo && corpo.erro) || ('Erro ' + resp.status));
                return corpo;
            });
        });
    }

    // ---------- estados de carregamento / erro / vazio (RF07)
    function estado(secao, tipo, texto) {
        var alvo = document.querySelector('[data-secao="' + secao + '"] .dash-estado');
        if (!alvo) return;
        alvo.className = 'dash-estado small ' + (tipo === 'erro' ? 'text-danger' : 'text-muted');
        alvo.textContent = texto || '';
        alvo.style.display = texto ? '' : 'none';
    }

    function desenhar(id, config) {
        if (graficos[id]) graficos[id].destroy();
        graficos[id] = new Chart($(id).getContext('2d'), config);
    }

    function limparGrafico(id) {
        if (graficos[id]) { graficos[id].destroy(); delete graficos[id]; }
    }

    function formatarValorEixo(v) { return moeda.format(v); }

    // ---------- seções
    function renderResumo(r) {
        $('k-faturamento').textContent = moeda.format(r.faturamentoTotal);
        $('k-pedidos').textContent = inteiro.format(r.pedidosValidos);
        $('k-ticket').textContent = moeda.format(r.ticketMedio);
        $('k-itens').textContent = inteiro.format(r.itensVendidos) + ' itens vendidos';
        $('k-cancelados').textContent = inteiro.format(r.pedidosCancelados) +
            ' (' + r.taxaCancelamentoPercentual.toFixed(1).replace('.', ',') + '%)';
        $('k-descontos').textContent = moeda.format(r.totalDescontos) + ' em descontos';
        variacao('k-faturamento-var', r.comparativo.variacaoFaturamentoPercentual);
        variacao('k-pedidos-var', r.comparativo.variacaoPedidosPercentual);
    }

    function variacao(id, valor) {
        var el = $(id);
        if (valor === null || valor === undefined) {
            el.className = 'small text-muted';
            el.textContent = 'sem período anterior para comparar';
            return;
        }
        var sobe = valor >= 0;
        el.className = 'small ' + (sobe ? 'text-success' : 'text-danger');
        el.textContent = (sobe ? '▲ ' : '▼ ') + Math.abs(valor).toFixed(1).replace('.', ',') + '% vs. período anterior';
    }

    function rotuloPeriodo(chave, agrup) {
        if (agrup === 'mes') { var m = chave.split('-'); return m[1] + '/' + m[0]; }
        var d = chave.split('-');
        var texto = d[2] + '/' + d[1];
        return agrup === 'semana' ? 'sem. ' + texto : texto;
    }

    function renderVendas(s) {
        var total = s.pontos.reduce(function (a, p) { return a + p.pedidos; }, 0);
        if (total === 0) { limparGrafico('c-vendas'); estado('vendas', 'vazio', 'Nenhuma venda no período selecionado.'); return; }
        estado('vendas', 'ok', '');
        desenhar('c-vendas', {
            type: 'bar',
            data: {
                labels: s.pontos.map(function (p) { return rotuloPeriodo(p.periodo, s.agrupamento); }),
                datasets: [
                    { type: 'line', label: 'Faturamento (R$)', yAxisID: 'y-fat', borderColor: CORES[0],
                      backgroundColor: 'rgba(13,110,253,.1)', fill: true, lineTension: 0.25,
                      data: s.pontos.map(function (p) { return p.faturamento; }) },
                    { type: 'bar', label: 'Pedidos', yAxisID: 'y-ped', backgroundColor: 'rgba(253,126,20,.55)',
                      data: s.pontos.map(function (p) { return p.pedidos; }) }
                ]
            },
            options: {
                responsive: true, maintainAspectRatio: false, legend: { position: 'bottom' },
                scales: {
                    yAxes: [
                        { id: 'y-fat', position: 'left', ticks: { beginAtZero: true, callback: formatarValorEixo } },
                        { id: 'y-ped', position: 'right', ticks: { beginAtZero: true, precision: 0 },
                          gridLines: { drawOnChartArea: false } }
                    ],
                    xAxes: [{ ticks: { autoSkip: true, maxTicksLimit: 15 } }]
                },
                tooltips: { callbacks: { label: function (item, data) {
                    var ds = data.datasets[item.datasetIndex];
                    return ds.label + ': ' + (item.datasetIndex === 0 ? moeda.format(item.yLabel) : inteiro.format(item.yLabel));
                } } }
            }
        });
    }

    function renderStatus(s) {
        if (s.total === 0) { limparGrafico('c-status'); estado('status', 'vazio', 'Nenhum pedido no período selecionado.'); return; }
        estado('status', 'ok', '');
        var itens = s.status.filter(function (x) { return x.quantidade > 0; });
        desenhar('c-status', {
            type: 'doughnut',
            data: {
                labels: itens.map(function (x) { return (ROTULO_STATUS[x.status] || x.status); }),
                datasets: [{ data: itens.map(function (x) { return x.quantidade; }),
                    backgroundColor: itens.map(function (x) { return CORES_STATUS[x.status] || '#6c757d'; }) }]
            },
            options: { responsive: true, maintainAspectRatio: false, legend: { position: 'bottom' } }
        });
    }

    function renderHoras(lista) {
        var total = lista.reduce(function (a, h) { return a + h.pedidos; }, 0);
        if (total === 0) { limparGrafico('c-horas'); estado('horas', 'vazio', 'Sem vendas para calcular horários de pico.'); return; }
        estado('horas', 'ok', '');
        desenhar('c-horas', {
            type: 'bar',
            data: {
                labels: lista.map(function (h) { return String(h.hora).padStart(2, '0') + 'h'; }),
                datasets: [{ label: 'Pedidos', backgroundColor: CORES[0],
                    data: lista.map(function (h) { return h.pedidos; }) }]
            },
            options: { responsive: true, maintainAspectRatio: false, legend: { display: false },
                scales: { yAxes: [{ ticks: { beginAtZero: true, precision: 0 } }] } }
        });
    }

    function renderCategorias(lista) {
        if (!lista.length) { limparGrafico('c-categorias'); estado('categorias', 'vazio', 'Nenhuma venda no período selecionado.'); return; }
        estado('categorias', 'ok', '');
        desenhar('c-categorias', {
            type: 'doughnut',
            data: {
                labels: lista.map(function (c) { return c.categoria; }),
                datasets: [{ data: lista.map(function (c) { return c.faturamento; }),
                    backgroundColor: lista.map(function (c, i) { return CORES[i % CORES.length]; }) }]
            },
            options: { responsive: true, maintainAspectRatio: false, legend: { position: 'right' },
                tooltips: { callbacks: { label: function (item, data) {
                    return data.labels[item.index] + ': ' + moeda.format(data.datasets[0].data[item.index]);
                } } } }
        });
    }

    function celula(tr, texto, classe) {
        var td = document.createElement('td');
        td.textContent = texto;           // textContent evita injeção de HTML
        if (classe) td.className = classe;
        tr.appendChild(td);
        return td;
    }

    function renderRanking(lista) {
        var corpo = $('t-ranking');
        corpo.textContent = '';
        if (!lista.length) { estado('ranking', 'vazio', 'Nenhum produto vendido no período selecionado.'); return; }
        estado('ranking', 'ok', '');
        lista.forEach(function (p) {
            var tr = document.createElement('tr');
            celula(tr, p.posicao + 'º', 'fw-bold');
            celula(tr, p.nome);
            celula(tr, p.categoria, 'text-muted');
            celula(tr, inteiro.format(p.quantidade), 'text-end');
            celula(tr, moeda.format(p.receita), 'text-end');
            corpo.appendChild(tr);
        });
    }

    function renderRecentes(lista) {
        var corpo = $('t-recentes');
        corpo.textContent = '';
        if (!lista.length) { estado('recentes', 'vazio', 'Nenhum pedido no período selecionado.'); return; }
        estado('recentes', 'ok', '');
        lista.forEach(function (p) {
            var tr = document.createElement('tr');
            celula(tr, new Date(p.dataHoraEntrada).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' }));
            celula(tr, p.cliente);
            celula(tr, p.itens, 'small');
            var td = document.createElement('td');
            tr.appendChild(td);
            var badge = document.createElement('span');
            badge.className = 'badge';
            badge.style.backgroundColor = CORES_STATUS[p.status] || '#6c757d';
            badge.textContent = ROTULO_STATUS[p.status] || p.status;
            td.appendChild(badge);
            celula(tr, moeda.format(p.valorTotal), 'text-end');
            corpo.appendChild(tr);
        });
    }

    // ---------- carga de dados (RF06/RF07)
    var SECOES = [
        { chave: 'kpis', carregar: function () { return buscar('/resumo'); }, render: renderResumo, aviso: false },
        { chave: 'vendas', grafico: 'c-vendas', carregar: function () { return buscar('/vendas', { agrupamento: $('f-agrupamento').value }); }, render: renderVendas },
        { chave: 'status', grafico: 'c-status', carregar: function () { return buscar('/pedidos-por-status'); }, render: renderStatus },
        { chave: 'horas', grafico: 'c-horas', carregar: function () { return buscar('/vendas-por-hora'); }, render: renderHoras },
        { chave: 'categorias', grafico: 'c-categorias', carregar: function () { return buscar('/vendas-por-categoria'); }, render: renderCategorias },
        { chave: 'ranking', carregar: function () { return buscar('/produtos-mais-vendidos', { limite: 10, ordenarPor: $('f-ordenar').value }); }, render: renderRanking },
        { chave: 'recentes', carregar: function () { return buscar('/pedidos-recentes', { limite: 10 }); }, render: renderRecentes }
    ];

    function atualizar() {
        if (carregando || !validarFiltros()) return;
        carregando = true;
        var global = $('dash-erro-global');
        global.classList.add('d-none');
        $('dash-atualizar').disabled = true;
        SECOES.forEach(function (s) { estado(s.chave, 'carregando', 'Carregando…'); });

        Promise.all(SECOES.map(function (s) {
            return s.carregar().then(function (dados) {
                s.render(dados);
                return null;
            }).catch(function (e) {
                if (s.grafico) limparGrafico(s.grafico);   // não deixa gráfico desatualizado ao lado do erro
                estado(s.chave, 'erro', 'Não foi possível carregar: ' + e.message);
                return e.message;
            });
        })).then(function (erros) {
            erros = erros.filter(Boolean);
            if (erros.length === SECOES.length) {
                global.textContent = 'Falha ao consultar a API do dashboard: ' + erros[0];
                global.classList.remove('d-none');
            } else if (!erros.length) {
                $('dash-atualizado').textContent = 'Atualizado às ' + new Date().toLocaleTimeString('pt-BR');
            }
        }).then(function () {
            carregando = false;
            $('dash-atualizar').disabled = false;
        });
    }

    function popularFiltros() {
        return fetch(API + '/filtros', { credentials: 'same-origin', headers: { 'Accept': 'application/json' } })
            .then(function (r) { if (!r.ok) throw new Error(); return r.json(); })
            .then(function (f) {
                f.status.forEach(function (s) {
                    var o = document.createElement('option');
                    o.value = s; o.textContent = ROTULO_STATUS[s] || s;
                    $('f-status').appendChild(o);
                });
                f.categorias.forEach(function (c) {
                    var o = document.createElement('option');
                    o.value = c; o.textContent = c;
                    $('f-categoria').appendChild(o);
                });
            }).catch(function () { /* filtros continuam utilizáveis sem as opções dinâmicas */ });
    }

    function configurarEventos() {
        $('dash-filtros').addEventListener('submit', function (e) { e.preventDefault(); atualizar(); });
        $('dash-atualizar').addEventListener('click', atualizar);
        $('f-agrupamento').addEventListener('change', atualizar);
        $('f-ordenar').addEventListener('change', atualizar);
        $('f-limpar').addEventListener('click', function () {
            $('f-status').value = ''; $('f-categoria').value = '';
            definirPeriodo(diasAtras(29), hoje());
            atualizar();
        });
        Array.prototype.forEach.call(document.querySelectorAll('[data-atalho]'), function (b) {
            b.addEventListener('click', function () {
                var a = b.getAttribute('data-atalho');
                if (a === 'hoje') definirPeriodo(hoje(), hoje());
                else if (a === 'mes') { var h = hoje(); definirPeriodo(new Date(h.getFullYear(), h.getMonth(), 1), h); }
                else definirPeriodo(diasAtras(parseInt(a, 10) - 1), hoje());
                atualizar();
            });
        });
        $('dash-auto').addEventListener('change', function (e) {
            if (timer) { clearInterval(timer); timer = null; }
            if (e.target.checked) timer = setInterval(atualizar, 60000);
        });
        // evita timers órfãos se o usuário sair da página
        window.addEventListener('beforeunload', function () { if (timer) clearInterval(timer); });
    }

    document.addEventListener('DOMContentLoaded', function () {
        definirPeriodo(diasAtras(29), hoje());
        configurarEventos();
        popularFiltros().then(atualizar);
    });
})();
