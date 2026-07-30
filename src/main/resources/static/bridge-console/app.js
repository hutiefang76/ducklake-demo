(() => {
  "use strict";

  const script = document.querySelector("script[data-bridge-console]");
  const scriptUrl = new URL(script.src, window.location.href);
  const contextRoot = scriptUrl.pathname.replace(/\/bridge-console\/app\.js$/, "");
  const apiRoot = `${contextRoot}/api/bridge`;
  const state = {
    scriptPage: 1,
    scriptPages: 1,
    runPage: 1,
    runPages: 1,
    selectedScript: null,
    selectedRun: null,
    executionEvidence: null,
    fileContracts: [],
    uploadedFile: null
  };

  const byId = (id) => document.getElementById(id);
  const text = (tag, value, className) => {
    const node = document.createElement(tag);
    node.textContent = value == null ? "—" : String(value);
    if (className) node.className = className;
    return node;
  };
  const pretty = (value) => JSON.stringify(value ?? {}, null, 2);
  const items = (value) => Array.isArray(value?.items) ? value.items : [];
  const hasOwn = (value, name) => Object.prototype.hasOwnProperty.call(value || {}, name);

  function supportType(level, entry = {}) {
    const normalizedLevel = String(level || "").toUpperCase();
    const normalizedRole = String(entry.role || entry.contract?.role || "").toUpperCase();
    const auxiliaryLevels = ["AUXILIARY", "AUXILIARY_FILE", "UNSUPPORTED", "NONE"];
    const auxiliaryRoles = ["AUXILIARY", "HELPER", "SUPPORT", "LIBRARY"];
    if (auxiliaryLevels.includes(normalizedLevel) || auxiliaryRoles.includes(normalizedRole)) {
      return "辅助文件";
    }
    const labels = {
      PYTHON_ONLY: "原生 Python",
      PARAMETERIZED: "自动参数",
      FULL: "完整 ETL 契约"
    };
    return labels[normalizedLevel] || "辅助文件";
  }

  function firstValue(entry, paths) {
    for (const path of paths) {
      let value = entry;
      for (const part of path) value = value?.[part];
      if (value !== undefined && value !== null && value !== "") return value;
    }
    return null;
  }

  function observed(value) {
    if (value === null) return "未返回";
    if (typeof value === "object") return value.status || value.name || value.type || "已声明";
    return String(value);
  }

  function schedulerBinding(entry) {
    const value = firstValue(entry, [
      ["scheduler", "binding_status"], ["scheduler", "binding_state"],
      ["scheduler", "binding", "status"], ["scheduler", "mapping_status"]
    ]);
    if (value !== null) return value;
    if (entry.scheduler?.bound === true) return "BOUND";
    if (entry.scheduler?.bound === false) return "UNBOUND";
    return null;
  }

  function executableBasis(entry) {
    const level = String(entry.support_level || "").toUpperCase();
    const role = firstValue(entry, [["role"], ["contract", "role"]]);
    const adapter = firstValue(entry, [["adapter"], ["contract", "adapter"]]);
    const etlRunnable = firstValue(entry, [["etl_script", "runnable"], ["contract", "etl_script", "runnable"]]);
    const syntax = firstValue(entry, [
      ["syntax_status"], ["compile_status"], ["cli_status"], ["scan", "compile_status"]
    ]);
    const binding = schedulerBinding(entry);
    let rule;
    if (level === "PYTHON_ONLY") {
      rule = "原生 Python 最终可执行需同时满足：adapter 为空、etl_script.runnable=true，且 DolphinScheduler main 中存在同名标准 PYTHON 节点并为 BOUND；role 参与分类，但不会单独排除原始辅助脚本。";
    } else if (["PARAMETERIZED", "FULL"].includes(level)) {
      rule = "自动参数/完整 ETL 契约最终可执行需同时满足：role=task、有效 adapter 和 contract，且 DolphinScheduler 工作流绑定为 BOUND。";
    } else {
      rule = "辅助文件不是直接执行入口；最终结果由 Bridge 扫描分类和 DolphinScheduler 绑定共同判定。";
    }
    return {
      rule: `Python 语法或编译成功只是前置门槛，不等于可执行。${rule}`,
      observed: `当前值：语法/编译=${observed(syntax)}；role=${observed(role)}；adapter=${adapter === null ? "空或未返回" : observed(adapter)}；etl_script.runnable=${observed(etlRunnable)}；DolphinScheduler=${observed(binding)}；Bridge runnable=${entry.runnable === true ? "true" : "false"}。`
    };
  }

  function parkPanel(panel, hostId) {
    if (!panel) return;
    panel.classList.add("hidden");
    byId("panel-parking").append(panel);
    byId(hostId)?.remove();
  }

  function attachScriptPanel(sourceRow) {
    const panel = byId("script-panel");
    parkPanel(byId("run-panel"), "run-inline-detail");
    parkPanel(panel, "script-inline-detail");
    const host = document.createElement("div");
    host.id = "script-inline-detail";
    host.className = "script-inline-detail";
    sourceRow.after(host);
    host.append(panel);
    panel.classList.remove("hidden");
  }

  function attachRunPanel(sourceRow) {
    const panel = byId("run-panel");
    parkPanel(panel, "run-inline-detail");
    const row = document.createElement("tr");
    row.id = "run-inline-detail";
    row.className = "run-inline-detail";
    const cell = document.createElement("td");
    cell.colSpan = 5;
    cell.append(panel);
    row.append(cell);
    sourceRow.after(row);
    panel.classList.remove("hidden");
  }

  function openTreeAncestors(row) {
    let current = row?.parentElement;
    while (current) {
      if (current.tagName === "DETAILS") current.open = true;
      current = current.parentElement;
    }
  }

  function findScriptRow(scriptId) {
    return Array.from(document.querySelectorAll(".script-row"))
      .find((row) => row.dataset.scriptId === String(scriptId));
  }

  function scriptPath(entry) {
    return String(entry.script_name || entry.source_path || entry.script_id || "未命名脚本")
      .replace(/\\/g, "/").replace(/\.py$/i, "");
  }

  function buildScriptTree(scripts) {
    const root = { folders: new Map(), scripts: [] };
    scripts.forEach((entry) => {
      const parts = scriptPath(entry).split("/").filter(Boolean);
      const name = parts.pop() || entry.script_id || "未命名脚本";
      let node = root;
      parts.forEach((folderName) => {
        if (!node.folders.has(folderName)) {
          node.folders.set(folderName, { name: folderName, folders: new Map(), scripts: [] });
        }
        node = node.folders.get(folderName);
      });
      node.scripts.push({ entry, name });
    });
    return root;
  }

  function treeScriptCount(node) {
    let count = node.scripts.length;
    node.folders.forEach((folder) => { count += treeScriptCount(folder); });
    return count;
  }

  function renderScriptRow(item, depth) {
    const row = document.createElement("div");
    row.className = "script-row";
    row.dataset.scriptId = item.entry.script_id;
    row.setAttribute("role", "row");
    const name = text("span", item.name, "tree-file-name");
    name.style.setProperty("--tree-indent", `${depth * 18}px`);
    const actions = document.createElement("span");
    actions.append(button("详情 / 执行", () => selectScript(item.entry.script_id, row)));
    row.append(
      name,
      text("span", item.entry.script_id),
      text("span", supportType(item.entry.support_level, item.entry)),
      text("span", item.entry.runnable ? "是" : "否"),
      actions
    );
    return row;
  }

  function renderScriptTree(node, host, depth = 0, expand = false) {
    Array.from(node.folders.values())
      .sort((left, right) => left.name.localeCompare(right.name, "zh-CN"))
      .forEach((folder) => {
        const details = document.createElement("details");
        details.className = "tree-folder";
        details.open = expand;
        const summary = document.createElement("summary");
        summary.className = "tree-folder-row";
        const name = text("span", `${folder.name} (${treeScriptCount(folder)})`, "tree-folder-name");
        name.style.setProperty("--tree-indent", `${depth * 18}px`);
        summary.append(name, text("span", ""), text("span", ""), text("span", ""), text("span", ""));
        const children = document.createElement("div");
        children.className = "tree-children";
        renderScriptTree(folder, children, depth + 1, expand);
        details.append(summary, children);
        host.append(details);
      });
    node.scripts
      .sort((left, right) => left.name.localeCompare(right.name, "zh-CN"))
      .forEach((item) => host.append(renderScriptRow(item, depth)));
  }

  function automaticParameters(entry) {
    if (String(entry?.support_level).toUpperCase() === "PYTHON_ONLY") {
      return { values: {}, missing: [], source: "原生 Python 固定无参数，直接执行原始脚本" };
    }
    const specs = Array.isArray(entry?.parameters)
      ? entry.parameters
      : (Array.isArray(entry?.contract?.parameters) ? entry.contract.parameters : []);
    const values = {};
    const missing = [];
    specs.forEach((spec) => {
      if (!spec?.name) return;
      if (hasOwn(spec, "default")) {
        values[spec.name] = spec.default;
      } else if (Array.isArray(spec.allowed_values) && spec.allowed_values.length > 0) {
        values[spec.name] = spec.allowed_values[0];
      } else if (spec.required) {
        missing.push(spec.name);
      }
    });
    return {
      values,
      missing,
      source: missing.length
        ? `契约缺少必填默认值：${missing.join(", ")}`
        : "参数由 Bridge 脚本契约的 default / allowed_values 自动生成"
    };
  }

  function dolphinSchedulerStartParams(run) {
    const parameters = run?.parameters
      && typeof run.parameters === "object"
      && !Array.isArray(run.parameters)
      ? run.parameters
      : {};
    if (Object.keys(parameters).length === 0) {
      return {
        startParams: {},
        transport: "原生 Python：DolphinScheduler startParams 为空，直接执行原始 Python 脚本"
      };
    }
    return {
      startParams: Object.assign({ run_id: run.run_id }, parameters, { run_id: run.run_id }),
      transport: "自动参数/完整 ETL 契约：DolphinScheduler startParams 包含 run_id 和直接业务参数"
    };
  }

  function message(value, error = false) {
    const node = byId("message");
    node.textContent = value;
    node.className = `${value ? "visible" : ""}${error ? " error" : ""}`;
    window.clearTimeout(message.timer);
    message.timer = window.setTimeout(() => { node.className = ""; }, 5000);
  }

  async function api(path, options = {}) {
    const headers = new Headers(options.headers || {});
    if (options.body !== undefined && !(options.body instanceof FormData) && !headers.has("Content-Type")) {
      headers.set("Content-Type", "application/json");
    }
    const response = await fetch(`${apiRoot}${path}`, {
      ...options,
      credentials: "same-origin",
      headers
    });
    const body = await response.json().catch(() => ({ code: "INVALID_JSON", message: "服务返回了非 JSON 内容" }));
    if (!response.ok) throw new Error(body.message || body.error || `${response.status} ${response.statusText}`);
    return body;
  }

  function fact(host, name, value) {
    const node = text("span", "", "fact");
    node.append(text("strong", name), text("span", value));
    host.append(node);
  }

  function badge(value) {
    const normalized = String(value || "UNKNOWN").toUpperCase();
    const bad = ["FAILED", "STOPPED", "CANCELLED", "INVALID", "UNAVAILABLE"].includes(normalized);
    return text("span", normalized, `badge${bad ? " bad" : ""}`);
  }

  function button(label, action, className = "secondary") {
    const node = text("button", label, className);
    node.type = "button";
    node.addEventListener("click", () => safe(action)());
    return node;
  }

  async function loadStatus() {
    const status = await api("/status");
    const node = byId("bridge-status");
    const ready = status.acceptance_ready === true;
    node.textContent = ready ? "Bridge 验收链已就绪" : (status.message || "Bridge 验收链不可用");
    node.className = `badge${ready ? "" : " bad"}`;
  }

  async function loadScanOptions() {
    const response = await api("/scans/options");
    const select = byId("scan-ref");
    const branches = Array.isArray(response.available_repository_refs)
      ? response.available_repository_refs : [];
    const selected = response.active_repository_ref || response.default_repository_ref || "refs/heads/main";
    const options = branches.includes(selected) ? branches : [selected, ...branches];
    select.replaceChildren(...options.map((ref) => {
      const option = document.createElement("option");
      option.value = ref;
      option.textContent = ref.replace(/^refs\/heads\//, "");
      return option;
    }));
    select.value = selected;
  }

  async function loadLatestScan() {
    const response = await api("/scans/latest");
    const scan = response.scan;
    const host = byId("scan-summary");
    host.replaceChildren();
    if (!scan) {
      fact(host, "状态", "尚未扫描");
      return;
    }
    fact(host, "Scan ID", scan.scan_id);
    fact(host, "状态", scan.state);
    fact(host, "分支", scan.repository_ref || "refs/heads/main");
    fact(host, "扫描文件", scan.discovered_count ?? 0);
    fact(host, "已入库", scan.accepted_count ?? 0);
    fact(host, "需处理", scan.rejected_count ?? 0);
    fact(host, "Commit", scan.resolved_head_commit || scan.expected_head_commit || "—");
  }

  async function startScan() {
    const repositoryRef = byId("scan-ref").value || "refs/heads/main";
    const scan = await api("/scans", {
      method: "POST",
      body: JSON.stringify({ repository_ref: repositoryRef })
    });
    message(`扫描已受理：${scan.scan_id || "已提交"}`);
    await loadLatestScan();
  }

  function scriptQuery() {
    const form = new FormData(byId("script-filter"));
    const query = new URLSearchParams({ all: "true", page_size: "200" });
    const q = String(form.get("q") || "").trim();
    const folder = String(form.get("folder_prefix") || "").trim();
    const supportLevel = String(form.get("support_level") || "").trim();
    const runnable = String(form.get("runnable") || "").trim();
    if (q) query.set("q", q);
    if (folder) query.set("folder_prefix", folder);
    if (supportLevel) query.set("support_level", supportLevel);
    if (runnable) query.set("runnable", runnable);
    query.set("recursive", form.get("recursive") ? "true" : "false");
    return query;
  }

  async function loadScripts() {
    const response = await api(`/scripts?${scriptQuery()}`);
    parkPanel(byId("run-panel"), "run-inline-detail");
    parkPanel(byId("script-panel"), "script-inline-detail");
    state.selectedScript = null;
    state.selectedRun = null;
    const rows = byId("script-rows");
    rows.replaceChildren();
    const scripts = items(response);
    if (!scripts.length) {
      rows.append(text("div", "没有匹配的业务脚本", "empty"));
    } else {
      const form = new FormData(byId("script-filter"));
      const expand = Boolean(String(form.get("q") || "").trim() || String(form.get("folder_prefix") || "").trim());
      renderScriptTree(buildScriptTree(scripts), rows, 0, expand);
    }
    const total = Number(response.total ?? response.count ?? scripts.length);
    byId("script-page").textContent = total === scripts.length
      ? `共 ${total} 个业务脚本，点击文件夹展开`
      : `已展示 ${scripts.length} / ${total} 个业务脚本；单次最多读取 200 个`;
  }

  async function selectScript(scriptId, sourceRow = findScriptRow(scriptId)) {
    if (!sourceRow) throw new Error("当前目录树中没有该脚本，请清除筛选条件后重试");
    openTreeAncestors(sourceRow);
    const detail = await api(`/scripts/${encodeURIComponent(scriptId)}`);
    const entry = detail.script || detail;
    state.selectedScript = entry;
    state.selectedRun = null;
    state.runPage = 1;
    attachScriptPanel(sourceRow);
    byId("script-title").textContent = entry.script_name;
    byId("script-id").textContent = `Bridge 脚本 ID：${entry.script_id}`;
    const host = byId("script-facts");
    host.replaceChildren();
    fact(host, "源码", entry.source_path);
    fact(host, "格式", entry.source_format);
    fact(host, "契约", entry.contract_status);
    fact(host, "支持类型", supportType(entry.support_level, entry));
    fact(host, "可执行", entry.runnable ? "是" : "否");
    const basis = executableBasis(entry);
    byId("runnable-basis").replaceChildren(
      text("strong", "可执行判断依据"),
      text("p", basis.rule),
      text("p", basis.observed, "hint")
    );
    const automatic = automaticParameters(entry);
    byId("run-parameters").textContent = pretty(automatic.values);
    byId("parameter-source").textContent = automatic.source;
    state.executionEvidence = null;
    byId("execution-evidence").textContent = "尚未执行";
    configureFileInput(entry);
    byId("script-technical").textContent = pretty({
      runnable_basis: basis,
      contract: entry.contract,
      parameters: entry.parameters,
      files: state.fileContracts,
      inputs: entry.inputs,
      outputs: entry.outputs,
      scheduler: entry.scheduler,
      scan: detail.scan
    });
    await Promise.all([loadCurrent(), loadRuns()]);
    byId("script-inline-detail").scrollIntoView({ behavior: "smooth", block: "nearest" });
  }
  function scriptFiles(entry) {
    if (Array.isArray(entry?.files)) return entry.files;
    if (Array.isArray(entry?.contract?.files)) return entry.contract.files;
    return [];
  }

  function usableFile(file) {
    return file && ["AVAILABLE", "READY"].includes(String(file.status || "").toUpperCase());
  }

  function updateRunEnabled() {
    const input = byId("run-file");
    const selected = Boolean(input?.files?.length);
    const singleContract = state.fileContracts.length === 1;
    const required = singleContract && state.fileContracts[0].required !== false;
    const fileReady = state.fileContracts.length === 0
      || (singleContract && (usableFile(state.uploadedFile) || (!required && !selected)));
    const automatic = automaticParameters(state.selectedScript);
    byId("run-start").disabled = !state.selectedScript?.runnable
      || automatic.missing.length > 0
      || !fileReady;
  }

  function configureFileInput(entry) {
    state.fileContracts = scriptFiles(entry);
    state.uploadedFile = null;
    const panel = byId("run-file-panel");
    const input = byId("run-file");
    const upload = byId("file-upload");
    const hint = byId("run-file-hint");
    const status = byId("run-file-status");
    input.value = "";
    status.textContent = "尚未上传";
    panel.classList.toggle("hidden", state.fileContracts.length === 0);
    if (state.fileContracts.length === 0) {
      input.disabled = true;
      upload.disabled = true;
      updateRunEnabled();
      return;
    }
    if (state.fileContracts.length !== 1) {
      input.disabled = true;
      upload.disabled = true;
      hint.textContent = `当前验收台仅支持单文件契约；此脚本声明了 ${state.fileContracts.length} 个文件。`;
      status.textContent = "暂不支持从页面执行";
      updateRunEnabled();
      return;
    }
    const contract = state.fileContracts[0];
    const extensions = Array.isArray(contract.extensions) ? contract.extensions : [];
    const contentTypes = Array.isArray(contract.content_types) ? contract.content_types : [];
    input.disabled = false;
    input.accept = [...extensions, ...contentTypes].join(",");
    upload.disabled = true;
    hint.textContent = `${contract.name || "input_file"} · ${contract.required === false ? "可选" : "必填"}`
      + `${extensions.length ? ` · ${extensions.join(" / ")}` : ""}`;
    updateRunEnabled();
  }

  function resetSelectedFile() {
    state.uploadedFile = null;
    byId("run-file-status").textContent = byId("run-file").files.length ? "待上传" : "尚未选择文件";
    byId("file-upload").disabled = byId("run-file").files.length !== 1;
    updateRunEnabled();
  }

  async function uploadFile() {
    const input = byId("run-file");
    if (state.fileContracts.length !== 1 || input.files.length !== 1) {
      throw new Error("请选择契约要求的单个输入文件");
    }
    const form = new FormData();
    form.append("file", input.files[0], input.files[0].name);
    byId("file-upload").disabled = true;
    byId("run-file-status").textContent = "正在上传…";
    try {
      const uploaded = await api("/files", { method: "POST", body: form });
      if (!uploaded.file_id) throw new Error("Bridge 未返回 file_id");
      const confirmed = await api(`/files/${encodeURIComponent(uploaded.file_id)}`);
      state.uploadedFile = confirmed;
      byId("run-file-status").textContent = `${confirmed.original_name || input.files[0].name} · `
        + `${confirmed.file_id} · ${confirmed.status || "UNKNOWN"}`;
      updateRunEnabled();
      message(`文件已上传：${confirmed.file_id}`);
    } catch (error) {
      state.uploadedFile = null;
      byId("run-file-status").textContent = "上传失败，可重新提交";
      byId("file-upload").disabled = false;
      updateRunEnabled();
      throw error;
    }
  }

  function businessRun(run) {
    if (!run) return null;
    return {
      run_id: run.run_id,
      script_name: run.script_name,
      state: run.state,
      created_at: run.created_at,
      started_at: run.started_at,
      finished_at: run.finished_at
    };
  }


  async function loadCurrent() {
    if (!state.selectedScript) return;
    const query = new URLSearchParams({ script_id: state.selectedScript.script_id });
    const current = await api(`/runs/current?${query}`);
    byId("current-run").textContent = pretty({
      script_name: current.script_name,
      queued_count: current.queued_count,
      latest_run: businessRun(current.latest_run),
      running_run: businessRun(current.running_run),
      next_queued_run: businessRun(current.next_queued_run),
      last_finished_run: businessRun(current.last_finished_run)
    });
  }

  async function startRun() {
    if (!state.selectedScript) throw new Error("请先选择业务脚本");
    const automatic = automaticParameters(state.selectedScript);
    if (automatic.missing.length) {
      throw new Error(`契约缺少必填默认值：${automatic.missing.join(", ")}`);
    }
    updateRunEnabled();
    if (byId("run-start").disabled) {
      throw new Error("请先上传脚本契约要求的输入文件");
    }
    const fileIds = usableFile(state.uploadedFile) ? [state.uploadedFile.file_id] : [];
    const requestBody = {
      script_id: state.selectedScript.script_id,
      parameters: automatic.values,
      file_ids: fileIds
    };
    const demoApiUrl = new URL(`${apiRoot}/runs`, window.location.href).href;
    state.executionEvidence = {
      run_id: null,
      chain: "Demo -> notebook-dolphin-bridge -> DolphinScheduler -> original script",
      demo_api: { method: "POST", url: demoApiUrl },
      bridge_api: {
        method: "POST",
        public_path: "/data-platform/notebook-dolphin-bridge/api/v1/runs",
        upstream_path: "/api/v1/runs",
        called_by: "DuckLake Demo server-side BFF"
      },
      dolphinscheduler: { direct_api_call: false, called_by: "notebook-dolphin-bridge" },
      request: requestBody,
      bridge_accept_response: null,
      bridge_query_response: null,
      run_result: null
    };
    byId("execution-evidence").textContent = pretty(state.executionEvidence);
    const result = await api("/runs", {
      method: "POST",
      body: JSON.stringify(requestBody)
    });
    state.executionEvidence.run_id = result.run_id || null;
    state.executionEvidence.bridge_accept_response = result;
    byId("execution-evidence").textContent = pretty(state.executionEvidence);
    message(`执行已受理：${result.run_id}`);
    await Promise.all([loadCurrent(), loadQueue(), loadRuns()]);
    if (result.run_id) await selectRun(result.run_id);
  }

  async function loadQueue() {
    const response = await api("/queue");
    const host = byId("queue-summary");
    host.replaceChildren();
    fact(host, "排队数", response.count ?? items(response).length);
    fact(host, "全局容量", response.global_capacity ?? "—");
    fact(host, "来源", response.source || "scheduler");
    const rows = byId("queue-rows");
    rows.replaceChildren();
    const queue = items(response);
    if (!queue.length) {
      const row = document.createElement("tr");
      const cell = text("td", "当前没有排队任务", "empty");
      cell.colSpan = 4;
      row.append(cell);
      rows.append(row);
    }
    queue.forEach((entry, index) => {
      const runId = entry.run_id || entry.id;
      const runLink = button(runId || "—", () => selectRun(runId));
      const row = document.createElement("tr");
      const runCell = document.createElement("td");
      runCell.append(runLink);
      row.append(
        text("td", entry.queue?.position ?? index + 1),
        text("td", entry.script_name || entry.script_id),
        runCell,
        text("td", entry.state || entry.queue_state)
      );
      rows.append(row);
    });
  }

  function runQuery() {
    const query = new URLSearchParams({ page: String(state.runPage), page_size: "25" });
    if (state.selectedScript) query.set("script_id", state.selectedScript.script_id);
    return query;
  }

  async function loadRuns() {
    const rows = byId("run-rows");
    parkPanel(byId("run-panel"), "run-inline-detail");
    rows.replaceChildren();
    if (!state.selectedScript) {
      byId("run-page").textContent = "请先选择业务脚本";
      byId("run-prev").disabled = true;
      byId("run-next").disabled = true;
      return;
    }
    const response = await api(`/runs?${runQuery()}`);
    const runs = items(response);
    if (!runs.length) {
      const row = document.createElement("tr");
      const cell = text("td", "此脚本暂无执行记录", "empty");
      cell.colSpan = 5;
      row.append(cell);
      rows.append(row);
    }
    runs.forEach((entry) => {
      const actions = document.createElement("td");
      const stateCell = document.createElement("td");
      stateCell.append(badge(entry.state));
      const row = document.createElement("tr");
      row.dataset.runId = entry.run_id;
      actions.append(button("查看", () => selectRun(entry.run_id, row)));
      row.append(
        text("td", entry.run_id),
        text("td", entry.script_name || entry.script_id),
        stateCell,
        text("td", entry.updated_at || entry.finished_at || entry.created_at),
        actions
      );
      rows.append(row);
    });
    const total = Number(response.total ?? response.count ?? runs.length);
    state.runPages = Number(response.total_pages || Math.max(1, Math.ceil(total / 25)));
    byId("run-page").textContent = `第 ${state.runPage} / ${state.runPages} 页，共 ${total} 条`;
    byId("run-prev").disabled = state.runPage <= 1;
    byId("run-next").disabled = state.runPage >= state.runPages;
  }

  async function selectRun(runId, sourceRow = null) {
    if (!runId) return;
    const [run, queue] = await Promise.all([
      api(`/runs/${encodeURIComponent(runId)}`),
      api(`/queue/${encodeURIComponent(runId)}`)
    ]);
    if (!state.selectedScript || state.selectedScript.script_id !== run.script_id) {
      const scriptRow = findScriptRow(run.script_id);
      if (!scriptRow) throw new Error("当前目录树中没有该 Run 对应的脚本，请清除筛选条件后重试");
      await selectScript(run.script_id, scriptRow);
    }
    const row = sourceRow && sourceRow.isConnected
      ? sourceRow
      : Array.from(byId("run-rows").querySelectorAll("tr"))
        .find((candidate) => candidate.dataset.runId === String(runId));
    if (!row) throw new Error("该 Run 不在当前执行记录页，请翻页后查看");
    attachRunPanel(row);
    state.selectedRun = run;
    byId("run-title").textContent = `执行详情 · Bridge Run ID：${run.run_id}`;
    byId("run-business").textContent = `${run.script_name || "—"} · Bridge 脚本 ID：${run.script_id || "—"}`;
    const host = byId("run-facts");
    host.replaceChildren();
    fact(host, "Bridge Run ID", run.run_id);
    fact(host, "状态", run.state);
    fact(host, "创建", run.created_at);
    fact(host, "开始", run.started_at);
    fact(host, "完成", run.finished_at);
    fact(host, "队列位置", queue.queue?.position ?? "—");
    fact(host, "DolphinScheduler Workflow Instance ID", run.scheduler?.workflow_instance_id ?? "—");
    fact(host, "DolphinScheduler Task Instance ID", run.scheduler?.task_instance_id ?? "—");
    const terminal = ["SUCCESS", "FAILED", "STOPPED", "CANCELLED"].includes(String(run.state).toUpperCase());
    const retryable = ["FAILED", "STOPPED", "CANCELLED"].includes(String(run.state).toUpperCase());
    byId("run-stop").disabled = terminal;
    byId("run-retry").disabled = !retryable;
    byId("run-technical").textContent = pretty({ source: run.source, scheduler: run.scheduler, queue });
    if (!state.executionEvidence || state.executionEvidence.run_id !== run.run_id) {
      state.executionEvidence = {
        run_id: run.run_id,
        chain: "Demo -> notebook-dolphin-bridge -> DolphinScheduler -> original script",
        demo_api: {
          method: "GET",
          url: new URL(`${apiRoot}/runs/${encodeURIComponent(run.run_id)}`, window.location.href).href
        },
        bridge_api: {
          method: "GET",
          public_path: `/data-platform/notebook-dolphin-bridge/api/v1/runs/${run.run_id}`,
          upstream_path: `/api/v1/runs/${run.run_id}`,
          called_by: "DuckLake Demo server-side BFF"
        },
        request: { run_id: run.run_id },
        bridge_accept_response: null,
        bridge_query_response: run,
        run_result: null
      };
    } else {
      state.executionEvidence.bridge_query_response = run;
    }
    if (state.executionEvidence) {
      const projectCode = run.scheduler?.namespace?.technical_id;
      const workflowCode = run.scheduler?.workflow_definition?.technical_id;
      const taskCode = run.scheduler?.task_definition?.technical_id;
      const schedulerStartParams = dolphinSchedulerStartParams(run);
      state.executionEvidence.dolphinscheduler = {
        direct_api_call: false,
        called_by: "notebook-dolphin-bridge",
        api: {
          method: "POST",
          path: projectCode
            ? `/projects/${projectCode}/executors/start-workflow-instance`
            : "Bridge response did not expose a project code"
        },
        request_projection: {
          workflowDefinitionCode: workflowCode,
          startNodeList: taskCode,
          startParams: schedulerStartParams.startParams,
          transport: schedulerStartParams.transport
        },
        response_projection: {
          workflow_instance_id: run.scheduler?.workflow_instance_id,
          task_instance_id: run.scheduler?.task_instance_id,
          state: run.scheduler?.state
        }
      };
      state.executionEvidence.run_result = {
        run_id: run.run_id,
        state: run.state,
        parameters: run.parameters,
        source: run.source,
        scheduler: run.scheduler,
        queue
      };
      byId("execution-evidence").textContent = pretty(state.executionEvidence);
    }
    byId("run-logs").textContent = "尚未查询日志";
    byId("run-inline-detail").scrollIntoView({ behavior: "smooth", block: "nearest" });
  }
  async function loadLogs() {
    if (!state.selectedRun) throw new Error("请先选择执行记录");
    const logs = await api(`/runs/${encodeURIComponent(state.selectedRun.run_id)}/logs?limit=500`);
    byId("run-logs").textContent = pretty(logs);
  }

  async function stopRun() {
    if (!state.selectedRun) throw new Error("请先选择执行记录");
    const response = await api(`/runs/${encodeURIComponent(state.selectedRun.run_id)}/stop`, {
      method: "POST",
      body: "{}"
    });
    message(`停止请求已受理：${response.run_id || state.selectedRun.run_id}`);
    await Promise.all([loadCurrent(), loadQueue(), loadRuns()]);
    await selectRun(state.selectedRun.run_id);
  }

  async function retryRun() {
    if (!state.selectedRun) throw new Error("请先选择执行记录");
    const previousRunId = state.selectedRun.run_id;
    const response = await api(`/runs/${encodeURIComponent(previousRunId)}/retry`, {
      method: "POST",
      headers: { "Idempotency-Key": `demo-retry-${previousRunId}-${Date.now()}` },
      body: "{}"
    });
    message(`重试已创建：${response.run_id}`);
    state.executionEvidence = null;
    await Promise.all([loadCurrent(), loadQueue(), loadRuns()]);
    await selectRun(response.run_id);
  }

  function safe(action) {
    return async (...args) => {
      try { await action(...args); }
      catch (error) { message(error.message || "操作失败", true); }
    };
  }

  function bind() {
    byId("scan-start").addEventListener("click", safe(startScan));
    byId("scan-refresh").addEventListener("click", safe(() => Promise.all([loadScanOptions(), loadLatestScan()])));
    byId("scripts-refresh").addEventListener("click", safe(loadScripts));
    byId("script-filter").addEventListener("submit", (event) => {
      event.preventDefault(); state.scriptPage = 1; safe(loadScripts)();
    });
    byId("current-refresh").addEventListener("click", safe(loadCurrent));
    byId("run-file").addEventListener("change", resetSelectedFile);
    byId("file-upload").addEventListener("click", safe(uploadFile));
    byId("run-start").addEventListener("click", safe(startRun));
    byId("queue-refresh").addEventListener("click", safe(loadQueue));
    byId("runs-refresh").addEventListener("click", safe(loadRuns));
    byId("run-prev").addEventListener("click", () => { state.runPage--; safe(loadRuns)(); });
    byId("run-next").addEventListener("click", () => { state.runPage++; safe(loadRuns)(); });
    byId("run-refresh").addEventListener("click", () => safe(() => selectRun(state.selectedRun?.run_id))());
    byId("run-log").addEventListener("click", safe(loadLogs));
    byId("run-retry").addEventListener("click", safe(retryRun));
    byId("run-stop").addEventListener("click", safe(stopRun));
  }

  bind();
  Promise.all([loadStatus(), loadScanOptions(), loadLatestScan(), loadScripts(), loadQueue()])
    .catch((error) => message(error.message || "初始化失败", true));
})();
