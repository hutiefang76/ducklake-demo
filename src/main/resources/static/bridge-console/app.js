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
    executionEvidence: null
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

  function supportType(level) {
    return {
      PYTHON_ONLY: "类型 1 · 原生 Python",
      PARAMETERIZED: "类型 2 · 自动参数",
      FULL: "类型 3 · 完整 ETL 契约"
    }[String(level || "").toUpperCase()] || `未知类型 · ${level || "—"}`;
  }

  function automaticParameters(entry) {
    if (String(entry?.support_level).toUpperCase() === "PYTHON_ONLY") {
      return { values: {}, missing: [], source: "类型 1 固定无参数，直接执行原始脚本" };
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
        transport: "类型 1：DolphinScheduler startParams 为空，直接执行原始 Python 脚本"
      };
    }
    return {
      startParams: Object.assign({ run_id: run.run_id }, parameters, { run_id: run.run_id }),
      transport: "类型 2/3：DolphinScheduler startParams 包含 run_id 和直接业务参数"
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
    const response = await fetch(`${apiRoot}${path}`, {
      credentials: "same-origin",
      headers: { "Content-Type": "application/json", ...(options.headers || {}) },
      ...options
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
    fact(host, "发现", scan.discovered_count ?? 0);
    fact(host, "接收", scan.accepted_count ?? 0);
    fact(host, "拒绝", scan.rejected_count ?? 0);
    fact(host, "Commit", scan.resolved_head_commit || scan.expected_head_commit || "—");
  }

  async function startScan() {
    const scan = await api("/scans", { method: "POST", body: "{}" });
    message(`扫描已受理：${scan.scan_id || "已提交"}`);
    await loadLatestScan();
  }

  function scriptQuery() {
    const form = new FormData(byId("script-filter"));
    const query = new URLSearchParams({ page: String(state.scriptPage), page_size: "25" });
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
    const rows = byId("script-rows");
    rows.replaceChildren();
    const scripts = items(response);
    if (!scripts.length) {
      const row = document.createElement("tr");
      const cell = text("td", "没有匹配的业务脚本", "empty");
      cell.colSpan = 5;
      row.append(cell);
      rows.append(row);
    }
    scripts.forEach((entry) => {
      const row = document.createElement("tr");
      const actions = document.createElement("td");
      actions.append(button("详情", () => selectScript(entry.script_id)));
      row.append(
        text("td", entry.script_name),
        text("td", entry.script_id),
        text("td", `${supportType(entry.support_level)} (${entry.support_level || "—"})`),
        text("td", entry.runnable ? "是" : "否"),
        actions
      );
      rows.append(row);
    });
    const total = Number(response.total ?? response.count ?? scripts.length);
    state.scriptPages = Number(response.total_pages || Math.max(1, Math.ceil(total / 25)));
    byId("script-page").textContent = `第 ${state.scriptPage} / ${state.scriptPages} 页，共 ${total} 个`;
    byId("script-prev").disabled = state.scriptPage <= 1;
    byId("script-next").disabled = state.scriptPage >= state.scriptPages;
  }

  async function selectScript(scriptId) {
    const detail = await api(`/scripts/${encodeURIComponent(scriptId)}`);
    const entry = detail.script || detail;
    state.selectedScript = entry;
    state.runPage = 1;
    byId("script-panel").classList.remove("hidden");
    byId("script-title").textContent = entry.script_name;
    byId("script-id").textContent = entry.script_id;
    const host = byId("script-facts");
    host.replaceChildren();
    fact(host, "源码", entry.source_path);
    fact(host, "格式", entry.source_format);
    fact(host, "契约", entry.contract_status);
    fact(host, "支持类型", supportType(entry.support_level));
    fact(host, "可执行", entry.runnable ? "是" : "否");
    const automatic = automaticParameters(entry);
    byId("run-parameters").textContent = pretty(automatic.values);
    byId("parameter-source").textContent = automatic.source;
    byId("run-start").disabled = !entry.runnable || automatic.missing.length > 0;
    state.executionEvidence = null;
    byId("execution-evidence").textContent = "尚未执行";
    byId("script-technical").textContent = pretty({
      contract: entry.contract,
      parameters: entry.parameters,
      inputs: entry.inputs,
      outputs: entry.outputs,
      scheduler: entry.scheduler,
      scan: detail.scan
    });
    await Promise.all([loadCurrent(), loadRuns()]);
    byId("script-panel").scrollIntoView({ behavior: "smooth", block: "start" });
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
    const requestBody = {
      script_id: state.selectedScript.script_id,
      parameters: automatic.values,
      file_ids: []
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
    const response = await api(`/runs?${runQuery()}`);
    const rows = byId("run-rows");
    rows.replaceChildren();
    const runs = items(response);
    if (!runs.length) {
      const row = document.createElement("tr");
      const cell = text("td", "暂无执行历史", "empty");
      cell.colSpan = 5;
      row.append(cell);
      rows.append(row);
    }
    runs.forEach((entry) => {
      const actions = document.createElement("td");
      actions.append(button("查看", () => selectRun(entry.run_id)));
      const stateCell = document.createElement("td");
      stateCell.append(badge(entry.state));
      const row = document.createElement("tr");
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

  async function selectRun(runId) {
    if (!runId) return;
    const [run, queue] = await Promise.all([
      api(`/runs/${encodeURIComponent(runId)}`),
      api(`/queue/${encodeURIComponent(runId)}`)
    ]);
    state.selectedRun = run;
    byId("run-panel").classList.remove("hidden");
    byId("run-title").textContent = run.run_id;
    byId("run-business").textContent = `${run.script_name || "—"} · ${run.script_id || "—"}`;
    const host = byId("run-facts");
    host.replaceChildren();
    fact(host, "状态", run.state);
    fact(host, "创建", run.created_at);
    fact(host, "开始", run.started_at);
    fact(host, "完成", run.finished_at);
    fact(host, "队列位置", queue.queue?.position ?? "—");
    const terminal = ["SUCCESS", "FAILED", "STOPPED", "CANCELLED"].includes(String(run.state).toUpperCase());
    byId("run-stop").disabled = terminal;
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
    byId("run-panel").scrollIntoView({ behavior: "smooth", block: "start" });
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
    await Promise.all([selectRun(state.selectedRun.run_id), loadCurrent(), loadQueue(), loadRuns()]);
  }

  function safe(action) {
    return async (...args) => {
      try { await action(...args); }
      catch (error) { message(error.message || "操作失败", true); }
    };
  }

  function bind() {
    byId("scan-start").addEventListener("click", safe(startScan));
    byId("scan-refresh").addEventListener("click", safe(loadLatestScan));
    byId("scripts-refresh").addEventListener("click", safe(loadScripts));
    byId("script-filter").addEventListener("submit", (event) => {
      event.preventDefault(); state.scriptPage = 1; safe(loadScripts)();
    });
    byId("script-prev").addEventListener("click", () => { state.scriptPage--; safe(loadScripts)(); });
    byId("script-next").addEventListener("click", () => { state.scriptPage++; safe(loadScripts)(); });
    byId("current-refresh").addEventListener("click", safe(loadCurrent));
    byId("run-start").addEventListener("click", safe(startRun));
    byId("queue-refresh").addEventListener("click", safe(loadQueue));
    byId("runs-refresh").addEventListener("click", safe(loadRuns));
    byId("run-prev").addEventListener("click", () => { state.runPage--; safe(loadRuns)(); });
    byId("run-next").addEventListener("click", () => { state.runPage++; safe(loadRuns)(); });
    byId("run-refresh").addEventListener("click", () => safe(() => selectRun(state.selectedRun?.run_id))());
    byId("run-log").addEventListener("click", safe(loadLogs));
    byId("run-stop").addEventListener("click", safe(stopRun));
  }

  bind();
  Promise.all([loadStatus(), loadLatestScan(), loadScripts(), loadQueue(), loadRuns()])
    .catch((error) => message(error.message || "初始化失败", true));
})();
