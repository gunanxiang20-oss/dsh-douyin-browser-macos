import { createRequire } from 'node:module';
import fs from 'node:fs';
import path from 'node:path';
import { homedir } from 'node:os';

// 独立软件直接使用 playwright-core；原插件的 CLI/MCP 安装保留兼容入口。
// 本地链接安装时真实路径在工作区，需要回到 DSH 配置目录里找 playwright-core。
const require = createRequire(import.meta.url);

function profileCandidates() {
  const dirs = [];
  if (process.env.DSH_PROFILE_DIR) dirs.push(process.env.DSH_PROFILE_DIR);
  try {
    const base = path.join(homedir(), '.dsh', 'profiles');
    for (const name of fs.readdirSync(base)) dirs.push(path.join(base, name));
  } catch { /* 没有 profile 目录时忽略。 */ }
  return dirs.filter(directory => { try { return fs.statSync(path.join(directory, 'node_modules', 'playwright-core')).isDirectory(); } catch { return false; } });
}

function loadPlaywright() {
  const attempts = [() => require('playwright-core')];
  for (const directory of profileCandidates()) attempts.push(() => createRequire(path.join(directory, 'package.json'))('playwright-core'));
  attempts.push(() => createRequire(require.resolve('@playwright/mcp/package.json'))('playwright-core'));
  let last;
  for (const attempt of attempts) {
    try { return attempt(); } catch (error) { last = error; }
  }
  throw last;
}

const playwright = loadPlaywright();
export const { chromium, webkit } = playwright;
