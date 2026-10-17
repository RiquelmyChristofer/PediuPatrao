(() => {
    function mensagemPorStatus(status, fallback) {
        if (status === 400) return 'Confira os dados informados e tente novamente.';
        if (status === 401) return 'Sua sessão expirou. Entre novamente para continuar.';
        if (status === 403) return 'Você não tem permissão para realizar esta ação.';
        if (status === 404) return 'O registro solicitado não foi encontrado ou já foi removido.';
        if (status === 409) return 'Não foi possível concluir porque os dados entraram em conflito. Atualize a página e tente novamente.';
        if (status >= 500) return 'Ocorreu um problema interno. Tente novamente em instantes.';
        return fallback;
    }

    async function mensagemFalhaApi(response, fallback) {
        let body = {};
        try {
            body = await response.clone().json();
        } catch {
            // Algumas respostas do servidor não possuem corpo JSON.
        }
        if (typeof body.erro === 'string' && body.erro.trim()) {
            return body.erro;
        }
        return mensagemPorStatus(response.status, fallback);
    }

    async function verificarRespostaApi(response, fallback) {
        if (response.redirected && new URL(response.url).pathname === '/login') {
            throw new Error(mensagemPorStatus(401, fallback));
        }
        if (!response.ok) {
            throw new Error(await mensagemFalhaApi(response, fallback));
        }
    }

    function mensagemFalha(error, fallback) {
        if (error instanceof TypeError && /^(Failed to fetch|NetworkError when attempting to fetch resource\.|Load failed)$/i.test(error.message)) {
            return 'Não foi possível conectar ao sistema. Verifique sua conexão e tente novamente.';
        }
        return error && error.message ? error.message : fallback;
    }

    window.apiFeedback = { verificarRespostaApi, mensagemFalha };
})();
