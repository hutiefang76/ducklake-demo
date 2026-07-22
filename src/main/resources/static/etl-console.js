(() => {
  "use strict";

  // 旧页面可能被浏览器缓存并继续请求 /etl-console.js。只负责跳回带版本号的
  // 新验收页，不复制当前 Bridge UI 逻辑。
  const target = new URL("etl-console.html?v=20260722-bridge-v1", window.location.href);
  if (window.location.href !== target.href) window.location.replace(target.href);
})();
