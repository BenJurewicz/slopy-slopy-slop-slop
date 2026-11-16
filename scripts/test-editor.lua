-- Exercise project hooks without loading the global init or starting LSP servers.
local root = vim.fn.getcwd()
local data = vim.fn.stdpath("data")
for _, plugin in ipairs({ "conform.nvim", "nvim-lint" }) do
    vim.opt.runtimepath:append(data .. "/lazy/" .. plugin)
end
vim.env.PATH = data .. "/mason/bin:" .. vim.env.PATH
local conform = require("conform")
conform.setup({ formatters_by_ft = { typescript = { "outside_formatter" } } })
dofile(root .. "/.nvim.lua")
vim.api.nvim_exec_autocmds("User", { pattern = "VeryLazy" })
local function buffer(path, ft, content)
    local buf = vim.api.nvim_create_buf(true, false)
    vim.api.nvim_buf_set_name(buf, path)
    vim.api.nvim_set_current_buf(buf)
    vim.bo[buf].filetype = ft
    vim.api.nvim_buf_set_lines(buf, 0, -1, false, { content })
    return buf
end
local function text(buf)
    return table.concat(vim.api.nvim_buf_get_lines(buf, 0, -1, false), "\n")
end
local function format(buf)
    local error
    conform.format(
        { bufnr = buf, async = false, timeout_ms = 20000 },
        function(err)
            error = err
        end
    )
    assert(not error, tostring(error))
end
local ts = buffer(
    root .. "/frontend/src/lib/__editor_test__.ts",
    "typescript",
    'const example={value:"x"}'
)
format(ts)
assert(text(ts) == "const example = { value: 'x' };")
local generated = buffer(
    root .. "/frontend/src/lib/generated/__editor_test__.ts",
    "typescript",
    'const example={value:"x"}'
)
format(generated)
assert(
    text(generated) == 'const example={value:"x"}',
    "generated source was formatted"
)
vim.api.nvim_exec_autocmds("BufReadPost", { buffer = generated })
assert(vim.b[generated].autoformat == false)
local outside = buffer(
    "/tmp/cloudmigration-outside-editor-test.ts",
    "typescript",
    "const example=1"
)
assert(conform.formatters_by_ft.typescript(outside)[1] == "outside_formatter")
local kotlin_path = root .. "/__editor_test__.gradle.kts"
local kotlin_source = 'plugins{id("java")}'
local kotlin = buffer(kotlin_path, "kotlin", kotlin_source)
local expected = vim.system({
    data .. "/mason/bin/ktlint",
    "--format",
    "--log-level=none",
    "--stdin",
    "--stdin-path",
    kotlin_path,
}, { cwd = root, stdin = kotlin_source }):wait(20000)
assert(expected.code == 0, expected.stderr)
format(kotlin)
assert(text(kotlin) .. "\n" == expected.stdout, "editor and CLI ktlint differ")
local python =
    buffer(root .. "/scripts/__editor_test__.py", "python", "value={'a':1}")
format(python)
assert(text(python) == 'value = {"a": 1}')
local shell = buffer(root .. "/scripts/__editor_test__.sh", "sh", "")
vim.api.nvim_buf_set_lines(
    shell,
    0,
    -1,
    false,
    { "if true;then", "echo yes", "fi" }
)
format(shell)
assert(text(shell) == "if true; then\n    echo yes\nfi")
local lua =
    buffer(root .. "/scripts/__editor_test__.lua", "lua", "local value={x=1}")
format(lua)
assert(text(lua) == "local value = { x = 1 }")
local java = buffer(
    root .. "/backend/src/main/java/__EditorTest.java",
    "java",
    "class Example {}"
)
local notification
local client = {
    name = "jdtls",
    config = { settings = { java = { other = true } } },
    notify = function(_, method, params)
        notification = { method, params }
    end,
}
local get_client = vim.lsp.get_client_by_id
vim.lsp.get_client_by_id = function()
    return client
end
vim.api.nvim_exec_autocmds(
    "LspAttach",
    { buffer = java, data = { client_id = 42 } }
)
vim.lsp.get_client_by_id = get_client
assert(client.config.settings.java.other)
assert(client.config.settings.java.format.settings.profile == "CloudMigration")
assert(
    client.config.settings.java.format.settings.url
        == vim.uri_from_fname(root .. "/backend/eclipse-formatter.xml")
)
assert(notification[1] == "workspace/didChangeConfiguration")
vim.api.nvim_set_current_buf(ts)
vim.api.nvim_buf_set_lines(ts, 0, -1, false, { "const unused = 1;" })
vim.api.nvim_exec_autocmds("BufWritePost", { buffer = ts })
local namespace = require("lint").get_namespace("cloudmigration_eslint")
assert(
    vim.wait(20000, function()
        return #vim.diagnostic.get(ts, { namespace = namespace }) > 0
    end, 50),
    "ESLint diagnostics did not arrive"
)
assert(
    vim.diagnostic.get(ts, { namespace = namespace })[1].code
        == "@typescript-eslint/no-unused-vars"
)
print(
    "Project editor checks passed: Prettier, ignored files, scope, ktlint, Java profile, ESLint."
)
vim.cmd("qa!")
