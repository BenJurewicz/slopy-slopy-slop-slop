-- Session-local configuration. Load explicitly or trust this file with 'exrc'.
local root = vim.fn.fnamemodify(debug.getinfo(1, "S").source:sub(2), ":p:h")
local group =
    vim.api.nvim_create_augroup("CloudMigrationProject", { clear = true })
local function relative(buf)
    local name = vim.api.nvim_buf_get_name(buf)
    if name:sub(1, #root + 1) == root .. "/" then
        return name:sub(#root + 2)
    end
end
local function ignored(path)
    local name = vim.fs.basename(path)
    local directory = "/" .. path
    return name == ".env"
        or name:match("^%.env%.")
        or path:match("%.xq$")
        or path:match("%.xqm$")
        or path:match("%.xquery$")
        or directory:find("/node_modules/", 1, true)
        or directory:find("/build/", 1, true)
        or directory:find("/.svelte-kit/", 1, true)
        or directory:find("/test-results/", 1, true)
        or directory:find("/playwright-report/", 1, true)
        or directory:find("/.gradle/", 1, true)
        or path:match("^frontend/src/lib/generated/")
        or path:match("^backend/src/test/resources/")
        or path:match("^examples/")
end
local function protect(buf)
    local path = relative(buf)
    if path and ignored(path) then
        vim.b[buf].autoformat = false
    end
end
vim.api.nvim_create_autocmd({ "BufReadPost", "BufNewFile" }, {
    group = group,
    callback = function(event)
        protect(event.buf)
    end,
})
local function java_profile(buf, client)
    if relative(buf) and client and client.name == "jdtls" then
        client.config.settings =
            vim.tbl_deep_extend("force", client.config.settings or {}, {
                java = {
                    format = {
                        enabled = true,
                        settings = {
                            url = vim.uri_from_fname(
                                root .. "/backend/eclipse-formatter.xml"
                            ),
                            profile = "CloudMigration",
                        },
                    },
                },
            })
        client:notify(
            "workspace/didChangeConfiguration",
            { settings = client.config.settings }
        )
    end
end
vim.api.nvim_create_autocmd("LspAttach", {
    group = group,
    callback = function(event)
        java_profile(event.buf, vim.lsp.get_client_by_id(event.data.client_id))
    end,
})
local function configure()
    local conform = require("conform")
    conform.formatters.cloudmigration_prettier = {
        inherit = "prettier",
        command = root .. "/frontend/node_modules/.bin/prettier",
        cwd = function()
            return root
        end,
        append_args = {
            "--config",
            root .. "/prettier.config.mjs",
            "--ignore-path",
            root .. "/.prettierignore",
        },
    }
    conform.formatters.cloudmigration_ktlint = {
        inherit = "ktlint",
        append_args = { "--stdin-path", "$FILENAME" },
    }
    for _, ft in ipairs({
        "javascript",
        "javascriptreact",
        "typescript",
        "typescriptreact",
        "svelte",
        "css",
        "html",
        "json",
        "jsonc",
        "yaml",
        "markdown",
        "xml",
        "kotlin",
        "python",
        "sh",
        "lua",
    }) do
        local previous = conform.formatters_by_ft[ft]
        conform.formatters_by_ft[ft] = function(buf)
            if relative(buf) then
                local native = {
                    kotlin = "cloudmigration_ktlint",
                    python = "ruff_format",
                    sh = "shfmt",
                    lua = "stylua",
                }
                return {
                    native[ft] or "cloudmigration_prettier",
                    lsp_format = "never",
                }
            end
            return type(previous) == "function" and previous(buf)
                or previous
                or {}
        end
    end
    local lint = require("lint")
    lint.linters.cloudmigration_eslint =
        vim.deepcopy(require("lint.linters.eslint"))
    lint.linters.cloudmigration_eslint.cmd = root
        .. "/frontend/node_modules/.bin/eslint"
    lint.linters.cloudmigration_eslint.cwd = root .. "/frontend"
    local eslint_types = {
        javascript = true,
        javascriptreact = true,
        typescript = true,
        typescriptreact = true,
        svelte = true,
    }
    vim.api.nvim_create_autocmd({ "BufEnter", "BufWritePost" }, {
        group = group,
        callback = function(event)
            local path = relative(event.buf)
            if
                path
                and path:match("^frontend/")
                and not ignored(path)
                and eslint_types[vim.bo[event.buf].filetype]
            then
                lint.try_lint("cloudmigration_eslint")
            end
        end,
    })
    for _, buf in ipairs(vim.api.nvim_list_bufs()) do
        protect(buf)
        for _, client in ipairs(vim.lsp.get_clients({ bufnr = buf })) do
            java_profile(buf, client)
        end
    end
end
if vim.v.vim_did_enter == 1 then
    configure()
else
    vim.api.nvim_create_autocmd("User", {
        pattern = "VeryLazy",
        group = group,
        once = true,
        callback = configure,
    })
end
